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



test('offscreen password templates do not block movie presentation', () => {
  const password={type:'password',id:'login-password',getAttribute:()=>'',getBoundingClientRect:()=>({height:30,top:-2000,bottom:-1970,left:0,right:200})};
  const document={querySelectorAll: selector=>selector==='input'?[password]:[]};
  const context={document,window:{innerHeight:600,innerWidth:1000},getComputedStyle:()=>({visibility:'visible'})};
  assert.equal(JSON.parse(vm.runInNewContext(asset('search-state.js'),context)).stage,'results');
  password.getBoundingClientRect=()=>({height:30,top:20,bottom:50,left:0,right:200});
  assert.equal(JSON.parse(vm.runInNewContext(asset('search-state.js'),context)).stage,'login');
});

function progressFixture(position=0,duration=7200){
  const messages=[],events={},seeks=[];let now=10000;
  const player={getState:()=> 'playing',on:(name,fn)=>{events[name]=fn;},seek:value=>seeks.push(value)};
  const window={vpPlayback:{postMessage:raw=>messages.push(JSON.parse(raw))},jwplayer:()=>player,addEventListener(){}};
  window.parent=window;
  const context={window,document:{querySelectorAll:()=>[],addEventListener(){}},location:{href:'https://vider.pl/embed/video/test'},URL,
    setInterval(){},Date:{now:()=>now}};
  vm.runInNewContext(asset('playback-progress.js'),context);
  window.vpPlayback.onmessage({data:JSON.stringify({token:'test-token',position,duration})});
  return {messages,events,seeks,window,tick:()=>{now+=5000;},time:(p,d=duration)=>events.time({position:p,duration:d})};
}
test('resume waits for content time, seeks once and never saves startup zero',()=>{
  const f=progressFixture(1800);f.time(0);assert.deepEqual(f.seeks,[1800]);assert.equal(f.messages.length,1);
  f.time(1801);assert.equal(f.messages.at(-1).position,1801);assert.equal(f.messages.at(-1).token,'test-token');
  f.time(1802);assert.equal(f.seeks.length,1);assert.equal(f.messages.length,2);
  f.tick();f.time(1806);assert.equal(f.messages.at(-1).position,1806);
});
test('advertising time and pauses cannot replace film checkpoint or trigger resume',()=>{
  const f=progressFixture(300);f.events.adStarted();f.time(4,30);f.events.pause();assert.equal(f.messages.length,1);assert.equal(f.seeks.length,0);
  f.events.adComplete();f.time(0);assert.deepEqual(f.seeks,[300]);f.time(301);f.events.adStarted();f.time(5,30);f.events.pause();assert.equal(f.messages.at(-1).position,301);
});
test('failed resume retries are bounded and preserve the stored timestamp',()=>{
  const f=progressFixture(800);for(let i=0;i<10;i++){f.time(0);f.tick();}assert.equal(f.seeks.length,3);assert.equal(f.messages.length,1);
});
test('pause and completion flush content immediately, changed editions start fresh',()=>{
  const f=progressFixture();f.time(100);f.time(101);f.events.pause();assert.equal(f.messages.at(-1).position,101);
  f.events.complete();assert.equal(f.messages.at(-1).ended,true);assert.equal(f.messages.at(-1).position,7200);
  const other=progressFixture(600);other.time(1,3600);assert.equal(other.seeks.length,0);assert.equal(other.messages.at(-1).duration,3600);
});
test('progress rejects live and invalid times and is idempotent without a native bridge',()=>{
  const f=progressFixture();f.time(20,Infinity);f.time(-1);f.time(90000);assert.equal(f.messages.length,1);
  assert.doesNotThrow(()=>vm.runInNewContext(asset('playback-progress.js'),{window:{}}));
});

function playTapFixture(){
  const messages=[],listeners={};let covered=false;
  const control={getAttribute:()=> 'Play',getBoundingClientRect:()=>({left:20,top:30,width:20,height:20}),contains:()=>false};
  const window={innerWidth:100,innerHeight:100,vpTvTouch:{postMessage:v=>messages.push(JSON.parse(v))},addEventListener:(n,f)=>listeners[n]=f};window.parent=window;
  const document={querySelectorAll:s=>s==='iframe[src]'?[]:[control],elementFromPoint:()=>covered?{}:control};
  const context={window,document,URL,location:{href:'https://vider.info/vid/+ftest'},getComputedStyle:()=>({display:'block',visibility:'visible'})};
  vm.runInNewContext(asset('tv-play-tap.js'),context);
  return {messages,listeners,control,window,document,cover:()=>{covered=true;}};
}
test('TV Play requests one scoped native tap at the visible original control, never an overlay',()=>{
  const f=playTapFixture();assert.equal(f.window.__vpTvTapPlay(''),false);
  assert.equal(f.window.__vpTvTapPlay('user-ok-nonce'),true);assert.equal(f.messages[0].id,'user-ok-nonce');assert.equal(f.messages[0].x,0.3);assert.equal(f.messages[0].y,0.4);
  f.cover();assert.equal(f.window.__vpTvTapPlay('another-nonce'),false);assert.equal(f.messages.length,1);
  f.control.getAttribute=()=> 'Pause';assert.equal(f.window.__vpTvTapPlay('pause-is-not-play'),false);
});
test('TV iframe tap coordinates account for borders and scaling, reject unknown frames and occlusion',()=>{
  const f=playTapFixture();f.window.innerWidth=1000;f.window.innerHeight=1000;
  const frame={src:'https://vider.pl/embed/video/test',contentWindow:{},offsetWidth:200,offsetHeight:100,clientLeft:2,clientTop:2,clientWidth:196,clientHeight:96,
    getBoundingClientRect:()=>({left:100,top:150,width:400,height:200})};
  f.document.querySelectorAll=()=>[frame];f.document.elementFromPoint=()=>frame;
  const event={origin:'https://vider.pl',source:frame.contentWindow,data:{type:'vp-tv-play-tap',id:'nonce',x:0.5,y:0.25}};
  f.listeners.message(event);assert.equal(f.messages.length,1);assert.equal(f.messages[0].x,0.3);assert.equal(f.messages[0].y,0.202);
  f.listeners.message({...event,origin:'https://ads.example'});f.listeners.message({...event,source:{}});
  f.document.elementFromPoint=()=>({});f.listeners.message(event);assert.equal(f.messages.length,1);
});
test('TV Play relay uses the validated parent origin and the API remains the pause path',()=>{
  const f=playTapFixture(),relayed=[];f.window.parent={postMessage:(data,origin)=>relayed.push({data,origin})};
  assert.equal(f.window.__vpTvTapPlay('nonce'),false);f.window.__vpTvTouchParent('https://evil.test');assert.equal(f.window.__vpTvTapPlay('nonce'),false);
  f.window.__vpTvTouchParent('https://vider.pl');assert.equal(f.window.__vpTvTapPlay('nonce'),true);assert.equal(relayed[0].origin,'https://vider.pl');assert.equal(f.messages.length,0);
  const calls=[];let state='paused';
  const player={getState:()=>state,play:()=>calls.push('api-play'),pause:()=>calls.push('pause')};
  const window={jwplayer:()=>player,__vpTvTapPlay:id=>{calls.push(id);return true;},parent:{},addEventListener(){}};
  vm.runInNewContext(asset('tv-media.js'),{window,document:{head:null,addEventListener(){}}});
  window.__vpTvCommand('toggle','remote-ok');state='playing';window.__vpTvCommand('toggle','next-ok');
  assert.deepEqual(calls,['remote-ok','pause']);
});
test('TV toggle follows content pause state after a resume seek even when JW state is stale',()=>{
  const calls=[];const video={currentSrc:'https://stream.vider.info/video/123/v.mp4',paused:true};
  const player={getState:()=> 'PLAYING',play:()=>calls.push('api-play'),pause:()=>calls.push('pause')};
  const window={jwplayer:()=>player,__vpTvTapPlay:id=>{calls.push(id);return true;},parent:{},addEventListener(){}};
  vm.runInNewContext(asset('tv-media.js'),{window,document:{head:null,querySelector:()=>video,addEventListener(){}}});
  window.__vpTvCommand('toggle','resume-ok');video.paused=false;window.__vpTvCommand('toggle','pause-ok');
  assert.deepEqual(calls,['resume-ok','pause']);
});

test('dedicated Play key stays playing instead of toggling the original control to pause',()=>{
  const calls=[];const player={getState:()=> 'playing',play:()=>calls.push('play')};
  const window={jwplayer:()=>player,__vpTvTapPlay:()=>{calls.push('tap');return true;},parent:{},addEventListener(){}};
  vm.runInNewContext(asset('tv-media.js'),{window,document:{head:null,addEventListener(){}}});
  window.__vpTvCommand('play','nonce');assert.deepEqual(calls,['play']);
});

function outcomeFixture({text='',spinner=false,links=[],scoped=true}={}){
  const area={innerText:text,getBoundingClientRect:()=>({height:30}),querySelectorAll:s=>s==='a[href]'?links:s.includes('spinner')&&spinner?[{getBoundingClientRect:()=>({height:15})}]:[]};
  const body={innerText:'',querySelectorAll:()=>[]};
  return JSON.parse(vm.runInNewContext(asset('search-outcome.js'),{document:{body,querySelector:s=>scoped?area:null},URL,location:{href:'https://freedisc.pl/search/all/Serial%20S01E02'},getComputedStyle:()=>({visibility:'visible'})}));
}
test('only a rendered empty-result message confirms an empty episode search',()=>{
  assert.equal(outcomeFixture({text:'Brak wyników wyszukiwania dla frazy: Serial S01E02'}).empty,true);
  const loading=outcomeFixture({text:'Proszę czekać, szukam...',spinner:true});assert.equal(loading.loading,true);assert.equal(loading.empty,false);
  assert.equal(outcomeFixture().empty,false);assert.equal(outcomeFixture({scoped:false}).empty,false);
});
test('search completion counts only provider file links and does not turn a spinner into an empty result',()=>{
  const links=['/vid/+fepisode','https://freedisc.pl/u,f-123,serial-s01e02-mp4','https://ads.example/u,f-1','https://user:secret@freedisc.pl/u,f-2'].map(href=>({getAttribute:()=>href}));
  const result=outcomeFixture({links});assert.equal(result.links,2);assert.equal(result.loading,false);assert.equal(result.scoped,true);
});
test('replaced players cannot emit a false content end or advertisement state for next-episode suggestions',()=>{
  let discover;const messages=[],first={},second={};let current;
  function player(events){return {getState:()=> 'playing',on:(name,fn)=>events[name]=fn,seek(){}};}
  current=player(first);
  const window={vpPlayback:{postMessage:raw=>messages.push(JSON.parse(raw))},jwplayer:()=>current,addEventListener(){}};window.parent=window;
  vm.runInNewContext(asset('playback-progress.js'),{window,document:{querySelectorAll:()=>[],addEventListener(){}},location:{href:'https://vider.info/embed/video/test'},setInterval:fn=>{discover=fn;},Date:{now:()=>10000}});
  window.vpPlayback.onmessage({data:JSON.stringify({token:'old',position:0,duration:100})});first.time({position:70,duration:100});
  current=player(second);discover();window.vpPlayback.onmessage({data:JSON.stringify({token:'new',position:0,duration:100})});second.time({position:71,duration:100});
  first.complete();first.adStarted();second.pause();
  assert.equal(messages.at(-1).token,'new');assert.equal(messages.at(-1).ended,false);assert.equal(messages.at(-1).position,71);
  second.complete();assert.equal(messages.at(-1).ended,true);
});

test('autostart retries readiness, suspends in background, confirms playback and never undoes pause',()=>{
  function fixture(){
    const messages=[],handlers={};let tick,cleared=0,state='idle';
    const document={hidden:false,querySelector:()=>null,addEventListener(){}};
    const player={on:(event,fn)=>handlers[event]=fn,getState:()=>state};
    const window={};vm.runInNewContext(asset('original-autostart.js'),{window,document,WeakSet,setInterval:fn=>(tick=fn,1),clearInterval:()=>cleared++});
    window.__vpStartOriginalPlayer(player,event=>messages.push(event));
    return {messages,handlers,document,tick:()=>tick(),state:v=>state=v,cleared:()=>cleared,window,player};
  }
  const f=fixture();assert.deepEqual(f.messages,['autoplay-request']);f.tick();assert.equal(f.messages.length,2);
  f.document.hidden=true;for(let i=0;i<40;i++)f.tick();assert.equal(f.messages.length,2);
  f.document.hidden=false;f.state('playing');f.tick();assert.equal(f.messages.at(-1),'autoplay-started');const n=f.messages.length;f.state('paused');f.tick();assert.equal(f.messages.length,n);assert.equal(f.cleared(),1);
  const p=fixture();p.window.__vpCancelAutoStart();p.tick();assert.deepEqual(p.messages,['autoplay-request','autoplay-cancelled']);
  const limit=fixture();for(let i=0;i<40;i++)limit.tick();assert.equal(limit.messages.filter(x=>x==='autoplay-request').length,30);assert.equal(limit.messages.at(-1),'autoplay-expired');
});


test('ad diagnostics observes coverage and ad events without changing the page or exposing text or addresses',()=>{
  const messages=[],handlers={};let tick,covered=true;
  const video={currentSrc:'https://private.example/movie?token=secret',readyState:2,networkState:1,paused:false,getBoundingClientRect:()=>({left:0,top:0,width:640,height:360})};
  const node={parentElement:null,getBoundingClientRect:()=>({width:640,height:360}),contains:hit=>hit===node,textContent:'private advertisement'};
  const document={hidden:false,querySelector:()=>video,querySelectorAll:selector=>selector==='.reklama-content-1'?[node]:[],elementFromPoint:()=>covered?node:video,addEventListener(){}};
  const window={vpDiagnostics:{postMessage:s=>messages.push(JSON.parse(s))},jwplayer:()=>({on:(n,f)=>handlers[n]=f,getState:()=> 'playing'})};
  const context={window,document,Map,WeakSet,getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'}),setInterval:fn=>tick=fn};
  // Keep a stable player so repeated samples do not register it twice.
  const player=window.jwplayer();window.jwplayer=()=>player;
  vm.runInNewContext(asset('ad-diagnostics.js'),context);
  const panel=messages.find(x=>x.kind==='freedisc-panel-1');assert.equal(panel.visible,true);assert.equal(panel.coversVideo,true);assert.equal(panel.sourceAssigned,true);
  const n=messages.length;tick();assert.equal(messages.length,n);covered=false;tick();assert.equal(messages.at(-1).coversVideo,false);
  handlers.adError({code:123,message:'secret message'});assert.equal(messages.at(-1).errorCode,123);assert.equal(messages.at(-1).signal,'adError');
  const output=JSON.stringify(messages);assert.equal(/secret|private|https:/.test(output),false);assert.equal(node.textContent,'private advertisement');
});

function googleFixture(entries,source='FREEDISC',options={}){
  const url=new URL(options.url||'https://www.google.com/search?q=fixture');
  const anchors=entries.map(e=>({textContent:e.text||'',getAttribute:n=>n==='href'?e.href:n==='title'?e.title||null:null,querySelector:s=>s==='h3,[role="heading"]'&&e.heading?{textContent:e.heading}:s==='img'&&e.image?{currentSrc:e.image,src:e.image,getAttribute:()=>null}:null}));
  const visible={getBoundingClientRect:()=>({width:100,height:100})};
  const document={querySelector:s=>s==='#topstuff'&&options.empty?{textContent:options.empty}:null,querySelectorAll:s=>s==='a[href]'?anchors:options.captcha?[visible]:[]};
  return JSON.parse(vm.runInNewContext(asset('google-results.js').replace('__VP_SOURCE__',JSON.stringify(source)),{document,location:url,URL,Map,getComputedStyle:()=>({display:'block',visibility:'visible'})}));
}
test('Google fallback unwraps redirects and accepts only movie links from the selected exact host',()=>{
  const result=googleFixture([
    {href:'https://freedisc.pl/embed/video/8307530?match=1',heading:'Ronin 1998'},
    {href:'/url?q='+encodeURIComponent('https://www.freedisc.pl/u,f-8307530,ronin-1998-mp4'),heading:'Duplicate'},
    {href:'https://freedisc.pl.evil.test/u,f-11,film-mp4',heading:'Wrong'},
    {href:'https://freedisc.pl@evil.test/u,f-12,film-mp4',heading:'Wrong'},
    {href:'https://freedisc.pl/u,d-13,folder',heading:'Folder'},
    {href:'https://vider.info/vid/+fother',heading:'Wrong provider'},
    {href:'/url?url='+encodeURIComponent('https://freedisc.pl/u,f-14,film-mp4?token=secret'),heading:'Another movie',image:'https://evil.test/poster.png'},
    {href:'https://freedisc.pl/u,f-15,music-mp3',heading:'Music'}
  ]);
  assert.equal(result.stage,'complete');assert.equal(result.rows.length,2);assert.equal(result.rows[0].url,'https://freedisc.pl/embed/video/8307530?match=1');assert.equal(result.rows[1].url,'https://freedisc.pl/u,f-14,film-mp4');assert.equal(result.rows[1].thumbnail,'');
});
test('Google Vider fallback canonicalizes known domains and deduplicates movie IDs',()=>{
  const result=googleFixture([{href:'https://vider.pl/user/ronin+ftest',heading:'Ronin'},{href:'https://vider.info/vid/+ftest',heading:'Duplicate'},{href:'https://vider.info/embed/video/next',heading:'Next'},{href:'https://vider.pl/dir/folder+ffake',heading:'Folder'},{href:'https://freedisc.pl/u,f-1,film-mp4',heading:'Other source'}],'VIDER');
  assert.equal(result.rows.length,2);assert.equal(result.rows[0].url,'https://vider.pl/vid/+ftest');assert.equal(result.rows[1].url,'https://vider.info/vid/+fnext');
});
test('Google challenges wait for manual access and only explicit empty results are completed',()=>{
  assert.equal(googleFixture([], 'FREEDISC',{url:'https://www.google.com/sorry/index'}).stage,'access');
  assert.equal(googleFixture([], 'FREEDISC',{url:'https://consent.google.com/m'}).stage,'access');
  assert.equal(googleFixture([], 'FREEDISC',{captcha:true}).stage,'access');
  assert.equal(googleFixture([]).stage,'pending');assert.equal(googleFixture([], 'FREEDISC',{empty:'Nie znaleziono wyników'}).stage,'complete');
});

test('native media messages address the authenticated player frame and cancel automatic retries',()=>{
  const f=progressFixture(0),calls=[];
  f.window.__vpTvCommand=(action,id)=>{calls.push([action,id]);return true;};f.window.__vpCancelAutoStart=()=>calls.push('cancel');f.window.__vpTvTouchParent=origin=>calls.push(origin);
  f.window.vpPlayback.onmessage({data:JSON.stringify({type:'media-command',token:'wrong',action:'toggle',requestId:'x',manual:true})});assert.equal(calls.length,0);
  f.window.vpPlayback.onmessage({data:JSON.stringify({type:'media-command',token:'test-token',action:'toggle',requestId:'ok',parentOrigin:'https://vider.pl',manual:true})});
  assert.deepEqual(calls,['cancel','https://vider.pl',['toggle','ok']]);assert.equal(f.messages.at(-1).event,'media-command-result');assert.equal(f.messages.at(-1).handled,true);
});
test('Google scrolling reaches an inner cookie panel without mutating a form',()=>{
  const changes=[];const inner={clientHeight:200,scrollHeight:1200,scrollTop:0,parentElement:null,getBoundingClientRect:()=>({left:0,right:600,top:0,bottom:200}),scrollBy:(x,y)=>changes.push(y)};
  const context={window:{innerHeight:400,innerWidth:600,scrollBy:()=>changes.push('outer')},document:{elementFromPoint:()=>inner,querySelectorAll:()=>[inner]},getComputedStyle:()=>({overflowY:'auto',display:'block',visibility:'visible'})};
  assert.equal(vm.runInNewContext(asset('google-scroll.js').replace('__VP_DIRECTION__','1'),context),true);assert.deepEqual(changes,[140]);inner.scrollTop=140;
  vm.runInNewContext(asset('google-scroll.js').replace('__VP_DIRECTION__','-1'),context);assert.deepEqual(changes,[140,-140]);
});
