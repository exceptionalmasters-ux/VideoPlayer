(function () {
  'use strict';
  function editable(node) {
    if (!node || node.disabled || node.readOnly || node.getBoundingClientRect().height <= 0) return false;
    if (node.isContentEditable || node.tagName === 'TEXTAREA') return true;
    return node.tagName === 'INPUT' && ['text', 'password', 'email', 'tel', 'number', 'search', 'url'].includes(node.type);
  }
  let input = editable(document.activeElement) ? document.activeElement : null;
  if (!input) {
    input = Array.from(document.querySelectorAll('input')).find(function (node) {
      return editable(node) && /captcha|antyspam|anti.?spam|kod.z.obrazka/i.test(
        (node.name || '') + ' ' + node.id + ' ' + (node.getAttribute('placeholder') || ''));
    });
  }
  if (!input) return false;
  input.focus();
  input.scrollIntoView({block: 'center', behavior: 'auto'});
  return true;
}());
