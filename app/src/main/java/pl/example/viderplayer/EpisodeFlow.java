package pl.example.viderplayer;

/** Search completion, not a timeout or CAPTCHA, is the only season-fallback signal. */
final class EpisodeFlow {
    enum Stage { IDLE, SEARCHING, ACCESS, READY, EMPTY, FAILED }
    final EpisodePlan current;
    EpisodePlan target;
    Stage stage=Stage.IDLE;
    boolean seasonFallback,ended,dismissed;
    EpisodeFlow(EpisodePlan current){this.current=current;}
    String playback(long position,long duration,boolean complete){
        if(duration<=0||duration>86400||position<0||position>duration+2||complete&&duration-position>2)return null;
        if(ended&&!complete&&position<duration-60){ended=false;dismissed=false;}
        if(complete)ended=true;
        if(stage!=Stage.IDLE||!complete&&(duration-position>30||duration-position<0))return null;
        target=current.next();if(target==null){stage=Stage.EMPTY;return null;}
        stage=Stage.SEARCHING;return target.query();
    }
    String results(boolean matching){
        if(stage!=Stage.SEARCHING&&stage!=Stage.ACCESS)return null;
        if(matching){stage=Stage.READY;return null;}
        if(!seasonFallback){seasonFallback=true;target=current.nextSeason();if(target!=null){stage=Stage.SEARCHING;return target.query();}}
        stage=Stage.EMPTY;return null;
    }
    void access(){if(stage==Stage.SEARCHING)stage=Stage.ACCESS;}
    void searching(){if(stage==Stage.ACCESS||stage==Stage.FAILED)stage=Stage.SEARCHING;}
    void failed(){if(stage!=Stage.READY&&stage!=Stage.EMPTY)stage=Stage.FAILED;}
    String retry(){if(target==null)return null;searching();return target.query();}
}
