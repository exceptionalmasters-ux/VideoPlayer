package pl.example.viderplayer;

import android.graphics.*;
import android.os.SystemClock;
import android.view.*;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Button;

/** Optional, visible remote cursor. Every click is explicitly initiated with OK. */
final class TvPointer extends View {
    private final FrameLayout root;
    private final WebView target;
    private final Runnable stopped;
    private final Button close;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float x,y;
    private boolean active;
    TvPointer(FrameLayout root,WebView target,Runnable stopped){
        super(root.getContext());this.root=root;this.target=target;this.stopped=stopped;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);setClickable(false);
        root.addView(this,new FrameLayout.LayoutParams(-1,-1));setVisibility(GONE);
        close=new Button(root.getContext());close.setText("Wyłącz kursor");close.setTextSize(16);TvUi.focusable(close);close.setFocusable(false);close.setFocusableInTouchMode(false);close.setOnClickListener(v->stop());
        FrameLayout.LayoutParams position=new FrameLayout.LayoutParams(-2,TvUi.dp(getContext(),48),Gravity.TOP|Gravity.END);position.setMargins(TvUi.dp(getContext(),12),TvUi.dp(getContext(),8),TvUi.dp(getContext(),12),0);
        root.addView(close,position);close.setVisibility(GONE);
    }
    boolean active(){return active;}
    boolean toggle(){
        if(active){stop();return false;}
        if(!TvUi.enabled(getContext())||!target.isShown()||target.getWidth()==0||target.getHeight()==0)return false;
        int[] web=new int[2],host=new int[2];target.getLocationOnScreen(web);root.getLocationOnScreen(host);
        x=web[0]-host[0]+target.getWidth()/2f;y=web[1]-host[1]+target.getHeight()/2f;active=true;setVisibility(VISIBLE);close.setVisibility(VISIBLE);bringToFront();target.requestFocus();invalidate();return true;
    }
    void stop(){if(!active)return;active=false;setVisibility(GONE);close.setVisibility(GONE);stopped.run();}
    boolean key(KeyEvent event){
        if(!active)return false;
        if(!target.isShown()){stop();return false;}
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_BACK||key==KeyEvent.KEYCODE_MENU){if(event.getAction()==KeyEvent.ACTION_UP)stop();return true;}
        if(key==KeyEvent.KEYCODE_DPAD_CENTER||key==KeyEvent.KEYCODE_ENTER){if(event.getAction()==KeyEvent.ACTION_UP)click();return true;}
        if(key!=KeyEvent.KEYCODE_DPAD_UP&&key!=KeyEvent.KEYCODE_DPAD_DOWN&&key!=KeyEvent.KEYCODE_DPAD_LEFT&&key!=KeyEvent.KEYCODE_DPAD_RIGHT)return false;
        if(event.getAction()==KeyEvent.ACTION_DOWN){
            float step=TvUi.dp(getContext(),event.getRepeatCount()>8?40:16);
            if(key==KeyEvent.KEYCODE_DPAD_LEFT)x-=step;else if(key==KeyEvent.KEYCODE_DPAD_RIGHT)x+=step;else if(key==KeyEvent.KEYCODE_DPAD_UP)y-=step;else y+=step;
            x=Math.max(1,Math.min(root.getWidth()-1,x));y=Math.max(1,Math.min(root.getHeight()-1,y));invalidate();
        }return true;
    }
    void dispose(){stop();root.removeView(this);root.removeView(close);}
    private void click(){
        long time=SystemClock.uptimeMillis();
        MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(time,time+50,MotionEvent.ACTION_UP,x,y,0);
        root.dispatchTouchEvent(down);root.dispatchTouchEvent(up);down.recycle();up.recycle();
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);if(!active)return;
        int[] overlay=new int[2],host=new int[2];getLocationOnScreen(overlay);root.getLocationOnScreen(host);
        float cx=host[0]-overlay[0]+x,cy=host[1]-overlay[1]+y,r=TvUi.dp(getContext(),10);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(TvUi.dp(getContext(),5));paint.setColor(Color.BLACK);canvas.drawCircle(cx,cy,r,paint);
        paint.setStrokeWidth(TvUi.dp(getContext(),2));paint.setColor(Color.rgb(114,255,215));canvas.drawCircle(cx,cy,r,paint);
        canvas.drawLine(cx-r-5,cy,cx+r+5,cy,paint);canvas.drawLine(cx,cy-r-5,cx,cy+r+5,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);canvas.drawCircle(cx,cy,TvUi.dp(getContext(),2),paint);
    }
}
