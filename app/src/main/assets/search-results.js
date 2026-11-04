(function () {
  'use strict';
  const movies = new Map();
  const viderHosts = ['vider.pl', 'www.vider.pl', 'vider.info', 'www.vider.info'];
  const freediscHosts = ['freedisc.pl', 'www.freedisc.pl'];
  const scope = document.querySelector('#search_files_result') || document;
  for (const anchor of scope.querySelectorAll('a[href]')) {
    try {
      const url = new URL(anchor.getAttribute('href'), location.href);
      if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password || (url.port && url.port !== '443')) continue;
      const path = decodeURIComponent(url.pathname);
      const freedisc = freediscHosts.includes(url.hostname);
      if (!freedisc && !viderHosts.includes(url.hostname)) continue;
      let key, slug, destination;
      const card = freedisc && anchor.closest ? anchor.closest('.search-element-container,.imageDisplay') : null;
      if (freedisc) {
        const match = path.match(/^\/([^/,]+),f-([0-9]+)(?:,([^/]+))?\/?$/);
        if (!match) continue;
        key = 'freedisc:' + match[2]; slug = match[3] || '';
        destination = 'https://' + url.hostname + url.pathname;
      } else {
        const match = path.match(/\/(?:([^/]*?)\+)(f[A-Za-z0-9]+)\/?$/);
        if (!match || /^\/dir\//.test(path)) continue;
        key = 'vider:' + match[2]; slug = match[1];
        destination = 'https://' + url.hostname + '/vid/+' + match[2];
      }
      const heading = anchor.querySelector('h3,[role="heading"],.user_name,.file-name,.file_name');
      const label = anchor.getAttribute('title') || anchor.getAttribute('aria-label');
      let title = heading ? heading.textContent : (label || anchor.textContent || '');
      let score = heading ? 3 : (label ? 2 : 1);
      title = title.replace(/\s+/g, ' ').trim();
      if (!title || /^https?:\/\//i.test(title) || title.length < 2) {
        title = slug.replace(/[-_]+/g, ' ').trim() || ('Film · ' + key.split(':')[1]); score = 0;
      }
      const image = anchor.querySelector('img') || (card ? card.querySelector('img') : null);
      let thumbnail = image ? (image.getAttribute('data-src') || image.currentSrc || image.src || '') : '';
      if (!thumbnail && card) {
        const art = card.querySelector('.thumb[style], [style*="background-image"]');
        const style = art ? art.getAttribute('style') : '';
        const match = style && style.match(/url\(["']?([^"')]+)["']?\)/);
        if (match) thumbnail = match[1];
      }
      if (thumbnail && !thumbnail.startsWith('data:')) thumbnail = new URL(thumbnail, location.href).href;
      if (thumbnail.length > 16000) thumbnail = '';
      if (freedisc) {
        const videoExtension = /(?:\.|-)(mp4|mkv|avi|webm|mov|m4v|mpg|mpeg|wmv|flv|ts)(?:$|[\s?#])/i.test(title + ' ' + slug)
          || /-(mp4|mkv|avi|webm|mov|m4v|mpg|mpeg|wmv|flv|ts)$/i.test(slug);
        const videoPoster = /\/photo\/[0-9]+\/7\//.test(thumbnail);
        const videoIcon = !!(card && card.querySelector('.file_icon_7,.icon-film'));
        if (!videoExtension && !videoPoster && !videoIcon) continue;
      }
      const existing = movies.get(key);
      if (existing) {
        if (score > existing.score) { existing.title = title.slice(0,180); existing.score = score; }
        if (!existing.thumbnail && thumbnail) existing.thumbnail = thumbnail;
      } else if (movies.size < 20) movies.set(key, {title:title.slice(0,180),score,url:destination,thumbnail});
    } catch (_) { /* Ignore malformed, unrelated and non-video links. */ }
  }
  return JSON.stringify(Array.from(movies.values(), function (item) {
    return {title:item.title,url:item.url,thumbnail:item.thumbnail};
  }));
}());
