(function () {
  'use strict';
  const old = document.getElementById('vp-focus-style');
  if (old) {
    old.remove();
    const saved = window.__vpPlayerLayout;
    if (saved) {
      if (saved.observer) saved.observer.disconnect();
      saved.frames.forEach(function (entry) {
        entry.frame.removeEventListener('load',entry.onLoad);
        try { entry.frame.contentWindow.postMessage({type:'vp-frame-fit',enabled:false},entry.origin); } catch (_) { }
      });
      if (saved.created) saved.viewport.remove();
      else if (saved.content === null) saved.viewport.removeAttribute('content');
      else saved.viewport.setAttribute('content',saved.content);
      delete window.__vpPlayerLayout;
    }
    document.querySelectorAll('[data-vp-hidden],[data-vp-parent],[data-vp-player]').forEach(function (node) {
      node.removeAttribute('data-vp-hidden');
      node.removeAttribute('data-vp-parent');
      node.removeAttribute('data-vp-player');
    });
    return 'page';
  }
  function containsMedia(node) {
    return node && (node.tagName === 'VIDEO' || node.tagName === 'IFRAME' || node.querySelector('iframe,video'));
  }
  let player = document.querySelector('.video-frame');
  if (!containsMedia(player)) {
    player = Array.from(document.querySelectorAll('iframe[src]')).find(function (frame) {
      try {
        const address = new URL(frame.src, location.href);
        return address.protocol === 'https:' && !address.username && !address.password && !address.port && ['freedisc.pl','www.freedisc.pl','stream.freedisc.pl',
          'stream2.freedisc.pl','ovh.freedisc.pl'].includes(address.hostname) && /^\/embed(?:\/|,)/.test(address.pathname);
      } catch (_) { return false; }
    });
  }
  if (!containsMedia(player)) player = document.querySelector('.jwplayer,.video-js');
  if (!containsMedia(player)) {
    const video = document.querySelector('video');
    player = video ? (video.closest('.jwplayer,.video-js') || video) : null;
  }
  if (!containsMedia(player)) return 'missing';
  // Keep the existing iframe and everything inside it, including its advertisements.
  // Only hide sibling branches outside the player. No navigation or iframe replacement.
  let viewport = document.querySelector('meta[name="viewport"]');
  const created = !viewport;
  if (!viewport) { viewport = document.createElement('meta'); viewport.name = 'viewport'; document.head.appendChild(viewport); }
  const saved = {viewport,created,content:viewport.getAttribute('content'),frames:[]};
  window.__vpPlayerLayout = saved;
  viewport.setAttribute('content','width=device-width,initial-scale=1');
  function fitFrames() {
    const frames = player.tagName === 'IFRAME' ? [player] : Array.from(player.querySelectorAll('iframe[src]'));
    frames.forEach(function (frame) {
      if (saved.frames.some(function (entry) { return entry.frame === frame; })) return;
      try {
        const address = new URL(frame.src,location.href);
        if (address.protocol !== 'https:' || address.username || address.password || address.port
            || !['freedisc.pl','www.freedisc.pl','stream.freedisc.pl','stream2.freedisc.pl','ovh.freedisc.pl',
              'vider.pl','www.vider.pl','vider.info','www.vider.info'].includes(address.hostname)) return;
        const onLoad = function () { frame.contentWindow.postMessage({type:'vp-frame-fit',enabled:true},address.origin); };
        saved.frames.push({frame,origin:address.origin,onLoad});
        frame.addEventListener('load',onLoad); onLoad();
      } catch (_) { }
    });
  }
  fitFrames();
  saved.observer = new MutationObserver(fitFrames);
  saved.observer.observe(player,{childList:true,subtree:true});
  let node = player;
  player.setAttribute('data-vp-player', '');
  while (node.parentElement && node !== document.body) {
    const parent = node.parentElement;
    parent.setAttribute('data-vp-parent', '');
    Array.from(parent.children).forEach(function (sibling) {
      if (sibling !== node && !['SCRIPT', 'STYLE', 'LINK'].includes(sibling.tagName)) {
        sibling.setAttribute('data-vp-hidden', '');
      }
    });
    node = parent;
  }
  const style = document.createElement('style');
  style.id = 'vp-focus-style';
  style.textContent = '[data-vp-hidden]{display:none!important}' +
    'html,body{width:100%!important;min-width:0!important;max-width:100%!important;margin:0!important;padding:0!important;overflow:hidden!important;background:#000!important}' +
    '[data-vp-parent]{min-width:0!important;max-width:100%!important;margin:0!important;padding:0!important;transform:none!important;overflow:visible!important}' +
    '[data-vp-player]{position:fixed!important;inset:0!important;width:100vw!important;' +
    'height:100vh!important;min-width:0!important;max-width:100vw!important;max-height:100vh!important;margin:0!important;padding:0!important;border:0!important;transform:none!important;aspect-ratio:auto!important;z-index:100!important;background:#000!important}' +
    '[data-vp-player] iframe,[data-vp-player] video{width:100%!important;height:100%!important;max-width:100%!important;border:0!important;object-fit:contain!important}' +
    'video[data-vp-player]{object-fit:contain!important}[data-vp-player] .jw-aspect{display:none!important}';
  document.head.appendChild(style);
  return 'player';
}());
