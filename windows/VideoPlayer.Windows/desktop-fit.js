(function(){
  'use strict';
  if(window.__vpDesktopFitInstalled)return;
  window.__vpDesktopFitInstalled=true;
  let target=null,size='',original=null,intent=null,parentOrigin='',reports=0;
  const origins=['https://freedisc.pl','https://www.freedisc.pl','https://stream.freedisc.pl','https://stream2.freedisc.pl','https://ovh.freedisc.pl','https://vider.info','https://www.vider.info','https://vider.pl','https://www.vider.pl'];
  window.__vpSetDesktopFit=function(on,parent){intent=on===true;if(origins.includes(parent))parentOrigin=parent;fit();if(intent&&window.parent!==window&&parentOrigin)window.parent.postMessage({type:'vp-desktop-frame-ready',page:location.href},parentOrigin);};
  window.addEventListener('message',function(e){
    if(!origins.includes(e.origin)||!e.data||e.data.type!=='vp-desktop-frame-ready')return;
    try{const u=new URL(e.data.page);if(u.origin!==e.origin||!/^\/embed(?:\/|,)/.test(u.pathname))return;}catch(_){return;}
    for(const frame of document.querySelectorAll('iframe'))if(frame.contentWindow===e.source){frame.setAttribute('data-vp-win-frame','');fit();break;}
  });
  function fit(){
    const focused=document.querySelector('[data-vp-player]');
    const embedded=document.getElementById('vp-frame-fit-style');
    const enabled=intent===null?(!!document.getElementById('vp-focus-style')||!!embedded):intent;
    const knownFrame=document.querySelector('iframe[data-vp-win-frame]');
    const player=enabled?(knownFrame||(focused&&focused.matches('.jwplayer,.video-js,.flowplayer')?focused:(focused||document).querySelector('.jwplayer,.video-js,.flowplayer'))||document.querySelector('.jwplayer,.video-js,.flowplayer')):null;
    if(!player){
      const old=document.getElementById('vp-desktop-fit-style');if(old)old.remove();
      document.querySelectorAll('[data-vp-win-player],[data-vp-win-parent]').forEach(n=>{n.removeAttribute('data-vp-win-player');n.removeAttribute('data-vp-win-parent');});
      if(target&&original)try{if(target.tagName!=='IFRAME'&&typeof window.jwplayer==='function')window.jwplayer(target.id||undefined).resize(original.width,original.height);}catch(_){}
      target=null;original=null;size='';return;
    }
    if(target!==player){document.querySelectorAll('[data-vp-win-player],[data-vp-win-parent]').forEach(n=>{n.removeAttribute('data-vp-win-player');n.removeAttribute('data-vp-win-parent');});const r=player.getBoundingClientRect();target=player;original={width:r.width,height:r.height};size='';}
    player.setAttribute('data-vp-win-player','');
    for(let p=player.parentElement;p&&p!==document.body;p=p.parentElement)p.setAttribute('data-vp-win-parent','');
    if(!document.getElementById('vp-desktop-fit-style')){
      const style=document.createElement('style');style.id='vp-desktop-fit-style';
      style.textContent='#embed_hover_header,.embed-header,.badge_rating,#tip{display:none!important}' +
        'html,body{margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important}' +
        '[data-vp-win-parent]{transform:none!important;contain:none!important;filter:none!important;perspective:none!important;overflow:visible!important}' +
        '[data-vp-win-player]{position:fixed!important;inset:0!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;min-width:0!important;min-height:0!important;max-width:none!important;max-height:none!important;margin:0!important;padding:0!important;transform:none!important;aspect-ratio:auto!important;background:#000!important}' +
        '[data-vp-win-player] iframe{width:100%!important;height:100%!important;border:0!important}' +
        '[data-vp-win-player] .jw-aspect{display:none!important}' +
        '[data-vp-win-player] .jw-media{position:absolute!important;inset:0!important;width:100%!important;height:100%!important}' +
        '[data-vp-win-player] video{width:100%!important;height:100%!important;object-fit:contain!important}' +
        '[data-vp-win-player] .jw-preview{background-size:contain!important}';
      document.head.appendChild(style);
    }
    const next=window.innerWidth+':'+window.innerHeight;
    if(next!==size){size=next;try{if(player.tagName!=='IFRAME'&&typeof window.jwplayer==='function')window.jwplayer(player.id||undefined).resize(window.innerWidth,window.innerHeight);}catch(_){}
      if(reports++<30&&window.vpPlayback){const r=player.getBoundingClientRect();window.vpPlayback.postMessage(JSON.stringify({event:'layout',page:location.href,width:Math.round(r.width),height:Math.round(r.height),viewportWidth:innerWidth,viewportHeight:innerHeight,frame:player.tagName==='IFRAME'}));}
    }
  }
  window.__vpDesktopFit=fit;
  new MutationObserver(fit).observe(document,{childList:true,subtree:true});
  setInterval(fit,1000);
  window.addEventListener('resize',fit);document.addEventListener('DOMContentLoaded',fit);fit();
}());
