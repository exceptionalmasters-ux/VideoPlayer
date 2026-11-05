(function(){
  'use strict';
  const scope=document.querySelector('#search_files_result')||document.querySelector('.search-results,.search-result-container')||document.body;
  if(!scope)return JSON.stringify({empty:false,loading:true,links:0,scoped:false});
  const visible=n=>!!n&&n.getBoundingClientRect().height>0&&getComputedStyle(n).visibility!=='hidden';
  const loading=Array.from(scope.querySelectorAll('.icon-spinner,.fa-spinner,[aria-busy="true"]')).some(visible);
  // innerText excludes the site's hidden script templates containing an empty-result message.
  const text=(scope.innerText||'').replace(/\s+/g,' ').trim();
  const empty=!loading&&/brak\s+(?:wyników|wynikow|filmów|filmow|plików|plikow)|nie znaleziono\s+(?:żadnych\s+|zadnych\s+)?(?:wyników|wynikow|filmów|filmow|plików|plikow)|no results found/i.test(text);
  let links=0;
  for(const a of scope.querySelectorAll('a[href]')){
    try{const u=new URL(a.getAttribute('href'),location.href);if(/^https?:$/.test(u.protocol)&&['vider.info','vider.pl','www.vider.info','www.vider.pl','freedisc.pl','www.freedisc.pl'].includes(u.hostname)&&(!u.port||u.port==='443')&&!u.username&&!u.password&&(/,f-\d+/.test(u.pathname)||/\/vid\/[^/]*\+f[a-z0-9]+/i.test(u.pathname)))links++;}catch(_){}
  }
  return JSON.stringify({empty,loading,scoped:scope!==document.body,links:Math.min(10000,links)});
}());
