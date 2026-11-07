using System.Text.RegularExpressions;
namespace VideoPlayer.Windows;
public sealed record EpisodePlan(string Series,int Season,int Episode)
{
    public string Query=>$"{Series} S{Season:00}E{Episode:00}";
    public EpisodePlan Next()=>this with {Episode=Episode+1};
    public EpisodePlan NextSeason()=>this with {Season=Season+1,Episode=1};
    public static EpisodePlan? Parse(string? title)
    {
        var m=Regex.Match(title??"",@"(?i)^(.*?)\b(?:s\s*(\d{1,2})\s*e\s*(\d{1,3})|(\d{1,2})x(\d{1,3}))\b");
        if(!m.Success||string.IsNullOrWhiteSpace(m.Groups[1].Value))return null;
        var season=int.Parse(m.Groups[2].Success?m.Groups[2].Value:m.Groups[4].Value);
        var episode=int.Parse(m.Groups[3].Success?m.Groups[3].Value:m.Groups[5].Value);
        return season>0&&episode>0?new(m.Groups[1].Value.Trim(' ','-','_','.'),season,episode):null;
    }
    public bool Matches(string title)
    {
        var other=Parse(title);return other!=null&&other.Season==Season&&other.Episode==Episode&&Normalize(other.Series)==Normalize(Series);
    }
    private static string Normalize(string value)=>Regex.Replace(value.ToLowerInvariant(),@"[^\p{L}\p{N}]","");
}
