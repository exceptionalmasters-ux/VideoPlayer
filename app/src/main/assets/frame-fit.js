(function () {
  'use strict';
  if (window.__vpFrameFitInstalled) return;
  window.__vpFrameFitInstalled = true;
  const origins = ['https://freedisc.pl','https://www.freedisc.pl','https://stream.freedisc.pl',
    'https://stream2.freedisc.pl','https://ovh.freedisc.pl','https://vider.pl','https://www.vider.pl',
    'https://vider.info','https://www.vider.info'];
  let enabled = false, observer, viewport, savedContent, createdViewport = false, lastSize = '';
  function forward(on) {
    document.querySelectorAll('iframe[src]').forEach(function (frame) {
      try {
        const origin = new URL(frame.src, location.href).origin;
        if (origins.includes(origin)) frame.contentWindow.postMessage({type:'vp-frame-fit',enabled:on},origin);
      } catch (_) { }
    });
  }
  function fit() {
    if (!enabled || !document.head || !document.body) return;
    if (!document.getElementById('vp-frame-fit-style')) {
      viewport = document.querySelector('meta[name="viewport"]');
      createdViewport = !viewport;
      if (!viewport) { viewport = document.createElement('meta'); viewport.name = 'viewport'; document.head.appendChild(viewport); }
      savedContent = viewport.getAttribute('content');
      viewport.setAttribute('content','width=device-width,initial-scale=1');
      const style = document.createElement('style'); style.id = 'vp-frame-fit-style';
      style.textContent = 'html,body{width:100%!important;min-width:0!important;max-width:100%!important;height:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important}' +
        '[data-vp-embed-parent]{width:100%!important;min-width:0!important;max-width:100%!important;height:100%!important;margin:0!important;padding:0!important;transform:none!important}' +
        '[data-vp-embed-player]{width:100vw!important;min-width:0!important;max-width:100vw!important;height:100vh!important;max-height:100vh!important;margin:0!important;padding:0!important;aspect-ratio:auto!important}' +
        '[data-vp-embed-player] .jw-aspect{display:none!important}' +
        'video{width:100%!important;height:100%!important;max-width:100%!important;object-fit:contain!important}' +
        '[data-vp-embed-player] .jw-preview{background-size:contain!important}';
      document.head.appendChild(style);
    }
    const player = document.querySelector('.jwplayer,.video-js,.flowplayer') || document.querySelector('video');
    if (player) {
      player.setAttribute('data-vp-embed-player','');
      let parent = player.parentElement;
      while (parent && parent !== document.body) { parent.setAttribute('data-vp-embed-parent',''); parent = parent.parentElement; }
      const size = window.innerWidth + ':' + window.innerHeight;
      if (size !== lastSize) {
        lastSize = size;
        try { if (typeof window.jwplayer === 'function') window.jwplayer().resize(window.innerWidth,window.innerHeight); } catch (_) { }
      }
    }
    forward(true);
  }
  function stop() {
    enabled = false; lastSize = '';
    if (observer) observer.disconnect();
    const style = document.getElementById('vp-frame-fit-style'); if (style) style.remove();
    document.querySelectorAll('[data-vp-embed-player],[data-vp-embed-parent]').forEach(function (node) {
      node.removeAttribute('data-vp-embed-player'); node.removeAttribute('data-vp-embed-parent');
    });
    if (viewport) {
      if (createdViewport) viewport.remove();
      else if (savedContent === null) viewport.removeAttribute('content');
      else viewport.setAttribute('content',savedContent);
      viewport = null;
    }
    forward(false);
  }
  window.addEventListener('message',function (event) {
    if (window.parent === window || event.source !== window.parent || !origins.includes(event.origin)
        || !event.data || event.data.type !== 'vp-frame-fit' || typeof event.data.enabled !== 'boolean') return;
    if (!event.data.enabled) { stop(); return; }
    if (!enabled) {
      enabled = true;
      observer = new MutationObserver(fit);
      observer.observe(document,{childList:true,subtree:true});
    }
    fit();
  });
  window.addEventListener('resize',fit);
  window.addEventListener('DOMContentLoaded',fit);
  window.addEventListener('load',function () { if (enabled) forward(true); },true);
}());
