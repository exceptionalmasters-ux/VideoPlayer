using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using System.IO;
namespace VideoPlayer.Windows;
public sealed record SavedMovie(string Url,string Title,string Thumbnail,long Opened)
{
    [JsonIgnore] public string Key => MovieCollections.Key(Url);
    [JsonIgnore] public MovieCard Card => new(Title,Url,Thumbnail);
}
public sealed class MovieCollections
{
    private sealed record Data(List<SavedMovie> History,List<SavedMovie> Favorites);
    private readonly string path;
    private List<SavedMovie> history=[],favorites=[];
    public MovieCollections(string path)
    {
        this.path=path;
        if (!File.Exists(path)) return;
        var data=JsonSerializer.Deserialize<Data>(File.ReadAllText(path)) ?? throw new InvalidDataException("Nieprawidłowy plik kolekcji.");
        history=Clean(data.History ?? []).Take(200).ToList(); favorites=Clean(data.Favorites ?? []);
    }
    private static List<SavedMovie> Clean(IEnumerable<SavedMovie> items) => items.Where(i=>i!=null && Policy.Movie(i.Url)).Select(i=>Normalize(i)).DistinctBy(i=>i.Key).ToList();
    public static string Key(string url)
    {
        if (!Policy.Movie(url)) return "";
        var freedisc=Policy.Site(url)==Source.Freedisc;
        var match=Regex.Match(new Uri(url).AbsolutePath,freedisc?@",f-([0-9]+)":@"\+(f[A-Za-z0-9]+)");
        return (freedisc?"freedisc:":"vider:")+match.Groups[1].Value;
    }
    private static bool Descriptive(string? value) => !string.IsNullOrWhiteSpace(value) && !value.StartsWith("https://") && !value.StartsWith("Film · ") && value is not ("Vider.info" or "Vider.pl" or "Freedisc" or "Vider");
    private static SavedMovie Normalize(SavedMovie i)
    {
        var u=new Uri(i.Url);
        var title=Descriptive(i.Title)?i.Title.Trim():"Film · "+Key(i.Url).Split(':')[1];
        return i with { Url=u.GetLeftPart(UriPartial.Path),Title=title[..Math.Min(title.Length,180)],Thumbnail=Policy.Thumbnail(i.Thumbnail)?i.Thumbnail:"" };
    }
    private SavedMovie Merge(MovieCard card,long time)
    {
        var old=Find(card.url);
        return Normalize(new(card.url,Descriptive(card.title)?card.title:old?.Title ?? "",Policy.Thumbnail(card.thumbnail)?card.thumbnail:old?.Thumbnail ?? "",time));
    }
    public IReadOnlyList<SavedMovie> Items(string collection) => (collection=="favorites"?favorites:history).ToArray();
    public SavedMovie? Find(string url) => history.Concat(favorites).FirstOrDefault(i=>i.Key==Key(url));
    public bool Favorite(string url) => favorites.Any(i=>i.Key==Key(url));
    public void Opened(MovieCard card)
    {
        if (!Policy.Movie(card.url)) return;
        var item=Merge(card,DateTimeOffset.UtcNow.ToUnixTimeMilliseconds());
        history.RemoveAll(i=>i.Key==item.Key); history.Insert(0,item); history=history.Take(200).ToList(); Update(favorites,item); Save();
    }
    public void Metadata(MovieCard card)
    {
        var old=Find(card.url); if (old==null) return;
        var item=Merge(card,old.Opened); Update(history,item); Update(favorites,item); Save();
    }
    private static void Update(List<SavedMovie> list,SavedMovie item) { var n=list.FindIndex(i=>i.Key==item.Key); if(n>=0)list[n]=item; }
    public bool Toggle(MovieCard card)
    {
        if (!Policy.Movie(card.url)) throw new ArgumentException("Movie URL required");
        if (Favorite(card.url)) { Remove("favorites",card.url); return false; }
        favorites.Insert(0,Merge(card,DateTimeOffset.UtcNow.ToUnixTimeMilliseconds())); Save(); return true;
    }
    public void Remove(string collection,string url) { (collection=="favorites"?favorites:history).RemoveAll(i=>i.Key==Key(url));Save(); }
    public void Clear(string collection) { (collection=="favorites"?favorites:history).Clear();Save(); }
    private void Save()
    {
        var directory=Path.GetDirectoryName(path); if(!string.IsNullOrEmpty(directory))Directory.CreateDirectory(directory);
        var temp=path+".tmp";
        try { File.WriteAllText(temp,JsonSerializer.Serialize(new Data(history,favorites))); File.Move(temp,path,true); }
        finally { if(File.Exists(temp))File.Delete(temp); }
    }
}
