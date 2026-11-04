package pl.example.viderplayer;

import android.view.KeyEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import java.util.*;

/** Explicit remote routes through visible native control rows. */
final class TvFocus {
    private TvFocus(){}
    static boolean visible(View view){
        if(!view.isEnabled()||!view.isFocusable())return false;
        for(View v=view;v!=null;){if(v.getVisibility()!=View.VISIBLE)return false;ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}
        return true;
    }
    static View column(List<View> row,int index,int count){
        return row.get(count<=1?0:Math.round(index*(row.size()-1f)/(count-1f)));
    }
    static List<List<View>> connect(List<List<View>> rows){
        List<List<View>> live=new ArrayList<>();
        for(List<View> row:rows){List<View> shown=new ArrayList<>();for(View v:row)if(visible(v))shown.add(v);if(!shown.isEmpty())live.add(shown);}
        for(int r=0;r<live.size();r++)for(int c=0;c<live.get(r).size();c++){
            List<View> row=live.get(r);View v=row.get(c);if(v.getId()==View.NO_ID)v.setId(View.generateViewId());
            v.setNextFocusLeftId(row.get(Math.max(0,c-1)).getId());v.setNextFocusRightId(row.get(Math.min(row.size()-1,c+1)).getId());
            v.setNextFocusUpId(r==0?v.getId():column(live.get(r-1),c,row.size()).getId());
            v.setNextFocusDownId(r==live.size()-1?v.getId():column(live.get(r+1),c,row.size()).getId());
            if(!(v instanceof EditText))v.setOnKeyListener((view,key,event)->{
                int id=key==KeyEvent.KEYCODE_DPAD_UP?view.getNextFocusUpId():key==KeyEvent.KEYCODE_DPAD_DOWN?view.getNextFocusDownId():key==KeyEvent.KEYCODE_DPAD_LEFT?view.getNextFocusLeftId():key==KeyEvent.KEYCODE_DPAD_RIGHT?view.getNextFocusRightId():View.NO_ID;
                if(id==View.NO_ID)return false;
                if(event.getAction()==KeyEvent.ACTION_DOWN){View target=view.getRootView().findViewById(id);if(target!=null)target.requestFocus();}return true;
            });
        }
        return live;
    }
}
