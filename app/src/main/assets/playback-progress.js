(function () {
  'use strict';
  // Use content events from the site's original player; adTime is deliberately ignored.
  if (window.__vpFlushProgress || !window.vpPlayback) return;
  const bridge=window.vpPlayback, players=new WeakSet();
  let player=null, token='', checkpoint=null, saved=null, restoring=false, ad=false;
  let sentAt=0, restoreAt=0, attempts=0;
  const page=location.href;
  function send(event,extra){bridge.postMessage(JSON.stringify(Object.assign({event,page,token},extra)));}
  function flush(){
    if(checkpoint && token && !restoring && !ad){send('progress',checkpoint);sentAt=Date.now();}
  }
  window.__vpFlushProgress=function(){
    flush();
    document.querySelectorAll('iframe[src]').forEach(function(frame){
      try{const u=new URL(frame.src,location.href);if(/^\/embed(?:\/|,)/.test(u.pathname))frame.contentWindow.postMessage({type:'vp-progress-flush'},u.origin);}catch(_){}
    });
  };
  window.addEventListener('message',function(e){
    if(e.source===window.parent&&e.data&&e.data.type==='vp-progress-flush'&&/^https:\/\/(www\.)?(vider\.(pl|info)|freedisc\.pl)$/.test(e.origin))flush();
  });
  bridge.onmessage=function(e){
    try{const value=JSON.parse(e.data);if(typeof value.token!=='string'||!Number.isFinite(value.position))return;
      token=value.token;saved=value;restoring=value.position>0;attempts=0;restoreAt=0;
    }catch(_){}
  };
  function time(data){
    if(!token||ad)return;
    const position=Number(data.position),duration=Number(data.duration);
    if(!Number.isFinite(position)||!Number.isFinite(duration)||duration<=0||duration>86400||position<0||position>duration+2)return;
    if(restoring){
      // A changed edition must not receive a timestamp belonging to another duration.
      if(saved.duration>0&&Math.abs(saved.duration-duration)>Math.max(2,duration*0.02)){restoring=false;}
      else if(Math.abs(position-saved.position)<=3){restoring=false;}
      else {
        if(Date.now()-restoreAt>=5000&&attempts<3){restoreAt=Date.now();attempts++;try{player.seek(Math.min(saved.position,duration-1));}catch(_){} }
        return; // Never overwrite the old position with startup zero or a failed seek.
      }
    }
    checkpoint={position:Math.floor(position),duration:Math.floor(duration),ended:false};
    if(Date.now()-sentAt>=5000)flush();
  }
  function discover(){
    try{
      const p=typeof window.jwplayer==='function'?window.jwplayer():null;
      if(!p||typeof p.on!=='function'||!p.getState||!p.getState()||players.has(p))return;
      players.add(p);player=p;token='';saved=null;checkpoint=null;ad=false;restoring=false;
      p.on('adStarted',function(){if(player===p)ad=true;});
      ['adComplete','adSkipped','adError'].forEach(function(event){p.on(event,function(){if(player===p)ad=false;});});
      p.on('time',function(data){if(player===p)time(data);});
      p.on('pause',function(){if(player===p)flush();});p.on('seeked',function(){if(player===p&&checkpoint)sentAt=0;});
      p.on('complete',function(){if(player===p&&!ad&&!restoring&&checkpoint){checkpoint.ended=true;checkpoint.position=checkpoint.duration;flush();}});
      send('hello',{});
    }catch(_){}
  }
  document.addEventListener('visibilitychange',flush);
  window.addEventListener('pagehide',flush);
  document.addEventListener('DOMContentLoaded',discover);
  setInterval(discover,1000);discover();
}());
