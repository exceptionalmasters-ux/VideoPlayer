const { test } = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const asset = name => fs.readFileSync(path.join(__dirname, '../app/src/main/assets', name), 'utf8');

test('media observer captures existing and dynamically inserted video once', () => {
  const messages = [];
  const handlers = {};
  let mutation;
  const video = {currentSrc: 'https://stream.vider.info/video/123/v.mp4?uid=example',
    readyState: 2, networkState: 1, paused: true, currentTime: 3.8,
    addEventListener: (name, callback) => { handlers[name] = callback; }};
  let videos = [];
  const window = { vpDiagnostics: {postMessage: value => messages.push(JSON.parse(value))} };
  const document = {querySelectorAll: () => videos, addEventListener() {}};
  const context = {window, document, MutationObserver: class {
    constructor(callback) { mutation = callback; } observe() {}
  }};
  vm.runInNewContext(asset('media-diagnostics.js'), context);
  assert.equal(messages.length, 0);
  videos = [video]; mutation(); mutation();
  assert.equal(messages.length, 1);
  assert.equal(messages[0].event, 'snapshot');
  assert.equal(messages[0].time, 3);
  handlers.playing();
  assert.equal(messages[1].event, 'playing');
  assert.equal(messages[1].source, video.currentSrc);
  vm.runInNewContext(asset('media-diagnostics.js'), context);
  assert.equal(messages.length, 2);
});

test('focus preserves the loaded frame and restores the full page', () => {
  const nodes = [];
  function element(tagName) {
    const attributes = new Set();
    const el = {tagName, children: [], parentElement: null,
      setAttribute: name => attributes.add(name), removeAttribute: name => attributes.delete(name),
      hasAttribute: name => attributes.has(name),
      appendChild(child) { this.children.push(child); child.parentElement = this; },
      remove() { if (this.parentElement) this.parentElement.children = this.parentElement.children.filter(n => n !== this); }};
    nodes.push(el); return el;
  }
  const body = element('BODY'); const head = element('HEAD'); const outer = element('DIV');
  const comments = element('DIV'); const player = element('DIV'); const frame = element('IFRAME');
  body.appendChild(outer); outer.appendChild(player); outer.appendChild(comments); player.appendChild(frame);
  player.querySelector = () => frame;
  const document = {body, head,
    getElementById: id => head.children.find(n => n.id === id),
    querySelector: () => player,
    querySelectorAll: () => nodes.filter(n => ['data-vp-hidden','data-vp-parent','data-vp-player'].some(k => n.hasAttribute(k))),
    createElement: element};
  const context = {document};
  assert.equal(vm.runInNewContext(asset('focus-player.js'), context), 'player');
  assert.equal(comments.hasAttribute('data-vp-hidden'), true);
  assert.equal(player.children[0], frame);
  assert.equal(frame.hasAttribute('data-vp-hidden'), false);
  assert.equal(vm.runInNewContext(asset('focus-player.js'), context), 'page');
  assert.equal(comments.hasAttribute('data-vp-hidden'), false);
  assert.equal(head.children.length, 0);
  assert.equal(player.children[0], frame);
});

test('focus does nothing when no player exists', () => {
  const document = {getElementById: () => null, querySelector: () => null};
  assert.equal(vm.runInNewContext(asset('focus-player.js'), {document}), 'missing');
});

function searchFixture(entries) {
  const anchors = entries.map(entry => ({
    textContent: entry.text || '',
    getAttribute(name) { return name === 'href' ? entry.href : (name === 'aria-label' ? entry.label : null); },
    querySelector(selector) {
      if (selector === 'img') return entry.image ? {src: entry.image} : null;
      return entry.heading ? {textContent: entry.heading} : null;
    }
  }));
  return JSON.parse(vm.runInNewContext(asset('search-results.js'), {
    document: {querySelectorAll: () => anchors},
    location: {href: 'https://www.google.com/search?q=movie'}, URL
  }));
}
test('search extracts movie cards from direct and Google redirect links, deduplicating domains', () => {
  const result = searchFixture([
    {href:'https://vider.info/vid/+fexample?tracking=private', heading:'Maverick', image:'https://img.vider.info/poster.png'},
    {href:'https://www.google.com/url?q=https%3A%2F%2Fvider.pl%2Fvid%2F%2Bfexample', heading:'Duplicate'},
    {href:'/url?url=https%3A%2F%2Fvider.pl%2Fvid%2Ftitle%2Bfother', heading:'Other movie'}
  ]);
  assert.equal(result.length, 2);
  assert.equal(result[0].url, 'https://vider.info/vid/+fexample');
  assert.equal(result[0].title, 'Maverick');
  assert.equal(result[0].thumbnail, 'https://img.vider.info/poster.png');
  assert.equal(result[1].url, 'https://vider.pl/vid/title+fother');
});
test('search rejects directories, lookalike domains, credentials and non-HTTPS targets', () => {
  assert.equal(searchFixture([
    {href:'https://vider.info.evil.test/vid/+fexample', heading:'Wrong host'},
    {href:'https://vider.pl/dir/+dexample', heading:'Directory'},
    {href:'https://user:secret@vider.pl/vid/+fexample', heading:'Credentials'},
    {href:'http://vider.info/vid/+fexample', heading:'HTTP'},
    {href:'javascript:alert(1)', heading:'Script'},
    {href:'https://example.org/', heading:'Other site'}
  ]).length, 0);
});
test('search does not invent posters and limits untrusted text and image data', () => {
  const result = searchFixture([
    {href:'https://vider.pl/vid/+fexample', heading:'A'.repeat(400), image:'data:image/png;base64,'+'X'.repeat(17000)},
    {href:'https://vider.pl/vid/+fother', text:' Other   film '}
  ]);
  assert.equal(result[0].title.length, 180);
  assert.equal(result[0].thumbnail, '');
  assert.equal(result[1].title, 'Other film');
  assert.equal(result[1].thumbnail, '');
});
