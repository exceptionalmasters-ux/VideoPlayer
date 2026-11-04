package pl.example.viderplayer;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

final class TvUi {
    private TvUi() {}
    static boolean enabled(Context context) {
        UiModeManager mode=(UiModeManager)context.getSystemService(Context.UI_MODE_SERVICE);
        return mode!=null && mode.getCurrentModeType()==Configuration.UI_MODE_TYPE_TELEVISION
            || context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }
    static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static GradientDrawable shape(Context context,boolean focused) {
        GradientDrawable shape=new GradientDrawable();
        shape.setColor(focused?Color.rgb(42,70,78):Color.rgb(29,40,53));
        shape.setCornerRadius(dp(context,10));
        shape.setStroke(dp(context,focused?3:1),focused?Color.rgb(114,220,196):Color.rgb(54,70,83));
        return shape;
    }
    static void focusable(View view) {
        Context context=view.getContext();
        if(!enabled(context))return;
        if(view.getId()==View.NO_ID)view.setId(View.generateViewId());
        view.setFocusable(true);view.setFocusableInTouchMode(true);
        if(view instanceof android.widget.TextView)((android.widget.TextView)view).setTextColor(Color.WHITE);
        StateListDrawable background=new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_focused},shape(context,true));
        background.addState(new int[]{android.R.attr.state_pressed},shape(context,true));
        background.addState(new int[]{},shape(context,false));
        view.setBackground(background);
        view.setOnFocusChangeListener((v,focused)->{
            v.setElevation(dp(context,focused?8:0));
            if(focused)v.requestRectangleOnScreen(new android.graphics.Rect(0,0,v.getWidth(),v.getHeight()),false);
        });
    }
    static void editor(EditText input) { editor(input,null); }
    static void editor(EditText input,Runnable submit) {
        if(!enabled(input.getContext()))return;
        focusable(input);
        input.setTextColor(Color.WHITE);input.setHintTextColor(Color.rgb(187,205,211));
        input.setHighlightColor(Color.argb(120,114,220,196));input.setCursorVisible(true);input.setTextSize(18);
        input.setShowSoftInputOnFocus(false);
        View.OnFocusChangeListener decoration=input.getOnFocusChangeListener();
        input.setOnFocusChangeListener((v,focused)->{
            if(decoration!=null)decoration.onFocusChange(v,focused);
            if(!focused){input.setShowSoftInputOnFocus(false);((InputMethodManager)input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);}
        });
        input.setPadding(dp(input.getContext(),12),0,dp(input.getContext(),12),0);
        input.setOnKeyListener((v,key,event)->{
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN){
                if(event.getAction()==KeyEvent.ACTION_DOWN){View next=input.focusSearch(key==KeyEvent.KEYCODE_DPAD_UP?View.FOCUS_UP:View.FOCUS_DOWN);if(next!=null)next.requestFocus();}
                return true;
            }
            if(key!=KeyEvent.KEYCODE_DPAD_CENTER&&key!=KeyEvent.KEYCODE_ENTER&&key!=KeyEvent.KEYCODE_NUMPAD_ENTER)return false;
            if(event.getAction()==KeyEvent.ACTION_UP){
                boolean confirm=submit!=null&&input.length()>0&&(key!=KeyEvent.KEYCODE_DPAD_CENTER||input.getShowSoftInputOnFocus());
                if(confirm)submit.run();else keyboard(input);
            }
            return true;
        });
    }
    static void keyboard(EditText input){
        if(enabled(input.getContext()))input.setShowSoftInputOnFocus(true);
        input.requestFocus();
        input.post(()->{if(input.isShown())((InputMethodManager)input.getContext().getSystemService(Context.INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_IMPLICIT);});
    }
    static void immersive(Activity activity) {
        if(enabled(activity))activity.getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}

