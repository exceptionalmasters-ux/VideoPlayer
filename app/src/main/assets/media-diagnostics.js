(function () {
  'use strict';
  if (window.__vpDiagnostics) return;
  window.__vpDiagnostics = true;
  const bound = new WeakSet();
  const events = ['play', 'playing', 'pause', 'waiting', 'stalled', 'error',
    'loadedmetadata', 'ended', 'seeking', 'seeked'];
  function report(video, event) {
    if (!window.vpDiagnostics) return;
    window.vpDiagnostics.postMessage(JSON.stringify({
      event: event,
      source: video.currentSrc || video.src || '',
      readyState: video.readyState,
      networkState: video.networkState,
      errorCode: video.error ? video.error.code : 0,
      paused: video.paused,
      time: Number.isFinite(video.currentTime) ? Math.floor(video.currentTime) : 0
    }));
  }
  function scan() {
    document.querySelectorAll('video').forEach(function (video) {
      if (bound.has(video)) return;
      bound.add(video);
      events.forEach(function (event) {
        video.addEventListener(event, function () { report(video, event); });
      });
      report(video, 'snapshot');
    });
  }
  new MutationObserver(scan).observe(document, {childList: true, subtree: true});
  document.addEventListener('DOMContentLoaded', scan);
  scan();
}());
