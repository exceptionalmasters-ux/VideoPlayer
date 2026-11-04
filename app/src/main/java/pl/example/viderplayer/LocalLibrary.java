package pl.example.viderplayer;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** One preferences file shared by search and player activities. */
public final class LocalLibrary {
    private final SharedPreferences prefs;
    public LocalLibrary(Context context){
        prefs=context.getSharedPreferences("movie_library",Context.MODE_PRIVATE);
        if(!prefs.getBoolean("migrated",false)){
            MovieLibrary library=read();
            for(MovieLibrary.Item item:decode(context.getSharedPreferences("MainActivity",Context.MODE_PRIVATE).getString("bookmarks","[]")))
                if(!library.favorite(item.url))library.toggle(item);
            save(library);prefs.edit().putBoolean("migrated",true).apply();
        }
    }
    private static List<MovieLibrary.Item> decode(String raw){
        List<MovieLibrary.Item> items=new ArrayList<>();
        try{JSONArray array=new JSONArray(raw);for(int i=0;i<array.length();i++){
            JSONObject o=array.optJSONObject(i);if(o!=null&&UrlPolicy.isMovie(o.optString("url")))items.add(new MovieLibrary.Item(o.optString("url"),o.optString("title"),o.optString("thumbnail"),o.optLong("opened"),o.optLong("position"),o.optLong("duration"),o.optBoolean("completed")));
        }}catch(JSONException ignored){}
        return items;
    }
    public static JSONObject json(MovieLibrary.Item item){
        JSONObject o=new JSONObject();try{o.put("url",item.url).put("title",item.title).put("thumbnail",item.thumbnail).put("opened",item.opened).put("position",item.position).put("duration",item.duration).put("completed",item.completed);}catch(JSONException impossible){throw new IllegalStateException(impossible);}return o;
    }
    private static JSONArray array(List<MovieLibrary.Item> items){JSONArray a=new JSONArray();for(MovieLibrary.Item item:items)a.put(json(item));return a;}
    private MovieLibrary read(){MovieLibrary l=new MovieLibrary();l.restore("history",decode(prefs.getString("history","[]")));l.restore("favorites",decode(prefs.getString("favorites","[]")));return l;}
    private void save(MovieLibrary l){prefs.edit().putString("history",array(l.items("history")).toString()).putString("favorites",array(l.items("favorites")).toString()).apply();}
    public JSONArray items(String collection){return array(read().items(collection));}
    public MovieLibrary.Item find(String url){return read().find(url);}
    public boolean favorite(String url){return read().favorite(url);}
    public void opened(String url,String title,String thumbnail){if(!UrlPolicy.isMovie(url))return;MovieLibrary l=read();l.opened(new MovieLibrary.Item(url,title,thumbnail,System.currentTimeMillis()));save(l);}
    public void metadata(String url,String title,String thumbnail){if(!UrlPolicy.isMovie(url))return;MovieLibrary l=read();l.metadata(new MovieLibrary.Item(url,title,thumbnail,0));save(l);}
    public boolean toggle(String url,String title,String thumbnail){MovieLibrary l=read();boolean added=l.toggle(new MovieLibrary.Item(url,title,thumbnail,System.currentTimeMillis()));save(l);return added;}
    public void progress(String url,long position,long duration,boolean ended){MovieLibrary l=read();l.progress(url,position,duration,ended);save(l);}
    public void remove(String collection,String url){MovieLibrary l=read();l.remove(collection,url);save(l);}
    public void clear(String collection){MovieLibrary l=read();l.clear(collection);save(l);}
}

