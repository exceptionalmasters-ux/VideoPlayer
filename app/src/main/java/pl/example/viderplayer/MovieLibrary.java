package pl.example.viderplayer;

import java.net.URI;
import java.util.*;
import java.util.regex.*;

/** Local movie collections, independent of either service's account. */
public final class MovieLibrary {
    public static final int HISTORY_LIMIT=200;
    public static final class Item {
        public final String url,title,thumbnail;
        public final long opened;
        public Item(String url,String title,String thumbnail,long opened){
            if(!UrlPolicy.isMovie(url))throw new IllegalArgumentException("Movie URL required");
            URI uri=URI.create(url);
            this.url="https://"+uri.getHost().toLowerCase(Locale.ROOT)+uri.getRawPath();
            this.title=title==null?"":title.trim().substring(0,Math.min(title.trim().length(),180));
            this.thumbnail=UrlPolicy.thumbnail(thumbnail)?thumbnail:"";
            this.opened=opened;
        }
        public String key(){return MovieLibrary.key(url);}
    }
    private final List<Item> history=new ArrayList<>(),favorites=new ArrayList<>();
    public static String key(String url){
        if(!UrlPolicy.isMovie(url))return "";
        String path=URI.create(url).getPath();
        Matcher m=Pattern.compile(SiteSource.fromUrl(url)==SiteSource.FREEDISC?",f-([0-9]+)":"\\+(f[A-Za-z0-9]+)").matcher(path);
        return m.find()?(SiteSource.fromUrl(url)==SiteSource.FREEDISC?"freedisc:":"vider:")+m.group(1):"";
    }
    private static boolean descriptive(String title){return title!=null&&!title.isEmpty()&&!title.startsWith("https://")&&!title.equalsIgnoreCase("Vider.info")&&!title.equalsIgnoreCase("Vider.pl")&&!title.equalsIgnoreCase("Freedisc")&&!title.startsWith("Film · ");}
    private static Item merge(Item old,Item fresh,long opened){
        String title=descriptive(fresh.title)?fresh.title:old!=null&&descriptive(old.title)?old.title:"Film · "+fresh.key().split(":")[1];
        return new Item(fresh.url,title,!fresh.thumbnail.isEmpty()?fresh.thumbnail:old==null?"":old.thumbnail,opened);
    }
    public Item find(String url){String key=key(url);for(Item i:history)if(i.key().equals(key))return i;for(Item i:favorites)if(i.key().equals(key))return i;return null;}
    public boolean favorite(String url){String key=key(url);return !key.isEmpty()&&favorites.stream().anyMatch(i->i.key().equals(key));}
    public void opened(Item fresh){
        Item item=merge(find(fresh.url),fresh,fresh.opened);
        history.removeIf(i->i.key().equals(item.key()));history.add(0,item);
        if(history.size()>HISTORY_LIMIT)history.remove(history.size()-1);
        updateIn(favorites,item);
    }
    public void metadata(Item fresh){
        Item old=find(fresh.url);if(old==null)return;
        Item item=merge(old,fresh,old.opened);updateIn(history,item);updateIn(favorites,item);
    }
    private static void updateIn(List<Item> list,Item item){for(int i=0;i<list.size();i++)if(list.get(i).key().equals(item.key())){list.set(i,item);break;}}
    public boolean toggle(Item fresh){
        if(favorite(fresh.url)){remove("favorites",fresh.url);return false;}
        favorites.add(0,merge(find(fresh.url),fresh,fresh.opened));return true;
    }
    public List<Item> items(String collection){return new ArrayList<>("favorites".equals(collection)?favorites:history);}
    public void remove(String collection,String url){String key=key(url);("favorites".equals(collection)?favorites:history).removeIf(i->i.key().equals(key));}
    public void clear(String collection){("favorites".equals(collection)?favorites:history).clear();}
    public void restore(String collection,List<Item> items){
        List<Item> target="favorites".equals(collection)?favorites:history;target.clear();Set<String> seen=new HashSet<>();
        for(Item i:items)if(seen.add(i.key())&&(target==favorites||target.size()<HISTORY_LIMIT))target.add(i);
    }
}
