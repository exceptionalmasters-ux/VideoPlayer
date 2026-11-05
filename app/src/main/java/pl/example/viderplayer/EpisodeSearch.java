package pl.example.viderplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.*;
import android.view.View;
import android.webkit.*;
import android.widget.FrameLayout;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** A separate search document: it never navigates, reloads or pauses the current movie. */
final class EpisodeSearch {
    interface Listener {
        void searching();
        void access(String stage);
        void results(JSONArray rows);
        void failed(String message);
    }
    final WebView engine;
    final FrameLayout host;
    private final Activity activity;
    private final SiteSource source;
    private final Listener listener;
    private final DiagnosticLog diagnostics;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean running,paused,disposed,loaded,routed,returnedHome,inAccess,accessSubmitted,returnToQuery;
    private int generation,routeAttempts,httpError;
    private String target,accessStage="";
    private EpisodePlan wanted;
    private long deadline,lastSubmit;
    private Runnable pending;
    @SuppressLint("SetJavaScriptEnabled")
    EpisodeSearch(Activity activity,FrameLayout root,SiteSource source,DiagnosticLog diagnostics,Listener listener){
        this.activity=activity;this.source=source;this.listener=listener;this.diagnostics=diagnostics;
        host=new FrameLayout(activity);root.addView(host,0,new FrameLayout.LayoutParams(-1,-1));
        engine=TvUi.enabled(activity)?new TvWebView(activity):new WebView(activity);
        engine.setId(View.generateViewId());host.addView(engine,new FrameLayout.LayoutParams(-1,-1));
        engine.setVisibility(View.INVISIBLE);
        WebSettings settings=engine.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportMultipleWindows(true);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setUserAgentString(source.userAgent(settings.getUserAgentString()));
        settings.setUseWideViewPort(source==SiteSource.FREEDISC);settings.setLoadWithOverviewMode(source==SiteSource.FREEDISC);
        CookieManager.getInstance().setAcceptThirdPartyCookies(engine,true);
        engine.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView view,boolean dialog,boolean gesture,android.os.Message result){return false;}
        });
        engine.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return !"https".equalsIgnoreCase(request.getUrl().getScheme());
                String url=request.getUrl().toString();
                if(source==SiteSource.FREEDISC&&SiteSource.isMobileFreediscUrl(url)){
                    if(running&&!returnedHome){returnedHome=true;handler.post(()->{if(running)engine.loadUrl(source.home+"/");});}
                    else if(running)fail("Freedisc nie otworzył wyszukiwarki. Spróbuj wyszukać ręcznie.");
                    return true;
                }
                return SiteSource.fromUrl(url)!=source||UrlPolicy.isMovie(url);
            }
            @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){loaded=false;httpError=0;if(inAccess){accessSubmitted=true;deadline=SystemClock.elapsedRealtime()+30000;}}
            @Override public void onPageFinished(WebView view,String url){loaded=SiteSource.fromUrl(view.getUrl())==source;CookieManager.getInstance().flush();schedule(100);}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(running&&request.isForMainFrame())fail("Nie udało się połączyć z "+source.label+". Możesz ponowić to samo wyszukiwanie.");}
            @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse response){if(running&&request.isForMainFrame())httpError=response.getStatusCode();}
        });
    }
    String asset(String name){
        try(java.io.InputStream in=activity.getAssets().open(name);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);
        }catch(java.io.IOException e){return "''";}
    }
    void start(String query){
        if(disposed)return;
        generation++;handler.removeCallbacksAndMessages(null);running=false;engine.stopLoading();
        wanted=EpisodePlan.parse(query);target=source.searchUrl(query);httpError=0;accessSubmitted=false;loaded=false;routed=false;returnedHome=false;routeAttempts=0;inAccess=false;accessStage="";returnToQuery=false;
        running=true;deadline=SystemClock.elapsedRealtime()+30000;diagnostics.add("episode.search.start",null,0);listener.searching();
        engine.onResume();
        engine.loadUrl(source==SiteSource.FREEDISC?source.home+"/":target);schedule(1000);
    }
    private boolean valid(int token){return !disposed&&!paused&&running&&generation==token;}
    private void schedule(long delay){
        if(disposed||paused||!running)return;
        if(pending!=null)handler.removeCallbacks(pending);
        final int token=generation;pending=()->{pending=null;if(valid(token))inspect(token);};handler.postDelayed(pending,delay);
    }
    private boolean onTarget(){return target!=null&&engine.getUrl()!=null&&android.net.Uri.parse(target).getPath().equals(android.net.Uri.parse(engine.getUrl()).getPath());}
    private static JSONObject object(String raw)throws Exception{return new JSONObject((String)new JSONTokener(raw).nextValue());}
    private void inspect(int token){
        if(!valid(token))return;
        if((!inAccess||accessSubmitted&&!loaded)&&SystemClock.elapsedRealtime()>deadline){fail("Wyszukiwanie w "+source.label+" nie zakończyło się. Nie przechodzę automatycznie do następnego sezonu.");return;}
        if(!loaded||SiteSource.fromUrl(engine.getUrl())!=source){schedule(500);return;}
        engine.evaluateJavascript(asset("search-state.js"),value->{
            if(!valid(token))return;
            if(!loaded){schedule(500);return;}
            try{
                String stage=object(value).optString("stage","results");
                if(!"results".equals(stage)){
                    inAccess=true;accessSubmitted=false;
                    if(!stage.equals(accessStage)){accessStage=stage;diagnostics.add("episode.search.access",null,"verification".equals(stage)?1:"consent".equals(stage)?2:3);listener.access(stage);}
                    if(engine.getVisibility()==View.VISIBLE)engine.evaluateJavascript(asset("verification-layout.js"),null);
                    schedule(1000);return; // No deadline or season fallback while the user is entering a code.
                }
                if(inAccess){inAccess=false;accessSubmitted=false;httpError=0;accessStage="";returnToQuery=!onTarget();routed=false;returnedHome=false;routeAttempts=0;deadline=SystemClock.elapsedRealtime()+30000;listener.searching();}
                if(httpError>=400){fail(source.label+" odpowiedział błędem HTTP "+httpError+". To nie oznacza końca sezonu.");return;}
                if(UrlPolicy.isHome(engine.getUrl())||returnToQuery){
                    if(source==SiteSource.FREEDISC){
                        if(routed){fail("Freedisc wrócił na stronę główną. Ponów to samo wyszukiwanie lub użyj wyszukiwania ręcznego.");return;}
                        if(++routeAttempts>20){fail("Moduł wyszukiwarki Freedisc nie uruchomił się.");return;}
                        engine.evaluateJavascript(asset("freedisc-search.js").replace("__VP_SEARCH_URL__",JSONObject.quote(target)),sent->{
                            if(!valid(token))return;
                            if("\"routed\"".equals(sent)){routed=true;returnToQuery=false;}
                            else if("\"error\"".equals(sent)||"\"invalid\"".equals(sent)){fail("Nie udało się uruchomić wyszukiwarki Freedisc.");return;}
                            schedule(1000);
                        });return;
                    }
                    if(returnedHome){fail("Serwis ponownie przekierował wyszukiwanie na stronę główną.");return;}
                    returnedHome=true;returnToQuery=false;loaded=false;engine.loadUrl(target);schedule(1000);return;
                }
                if(!onTarget()){schedule(1000);return;}
                read(token);
            }catch(Exception e){fail("Nie udało się rozpoznać stanu wyszukiwania. Spróbuj ręcznie.");}
        });
    }
    private void read(int token){
        engine.evaluateJavascript(asset("search-outcome.js"),raw->{
            if(!valid(token))return;
            if(!loaded||!onTarget()){schedule(500);return;}
            try{
                JSONObject outcome=object(raw);
                if(outcome.optBoolean("loading")||source==SiteSource.FREEDISC&&!outcome.optBoolean("scoped")){schedule(1000);return;}
                engine.evaluateJavascript(asset("search-results.js"),value->{
                    if(!valid(token))return;
                    if(!loaded||!onTarget()){schedule(500);return;}
                    try{
                        if(value==null||value.length()>600000){fail("Odpowiedź wyszukiwania jest zbyt duża.");return;}
                        JSONArray rows=new JSONArray((String)new JSONTokener(value).nextValue()),accepted=new JSONArray();
                        java.util.Set<String> seen=new java.util.HashSet<>();
                        for(int i=0;i<Math.min(rows.length(),20);i++){
                            JSONObject item=rows.optJSONObject(i);if(item==null)continue;
                            String url=item.optString("url"),title=item.optString("title").replaceAll("[\\p{Cntrl}]"," ").trim();
                            if(url.length()>4096||!UrlPolicy.isMovie(url)||SiteSource.fromUrl(url)!=source||title.isEmpty()||!seen.add(MovieLibrary.key(url)))continue;
                            JSONObject safe=new JSONObject().put("url",url).put("title",title.substring(0,Math.min(title.length(),180)));
                            String thumbnail=item.optString("thumbnail");safe.put("thumbnail",UrlPolicy.thumbnail(thumbnail)?thumbnail:"");accepted.put(safe);
                        }
                        boolean matching=false;
                        for(int i=0;i<accepted.length();i++){
                            JSONObject item=accepted.optJSONObject(i);
                            if(wanted!=null&&wanted.matches(item.optString("title")))matching=true;
                            if(!matching&&wanted!=null&&source==SiteSource.FREEDISC){String path=android.net.Uri.parse(item.optString("url")).getLastPathSegment();if(path!=null&&path.contains(","))matching=wanted.matches(path.substring(path.lastIndexOf(',')+1));}
                        }
                        if(matching||outcome.optBoolean("empty")||outcome.optBoolean("scoped")&&outcome.optInt("links")>0){
                            running=false;handler.removeCallbacksAndMessages(null);engine.onPause();diagnostics.add("episode.search.finished",null,accepted.length());listener.results(accepted);
                        }else schedule(1000);
                    }catch(Exception e){fail("Nie udało się odczytać miniatur kolejnego odcinka.");}
                });
            }catch(Exception e){fail("Nie rozpoznano odpowiedzi wyszukiwarki.");}
        });
    }
    void check(){if(running)schedule(50);}
    void submitCode(String text){
        if(!running||!inAccess)return;
        final int token=generation;
        engine.evaluateJavascript(asset("verification-fill.js").replace("__VP_MANUAL_CODE__",JSONObject.quote(text.trim())),value->{if(valid(token)&&"true".equals(value))submitExistingCode();});
    }
    void submitExistingCode(){
        if(!running||!inAccess||SystemClock.elapsedRealtime()-lastSubmit<600)return;
        lastSubmit=SystemClock.elapsedRealtime();
        engine.evaluateJavascript(asset("verification-submit.js"),value->{if("true".equals(value)){accessSubmitted=true;deadline=SystemClock.elapsedRealtime()+30000;}CookieManager.getInstance().flush();schedule(300);});
    }
    private void fail(String message){if(disposed||!running)return;running=false;handler.removeCallbacksAndMessages(null);engine.onPause();diagnostics.add("episode.search.failed",null,0);listener.failed(message);}
    void pause(){paused=true;handler.removeCallbacksAndMessages(null);engine.onPause();}
    void resume(){if(disposed)return;paused=false;if(running){engine.onResume();deadline=SystemClock.elapsedRealtime()+30000;schedule(100);}}
    void dispose(){if(disposed)return;disposed=true;running=false;generation++;handler.removeCallbacksAndMessages(null);engine.stopLoading();if(engine.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)engine.getParent()).removeView(engine);if(host.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)host.getParent()).removeView(host);engine.destroy();}
}
