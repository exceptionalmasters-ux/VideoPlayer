(function () {
  'use strict';
  try {
    const target = new URL(__VP_SEARCH_URL__);
    const current = new URL(location.href);
    const hosts = ['freedisc.pl','www.freedisc.pl'];
    if (current.protocol !== 'https:' || target.protocol !== 'https:'
        || !hosts.includes(current.hostname) || !hosts.includes(target.hostname)
        || current.port || target.port || current.username || current.password
        || !/^\/search\/all\/[^/]+$/.test(target.pathname) || target.username || target.password
        || target.search || target.hash) return 'invalid';
    if (!window.Router || typeof window.Router.navigate !== 'function'
        || !window.App || !window.App.Instance || !window.App.Instance.Search) return 'waiting';
    // The same router and trigger flag used by Freedisc's own search button.
    window.Router.navigate(target.pathname, true);
    return 'routed';
  } catch (_) { return 'error'; }
}());
