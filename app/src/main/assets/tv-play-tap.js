(function () {
  'use strict';
  if(window.__vpTvTapPlay)return;
  const origins=['https://freedisc.pl','https://www.freedisc.pl','https://stream.freedisc.pl','https://stream2.freedisc.pl','https://ovh.freedisc.pl',
    'https://vider.pl','https://www.vider.pl','https://vider.info','https://www.vider.info'];
  let parentOrigin='';
  window.__vpTvTouchParent=function(origin){if(origins.includes(origin))parentOrigin=origin;};
  function normalized(point){
    const viewport=window.visualViewport;
    const width=viewport?viewport.width:window.innerWidth,height=viewport?viewport.height:window.innerHeight;
    const x=(point.x-(viewport?viewport.offsetLeft:0))/width,y=(point.y-(viewport?viewport.offsetTop:0))/height;
    return Number.isFinite(x)&&Number.isFinite(y)&&x>=0&&x<=1&&y>=0&&y<=1?{x,y}:null;
  }
  function send(id,point){
    const n=normalized(point);if(!n||typeof id!=='string'||!id||id.length>80)return false;
    const message={type:'vp-tv-play-tap',id,x:n.x,y:n.y};
    if(window.parent===window){
      if(!window.vpTvTouch)return false;
      window.vpTvTouch.postMessage(JSON.stringify(message));
    }else{
      if(!parentOrigin)return false;
      window.parent.postMessage(message,parentOrigin);
    }
    return true;
  }
  window.__vpTvTapPlay=function(id){
    if(!id)return false;
    // Activate the original Play control with a real native touch, not an untrusted DOM click.
    const controls=document.querySelectorAll('.jwplayer .jw-display-icon-display,.jwplayer .jw-display-icon-display .jw-icon-display,.jwplayer .jw-icon-playback');
    for(const control of controls){
      if(/pause|pauza|wstrzymaj/i.test(control.getAttribute('aria-label')||''))continue;
      const r=control.getBoundingClientRect(),style=getComputedStyle(control);
      if(r.width<=0||r.height<=0||style.display==='none'||style.visibility==='hidden')continue;
      const point={x:r.left+r.width/2,y:r.top+r.height/2};
      const hit=document.elementFromPoint(point.x,point.y);
      // Never click an advertisement, a verification form or another overlay above the control.
      if(hit!==control&&!control.contains(hit))continue;
      if(send(id,point))return true;
    }
    return false;
  };
  window.addEventListener('message',function(event){
    const data=event.data;
    if(!data||data.type!=='vp-tv-play-tap'||!origins.includes(event.origin)||typeof data.id!=='string'||data.id.length>80
      ||!Number.isFinite(data.x)||!Number.isFinite(data.y)||data.x<0||data.x>1||data.y<0||data.y>1)return;
    for(const frame of document.querySelectorAll('iframe[src]')){
      if(frame.contentWindow!==event.source)continue;
      try{
        const u=new URL(frame.src,location.href);
        if(u.origin!==event.origin||!origins.includes(u.origin)||u.username||u.password||!/^\/embed(?:\/|,)/.test(u.pathname))return;
        const r=frame.getBoundingClientRect();if(!frame.offsetWidth||!frame.offsetHeight)return;
        const sx=r.width/frame.offsetWidth,sy=r.height/frame.offsetHeight;
        const point={x:r.left+(frame.clientLeft+data.x*frame.clientWidth)*sx,y:r.top+(frame.clientTop+data.y*frame.clientHeight)*sy};
        const hit=document.elementFromPoint(point.x,point.y);
        if(hit!==frame)return;
        send(data.id,point);
      }catch(_){}
      return;
    }
  });
}());
