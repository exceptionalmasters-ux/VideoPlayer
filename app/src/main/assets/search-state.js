(function () {
  'use strict';
  // Inspect our hidden sibling branches without changing the site's own hidden styles.
  // Restoring these attributes synchronously prevents a visible page flash.
  const masked=Array.from(document.querySelectorAll('[data-vp-hidden]'));
  masked.forEach(function(node){node.removeAttribute('data-vp-hidden');});
  try {
    function visible(node) { return !!node && node.getBoundingClientRect().height > 0 && getComputedStyle(node).visibility !== 'hidden'; }
    const inputs = Array.from(document.querySelectorAll('input'));
    const captcha = inputs.some(function (node) {
      return visible(node) && /captcha|antyspam|anti.?spam|kod.z.obrazka/i.test(
        (node.name || '') + ' ' + node.id + ' ' + (node.getAttribute('placeholder') || ''));
    }) || Array.from(document.querySelectorAll('iframe[src*="recaptcha"],img[src*="captcha"]')).some(visible);
    function onscreen(node){
      if(!visible(node))return false;
      const r=node.getBoundingClientRect();
      const h=typeof window!=='undefined'&&window.innerHeight||Infinity,w=typeof window!=='undefined'&&window.innerWidth||Infinity;
      return typeof r.top!=='number'||r.bottom>0&&r.top<h&&r.right>0&&r.left<w;
    }
    const consent = Array.from(document.querySelectorAll('#qc-cmp2-ui,#onetrust-banner-sdk,.qc-cmp2-consent-info')).some(onscreen);
    const password = inputs.some(function (node) { return node.type === 'password' && onscreen(node); });
    return JSON.stringify({stage: captcha ? 'verification' : (consent ? 'consent' : (password ? 'login' : 'results')),
      anchors: Math.min(10000, document.querySelectorAll('a[href]').length)});
  } finally {
    masked.forEach(function(node){node.setAttribute('data-vp-hidden','');});
  }
}());

