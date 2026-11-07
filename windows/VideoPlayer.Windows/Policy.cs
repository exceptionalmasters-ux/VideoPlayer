using System.Text.RegularExpressions;
namespace VideoPlayer.Windows;
public enum Source { Vider, Freedisc }
public static class Policy
{
    private static readonly string[] Hosts = ["vider.info", "www.vider.info", "vider.pl", "www.vider.pl", "freedisc.pl", "www.freedisc.pl"];
    public static bool Allowed(string? value) => Uri.TryCreate(value, UriKind.Absolute, out var u) && u.Scheme == "https" && u.Port == 443 && u.UserInfo.Length == 0 && Hosts.Contains(u.IdnHost.ToLowerInvariant());
    public static Source Site(string url) => new Uri(url).IdnHost.EndsWith("freedisc.pl", StringComparison.OrdinalIgnoreCase) ? Source.Freedisc : Source.Vider;
    public static string Home(Source source) => source == Source.Vider ? "https://vider.info/" : "https://freedisc.pl/";
    public static string Search(Source source, string query) => Home(source) + "search/all/" + Uri.EscapeDataString(query.Trim());
    public static bool Movie(string? value) => Allowed(value) && Regex.IsMatch(new Uri(value!).AbsolutePath, Site(value!) == Source.Vider ? @"^/vid/[^/]*\+f[A-Za-z0-9]+/?$" : @"^(?:/[^/,]+,f-[0-9]+(?:,[^/]+)?/?|/embed/video/[0-9]+/?)$");
    public static string MovieUrl(string value) { var u=new Uri(value); return u.GetLeftPart(UriPartial.Path)+(Site(value)==Source.Freedisc && u.AbsolutePath.StartsWith("/embed/video/") && u.Query.TrimStart('?').Split('&').Contains("match=1")?"?match=1":""); }
    public static bool Google(string? value) => Uri.TryCreate(value,UriKind.Absolute,out var u) && u.Scheme=="https" && u.Port==443 && u.UserInfo.Length==0 && ((u.IdnHost is "www.google.com" or "www.google.pl" or "google.com" or "google.pl") && (u.AbsolutePath is "/search" or "/sorry" || u.AbsolutePath.StartsWith("/sorry/")) || u.IdnHost is "consent.google.com" or "consent.google.pl");
    public static string GoogleSearch(Source source,string query) => "https://www.google.com/search?hl=pl&q="+Uri.EscapeDataString(query.Trim()+(source==Source.Freedisc?" freedisc site:freedisc.pl":" vider (site:vider.info OR site:vider.pl)"));
    public static bool HomePage(string? value) => Allowed(value) && new Uri(value!).AbsolutePath == "/";
    public static bool Thumbnail(string? value) => Uri.TryCreate(value, UriKind.Absolute, out var u) && u.Scheme == "https" && u.Port == 443 && u.UserInfo.Length == 0 && new[] { "img.vider.info", "img.freedisc.pl" }.Contains(u.IdnHost.ToLowerInvariant());
    public static string Redact(string? value) => Allowed(value) ? new Uri(value!).GetLeftPart(UriPartial.Authority) + (Movie(value) ? "/[movie]" : "/[page]") : "[external]";
}
public sealed class Presentation
{
    public bool Pending { get; private set; } = true;
    private bool automatic;
    public void Navigation() { Pending = true; automatic = false; }
    public void Entered() { Pending = false; automatic = true; }
    public void User() { Pending = false; automatic = false; }
    public void Blocked() { if (automatic) Pending = true; automatic = false; }
}
public sealed record MovieCard(string title, string url, string thumbnail);

