(function () {
  'use strict';
  const code = __VP_MANUAL_CODE__;
  const fields = Array.from(document.querySelectorAll('input'));
  const input = fields.find(function (node) {
    return !node.disabled && !node.readOnly && node.getBoundingClientRect().height > 0 &&
      /captcha|antyspam|anti.?spam|kod.z.obrazka/i.test(
        (node.name || '') + ' ' + node.id + ' ' + (node.getAttribute('placeholder') || ''));
  });
  if (!input) return false;
  input.value = code;
  input.dispatchEvent(new Event('input', {bubbles: true}));
  input.dispatchEvent(new Event('change', {bubbles: true}));
  return true;
}());
