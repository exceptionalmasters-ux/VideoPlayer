(function () {
  'use strict';
  const input=Array.from(document.querySelectorAll('input')).find(function(node){
    return !node.disabled&&!node.readOnly&&node.value&&node.getBoundingClientRect().height>0&&
      /captcha|antyspam|anti.?spam|kod.z.obrazka/i.test((node.name||'')+' '+node.id+' '+(node.getAttribute('placeholder')||''));
  });
  if(!input)return false;
  let scope=input.parentElement;
  for(let depth=0;scope&&depth<6;depth++,scope=scope.parentElement){
    const button=Array.from(scope.querySelectorAll('button,input[type=submit],a[role=button]')).find(function(node){
      return !node.disabled&&node.getBoundingClientRect().height>0&&
        /wchodz|wejd|zatwierd|potwierd|sprawd|kontynuuj|odblokuj|dalej/i.test(node.textContent||node.value||'');
    });
    if(button){button.click();return true;}
    if(scope===document.body||scope.tagName==='FORM')break;
  }
  return false;
}());
