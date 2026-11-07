(function(){
  'use strict';
  if(window.__vpDesktopFitInstalled)return;
  window.__vpDesktopFitInstalled=true;
  let target=null,size='',original=null;
  function fit(){
    const focused=document.querySelector('[data-vp-player]');
    const embedded=document.getElementById('vp-frame-fit-style');
    const enabled=!!document.getElementById('vp-focus-style')||!!embedded;
    const player=enabled?(focused&&focused.matches('.jwplayer,.video-js,.flowplayer')?focused:(focused||document).querySelector('.jwplayer,.video-js,.flowplayer')):null;
    if(!player){
      const old=document.getElementById('vp-desktop-fit-style');if(old)old.remove();
      document.querySelectorAll('[data-vp-win-player],[data-vp-win-parent]').forEach(n=>{n.removeAttribute('data-vp-win-player');n.removeAttribute('data-vp-win-parent');});
      if(target&&original)try{if(typeof window.jwplayer==='function')window.jwplayer(target.id||undefined).resize(original.width,original.height);}catch(_){}
      target=null;original=null;size='';return;
    }
    if(target!==player){const r=player.getBoundingClientRect();target=player;original={width:r.width,height:r.height};size='';}
    player.setAttribute('data-vp-win-player','');
    for(let p=player.parentElement;p&&p!==document.body;p=p.parentElement)p.setAttribute('data-vp-win-parent','');
    if(!document.getElementById('vp-desktop-fit-style')){
      const style=document.createElement('style');style.id='vp-desktop-fit-style';
      style.textContent='[data-vp-win-parent]{transform:none!important;contain:none!important;filter:none!important;perspective:none!important;overflow:visible!important}' +
        '[data-vp-win-player]{position:fixed!important;inset:0!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;min-width:0!important;min-height:0!important;max-width:none!important;max-height:none!important;margin:0!important;padding:0!important;transform:none!important;aspect-ratio:auto!important;background:#000!important}' +
        '[data-vp-win-player] .jw-aspect{display:none!important}' +
        '[data-vp-win-player] .jw-media{position:absolute!important;inset:0!important;width:100%!important;height:100%!important}' +
        '[data-vp-win-player] video{width:100%!important;height:100%!important;object-fit:contain!important}' +
        '[data-vp-win-player] .jw-preview{background-size:contain!important}';
      document.head.appendChild(style);
    }
    const next=window.innerWidth+':'+window.innerHeight;
    if(next!==size){size=next;try{if(typeof window.jwplayer==='function')window.jwplayer(player.id||undefined).resize(window.innerWidth,window.innerHeight);}catch(_){} }
  }
  window.__vpDesktopFit=fit;
  new MutationObserver(fit).observe(document,{childList:true,subtree:true});
  window.addEventListener('resize',fit);document.addEventListener('DOMContentLoaded',fit);fit();
}());
