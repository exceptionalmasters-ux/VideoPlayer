const {test}=require('node:test');
const assert=require('node:assert/strict');
const vm=require('node:vm');
const fs=require('node:fs');
const path=require('node:path');
function setup(){
 let now=10000;const messages=[],timers=[],handlers={};
 let video={readyState:1,duration:7200,currentTime:0,addEventListener(){}};
 const player={state:'paused',position:0,seeks:[],getState(){return this.state},getPosition(){return this.position},getDuration(){return 7200},getContainer(){return{querySelector(){return video}}},on(n,f){(handlers[n]??=[]).push(f)},seek(p){this.seeks.push(p);if(this.state==='playing'){this.position=p;this.emit('time',{position:p,duration:7200})}},emit(n,d){for(const f of handlers[n]??[])f(d)}};
 const bridge={postMessage(s){messages.push(JSON.parse(s))}};
 const window={vpPlayback:bridge,jwplayer:()=>player,addEventListener(){}};
 const document={querySelector(s){if(s.includes('jw-flag-ads'))return null;return s.includes('video')?video:{id:'content'}},addEventListener(){}};
 const location={href:'https://freedisc.pl/embed/video/123?match=1'};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../VideoPlayer.Windows/desktop-progress.js'),'utf8'),{window,document,location,Date:{now:()=>now},setInterval(f){timers.push(f)},Number,JSON});
 return{player,messages,window,location,reply(v){bridge.onmessage({data:JSON.stringify(v)})},tick(){now+=2000;timers[0]()},swapVideo(){video={...video,readyState:1,duration:30}},progress(){return messages.filter(m=>m.event==='progress')}};
}
test('resume waits for real seek after playback begins and never saves startup zero',()=>{
 const s=setup();assert.equal(s.messages[0].event,'hello');s.reply({token:'t',position:1000,duration:7200});
 assert.equal(s.progress().length,0);assert.equal(s.player.position,0);
 s.tick();assert.equal(s.progress().length,0);
 s.player.state='playing';s.tick();
 assert.equal(s.player.position,1000);assert.equal(s.progress().at(-1).position,1000);assert.ok(s.messages.some(m=>m.event==='resume-applied'));
 s.player.position=1234;s.player.emit('pause');assert.equal(s.progress().at(-1).position,1234);
});
test('ad video replacement does not rebind the content player or save ad position',()=>{
 const s=setup();s.reply({token:'t',position:0,duration:0});s.player.state='playing';s.player.position=487;s.tick();
 const helloCount=s.messages.filter(m=>m.event==='hello').length;s.player.emit('adStarted');s.swapVideo();s.tick();s.player.emit('time',{position:3,duration:30});
 assert.equal(s.messages.filter(m=>m.event==='hello').length,helloCount);assert.ok(s.progress().every(p=>p.duration!==30));
 s.player.emit('adComplete');s.player.position=555;s.player.emit('pause');assert.equal(s.progress().at(-1).position,555);
});
test('flush samples the final interruption time and uses the current document URL',()=>{
 const s=setup();s.reply({token:'t',position:0,duration:0});s.player.position=901;s.location.href+='&view=player';
 s.reply({type:'flush',token:'t',requestId:'leave'});
 assert.equal(s.progress().at(-1).position,901);assert.equal(s.progress().at(-1).page,s.location.href);assert.equal(s.messages.at(-1).event,'flush-result');assert.equal(s.messages.at(-1).requestId,'leave');
});
test('completed content is saved as ended, while ad completion cannot finish content',()=>{
 const s=setup();s.reply({token:'t',position:0,duration:0});s.player.position=100;s.player.emit('adStarted');s.player.emit('complete');assert.ok(!s.progress().some(p=>p.ended));
 s.player.emit('adComplete');s.player.position=7200;s.player.emit('complete');assert.equal(s.progress().at(-1).ended,true);assert.equal(s.progress().at(-1).position,7200);
});
test('episode clock continues while bookmark restoration is waiting',()=>{
 const s=setup();s.reply({token:'t',position:7000,duration:7200});s.player.position=7195;s.tick();
 assert.equal(s.progress().length,0);assert.ok(s.messages.some(m=>m.event==='episode-clock'&&m.position===7195&&m.duration===7200));
});
