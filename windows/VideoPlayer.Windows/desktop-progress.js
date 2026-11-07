(function(){
  'use strict';
  if(window.__vpFlushProgress||!window.vpPlayback)return;
  const bridge=window.vpPlayback;
  let player=null,video=null,token='',saved=null,restoring=false,ad=false,checkpoint=null;
  let sentAt=0,helloAt=0,seekAt=0,attempts=0,failed=false,lastDuration=0,completed=false;
  function send(event,extra){bridge.postMessage(JSON.stringify(Object.assign({event,page:location.href,token},extra)));}
  function adActive(){if(ad||document.querySelector('.jwplayer.jw-flag-ads,.jwplayer.jw-flag-admode'))return true;try{const s=player&&player.getAdState&&String(player.getAdState()).toLowerCase();return ['adplaying','adpaused','adloading','playing','paused','loading'].includes(s);}catch(_){return false;}}
  function valid(position,duration){return Number.isFinite(position)&&Number.isFinite(duration)&&duration>0&&duration<=86400&&position>=0&&position<=duration+2;}
  function duration(){let d=0;try{d=player&&player.getDuration?Number(player.getDuration()):video?Number(video.duration):0;}catch(_){}return Number.isFinite(d)&&d>0?d:lastDuration;}
  function flush(){if(checkpoint&&token&&!restoring&&!adActive()){send('progress',checkpoint);sentAt=Date.now();}}
  function restore(position,d){
    if(!restoring)return false;
    if(saved.duration>0&&Math.abs(saved.duration-d)>Math.max(2,d*.02)){restoring=false;send('resume-skipped-duration',{});return false;}
    const target=Math.min(saved.position,Math.max(0,d-1));
    if(Math.abs(position-target)<=1.5){restoring=false;failed=false;send('resume-applied',{position:Math.floor(position),duration:d});return false;}
    // A ready/metadata notification can precede playback; keep the old bookmark until a seek really takes effect.
    if(!failed&&Date.now()-seekAt>=1000){
      if(attempts>=12){failed=true;send('resume-failed',{});return true;}
      let ready=video?video.readyState>=1:player&&player.getState&&String(player.getState()).toLowerCase()!=='idle';
      if(ready){seekAt=Date.now();attempts++;try{if(player&&player.seek)player.seek(target);else if(video)video.currentTime=target;}catch(_){} }
    }
    return true;
  }
  function record(position,d,ended){
    if(!token||adActive()||!valid(position,d))return;
    lastDuration=d;
    if(restore(position,d))return;
    checkpoint={position:Math.floor(ended?d:position),duration:Math.floor(d),ended:!!ended};
    if(ended||Date.now()-sentAt>=2000)flush();
  }
  function sample(){
    if(!token||adActive())return;
    try{const p=player&&player.getPosition?Number(player.getPosition()):video?Number(video.currentTime):NaN;record(completed?duration():p,duration(),completed);}catch(_){}
  }
  window.__vpFlushProgress=function(){sample();flush();};
  bridge.onmessage=function(e){try{
    const value=JSON.parse(e.data);
    if(value.type==='flush'){
      if(value.token!==token)return;sample();flush();send('flush-result',{requestId:value.requestId});return;
    }
    if(value.type==='media-command'){
      if(!token||value.token!==token||typeof value.requestId!=='string'||value.requestId.length>80)return;
      if(value.manual===true&&window.__vpCancelAutoStart)window.__vpCancelAutoStart();
      if(window.__vpTvTouchParent)window.__vpTvTouchParent(value.parentOrigin);
      const handled=!!(window.__vpTvCommand&&window.__vpTvCommand(value.action,value.requestId));
      send('media-command-result',{requestId:value.requestId,handled});return;
    }
    if(typeof value.token!=='string'||!Number.isFinite(value.position))return;
    token=value.token;saved=value;restoring=value.position>0;attempts=0;seekAt=0;failed=false;
    if(restoring)send('resume-pending',{position:value.position,duration:value.duration});
    sample();
    if(value.autostart===true&&window.__vpStartOriginalPlayer)window.__vpStartOriginalPlayer(player,send);
  }catch(_){} };
  function attach(p,v){
    player=p;video=v;token='';saved=null;restoring=false;ad=false;checkpoint=null;lastDuration=0;completed=false;sentAt=0;helloAt=0;
    const active=()=>player===p&&(p!==null||video===v);
    if(p){
      p.on('adStarted',()=>{if(active())ad=true;});
      ['adComplete','adSkipped','adError'].forEach(n=>p.on(n,()=>{if(active()){ad=false;sample();}}));
      p.on('time',d=>{if(active())record(Number(d.position),Number(d.duration)||duration(),false);});
      ['ready','firstFrame','play','seeked'].forEach(n=>p.on(n,()=>{if(active()){if(n==='play'||n==='seeked')completed=false;if(restoring&&(n==='firstFrame'||n==='ready')){failed=false;attempts=0;seekAt=0;}sample();}}));
      p.on('pause',()=>{if(active()){if(window.__vpCancelAutoStart)window.__vpCancelAutoStart();sample();flush();}});
      p.on('complete',()=>{if(active()&&!adActive()){completed=true;const d=duration();record(d,d,true);flush();}});
    }
    if(v){
      ['loadedmetadata','durationchange','canplay','playing','seeked','timeupdate'].forEach(n=>v.addEventListener(n,()=>{if(active())sample();}));
      v.addEventListener('pause',()=>{if(active()){sample();flush();}});
      v.addEventListener('ended',()=>{if(active()&&!p){const d=duration();record(d,d,true);flush();}});
    }
  }
  function tick(){try{
    let p=null;try{const node=document.querySelector('[data-vp-win-player].jwplayer,[data-vp-player].jwplayer,.jwplayer');p=typeof window.jwplayer==='function'?window.jwplayer(node&&node.id||undefined):null;if(!p||!p.on||!p.getState||!p.getState())p=null;}catch(_){}
    let v=p&&p.getContainer&&p.getContainer()?p.getContainer().querySelector('video'):document.querySelector('.jwplayer video,[data-vp-player] video,video');
    if(!p&&(!v||v.readyState<1||!Number.isFinite(v.duration)||v.duration<=0))return;
    if(p!==player||!p&&v!==video)attach(p,v);else if(p&&v!==video)video=v;
    if(!token&&Date.now()-helloAt>2000){helloAt=Date.now();send('hello',{});}else sample();
  }catch(_){} }
  document.addEventListener('visibilitychange',window.__vpFlushProgress);window.addEventListener('pagehide',window.__vpFlushProgress);
  document.addEventListener('DOMContentLoaded',tick);setInterval(tick,1000);tick();
}());
