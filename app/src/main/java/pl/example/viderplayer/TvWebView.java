package pl.example.viderplayer;

import android.content.Context;
import android.os.Build;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.WindowInsets;
import android.view.inputmethod.*;
import android.webkit.WebView;
import org.json.*;

/** Preserve WebView typing, but commit a manually entered CAPTCHA with the TV IME action. */
final class TvWebView extends WebView {
    private volatile boolean codePage,codeFocused,filled;
    private boolean pending;
    private Runnable confirm;
    private long lastConfirm;
    TvWebView(Context context){super(context);}
    void codePage(boolean enabled,Runnable action){boolean changed=codePage!=enabled;codePage=enabled;confirm=action;if(!enabled){codeFocused=false;filled=false;}else if(changed){probeCodeFocus();if(hasFocus())post(()->((InputMethodManager)getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).restartInput(this));}}
    private void probeCodeFocus(){
        if(!codePage||!UrlPolicy.allowed(getUrl()))return;
        evaluateJavascript("(function(){var e=document.activeElement;var ok=!!e&&e.tagName==='INPUT'&&!e.disabled&&!e.readOnly&&e.type!=='password'&&/captcha|antyspam|anti.?spam|kod.z.obrazka|przepisz/i.test((e.name||'')+' '+e.id+' '+(e.getAttribute('placeholder')||''));return JSON.stringify({code:ok,filled:ok&&!!e.value});}())",value->{
            if(!codePage)return;
            try{JSONObject data=new JSONObject((String)new JSONTokener(value).nextValue());codeFocused=data.optBoolean("code");filled=data.optBoolean("filled");}catch(Exception ignored){}
        });
    }
    private void requestConfirmation(){
        if(!codePage||confirm==null||pending)return;
        long now=android.os.SystemClock.uptimeMillis();if(now-lastConfirm<600)return;
        lastConfirm=now;pending=true;
        postDelayed(()->{pending=false;if(codePage&&confirm!=null&&UrlPolicy.allowed(getUrl()))confirm.run();},80);
    }
    @Override public InputConnection onCreateInputConnection(EditorInfo info){
        InputConnection original=super.onCreateInputConnection(info);
        if(original==null||!TvUi.enabled(getContext())||!codePage)return original;
        int action=info.imeOptions&EditorInfo.IME_MASK_ACTION;
        info.imeOptions=(info.imeOptions&~EditorInfo.IME_MASK_ACTION)|EditorInfo.IME_ACTION_DONE;
        post(this::probeCodeFocus);
        return new InputConnectionWrapper(original,false){
            @Override public boolean commitText(CharSequence text,int position){boolean result=super.commitText(text,position);if(text!=null&&text.length()>0)filled=true;postDelayed(TvWebView.this::probeCodeFocus,50);return result;}
            @Override public boolean setComposingText(CharSequence text,int position){boolean result=super.setComposingText(text,position);if(text!=null&&text.length()>0)filled=true;postDelayed(TvWebView.this::probeCodeFocus,50);return result;}
            @Override public boolean performEditorAction(int requested){
                if(codePage&&codeFocused&&hasFocus()){super.finishComposingText();post(TvWebView.this::requestConfirmation);return true;}
                return super.performEditorAction(action);
            }
        };
    }
    private boolean keyboardVisible(){
        WindowInsets insets=getRootWindowInsets();if(Build.VERSION.SDK_INT>=30&&insets!=null)return insets.isVisible(WindowInsets.Type.ime());
        Rect r=new Rect();getWindowVisibleDisplayFrame(r);return getRootView().getHeight()-r.height()>TvUi.dp(getContext(),120);
    }
    boolean confirmCodeKey(KeyEvent event){
        int key=event.getKeyCode();
        if(TvUi.enabled(getContext())&&codePage&&codeFocused&&filled&&hasFocus()&&!keyboardVisible()
            &&(key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER)){
            if(event.getAction()==KeyEvent.ACTION_UP)requestConfirmation();return true;
        }return false;
    }
    @Override public boolean dispatchKeyEventPreIme(KeyEvent event){return confirmCodeKey(event)||super.dispatchKeyEventPreIme(event);}
}
