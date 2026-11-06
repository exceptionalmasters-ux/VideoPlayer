(function () {
  'use strict';
  if (window.__vpTvCommand) return;
  const origins=['https://freedisc.pl','https://www.freedisc.pl','https://stream.freedisc.pl','https://stream2.freedisc.pl','https://ovh.freedisc.pl',
    'https://vider.pl','https://www.vider.pl','https://vider.info','https://www.vider.info'];
  const actions=['toggle','play','pause','back','forward'];
  window.__vpTvCommand=function (action,requestId) {
    if (!actions.includes(action)) return false;
    // Prefer the original player's API so its advertisement rules remain in effect.
    try {
      const selected=document.querySelector?document.querySelector('[data-vp-player].jwplayer,[data-vp-embed-player].jwplayer,.jwplayer'):null;
      const player=typeof window.jwplayer==='function'?window.jwplayer(selected&&selected.id||undefined):null;
      if (player && typeof player.getState==='function' && player.getState()) {
        let container=null;try{container=typeof player.getContainer==='function'?player.getContainer():null;}catch(_){}
        const video=container&&container.querySelector?container.querySelector('video'):document.querySelector?document.querySelector('.jwplayer video.jw-video,.jwplayer video'):null;
        const playing=video&&(video.currentSrc||video.readyState>0)? !video.paused&&!video.ended : String(player.getState()).toLowerCase()==='playing';
        if(((action==='play'||action==='toggle')&&!playing)&&window.__vpTvTapPlay&&window.__vpTvTapPlay(requestId))return true;
        if(action==='toggle') { if(playing)player.pause(true);else player.play(true); }
        else if(action==='play')player.play(true);
        else if(action==='pause')player.pause(true);
        else player.seek(Math.max(0,player.getPosition()+(action==='back'?-10:10)));
        return true;
      }
    } catch (_) { }
    const video=Array.from(document.querySelectorAll('video')).find(function (node) {
      return node.getBoundingClientRect().height>0;
    });
    if(video){
      if(action==='toggle'||action==='play'){
        if(action==='toggle'&&!video.paused)video.pause();
        else {const pending=video.play();if(pending&&pending.catch)pending.catch(function(){});}
      } else if(action==='pause')video.pause();
      else if(Number.isFinite(video.duration)&&video.seekable&&video.seekable.length){
        const lower=video.seekable.start(0),upper=video.seekable.end(video.seekable.length-1);
        video.currentTime=Math.max(lower,Math.min(upper,video.currentTime+(action==='back'?-10:10)));
      }
      return true;
    }
    let sent=false;
    document.querySelectorAll('iframe[src]').forEach(function(frame){
      try{
        const url=new URL(frame.src,location.href);
        if(origins.includes(url.origin)&&!url.username&&!url.password&&/^\/embed(?:\/|,)/.test(url.pathname)){
          frame.contentWindow.postMessage({type:'vp-tv-command',action,requestId},url.origin);sent=true;
        }
      }catch(_){}
    });
    return sent;
  };
  window.addEventListener('message',function(event){
    if(window.parent===window||event.source!==window.parent||!origins.includes(event.origin)
      ||!event.data||event.data.type!=='vp-tv-command'||!actions.includes(event.data.action))return;
    if(window.__vpTvTouchParent)window.__vpTvTouchParent(event.origin);
    window.__vpTvCommand(event.data.action,event.data.requestId);
  });
  function outline(){
    if(!document.head||document.getElementById('vp-tv-focus-style'))return;
    const style=document.createElement('style');style.id='vp-tv-focus-style';
    style.textContent='a:focus,button:focus,input:focus,select:focus,[tabindex]:focus{outline:3px solid #72dcc4!important;outline-offset:3px!important}';
    document.head.appendChild(style);
  }
  document.addEventListener('DOMContentLoaded',outline);outline();
}());

