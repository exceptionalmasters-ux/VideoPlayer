(function(){
  'use strict';
  if(window.__vpAdDiagnostics||!window.vpDiagnostics)return;
  window.__vpAdDiagnostics=true;
  const bridge=window.vpDiagnostics,seen=new Map(),players=new WeakSet();let count=0;
  const layers=[['freedisc-panel-1','.reklama-content-1'],['freedisc-panel-2','.reklama-content-2'],['adblock-message','#adsblocker_detected'],['background-wait','#background-wait'],['jw-vast','.jw-plugin-vast']];
  function send(data){if(count++<120)bridge.postMessage(JSON.stringify(data));}
  function state(player){try{const s=String(player.getState()).toLowerCase();return ['idle','buffering','paused','playing','complete','error'].includes(s)?s:'unknown';}catch(_){return 'unknown';}}
  function videoState(){const v=document.querySelector('video');return {videoPresent:!!v,sourceAssigned:!!(v&&(v.currentSrc||v.getAttribute('src'))),readyState:v?v.readyState:0,networkState:v?v.networkState:0,paused:v?v.paused:true};}
  function sample(){
    if(document.hidden)return;
    for(const entry of layers){
      const nodes=Array.from(document.querySelectorAll(entry[1]));let visible=false,coversVideo=false,width=0,height=0;
      const video=document.querySelector('video'),vr=video&&video.getBoundingClientRect();
      for(const node of nodes){
        const r=node.getBoundingClientRect();let shown=r.width>0&&r.height>0;
        for(let parent=node,depth=0;parent&&depth++<30;parent=parent.parentElement){const s=getComputedStyle(parent);if(s.display==='none'||s.visibility==='hidden'||Number(s.opacity)===0){shown=false;break;}}
        if(!shown)continue;visible=true;width=Math.max(width,Math.round(r.width));height=Math.max(height,Math.round(r.height));
        if(vr&&vr.width>0&&vr.height>0){const hit=document.elementFromPoint(vr.left+vr.width/2,vr.top+vr.height/2);if(hit&&(hit===node||node.contains(hit)))coversVideo=true;}
      }
      const data=Object.assign({event:'ad-layer',kind:entry[0],present:nodes.length>0,visible,coversVideo,width,height},videoState());
      const key=JSON.stringify(data);if(seen.get(entry[0])!==key){seen.set(entry[0],key);send(data);}
    }
    try{
      const player=typeof window.jwplayer==='function'?window.jwplayer():null;
      if(player&&typeof player.on==='function'&&typeof player.getState==='function'){
        const report=(signal,extra)=>send(Object.assign({event:'ad-player',signal,state:state(player),errorCode:extra&&Number.isFinite(extra.code)?extra.code:0},videoState()));
        if(!players.has(player)){
          players.add(player);['adStarted','adComplete','adSkipped','adError'].forEach(signal=>player.on(signal,data=>report(signal,data)));
        }
        const key=JSON.stringify(Object.assign({state:state(player)},videoState()));
        if(seen.get('player-state')!==key){seen.set('player-state',key);report('snapshot');}
      }
    }catch(_){}
  }
  document.addEventListener('DOMContentLoaded',sample);setInterval(sample,2000);sample();
}());
