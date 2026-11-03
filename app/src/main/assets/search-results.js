(function () {
  'use strict';
  const movies = new Map();
  const hosts = ['vider.pl', 'www.vider.pl', 'vider.info', 'www.vider.info'];
  for (const anchor of document.querySelectorAll('a[href]')) {
    try {
      let url = new URL(anchor.getAttribute('href'), location.href);
      if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || !hosts.includes(url.hostname)) continue;
      const path = decodeURIComponent(url.pathname);
      // The site supports both /vid/+fID and the older /profile/title+fID address.
      const match = path.match(/\/(?:([^/]*?)\+)(f[A-Za-z0-9]+)\/?$/);
      if (!match || /^\/dir\//.test(path)) continue;
      const heading = anchor.querySelector('h3,[role="heading"],.user_name,.file-name,.file_name');
      const label = anchor.getAttribute('title') || anchor.getAttribute('aria-label');
      let title = heading ? heading.textContent : (label || anchor.textContent || '');
      let score = heading ? 3 : (label ? 2 : 1);
      title = title.replace(/\s+/g, ' ').trim();
      if (!title || /^https?:\/\//i.test(title) || title.length < 2) {
        title = match[1].replace(/[-_]+/g, ' ').trim() || ('Film · ' + match[2]);
        score = 0;
      }
      const image = anchor.querySelector('img');
      let thumbnail = image ? (image.currentSrc || image.src || image.getAttribute('data-src') || '') : '';
      if (thumbnail.length > 16000) thumbnail = '';
      const existing = movies.get(match[2]);
      if (existing) {
        if (score > existing.score) { existing.title = title.slice(0, 180); existing.score = score; }
        if (!existing.thumbnail && thumbnail) existing.thumbnail = thumbnail;
      } else if (movies.size < 20) {
        movies.set(match[2], {title: title.slice(0, 180), score,
          url: 'https://' + url.hostname + '/vid/+' + match[2], thumbnail});
      }
    } catch (_) { /* Ignore malformed and unrelated links. */ }
  }
  return JSON.stringify(Array.from(movies.values(), function (item) {
    return {title: item.title, url: item.url, thumbnail: item.thumbnail};
  }));
}());
