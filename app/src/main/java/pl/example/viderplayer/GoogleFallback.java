package pl.example.viderplayer;

import android.app.Activity;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Independent, bounded Google search with native-only results and manual verification. */
final class GoogleFallback {
    interface Listener {void results(JSONArray rows);void access();void closed();void failed(String message);}
    final WebView engine;
    private final Activity activity;
    private final FrameLayout root,host;
    private final SiteSource source;
    private final DiagnosticLog log;
    private final Listener listener;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String title="",destination="";
    private boolean running,disposed,paused,inAccess,returned;
    private int generation,httpError;
    private long deadline;
    private Runnable pending;
    private LinearLayout panel;
    private TvPointer pointer;
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    GoogleFallback(Activity activity,FrameLayout root,SiteSource source,DiagnosticLog log,Listener listener){
        this.activity=activity;this.root=root;this.source=source;this.log=log;this.listener=listener;
        host=new FrameLayout(activity);root.addView(host,0,new FrameLayout.LayoutParams(-1,-1));
        engine=TvUi.enabled(activity)?new TvWebView(activity):new WebView(activity);engine.setId(View.generateViewId());host.addView(engine,new FrameLayout.LayoutParams(-1,-1));engine.setVisibility(View.INVISIBLE);
        WebSettings s=engine.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);s.setMediaPlaybackRequiresUserGesture(true);s.setSupportMultipleWindows(true);s.setJavaScriptCanOpenWindowsAutomatically(false);s.setUseWideViewPort(true);s.setLoadWithOverviewMode(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(engine,true);
        engine.setWebChromeClient(new WebChromeClient(){@Override public boolean onCreateWindow(WebView v,boolean d,boolean g,Message m){return false;}});
        engine.setDownloadListener((url,ua,disposition,mime,length)->fail("Google zwrócił plik zamiast wyników."));
        engine.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                if(!request.isForMainFrame())return !"https".equalsIgnoreCase(request.getUrl().getScheme());
                String url=request.getUrl().toString();
                if(UrlPolicy.isMovie(url)&&SiteSource.fromUrl(url)==source&&panel!=null){
                    try{JSONArray rows=new JSONArray().put(new JSONObject().put("url",UrlPolicy.movieUrl(url)).put("title",title).put("thumbnail",""));complete(rows);}catch(Exception ignored){}
                    return true;
                }
                return !GoogleSearchPolicy.allowed(url);
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){httpError=0;}
            @Override public void onPageFinished(WebView v,String url){CookieManager.getInstance().flush();schedule(100);}
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame()&&running)fail("Nie udało się połączyć z Google. Spróbuj ponownie później.");}
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response){if(r.isForMainFrame())httpError=response.getStatusCode();}
        });
    }
    void start(String query){
        title=query==null?"":query.trim();destination=GoogleSearchPolicy.url(title,source);generation++;running=true;inAccess=false;returned=false;httpError=0;deadline=SystemClock.elapsedRealtime()+30000;
        log.add("search.google.start",null,source.ordinal());engine.post(()->{if(!disposed&&running)engine.loadUrl(destination);});schedule(500);
    }
    private String script(){try(java.io.InputStream in=activity.getAssets().open("google-results.js");java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8).replace("__VP_SOURCE__",JSONObject.quote(source.name()));}catch(Exception e){return "'{}'";}}
    private boolean valid(int token){return !disposed&&!paused&&running&&token==generation;}
    private void schedule(long delay){if(disposed||paused||!running)return;if(pending!=null)handler.removeCallbacks(pending);int token=generation;pending=()->{pending=null;if(valid(token))inspect(token);};handler.postDelayed(pending,delay);}
    private void inspect(int token){
        if(!valid(token))return;
        if(!GoogleSearchPolicy.allowed(engine.getUrl())){if(SystemClock.elapsedRealtime()>deadline)fail("Google nie otworzył wyszukiwania.");else schedule(500);return;}
        engine.evaluateJavascript(script(),raw->{
            if(!valid(token))return;
            try{
                if(raw==null||raw.length()>600000)throw new IllegalArgumentException();
                JSONObject report=new JSONObject((String)new JSONTokener(raw).nextValue());String page=report.optString("page");
                if(!GoogleSearchPolicy.allowed(page)||!page.equals(engine.getUrl())){schedule(500);return;}
                String stage=report.optString("stage");
                if("access".equals(stage)){
                    if(!inAccess){inAccess=true;log.add("search.google.access",null,0);listener.access();}
                    schedule(1000);return; // No timeout while the user completes Google's own form.
                }
                if(inAccess){inAccess=false;deadline=SystemClock.elapsedRealtime()+30000;hideAccess(false);}
                if(httpError>=400){fail("Google odpowiedział błędem HTTP "+httpError+". To nie oznacza braku filmu.");return;}
                if(!GoogleSearchPolicy.results(page,title,source)){
                    if(!returned){returned=true;engine.loadUrl(destination);schedule(500);return;}
                }else if("complete".equals(stage)){
                    JSONArray input=report.optJSONArray("rows"),rows=new JSONArray();java.util.Set<String> seen=new java.util.HashSet<>();
                    if(input!=null)for(int i=0;i<Math.min(24,input.length());i++){
                        JSONObject row=input.optJSONObject(i);if(row==null)continue;String url=row.optString("url"),name=row.optString("title").replaceAll("[\\p{Cntrl}]"," ").trim();
                        if(url.length()>4096||!UrlPolicy.isMovie(url)||SiteSource.fromUrl(url)!=source||name.isEmpty()||!seen.add(MovieLibrary.key(url)))continue;
                        String image=row.optString("thumbnail");boolean inline=image.length()<16000&&image.matches("(?s)^data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\r\\n]+$");
                        rows.put(new JSONObject().put("url",UrlPolicy.movieUrl(url)).put("title",name.substring(0,Math.min(180,name.length()))).put("thumbnail",inline||UrlPolicy.thumbnail(image)?image:""));
                    }
                    if(rows.length()==0&&!report.optBoolean("empty")){schedule(1000);if(SystemClock.elapsedRealtime()>deadline)fail("Nie rozpoznano wyników Google.");return;}
                    complete(rows);return;
                }
                if(SystemClock.elapsedRealtime()>deadline){fail("Google nie zwrócił rozpoznanych wyników w ciągu 30 sekund. Spróbuj ponownie później.");return;}
                schedule(1000);
            }catch(Exception e){if(SystemClock.elapsedRealtime()>deadline)fail("Nie udało się odczytać wyników Google.");else schedule(1000);}
        });
    }
    private void complete(JSONArray rows){if(!running)return;running=false;handler.removeCallbacksAndMessages(null);hideAccess(false);engine.onPause();log.add("search.google.finished",null,rows.length());listener.results(rows);}
    private void fail(String message){if(disposed||!running)return;running=false;handler.removeCallbacksAndMessages(null);hideAccess(false);engine.onPause();log.add("search.google.failed",null,0);listener.failed(message);}
    void showAccess(){
        if(disposed||!running||panel!=null)return;
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(12),dp(10),dp(12),dp(10));panel.setBackgroundColor(Color.rgb(17,24,32));
        TextView heading=new TextView(activity);heading.setText("Potwierdzenie Google · "+source.label);heading.setTextSize(20);heading.setTextColor(Color.WHITE);panel.addView(heading);
        TextView help=new TextView(activity);help.setText("Dokończ zgodę lub weryfikację na oryginalnej stronie Google. Potem wrócimy do kafelków wyłącznie z "+source.label+".");help.setTextColor(Color.LTGRAY);panel.addView(help);
        ((android.view.ViewGroup)engine.getParent()).removeView(engine);panel.addView(engine,new LinearLayout.LayoutParams(-1,0,1));engine.setVisibility(View.VISIBLE);engine.setFocusableInTouchMode(true);
        LinearLayout actions=new LinearLayout(activity);button(actions,"Sprawdź",()->schedule(50));button(actions,"Klawiatura",()->{engine.requestFocus();((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(engine,InputMethodManager.SHOW_IMPLICIT);});
        if(TvUi.enabled(activity))button(actions,"Strona",engine::requestFocus);
        if(TvUi.enabled(activity))button(actions,"Kursor",()->{if(pointer!=null)pointer.toggle();});
        button(actions,"Wróć",()->hideAccess(true));panel.addView(actions);root.addView(panel,new FrameLayout.LayoutParams(-1,-1));panel.bringToFront();
        if(TvUi.enabled(activity)){
            pointer=new TvPointer(root,engine,engine::requestFocus);java.util.List<View> buttons=new java.util.ArrayList<>();for(int i=0;i<actions.getChildCount();i++)buttons.add(actions.getChildAt(i));TvFocus.connect(java.util.Arrays.asList(buttons));actions.getChildAt(0).post(()->{if(panel!=null)actions.getChildAt(0).requestFocus();});
        }else engine.requestFocus();
    }
    private void button(LinearLayout row,String title,Runnable action){Button b=new Button(activity);b.setText(title);b.setTextSize(14);b.setTextColor(Color.WHITE);TvUi.focusable(b);b.setOnClickListener(v->action.run());row.addView(b);}
    private int dp(int value){return TvUi.dp(activity,value);}
    private void hideAccess(boolean notify){if(panel==null)return;if(pointer!=null){pointer.dispose();pointer=null;}((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(engine.getWindowToken(),0);panel.removeView(engine);host.addView(engine,new FrameLayout.LayoutParams(-1,-1));engine.setVisibility(View.INVISIBLE);root.removeView(panel);panel=null;if(notify)listener.closed();}
    boolean accessOpen(){return panel!=null;}
    boolean key(KeyEvent e){
        if(pointer!=null&&pointer.key(e))return true;
        if(panel!=null&&TvUi.enabled(activity)&&e.getKeyCode()==KeyEvent.KEYCODE_MENU){if(e.getAction()==KeyEvent.ACTION_UP&&pointer!=null)pointer.toggle();return true;}
        return false;
    }
    boolean back(){if(pointer!=null&&pointer.active()){pointer.stop();return true;}if(panel!=null){hideAccess(true);return true;}return false;}
    void pause(){paused=true;if(pointer!=null)pointer.stop();handler.removeCallbacksAndMessages(null);engine.onPause();}
    void resume(){if(disposed)return;paused=false;if(running){engine.onResume();if(!inAccess)deadline=SystemClock.elapsedRealtime()+30000;schedule(100);}}
    void dispose(){if(disposed)return;disposed=true;running=false;generation++;handler.removeCallbacksAndMessages(null);hideAccess(false);engine.stopLoading();if(engine.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)engine.getParent()).removeView(engine);root.removeView(host);engine.destroy();}
}
