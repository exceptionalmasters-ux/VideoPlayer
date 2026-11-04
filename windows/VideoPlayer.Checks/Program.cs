using VideoPlayer.Windows;
void Check(bool result, string message) { if (!result) throw new Exception(message); }
foreach (var bad in new[] { "http://vider.info/", "https://vider.info.evil.test/vid/+fabc", "https://user@vider.info/", "https://vider.info:8443/", "file:///test", "javascript:alert(1)" }) Check(!Policy.Allowed(bad), bad);
Check(Policy.Movie("https://vider.pl/vid/+fxn8nev"), "Vider movie");
Check(Policy.Movie("https://freedisc.pl/margot223,f-19724546,ronin-1998-mp4"), "Freedisc movie");
Check(!Policy.Movie("https://vider.info/dir/+d123"), "Directory must not be a movie");
Check(Policy.Search(Source.Freedisc, "Żółć & /?#").EndsWith("%C5%BB%C3%B3%C5%82%C4%87%20%26%20%2F%3F%23"), "Encoded search");
Check(!Policy.Thumbnail("https://img.vider.info.evil.test/a.png"), "Thumbnail host");
Check(!Policy.Redact("https://vider.info/search/all/private?token=secret").Contains("secret"), "Redaction");
var p = new Presentation(); Check(p.Pending, "Initial pending"); p.Entered(); p.Blocked(); Check(p.Pending, "Resume after challenge"); p.User(); p.Blocked(); Check(!p.Pending, "Respect manual exit"); p.Navigation(); Check(p.Pending, "New movie resets");
Console.WriteLine("Windows policies: all checks passed.");
