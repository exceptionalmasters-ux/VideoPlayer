using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
namespace VideoPlayer.Windows;
public sealed record EpisodePlan(string Series,int Season,int Episode)
{
    private static readonly Regex Number=new(@"(?i)(?<![\p{L}\p{N}])(?:s(\d{1,2})[ ._-]*e(\d{1,3})|(\d{1,2})x(\d{1,3}))(?![\p{L}\p{N}])");
    public string Query=>$"{Series} S{Season:00}E{Episode:00}";
    public EpisodePlan Next()=>this with {Episode=Episode+1};
    public EpisodePlan NextSeason()=>this with {Season=Season+1,Episode=1};
    public static EpisodePlan? Parse(string? title)
    {
        if(string.IsNullOrWhiteSpace(title)||title.Length>1000)return null;
        var matches=Number.Matches(title);if(matches.Count!=1)return null;
        var m=matches[0];
        var series=Regex.Replace(title[..m.Index].Replace('\u00a0',' '),@"[._]+"," ");
        series=Regex.Replace(series,@"\s+"," ").Trim(' ','-','_','.',':');
        int season=int.Parse(m.Groups[1].Success?m.Groups[1].Value:m.Groups[3].Value),episode=int.Parse(m.Groups[2].Success?m.Groups[2].Value:m.Groups[4].Value);
        return series.Length>=2&&season>0&&episode>0?new(series,season,episode):null;
    }
    public bool Matches(string title)
    {
        var other=Parse(title);return other!=null&&other.Season==Season&&other.Episode==Episode&&Normalize(other.Series)==Normalize(Series);
    }
    private static string Normalize(string value)=>Regex.Replace(new string(value.Normalize(NormalizationForm.FormD).Where(c=>CharUnicodeInfo.GetUnicodeCategory(c)!=UnicodeCategory.NonSpacingMark).ToArray()).ToLowerInvariant().Replace('ł','l'),@"[^\p{L}\p{N}]+"," ").Trim();
    public static EpisodePlan? Resolve(string? title,string? original,string? url)
    {
        var plan=Parse(title)??Parse(original);if(plan!=null)return plan;
        if(!Policy.Movie(url))return null;
        var path=Uri.UnescapeDataString(new Uri(url!).AbsolutePath);
        var comma=path.IndexOf(",f-",StringComparison.Ordinal);if(comma<0)return null;
        var slug=path.IndexOf(',',comma+3);return slug>=0?Parse(path[(slug+1)..].Replace('-',' ')):null;
    }
}
