(function(){
  'use strict';
  const source=__VP_SOURCE__, rows=new Map();
  const vider=['vider.info','www.vider.info','vider.pl','www.vider.pl'],freedisc=['freedisc.pl','www.freedisc.pl'];
  const google=['google.com','www.google.com','google.pl','www.google.pl'];
  function visible(node){if(!node)return false;const r=node.getBoundingClientRect(),s=getComputedStyle(node);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden';}
  const captcha=location.pathname.startsWith('/sorry')||Array.from(document.querySelectorAll('iframe[src*="recaptcha"],form[action*="Captcha"],#captcha-form')).some(visible);
  const consent=location.hostname.startsWith('consent.google.')||Array.from(document.querySelectorAll('form[action*="consent.google"]')).some(visible);
  if(captcha||consent)return JSON.stringify({page:location.href,stage:'access',rows:[]});
  for(const anchor of document.querySelectorAll('a[href]')){
    try{
      let url=new URL(anchor.getAttribute('href'),location.href);
      if(google.includes(url.hostname)&&url.pathname==='/url')url=new URL(url.searchParams.get('q')||url.searchParams.get('url')||'');
      if(!['https:','http:'].includes(url.protocol)||url.username||url.password||url.port&&url.port!=='443')continue;
      const path=decodeURIComponent(url.pathname);let id,destination,slug='';
      if(source==='FREEDISC'){
        if(!freedisc.includes(url.hostname))continue;
        const file=path.match(/^\/[^/,]+,f-([0-9]+)(?:,([^/]+))?\/?$/),embed=path.match(/^\/embed\/video\/([0-9]+)\/?$/);
        if(!file&&!embed)continue;id='freedisc:'+(file||embed)[1];slug=file&&file[2]||'';
        if(/(?:\.|-)(mp3|pdf|jpg|png|zip|rar)$/i.test(slug))continue;
        destination='https://'+url.hostname+url.pathname+(embed&&url.searchParams.get('match')==='1'?'?match=1':'');
      }else{
        if(!vider.includes(url.hostname)||path.startsWith('/dir/'))continue;
        const file=path.match(/\/([^/]*?)\+(f[A-Za-z0-9]+)\/?$/),embed=path.match(/^\/embed\/video\/([A-Za-z0-9]+)\/?$/);
        if(!file&&!embed)continue;const key=file?file[2]:'f'+embed[1];id='vider:'+key;slug=file?file[1]:'';destination='https://'+url.hostname+'/vid/+'+key;
      }
      const heading=anchor.querySelector('h3,[role="heading"]');
      let title=(heading?heading.textContent:anchor.getAttribute('title')||anchor.textContent||'').replace(/\s+/g,' ').trim();
      if(!title||/^https?:\/\//i.test(title))title=slug.replace(/[-_]+/g,' ').trim()||'Film · '+id.split(':')[1];
      const image=anchor.querySelector('img');let thumbnail=image?(image.currentSrc||image.getAttribute('data-src')||image.src||''):'';
      if(thumbnail.startsWith('data:')){if(!/^data:image\/(png|jpeg|webp);base64,/i.test(thumbnail)||thumbnail.length>16000)thumbnail='';}
      else if(thumbnail){const poster=new URL(thumbnail,location.href);if(poster.protocol!=='https:'||poster.username||poster.password||poster.port&&poster.port!=='443'||!(['img.vider.info','img.freedisc.pl','gstatic.com'].includes(poster.hostname)||poster.hostname.endsWith('.gstatic.com')))thumbnail='';else thumbnail=poster.href;}
      if(!rows.has(id)&&rows.size<20)rows.set(id,{url:destination,title:title.slice(0,180),thumbnail});
    }catch(_){}
  }
  const emptyNode=document.querySelector('#topstuff');
  const empty=!!emptyNode&&/did not match any documents|no results found|nie znaleziono wyników|nie pasują do żadnych dokumentów|brak wyników/i.test(emptyNode.textContent||'');
  return JSON.stringify({page:location.href,stage:rows.size||empty?'complete':'pending',empty,rows:Array.from(rows.values())});
}());
