(function () {
  'use strict';
  function restore() {
    const saved = window.__vpCodeLayout;
    if (!saved) return;
    const style = document.getElementById('vp-code-layout-style'); if (style) style.remove();
    if(saved.image&&saved.position)saved.image.removeEventListener('load',saved.position);
    document.querySelectorAll('[data-vp-code-region],[data-vp-code-parent]').forEach(function (node) {
      node.removeAttribute('data-vp-code-region'); node.removeAttribute('data-vp-code-parent');
    });
    if (saved.created) saved.viewport.remove();
    else if (saved.content === null) saved.viewport.removeAttribute('content');
    else saved.viewport.setAttribute('content',saved.content);
    delete window.__vpCodeLayout;
  }
  const input = Array.from(document.querySelectorAll('input')).find(function (node) {
    return !node.disabled && !node.readOnly && node.getBoundingClientRect().height > 0 &&
      /captcha|antyspam|anti.?spam|kod.z.obrazka/i.test((node.name || '') + ' ' + node.id + ' ' + (node.getAttribute('placeholder') || ''));
  });
  if (!input) { restore(); return false; }
  if (window.__vpCodeLayout && window.__vpCodeLayout.input === input) {
    const saved=window.__vpCodeLayout;
    if(saved.image&&saved.image.isConnected===false)restore();
    else{if(saved.width!==window.innerWidth||saved.height!==window.innerHeight)saved.position();return true;}
  }
  restore();
  let region = input.closest('form') || input.parentElement;
  if (!region || region === document.body) region = input.parentElement || document.body;
  let viewport = document.querySelector('meta[name="viewport"]');
  const created = !viewport;
  if (!viewport) { viewport = document.createElement('meta'); viewport.name = 'viewport'; document.head.appendChild(viewport); }
  window.__vpCodeLayout = {input,viewport,created,content:viewport.getAttribute('content')};
  viewport.setAttribute('content','width=device-width,initial-scale=1,user-scalable=yes');
  region.setAttribute('data-vp-code-region','');
  let parent = input.parentElement;
  while (parent) { parent.setAttribute('data-vp-code-parent',''); parent = parent.parentElement; }
  const style = document.createElement('style'); style.id = 'vp-code-layout-style';
  style.textContent = 'html,body{width:100%!important;min-width:0!important;max-width:100%!important;margin:0!important;padding:0!important;overflow-x:hidden!important}' +
    '[data-vp-code-parent]{min-width:0!important;max-width:100%!important;width:100%!important;margin-left:0!important;margin-right:0!important;padding-left:0!important;padding-right:0!important;transform:none!important}' +
    '[data-vp-code-region]{box-sizing:border-box!important;width:calc(100% - 24px)!important;min-width:0!important;max-width:480px!important;margin:12px auto!important;padding:12px!important;float:none!important;position:relative!important;left:auto!important;right:auto!important;font-size:18px!important;transform:none!important}' +
    '[data-vp-code-region] input:not([type=hidden]){box-sizing:border-box!important;min-width:0!important;max-width:100%!important;width:100%!important;min-height:48px!important;font-size:20px!important}' +
    '[data-vp-code-region] img,[data-vp-code-region] canvas{max-width:100%!important;height:auto!important}' +
    '[data-vp-code-region] button,[data-vp-code-region] input[type=submit]{min-height:48px!important;font-size:18px!important}';
  document.head.appendChild(style);
  const saved=window.__vpCodeLayout;
  const images=Array.from(region.querySelectorAll('img,canvas'));
  let image=images.find(function(node){return /captcha|antyspam|obraz|kod/i.test((node.id||'')+' '+(node.src||'')+' '+(node.alt||''));})||images[0];
  if(!image){image=Array.from(document.querySelectorAll('img')).find(function(node){return /captcha|antyspam/i.test((node.id||'')+' '+(node.src||''))&&node.getBoundingClientRect().height>0;});}
  saved.position=function(){
    if(window.__vpCodeLayout!==saved)return;
    saved.width=window.innerWidth;saved.height=window.innerHeight;
    (image||region).scrollIntoView({block:'center',behavior:'auto'});
  };
  saved.image=image;
  if(image)image.addEventListener('load',saved.position);
  saved.position();
  return true;
}());

