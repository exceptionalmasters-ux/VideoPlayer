package pl.example.viderplayer;
import android.content.*;
import android.view.View;
import android.widget.GridLayout;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.SystemClock;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CollectionsTest {
    private static Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
    private static void show(SearchActivity activity,String collection){try{Method m=SearchActivity.class.getDeclaredMethod("showCollection",String.class);m.setAccessible(true);m.invoke(activity,collection);}catch(Exception e){throw new AssertionError(e);}}
    private static void clickDialog(String text){
        long end=SystemClock.elapsedRealtime()+5000;
        do{AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            if(root!=null)for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text))if(n.isClickable()&&text.equalsIgnoreCase(n.getText()==null?"":n.getText().toString())){assertTrue(n.performAction(AccessibilityNodeInfo.ACTION_CLICK));return;}
            SystemClock.sleep(100);
        }while(SystemClock.elapsedRealtime()<end);
        fail("Missing dialog button: "+text);
    }
    private static void restore(SharedPreferences prefs,Map<String,?> values){SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> i:values.entrySet()){Object v=i.getValue();if(v instanceof String)e.putString(i.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(i.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(i.getKey(),(Long)v);}assertTrue(e.commit());}
    @Test public void migrationPersistentTilesAndClearConfirmationPreserveFavorites()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs=context.getSharedPreferences("movie_library",0),legacy=context.getSharedPreferences("MainActivity",0);
        Map<String,?> previous=new HashMap<>(prefs.getAll()),old=new HashMap<>(legacy.getAll());
        String a="https://vider.info/vid/+fhistorytest",b="https://freedisc.pl/test,f-123,ronin-mp4";
        try{
            assertTrue(prefs.edit().clear().commit());assertTrue(legacy.edit().putString("bookmarks","[{\"url\":\""+a+"\",\"title\":\"Maverick\"}]").commit());
            LocalLibrary library=new LocalLibrary(context);assertTrue(library.favorite(a));
            library.opened(a,"Maverick","");library.progress(a,3723,7200,false);library.opened(b,"Ronin","");library.opened("https://vider.pl/vid/+fhistorytest","","");
            assertEquals(2,new LocalLibrary(context).items("history").length());
            assertEquals(3723,new LocalLibrary(context).find(a).position);
            assertEquals(3723,new LocalLibrary(context).items("favorites").getJSONObject(0).getLong("position"));
            try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(new Intent(context,SearchActivity.class).putExtra("collection","history"))){
                scenario.onActivity(activity->{try{assertEquals(2,((GridLayout)field(activity,"grid")).getChildCount());((View)field(activity,"clearCollection")).performClick();}catch(Exception e){throw new AssertionError(e);}});
                clickDialog("Anuluj");assertEquals(2,new LocalLibrary(context).items("history").length());
            assertEquals(3723,new LocalLibrary(context).find(a).position);
            assertEquals(3723,new LocalLibrary(context).items("favorites").getJSONObject(0).getLong("position"));
                scenario.onActivity(activity->{try{((View)field(activity,"clearCollection")).performClick();}catch(Exception e){throw new AssertionError(e);}});
                clickDialog("Wyczyść");InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                scenario.onActivity(activity->{try{assertEquals(0,((GridLayout)field(activity,"grid")).getChildCount());show(activity,"favorites");assertEquals(1,((GridLayout)field(activity,"grid")).getChildCount());}catch(Exception e){throw new AssertionError(e);}});
                assertTrue(new LocalLibrary(context).favorite(a));
                library.remove("favorites",a);assertFalse(new LocalLibrary(context).favorite(a));
                scenario.onActivity(activity->{try{show(activity,"favorites");assertEquals(0,((GridLayout)field(activity,"grid")).getChildCount());}catch(Exception e){throw new AssertionError(e);}});
            }
        }finally{restore(prefs,previous);restore(legacy,old);}
    }

    @Test public void fullHistoryIsPagedAndKeepsResumePositionsAcrossRecreation()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs=context.getSharedPreferences("movie_library",0);Map<String,?> previous=new HashMap<>(prefs.getAll());
        org.json.JSONArray history=new org.json.JSONArray();
        for(int i=0;i<MovieLibrary.HISTORY_LIMIT;i++)history.put(new org.json.JSONObject().put("url","https://vider.info/vid/+fbulk"+i).put("title","Historia serialu S01E"+i).put("opened",200-i).put("position",i==124?3723:0).put("duration",7200));
        String stored=history.toString();
        try{
            assertTrue(prefs.edit().clear().putBoolean("migrated",true).putString("history",stored).putString("favorites",new org.json.JSONArray().put(history.getJSONObject(0)).toString()).commit());
            try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(new Intent(context,SearchActivity.class).putExtra("collection","history"))){
                final int[] pageSize={0},lastFirst={0};
                scenario.onActivity(activity->{try{GridLayout grid=(GridLayout)field(activity,"grid");CollectionPage page=(CollectionPage)field(activity,"currentPage");pageSize[0]=page.size;assertEquals(200,page.total);assertEquals(page.size,grid.getChildCount());assertTrue(grid.getChildCount()<=24);}catch(Exception e){throw new AssertionError(e);}});
                int pages=(200+pageSize[0]-1)/pageSize[0];
                for(int n=1;n<pages;n++){
                    scenario.onActivity(activity->{try{((View)field(activity,"pageNext")).performClick();CollectionPage page=(CollectionPage)field(activity,"currentPage");GridLayout grid=(GridLayout)field(activity,"grid");assertTrue(grid.getChildCount()<=24);assertEquals(page.end-page.first,grid.getChildCount());lastFirst[0]=page.first;}catch(Exception e){throw new AssertionError(e);}});
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                }
                scenario.onActivity(activity->{try{CollectionPage page=(CollectionPage)field(activity,"currentPage");assertEquals(page.pages,page.number);assertFalse(((View)field(activity,"pageNext")).isEnabled());((View)field(activity,"pagePrevious")).performClick();lastFirst[0]=((CollectionPage)field(activity,"currentPage")).first;}catch(Exception e){throw new AssertionError(e);}});
                scenario.recreate();
                scenario.onActivity(activity->{try{CollectionPage page=(CollectionPage)field(activity,"currentPage");assertEquals(lastFirst[0],page.first);assertTrue(((GridLayout)field(activity,"grid")).getChildCount()<=24);}catch(Exception e){throw new AssertionError(e);}});
            }
            assertEquals(stored,prefs.getString("history",""));assertEquals(200,new LocalLibrary(context).items("history").length());assertEquals(3723,new LocalLibrary(context).find("https://vider.info/vid/+fbulk124").position);assertEquals(1,new LocalLibrary(context).items("favorites").length());
        }finally{restore(prefs,previous);}
    }
    @Test public void malformedCollectionTypeDoesNotCrashOrEraseOtherLists()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();SharedPreferences prefs=context.getSharedPreferences("movie_library",0);Map<String,?> previous=new HashMap<>(prefs.getAll());
        try{
            assertTrue(prefs.edit().clear().putBoolean("migrated",true).putInt("history",7).putString("favorites","[{\"url\":\"https://vider.info/vid/+ffavorite\",\"title\":\"Ulubiony\"}]").commit());
            LocalLibrary library=new LocalLibrary(context);assertEquals(0,library.items("history").length());assertEquals(1,library.items("favorites").length());assertEquals(7,prefs.getInt("history",0));
            try(ActivityScenario<SearchActivity> scenario=ActivityScenario.launch(new Intent(context,SearchActivity.class).putExtra("collection","history"))){scenario.onActivity(activity->{try{assertEquals(0,((GridLayout)field(activity,"grid")).getChildCount());}catch(Exception e){throw new AssertionError(e);}});}
        }finally{restore(prefs,previous);}
    }
}


