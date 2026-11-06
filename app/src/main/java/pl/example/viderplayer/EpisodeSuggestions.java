package pl.example.viderplayer;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.*;
import android.view.inputmethod.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Native end screen and access dialog above the existing movie WebView (including fullscreen). */
final class EpisodeSuggestions {
    interface Host { void open(JSONObject item); void manual(String query,SiteSource source); void beforePanel(); void restoreFocus(); }
    private final Activity activity;
    private final FrameLayout root;
    private final DiagnosticLog diagnostics;
    private final Host host;
    private final boolean tv;
    private final ThumbnailLoader thumbnails=new ThumbnailLoader();
    private String movieKey="",error="",rendered="",enteredCode="",accessStage="";
    private SiteSource source;
    EpisodeFlow flow;
    EpisodeSearch worker;
    JSONArray rows=new JSONArray();
    LinearLayout panel,accessPanel;
    GridLayout cards;
    EditText code;
    Button notice;
    private TvPointer accessPointer;
    private boolean disposed,paused;
    private String deferredMovie,deferredTitle;
    private long deferredPosition,deferredDuration;
    private boolean deferredEnded;
    EpisodeSuggestions(Activity activity,FrameLayout root,DiagnosticLog diagnostics,Host host){
        this.activity=activity;this.root=root;this.diagnostics=diagnostics;this.host=host;tv=TvUi.enabled(activity);
        notice=new Button(activity);notice.setTextColor(Color.WHITE);notice.setBackgroundColor(Color.rgb(29,62,70));notice.setTextSize(tv?16:13);
        TvUi.focusable(notice);notice.setVisibility(View.GONE);notice.setOnClickListener(v->showAccess());
        FrameLayout.LayoutParams place=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);place.setMargins(dp(12),dp(12),dp(12),dp(12));root.addView(notice,place);
        root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(panel!=null&&(r-l!=or-ol||b-t!=ob-ot)){rendered="";root.post(()->{if(!disposed&&panel!=null)showPanel();});}});
    }
    void observe(String movie,String title,long position,long duration,boolean ended){
        if(disposed||!UrlPolicy.isMovie(movie)||duration<=0||duration>86400||position<0||position>duration+2)return;
        if(paused){deferredMovie=movie;deferredTitle=title;deferredPosition=position;deferredDuration=duration;deferredEnded=ended;return;}
        String key=MovieLibrary.key(movie);
        if(!movieKey.equals(key)){reset();movieKey=key;source=SiteSource.fromUrl(movie);}
        if(flow==null){EpisodePlan current=EpisodePlan.parse(title);if(current==null)return;flow=new EpisodeFlow(current);}
        String query=flow.playback(position,duration,ended);
        if(query!=null)search().start(query);
        update();
    }
    EpisodeSearch search(){
        if(worker==null){
            worker=new EpisodeSearch(activity,root,source,diagnostics,new EpisodeSearch.Listener(){
                @Override public void searching(){if(disposed||flow==null)return;flow.searching();error="";accessStage="";update();}
                @Override public void access(String stage){if(disposed||flow==null)return;boolean reopen=accessPanel!=null&&!stage.equals(accessStage);accessStage=stage;flow.access();if(reopen)closeAccess();update();if(reopen&&accessPanel==null)showAccess();}
                @Override public void accessClosed(){if(flow!=null&&flow.ended)flow.dismissed=true;update();host.restoreFocus();}
                @Override public void failed(String message){if(disposed||flow==null)return;flow.failed();error=message;update();}
                @Override public void results(JSONArray found){
                    if(disposed||flow==null)return;
                    JSONArray matching=new JSONArray();
                    for(int i=0;i<found.length()&&matching.length()<8;i++){
                        JSONObject item=found.optJSONObject(i);if(item==null)continue;
                        boolean match=flow.target!=null&&flow.target.matches(item.optString("title"));
                        if(!match&&source==SiteSource.FREEDISC){String path=android.net.Uri.parse(item.optString("url")).getLastPathSegment();if(path!=null&&path.contains(","))match=flow.target!=null&&flow.target.matches(path.substring(path.lastIndexOf(',')+1));}
                        if(match)matching.put(item);
                    }
                    rows=matching;
                    String next=flow.results(rows.length()>0);
                    if(next!=null){diagnostics.add("episode.search.next_season",null,0);search().start(next);}else update();
                }
            });
        }
        return worker;
    }
    private void update(){
        if(disposed||paused||flow==null)return;
        if(accessPanel!=null&&flow.stage!=EpisodeFlow.Stage.ACCESS)closeAccess();
        if(panel!=null&&!flow.nearEnd&&!flow.ended){removePanel();host.restoreFocus();}
        notice.setText(tv?"Kolejny odcinek wymaga potwierdzenia · ↓":"Kolejny odcinek wymaga potwierdzenia · dotknij");
        notice.setVisibility(flow.stage==EpisodeFlow.Stage.ACCESS&&(!flow.ended||flow.dismissed)&&accessPanel==null?View.VISIBLE:View.GONE);
        if(notice.getVisibility()==View.VISIBLE)notice.bringToFront();
        if((flow.ended||flow.nearEnd&&flow.stage==EpisodeFlow.Stage.READY)&&!flow.dismissed){
            if(flow.stage==EpisodeFlow.Stage.ACCESS){if(accessPanel==null)showAccess();}
            else if(accessPanel==null)showPanel();
        }
    }
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(dp(8),dp(4),dp(8),dp(4));return v;}
    private Button button(LinearLayout row,String value,Runnable action){
        Button v=new Button(activity);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(tv?15:12);v.setMinWidth(0);v.setMinimumWidth(0);TvUi.focusable(v);v.setOnClickListener(w->action.run());
        row.addView(v,new LinearLayout.LayoutParams(-2,dp(48)));return v;
    }
    private void removePanel(){if(panel!=null){root.removeView(panel);panel=null;cards=null;}rendered="";}
    private String query(){return flow==null?"":flow.target==null?flow.current.query():flow.target.query();}
    private void showPanel(){
        String marker=flow.stage+"|"+query()+"|"+rows.length()+"|"+error;
        if(panel!=null&&marker.equals(rendered))return;
        removePanel();host.beforePanel();thumbnails.nextGeneration();
        panel=new LinearLayout(activity);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(tv?24:8),dp(10),dp(tv?24:8),dp(10));panel.setBackgroundColor(Color.argb(245,17,24,32));
        TextView heading=text("Kolejny odcinek",tv?23:21);heading.setTypeface(null,Typeface.BOLD);panel.addView(heading);panel.addView(text(query(),tv?17:15));
        String state=flow.stage==EpisodeFlow.Stage.READY?"Wybierz wersję odcinka":flow.stage==EpisodeFlow.Stage.EMPTY?"Nie znaleziono kolejnego odcinka ani pierwszego odcinka następnego sezonu.":flow.stage==EpisodeFlow.Stage.FAILED?error:"Szukam kolejnego odcinka…";
        panel.addView(text(state,tv?16:14));
        ScrollView scroll=new ScrollView(activity);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        cards=new GridLayout(activity);int columns=tv?5:3;cards.setColumnCount(columns);scroll.addView(cards);
        List<List<View>> focusRows=new ArrayList<>();List<View> current=new ArrayList<>();
        int width=(activity.getResources().getDisplayMetrics().widthPixels-dp(tv?72:32))/columns;
        if(flow.stage==EpisodeFlow.Stage.READY)for(int i=0;i<rows.length();i++){
            JSONObject item=rows.optJSONObject(i);if(item==null)continue;
            LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(4),dp(4),dp(4),dp(4));card.setBackgroundColor(Color.rgb(29,40,53));
            card.setFocusable(true);card.setFocusableInTouchMode(true);card.setId(View.generateViewId());TvUi.focusable(card);card.setDescendantFocusability(android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS);
            GridLayout.LayoutParams size=new GridLayout.LayoutParams();size.width=width;size.height=dp(tv?136:122);size.setMargins(dp(2),dp(2),dp(2),dp(2));cards.addView(card,size);
            ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setBackgroundColor(Color.rgb(36,61,72));card.addView(image,new LinearLayout.LayoutParams(-1,dp(tv?72:58)));
            TextView name=text(item.optString("title"),tv?15:13);name.setMaxLines(2);name.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(name);
            card.setContentDescription("Otwórz kolejny odcinek: "+item.optString("title"));card.setOnClickListener(v->host.open(item));
            thumbnails.load(image,item.optString("url"),item.optString("thumbnail"),worker==null?"":worker.engine.getSettings().getUserAgentString());
            current.add(card);if(current.size()==columns){focusRows.add(current);current=new ArrayList<>();}
        }
        if(!current.isEmpty())focusRows.add(current);
        LinearLayout actions=new LinearLayout(activity);panel.addView(actions);
        List<View> footer=new ArrayList<>();
        if(flow.stage==EpisodeFlow.Stage.FAILED)footer.add(button(actions,"Ponów",()->{String q=flow.retry();if(q!=null)search().start(q);}));
        footer.add(button(actions,"Szukaj ręcznie",()->host.manual(query(),source)));
        footer.add(button(actions,"Zamknij",this::dismiss));focusRows.add(footer);TvFocus.connect(focusRows);
        int available=root.getHeight()>0?root.getHeight():activity.getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams pos=new FrameLayout.LayoutParams(-1,Math.min(available,Math.max(dp(310),available*2/5)),Gravity.BOTTOM);root.addView(panel,pos);panel.bringToFront();rendered=marker;
        if(tv){View first=cards.getChildCount()>0?cards.getChildAt(0):footer.get(0);first.post(first::requestFocus);}
        diagnostics.add("episode.panel.shown",null,rows.length());
    }
    void showAccess(){
        if(disposed||worker==null||flow==null||flow.stage!=EpisodeFlow.Stage.ACCESS||accessPanel!=null)return;
        removePanel();host.beforePanel();notice.setVisibility(View.GONE);flow.dismissed=false;
        if(worker.google!=null){worker.google.showAccess();return;}
        accessPanel=new LinearLayout(activity);accessPanel.setOrientation(LinearLayout.VERTICAL);accessPanel.setPadding(dp(tv?24:8),dp(8),dp(tv?24:8),dp(8));accessPanel.setBackgroundColor(Color.rgb(17,24,32));
        accessPanel.addView(text("Potwierdź dostęp · "+query(),tv?20:18));
        accessPanel.addView(text("Wpisz kod ręcznie lub dokończ zgodę / logowanie na stronie. Potem automatycznie wznowimy to samo wyszukiwanie.",tv?15:13));
        LinearLayout codeRow=new LinearLayout(activity);
        code=tv?new TvEditText(activity):new EditText(activity);code.setTextColor(Color.WHITE);code.setHintTextColor(Color.LTGRAY);code.setHint("Kod z obrazka");code.setSingleLine(true);code.setTextSize(tv?18:16);code.setImeOptions(EditorInfo.IME_ACTION_DONE);code.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        code.setText(enteredCode);TvUi.editor(code,this::submit);code.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_DONE||action==EditorInfo.IME_ACTION_GO||action==EditorInfo.IME_ACTION_SEARCH){submit();return true;}return false;});
        codeRow.addView(code,new LinearLayout.LayoutParams(0,dp(52),1));Button submit=button(codeRow,"Zatwierdź",this::submit);accessPanel.addView(codeRow);
        if(worker.engine.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)worker.engine.getParent()).removeView(worker.engine);
        accessPanel.addView(worker.engine,new LinearLayout.LayoutParams(-1,0,1));worker.engine.setVisibility(View.VISIBLE);
        LinearLayout actions=new LinearLayout(activity);accessPanel.addView(actions);
        Button check=button(actions,"Sprawdź",worker::check),keyboard=button(actions,"Klawiatura",()->{
            if(codeRow.getVisibility()==View.VISIBLE){code.requestFocus();((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(code,InputMethodManager.SHOW_IMPLICIT);}else worker.engine.requestFocus();
        });
        if(tv)button(actions,"Kursor",()->{if(accessPointer!=null)accessPointer.toggle();});
        button(actions,"Wróć do filmu",()->{if(flow!=null&&flow.ended)flow.dismissed=true;closeAccess();update();host.restoreFocus();});
        root.addView(accessPanel,new FrameLayout.LayoutParams(-1,-1));accessPanel.bringToFront();
        worker.engine.evaluateJavascript(worker.asset("verification-layout.js"),result->{
            if(accessPanel==null)return;boolean captcha="true".equals(result);codeRow.setVisibility(captcha?View.VISIBLE:View.GONE);keyboard.setText(captcha?"Klawiatura":"Strona");
            TvFocus.connect(Arrays.asList(Arrays.asList(code,submit),children(actions)));
            if(captcha){code.requestFocus();if(!tv)code.post(()->((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(code,InputMethodManager.SHOW_IMPLICIT));}
            else check.requestFocus();
        });
        if(tv){((TvWebView)worker.engine).codePage("verification".equals(accessStage),worker::submitExistingCode);if(accessPointer==null)accessPointer=new TvPointer(root,worker.engine,()->{if(accessPanel!=null&&code!=null&&code.isShown())code.requestFocus();});}
        worker.check();
    }
    private List<View> children(LinearLayout row){List<View> list=new ArrayList<>();for(int i=0;i<row.getChildCount();i++)list.add(row.getChildAt(i));return list;}
    private void submit(){if(code==null||worker==null)return;if(code.length()==0){code.setError("Wpisz kod z obrazka");return;}worker.submitCode(code.getText().toString());((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(code.getWindowToken(),0);}
    private void closeAccess(){
        if(accessPanel==null)return;
        enteredCode=flow!=null&&flow.stage==EpisodeFlow.Stage.ACCESS&&code!=null?code.getText().toString():"";
        if(accessPointer!=null)accessPointer.stop();
        ((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(accessPanel.getWindowToken(),0);
        if(worker!=null){if(tv)((TvWebView)worker.engine).codePage(false,null);accessPanel.removeView(worker.engine);worker.host.addView(worker.engine,new FrameLayout.LayoutParams(-1,-1));worker.engine.setVisibility(View.INVISIBLE);}
        root.removeView(accessPanel);accessPanel=null;code=null;host.restoreFocus();
    }
    void overlayTop(){if(accessPanel!=null){if(accessPointer!=null)accessPointer.stop();accessPanel.bringToFront();}else if(panel!=null)panel.bringToFront();else if(notice.getVisibility()==View.VISIBLE)notice.bringToFront();}
    boolean modal(){return panel!=null||accessPanel!=null||worker!=null&&worker.google!=null&&worker.google.accessOpen();}
    boolean key(KeyEvent event,boolean fullscreen){
        if(worker!=null&&worker.google!=null&&worker.google.key(event))return true;
        if(accessPointer!=null&&accessPointer.key(event))return true;
        if(tv&&accessPanel!=null&&event.getKeyCode()==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP&&accessPointer!=null)accessPointer.toggle();return true;}
        if(tv&&accessPanel==null&&panel==null&&fullscreen&&notice.getVisibility()==View.VISIBLE&&event.getKeyCode()==KeyEvent.KEYCODE_DPAD_DOWN){if(event.getAction()==KeyEvent.ACTION_UP)showAccess();return true;}
        if(tv&&accessPanel!=null&&worker.engine.hasFocus()){
            int k=event.getKeyCode();if(k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_DOWN||k==KeyEvent.KEYCODE_DPAD_LEFT||k==KeyEvent.KEYCODE_DPAD_RIGHT){
                if(event.getAction()==KeyEvent.ACTION_UP){int meta=k==KeyEvent.KEYCODE_DPAD_UP||k==KeyEvent.KEYCODE_DPAD_LEFT?KeyEvent.META_SHIFT_ON:0;worker.engine.dispatchKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_TAB,0,meta));worker.engine.dispatchKeyEvent(new KeyEvent(0,0,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_TAB,0,meta));}return true;
            }
        }
        return false;
    }
    boolean back(){
        if(worker!=null&&worker.google!=null&&worker.google.back())return true;
        if(accessPointer!=null&&accessPointer.active()){accessPointer.stop();return true;}
        if(accessPanel!=null){if(flow!=null&&flow.ended)flow.dismissed=true;closeAccess();update();return true;}
        if(panel!=null){dismiss();return true;}return false;
    }
    private void dismiss(){if(flow!=null)flow.dismissed=true;removePanel();host.restoreFocus();}
    void reset(){
        closeAccess();removePanel();notice.setVisibility(View.GONE);if(accessPointer!=null){accessPointer.dispose();accessPointer=null;}
        if(worker!=null){worker.dispose();worker=null;}flow=null;rows=new JSONArray();movieKey="";error="";enteredCode="";accessStage="";deferredMovie=null;deferredTitle=null;thumbnails.nextGeneration();
    }
    void pause(){paused=true;if(accessPointer!=null)accessPointer.stop();if(worker!=null)worker.pause();}
    void resume(){paused=false;if(worker!=null)worker.resume();if(deferredMovie!=null){String movie=deferredMovie,title=deferredTitle;long position=deferredPosition,duration=deferredDuration;boolean ended=deferredEnded;deferredMovie=null;deferredTitle=null;observe(movie,title,position,duration,ended);}update();}
    void dispose(){if(disposed)return;reset();disposed=true;thumbnails.close();root.removeView(notice);}
    private int dp(int value){return TvUi.dp(activity,value);}
}
