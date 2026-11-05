(function(){
  'use strict';
  if(window.__vpStartOriginalPlayer)return;
  const armed=new WeakSet();
  window.__vpStartOriginalPlayer=function(player,notify){
    if(!player||armed.has(player))return;
    armed.add(player);
    let attempts=0,finished=false,timer;
    function stop(){finished=true;if(timer)clearInterval(timer);}
    function playing(){
      try{
        const video=document.querySelector('.jwplayer video.jw-video');
        return video&&video.currentSrc?!video.paused&&!video.ended:String(player.getState()).toLowerCase()==='playing';
      }catch(_){return false;}
    }
    function tick(){
      if(finished)return;
      if(playing()){stop();notify('autoplay-started',{});return;}
      if(document.hidden)return;
      if(++attempts>30){stop();notify('autoplay-expired',{});return;}
      notify('autoplay-request',{});
    }
    window.__vpCancelAutoStart=function(){if(!finished){stop();notify('autoplay-cancelled',{});}};
    document.addEventListener('pagehide',stop);
    timer=setInterval(tick,1500);tick();
  };
}());
