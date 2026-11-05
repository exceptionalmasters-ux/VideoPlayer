package pl.example.viderplayer;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.*;

/** Only explicit single-episode identifiers are eligible for automatic suggestions. */
final class EpisodePlan {
    private static final Pattern NUMBER=Pattern.compile("(?i)(?<![\\p{L}\\p{N}])(?:s(\\d{1,2})[ ._-]*e(\\d{1,3})|(\\d{1,2})x(\\d{1,3}))(?![\\p{L}\\p{N}])");
    final String series;
    final int season,episode;
    EpisodePlan(String series,int season,int episode){this.series=series;this.season=season;this.episode=episode;}
    static EpisodePlan parse(String title){
        if(title==null||title.length()>1000)return null;
        Matcher m=NUMBER.matcher(title);if(!m.find())return null;
        String series=title.substring(0,m.start()).replace('\u00a0',' ').replaceAll("[._]+"," ").replaceAll("^[\\s\\-:]+|[\\s\\-:]+$","").replaceAll("\\s+"," ").trim();
        int season=Integer.parseInt(m.group(1)!=null?m.group(1):m.group(3)),episode=Integer.parseInt(m.group(2)!=null?m.group(2):m.group(4));
        if(series.length()<2||season<1||episode<1)return null;
        if(m.find())return null; // Packs and ambiguous titles require manual selection.
        return new EpisodePlan(series,season,episode);
    }
    EpisodePlan next(){return episode<999?new EpisodePlan(series,season,episode+1):null;}
    EpisodePlan nextSeason(){return season<99?new EpisodePlan(series,season+1,1):null;}
    String query(){return series+String.format(Locale.ROOT," S%02dE%02d",season,episode);}
    private static String normalized(String s){return Normalizer.normalize(s,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).replace('ł','l').replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    boolean matches(String title){EpisodePlan other=parse(title);return other!=null&&season==other.season&&episode==other.episode&&normalized(series).equals(normalized(other.series));}
}
