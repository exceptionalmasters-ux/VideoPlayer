package pl.example.viderplayer;
import java.net.*;
import java.util.*;
/** Google is allowed only inside the search worker; it is never a movie provider. */
public final class GoogleSearchPolicy {
    private GoogleSearchPolicy(){}
    public static String query(String title,SiteSource source){
        String clean=title==null?"":title.trim();clean=clean.substring(0,Math.min(220,clean.length()));
        return clean+" "+source.label.toLowerCase(Locale.ROOT)+" "+(source==SiteSource.FREEDISC?"site:freedisc.pl":"(site:vider.info OR site:vider.pl)");
    }
    public static String url(String title,SiteSource source){try{return "https://www.google.com/search?hl=pl&q="+URLEncoder.encode(query(title,source),"UTF-8");}catch(Exception e){throw new IllegalArgumentException(e);}}
    public static boolean allowed(String value){
        try{URI u=URI.create(value);String h=u.getHost(),p=u.getPath();
            if(!"https".equalsIgnoreCase(u.getScheme())||u.getRawUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443||h==null||p==null)return false;
            h=h.toLowerCase(Locale.ROOT);
            if(h.equals("consent.google.com")||h.equals("consent.google.pl"))return true;
            return Arrays.asList("google.com","www.google.com","google.pl","www.google.pl").contains(h)&&(p.equals("/")||p.equals("/search")||p.equals("/sorry")||p.startsWith("/sorry/"));
        }catch(RuntimeException e){return false;}
    }
    public static boolean results(String value,String title,SiteSource source){
        if(!allowed(value))return false;
        try{URI u=URI.create(value);if(!"/search".equals(u.getPath())||u.getRawQuery()==null)return false;
            for(String pair:u.getRawQuery().split("&")){String[] kv=pair.split("=",2);if(kv.length==2&&kv[0].equals("q"))return URLDecoder.decode(kv[1],"UTF-8").equals(query(title,source));}
        }catch(Exception ignored){}return false;
    }
}
