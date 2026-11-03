(function () {
  'use strict';
  const results = [];
  const seen = new Set();
  for (const anchor of document.querySelectorAll('a[href]')) {
    try {
      let url = new URL(anchor.getAttribute('href'), location.href);
      if ((url.hostname === 'www.google.com' || url.hostname === 'www.google.pl' ||
          url.hostname === 'google.com' || url.hostname === 'google.pl') && url.pathname === '/url') {
        const target = url.searchParams.get('q') || url.searchParams.get('url');
        if (!target) continue;
        url = new URL(target);
      }
      if (url.protocol !== 'https:' || url.username || url.password ||
          !['vider.pl','www.vider.pl','vider.info','www.vider.info'].includes(url.hostname)) continue;
      const path = decodeURIComponent(url.pathname);
      const match = path.match(/^\/vid\/(?:[^/]*\+)(f[A-Za-z0-9]+)\/?$/);
      if (!match || seen.has(match[1])) continue;
      const heading = anchor.querySelector('h3,[role="heading"]');
      let title = heading ? heading.textContent : (anchor.getAttribute('aria-label') || anchor.textContent);
      title = (title || '').replace(/\s+/g, ' ').trim();
      if (!title || /^https?:\/\//i.test(title) || title.length < 2) continue;
      // Only associate an image that actually belongs to this result link.
      const image = anchor.querySelector('img');
      const thumbnail = image ? (image.currentSrc || image.src || image.getAttribute('data-src') || '') : '';
      seen.add(match[1]);
      results.push({title: title.slice(0, 180), url: url.origin + url.pathname,
        thumbnail: thumbnail.length <= 16000 ? thumbnail : ''});
      if (results.length >= 20) break;
    } catch (_) { /* Ignore malformed and unrelated links. */ }
  }
  return JSON.stringify(results);
}());
