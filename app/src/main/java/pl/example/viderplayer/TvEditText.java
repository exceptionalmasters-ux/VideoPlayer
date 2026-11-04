package pl.example.viderplayer;

import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.view.KeyEvent;
import android.view.WindowInsets;
import android.widget.EditText;

/** Keep a closed TV IME from consuming physical Enter as a request to reopen it. */
final class TvEditText extends EditText {
    private Runnable confirm;
    TvEditText(Context context){super(context);}
    void confirmation(Runnable action){confirm=action;}
    private boolean keyboardVisible(){
        WindowInsets insets=getRootWindowInsets();
        if(Build.VERSION.SDK_INT>=30&&insets!=null)return insets.isVisible(WindowInsets.Type.ime());
        Rect visible=new Rect();getWindowVisibleDisplayFrame(visible);
        return getRootView().getHeight()-visible.height()>TvUi.dp(getContext(),120);
    }
    @Override public boolean dispatchKeyEventPreIme(KeyEvent event){
        int key=event.getKeyCode();
        if(TvUi.enabled(getContext())&&confirm!=null&&length()>0
            &&(key==KeyEvent.KEYCODE_ENTER||key==KeyEvent.KEYCODE_NUMPAD_ENTER)&&!keyboardVisible()){
            if(event.getAction()==KeyEvent.ACTION_UP)confirm.run();
            return true;
        }
        return super.dispatchKeyEventPreIme(event);
    }
}
