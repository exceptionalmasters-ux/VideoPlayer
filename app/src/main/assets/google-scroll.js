(function(){
  'use strict';
  const direction=__VP_DIRECTION__;
  const height=window.innerHeight,width=window.innerWidth;
  function candidate(node){
    if(!node||node.clientHeight<60||node.scrollHeight<=node.clientHeight+2)return false;
    const r=node.getBoundingClientRect(),s=getComputedStyle(node);
    return r.bottom>0&&r.top<height&&r.right>0&&r.left<width&&s.display!=='none'&&s.visibility!=='hidden'&&/(auto|scroll)/.test(s.overflowY)&& (direction>0?node.scrollTop+node.clientHeight<node.scrollHeight-2:node.scrollTop>0);
  }
  let chosen=null,node=document.elementFromPoint(width/2,height/2);
  for(;node;node=node.parentElement)if(candidate(node)){chosen=node;break;}
  if(!chosen){let best=0;for(const element of document.querySelectorAll('body *')){if(!candidate(element))continue;const r=element.getBoundingClientRect(),area=Math.max(0,Math.min(width,r.right)-Math.max(0,r.left))*Math.max(0,Math.min(height,r.bottom)-Math.max(0,r.top));if(area>best){best=area;chosen=element;}}}
  if(chosen){chosen.scrollBy(0,direction*Math.max(100,chosen.clientHeight*0.7));return true;}
  window.scrollBy(0,direction*Math.max(100,height*0.7));return true;
}());
