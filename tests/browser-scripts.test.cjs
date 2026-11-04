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
      hasAttribute: name => attributes.has(name), getAttribute: () => null,
      addEventListener(){}, removeEventListener(){}, contentWindow:{postMessage(){}},
      appendChild(child) { this.children.push(child); child.parentElement = this; },
      remove() { if (this.parentElement) this.parentElement.children = this.parentElement.children.filter(n => n !== this); }};
    nodes.push(el); return el;
  }
  const body = element('BODY'); const head = element('HEAD'); const outer = element('DIV');
  const comments = element('DIV'); const player = element('DIV'); const frame = element('IFRAME');
  body.appendChild(outer); outer.appendChild(player); outer.appendChild(comments); player.appendChild(frame);
  player.querySelector = () => frame;
  player.querySelectorAll = () => [frame];
  const document = {body, head,
    getElementById: id => head.children.find(n => n.id === id),
    querySelector: selector => selector.startsWith('meta') ? null : player,
    querySelectorAll: () => nodes.filter(n => ['data-vp-hidden','data-vp-parent','data-vp-player'].some(k => n.hasAttribute(k))),
    createElement: element};
  const context = {document,window:{},URL,location:{href:'https://vider.pl/vid/+fexample'},
    MutationObserver:class {observe(){} disconnect(){}}};
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
  const document = {getElementById: () => null, querySelector: () => null, querySelectorAll: () => []};
  assert.equal(vm.runInNewContext(asset('focus-player.js'), {document}), 'missing');
});

function searchFixture(entries, base = "https://vider.info/search/all/movie") {
  const anchors = entries.map(entry => ({
    textContent: entry.text || '',
    getAttribute(name) { return name === 'href' ? entry.href : (name === 'title' ? entry.title : (name === 'aria-label' ? entry.label : null)); },
    closest() { return {querySelector(selector) {
      if (selector === 'img') return entry.lazy ? {src:'/loader.gif',getAttribute:() => entry.lazy} : null;
      if (selector.startsWith('.thumb')) return entry.background ? {getAttribute:() => 'background:url("'+entry.background+'")'} : null;
      if (selector.startsWith('.file_icon')) return entry.videoIcon ? {} : null;
      return null;
    }}; },
    querySelector(selector) {
      if (selector === 'img') return entry.image ? {src: entry.image, getAttribute: name => name === 'data-src' ? entry.lazy : null} : null;
      return entry.heading ? {textContent: entry.heading} : null;
    }
  }));
  return JSON.parse(vm.runInNewContext(asset('search-results.js'), {
    document: {querySelector: () => null, querySelectorAll: () => anchors},
    location: {href: base}, URL
  }));
}
test('search extracts native movie cards from current and legacy Vider links, deduplicating domains', () => {
  const result = searchFixture([
    {href:'https://vider.info/vid/+fexample?tracking=private', heading:'Maverick', image:'https://img.vider.info/poster.png'},
    {href:'https://vider.pl/vid/+fexample', heading:'Duplicate'},
    {href:'https://vider.pl/some-profile/title+fother', heading:'Other movie'}
  ]);
  assert.equal(result.length, 2);
  assert.equal(result[0].url, 'https://vider.info/vid/+fexample');
  assert.equal(result[0].title, 'Maverick');
  assert.equal(result[0].thumbnail, 'https://img.vider.info/poster.png');
  assert.equal(result[1].url, 'https://vider.pl/vid/+fother');
});
test('search rejects directories, lookalike domains, credentials and unsafe schemes', () => {
  assert.equal(searchFixture([
    {href:'https://vider.info.evil.test/vid/+fexample', heading:'Wrong host'},
    {href:'https://vider.pl/dir/+dexample', heading:'Directory'},
    {href:'https://user:secret@vider.pl/vid/+fexample', heading:'Credentials'},
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

test('search merges image-only links with their title and upgrades known HTTP links', () => {
  const result = searchFixture([
    {href:'http://vider.info/a-profile/movie-title+fexample', image:'https://img.vider.info/i/example.png'},
    {href:'https://vider.info/vid/+fexample', heading:'Correct movie title'}
  ]);
  assert.equal(result.length, 1);
  assert.equal(result[0].url, 'https://vider.info/vid/+fexample');
  assert.equal(result[0].title, 'Correct movie title');
  assert.equal(result[0].thumbnail, 'https://img.vider.info/i/example.png');
});
function searchState(inputs, consent = []) {
  const document = {querySelectorAll(selector) {
    if (selector === 'input') return inputs;
    if (selector.startsWith('#qc-')) return consent;
    return [];
  }};
  return JSON.parse(vm.runInNewContext(asset('search-state.js'), {
    document, getComputedStyle: () => ({visibility: 'visible'})
  }));
}
test('search requests in-app verification only for visible challenges or consent', () => {
  const captcha = {name:'captcha_code', id:'', getAttribute:() => '', getBoundingClientRect:() => ({height:40})};
  assert.equal(searchState([captcha]).stage, 'verification');
  const hiddenLogin = {type:'password', name:'', id:'password', getAttribute:() => '', getBoundingClientRect:() => ({height:0})};
  assert.equal(searchState([hiddenLogin]).stage, 'results');
  const banner = {getBoundingClientRect:() => ({height:100})};
  assert.equal(searchState([], [banner]).stage, 'consent');
  assert.equal(searchState([]).anchors, 0);
});

test('search resolves relative Vider movie links into native cards', () => {
  const result = searchFixture([{href:'/vid/+fexample', heading:'A movie'}]);
  assert.equal(result.length, 1);
  assert.equal(result[0].url, 'https://vider.info/vid/+fexample');
});

function editableInput(extra = {}) {
  return {tagName:'INPUT',type:'text',name:'captcha_code',id:'code',
    getBoundingClientRect:() => ({height:30}),getAttribute:() => '',
    focus(){this.focused=true;},scrollIntoView(){this.scrolled=true;},...extra};
}
test('verification focus selects the visible code input without changing its value', () => {
  const hidden = editableInput({getBoundingClientRect:() => ({height:0})});
  const input = editableInput({value:'unchanged'});
  const document = {activeElement:null,querySelectorAll:() => [hidden,input]};
  assert.equal(vm.runInNewContext(asset('verification-focus.js'), {document}), true);
  assert.equal(input.focused,true); assert.equal(input.scrolled,true);
  assert.equal(input.value,'unchanged'); assert.equal(hidden.focused,undefined);
});
test('verification focus preserves the field the user selected and ignores consent-only pages', () => {
  const selected = editableInput({type:'email',name:'email'});
  const captcha = editableInput();
  assert.equal(vm.runInNewContext(asset('verification-focus.js'), {document:{activeElement:selected,querySelectorAll:() => [captcha]}}),true);
  assert.equal(selected.focused,true); assert.equal(captcha.focused,undefined);
  assert.equal(vm.runInNewContext(asset('verification-focus.js'), {document:{activeElement:null,querySelectorAll:() => []}}),false);
});

test('manual verification copies only user-entered text and dispatches input/change without submitting', () => {
  const events=[];
  const input=editableInput({value:'',dispatchEvent(event){events.push(event.type);},submit(){throw Error('Must not submit');}});
  const code='a"b\\c';
  const script=asset('verification-fill.js').replace('__VP_MANUAL_CODE__',JSON.stringify(code));
  assert.equal(vm.runInNewContext(script,{document:{querySelectorAll:()=>[input]},Event:class {constructor(type){this.type=type;}}}),true);
  assert.equal(input.value,code);
  assert.deepEqual(events,['input','change']);
});
test('manual verification does not write into hidden or unrelated inputs', () => {
  const unrelated=editableInput({name:'email',id:'email',value:'unchanged'});
  const hidden=editableInput({value:'unchanged',getBoundingClientRect:()=>({height:0})});
  assert.equal(vm.runInNewContext(asset('verification-fill.js').replace('__VP_MANUAL_CODE__','"test"'),
    {document:{querySelectorAll:()=>[unrelated,hidden]},Event:class{}}),false);
  assert.equal(unrelated.value,'unchanged');assert.equal(hidden.value,'unchanged');
});


test('Freedisc cards use observed file URLs and lazy posters; ignore audio, documents and directories', () => {
  const result = searchFixture([
    {href:'/margot223,f-19724546,ronin-1998-mp4', title:'Ronin (1998).mp4', lazy:'https://img.freedisc.pl/photo/19724546/7/2/ronin.png'},
    {href:'https://www.freedisc.pl/margot223,f-19724546,ronin-1998-mp4', title:'Duplicate.mp4'},
    {href:'/someone,f-22,audio-mp3', title:'Music.mp3', lazy:'https://img.freedisc.pl/photo/22/6/2/audio.png'},
    {href:'/someone,f-23,document-pdf', title:'Document.pdf'},
    {href:'/someone,d-24,folder', title:'Folder'},
    {href:'https://freedisc.pl.evil.test/user,f-25,movie-mp4', title:'Wrong.mp4'},
    {href:'https://user:pass@freedisc.pl/user,f-26,movie-mp4', title:'Credentials.mp4'},
    {href:'https://freedisc.pl:444/user,f-27,movie-mp4', title:'Port.mp4'}
  ], 'https://freedisc.pl/search/all/Ronin');
  assert.equal(result.length, 1);
  assert.equal(result[0].url, 'https://freedisc.pl/margot223,f-19724546,ronin-1998-mp4');
  assert.equal(result[0].title, 'Ronin (1998).mp4');
  assert.equal(result[0].thumbnail, 'https://img.freedisc.pl/photo/19724546/7/2/ronin.png');
});
test('Freedisc home-style cards read CSS posters and file type icons without inventing URLs', () => {
  const result = searchFixture([
    {href:'/user,f-30,a-title', title:'A title', background:'https://img.freedisc.pl/photo/30/7/2/a.png'},
    {href:'/user,f-31,another', title:'Another', videoIcon:true}
  ], 'https://freedisc.pl/search/all/title');
  assert.equal(result.length, 2);
  assert.equal(result[0].thumbnail, 'https://img.freedisc.pl/photo/30/7/2/a.png');
  assert.equal(result[1].thumbnail, '');
});

test('focus selects an existing Freedisc embed frame and preserves it on restore', () => {
  function element(tagName) {
    const attrs=new Set();
    return {tagName,children:[],parentElement:null,querySelector:()=>null,
      setAttribute:k=>attrs.add(k),removeAttribute:k=>attrs.delete(k),hasAttribute:k=>attrs.has(k),getAttribute:()=>null,
      addEventListener(){},removeEventListener(){},contentWindow:{postMessage(){}},
      appendChild(n){this.children.push(n);n.parentElement=this;},
      remove(){this.parentElement.children=this.parentElement.children.filter(n=>n!==this);}};
  }
  const body=element('BODY'), head=element('HEAD'), frame=element('IFRAME'), comments=element('DIV');
  frame.src='https://freedisc.pl/embed/video/19724546/ronin';
  body.appendChild(frame); body.appendChild(comments);
  const nodes=[body,head,frame,comments];
  const document={body,head,createElement:element,querySelector:()=>null,
    getElementById:id=>head.children.find(n=>n.id===id),
    querySelectorAll:s=>s==='iframe[src]'?[frame]:nodes};
  const context={document,window:{},URL,location:{href:'https://freedisc.pl/user,f-19724546,ronin-mp4'},
    MutationObserver:class {observe(){} disconnect(){}}};
  assert.equal(vm.runInNewContext(asset('focus-player.js'),context),'player');
  assert.equal(frame.hasAttribute('data-vp-player'),true);
  assert.equal(comments.hasAttribute('data-vp-hidden'),true);
  assert.equal(vm.runInNewContext(asset('focus-player.js'),context),'page');
  assert.equal(frame.src,'https://freedisc.pl/embed/video/19724546/ronin');
  assert.equal(body.children[0],frame);
  assert.equal(comments.hasAttribute('data-vp-hidden'),false);
});


function routeFreedisc(target, extra = {}) {
  const calls=[];
  const window={App:{Instance:{Search:{}}},Router:{navigate:(path,trigger)=>calls.push({path,trigger})},...extra};
  const script=asset('freedisc-search.js').replace('__VP_SEARCH_URL__',JSON.stringify(target));
  const result=vm.runInNewContext(script,{window,URL,location:{href:'https://freedisc.pl/'}});
  return {result,calls};
}
test('Freedisc search uses the site router after it initializes, with encoded title and trigger flag',()=>{
  const value=routeFreedisc('https://freedisc.pl/search/all/Ronin%201998%2FPL');
  assert.equal(value.result,'routed');
  assert.equal(value.calls.length,1);
  assert.equal(value.calls[0].path,'/search/all/Ronin%201998%2FPL');
  assert.equal(value.calls[0].trigger,true);
  assert.equal(routeFreedisc('https://www.freedisc.pl/search/all/Ronin').result,'routed');
  assert.equal(routeFreedisc('https://freedisc.pl/search/all/Ronin',{Router:null}).result,'waiting');
  assert.equal(routeFreedisc('https://freedisc.pl/search/all/Ronin',{App:{}}).result,'waiting');
});
test('Freedisc router never routes unsafe destinations or arbitrary paths',()=>{
  for(const target of ['http://freedisc.pl/search/all/Ronin','https://evil.test/search/all/Ronin',
    'https://freedisc.pl:444/search/all/Ronin','https://user:pass@freedisc.pl/search/all/Ronin',
    'https://freedisc.pl/profile','https://freedisc.pl/search/all/Ronin?token=secret',
    'https://freedisc.pl/search/all/Ronin#private']) {
    const value=routeFreedisc(target);
    assert.equal(value.result,'invalid',target);
    assert.equal(value.calls.length,0);
  }
});


test('frame sizing ignores messages from unrelated origins or windows',()=>{
  const handlers={}; const parent={};
  const window={parent,addEventListener:(name,callback)=>{handlers[name]=callback;}};
  const context={window,document:{},MutationObserver:class{constructor(){throw Error('Unexpected DOM mutation');}}};
  vm.runInNewContext(asset('frame-fit.js'),context);
  for(const event of [
    {source:parent,origin:'https://evil.test',data:{type:'vp-frame-fit',enabled:true}},
    {source:{},origin:'https://freedisc.pl',data:{type:'vp-frame-fit',enabled:true}},
    {source:parent,origin:'https://freedisc.pl',data:{type:'other',enabled:true}},
    {source:parent,origin:'https://freedisc.pl',data:{type:'vp-frame-fit',enabled:'true'}}
  ]) assert.doesNotThrow(()=>handlers.message(event));
});


test('TV commands use the original player API and do not act on unknown commands',()=>{
  const calls=[];let state='playing';
  const player={getState:()=>state,getPosition:()=>6,play:value=>calls.push(['play',value]),
    pause:value=>calls.push(['pause',value]),seek:value=>calls.push(['seek',value])};
  const window={jwplayer:()=>player,parent:{},addEventListener(){}};
  const document={head:null,addEventListener(){}};
  vm.runInNewContext(asset('tv-media.js'),{window,document});
  assert.equal(window.__vpTvCommand('toggle'),true);state='paused';window.__vpTvCommand('toggle');
  window.__vpTvCommand('back');window.__vpTvCommand('forward');
  assert.deepEqual(calls,[['pause',true],['play',true],['seek',0],['seek',16]]);
  assert.equal(window.__vpTvCommand('navigate'),false);
});
test('TV commands are sent only to known HTTPS embed frames',()=>{
  const sent=[];
  const frames=['https://freedisc.pl/embed/video/12','https://ads.example/embed/video/12','http://freedisc.pl/embed/video/12',
    'https://user:secret@freedisc.pl/embed/video/12','https://freedisc.pl/profile'].map(src=>({src,contentWindow:{postMessage:(data,origin)=>sent.push({data,origin})}}));
  const window={parent:{},addEventListener(){}};
  const document={head:null,addEventListener(){},querySelectorAll:selector=>selector==='video'?[]:frames};
  vm.runInNewContext(asset('tv-media.js'),{window,document,URL,location:{href:'https://freedisc.pl/user,f-12,movie-mp4'}});
  assert.equal(window.__vpTvCommand('pause'),true);
  assert.equal(sent.length,1);assert.equal(sent[0].origin,'https://freedisc.pl');assert.equal(sent[0].data.action,'pause');
});
test('remote verification submits only a visible confirmation button next to user-filled code',()=>{
  let clicks=0;
  const button={textContent:'Wchodzę',getBoundingClientRect:()=>({height:40}),click:()=>clicks++};
  const form={tagName:'FORM',querySelectorAll:()=>[button],parentElement:null};
  const input=editableInput({value:'manuallytyped',parentElement:form});
  assert.equal(vm.runInNewContext(asset('verification-submit.js'),{document:{querySelectorAll:()=>[input]}}),true);
  assert.equal(clicks,1);
  input.value='';
  assert.equal(vm.runInNewContext(asset('verification-submit.js'),{document:{querySelectorAll:()=>[input]}}),false);
  assert.equal(clicks,1);
});


test('presentation detects a later challenge in branches hidden by film focus and restores the mask',()=>{
  let hidden=true;
  const branch={removeAttribute:()=>{hidden=false;},setAttribute:()=>{hidden=true;}};
  const input={name:'captcha_code',id:'code',getAttribute:()=>'',getBoundingClientRect:()=>({height:hidden?0:40})};
  const document={querySelectorAll(selector){if(selector==='[data-vp-hidden]')return [branch];if(selector==='input')return [input];return [];}};
  const result=JSON.parse(vm.runInNewContext(asset('search-state.js'),{document,getComputedStyle:()=>({visibility:'visible'})}));
  assert.equal(result.stage,'verification');assert.equal(hidden,true);
});
