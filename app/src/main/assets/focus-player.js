(function () {
  'use strict';
  const old = document.getElementById('vp-focus-style');
  if (old) {
    old.remove();
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
  if (!containsMedia(player)) player = document.querySelector('.jwplayer,.video-js');
  if (!containsMedia(player)) {
    const video = document.querySelector('video');
    player = video ? (video.closest('.jwplayer,.video-js') || video) : null;
  }
  if (!containsMedia(player)) {
    player = Array.from(document.querySelectorAll('iframe[src]')).find(function (frame) {
      try {
        const address = new URL(frame.src, location.href);
        return address.protocol === 'https:' && ['freedisc.pl','www.freedisc.pl','stream.freedisc.pl',
          'stream2.freedisc.pl','ovh.freedisc.pl'].includes(address.hostname) && /^\/embed(?:\/|,)/.test(address.pathname);
      } catch (_) { return false; }
    });
  }
  if (!containsMedia(player)) return 'missing';
  // Keep the existing iframe and everything inside it, including its advertisements.
  // Only hide sibling branches outside the player. No navigation or iframe replacement.
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
    'html,body{margin:0!important;padding:0!important;background:#000!important}' +
    '[data-vp-parent]{transform:none!important;overflow:visible!important}' +
    '[data-vp-player]{position:fixed!important;inset:0!important;width:100vw!important;' +
    'height:100vh!important;margin:0!important;padding:0!important;z-index:100!important;background:#000!important}' +
    '[data-vp-player]>iframe,[data-vp-player]>video{width:100%!important;height:100%!important;border:0!important}';
  document.head.appendChild(style);
  return 'player';
}());
