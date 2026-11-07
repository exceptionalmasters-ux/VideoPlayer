(function(){
  'use strict';
  if(window.__vpEpisodeEvents)return;window.__vpEpisodeEvents=true;
  let token='',player=null;const seen=new WeakSet();let ad=false;
  function send(event,extra){if(token&&window.vpPlayback)window.vpPlayback.postMessage(JSON.stringify(Object.assign({event,page:location.href,token},extra)));}
  function title(){try{const item=player&&player.getPlaylistItem&&player.getPlaylistItem();if(item&&typeof item.title==='string'&&item.title.length<=1000)send('episode-title',{title:item.title});}catch(_){} }
  window.chrome.webview.addEventListener('message',function(e){const d=e.data;if(d&&typeof d.token==='string'&&Number.isFinite(d.position)){token=d.token;title();}});
  function discover(){try{
    const p=typeof window.jwplayer==='function'?window.jwplayer():null;if(!p||!p.on||!p.getState||!p.getState()||seen.has(p))return;
    seen.add(p);player=p;ad=false;
    p.on('adStarted',function(){if(player===p)ad=true;});
    ['adComplete','adSkipped','adError'].forEach(n=>p.on(n,function(){if(player===p)ad=false;}));
    p.on('complete',function(){if(player!==p||ad)return;let duration=0;try{duration=Number(p.getDuration());}catch(_){}send('ended',{duration:Number.isFinite(duration)?duration:0});});title();
  }catch(_){} }
  document.addEventListener('DOMContentLoaded',discover);setInterval(discover,1000);discover();
}());
