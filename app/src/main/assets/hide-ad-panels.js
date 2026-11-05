(function(){
  'use strict';
  // Only the two independently timed Freedisc image panels are known here.
  if(!/^https:\/\/(www\.)?freedisc\.pl$/.test(location.origin))return;
  function install(){
    if(!document.head||document.getElementById('vp-ad-panels-style'))return;
    const style=document.createElement('style');style.id='vp-ad-panels-style';
    style.textContent='.reklama-content-1,.reklama-content-2{opacity:0!important;pointer-events:none!important}';
    document.head.appendChild(style);
  }
  document.addEventListener('DOMContentLoaded',install);
  new MutationObserver(install).observe(document,{childList:true,subtree:true});install();
}());
