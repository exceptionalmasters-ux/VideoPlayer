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

var folder=Path.Combine(Path.GetTempPath(),"VideoPlayer-check-"+Guid.NewGuid().ToString("N"));
var file=Path.Combine(folder,"collections.json");
try {
 var saved=new MovieCollections(file);
 var ronin=new MovieCard("Ronin","https://vider.info/vid/+fone","https://img.vider.info/poster.png");
 saved.Opened(ronin);saved.Toggle(ronin);
 saved.Opened(new("Maverick","https://freedisc.pl/user,f-123,maverick-mp4",""));
 saved.Opened(new("","https://vider.pl/vid/+fone?tracking=secret",""));
 var reload=new MovieCollections(file);
 Check(reload.Items("history").Count==2,"Persistent history deduplication");
 Check(reload.Items("history")[0].Title=="Ronin","Metadata preserved on reopen");
 Check(!File.ReadAllText(file).Contains("secret"),"No URL tracking in collections");
 reload.Clear("history");reload.Metadata(ronin);
 Check(reload.Items("history").Count==0 && reload.Favorite(ronin.url),"History deletion independent of favorites");
 reload.Remove("favorites",ronin.url);reload.Metadata(ronin);
 var empty=new MovieCollections(file);Check(empty.Items("history").Count==0&&empty.Items("favorites").Count==0,"Deletion survives restart and metadata cannot resurrect");
 Check(MovieCollections.Key("https://freedisc.pl/user,f-123,old")==MovieCollections.Key("https://www.freedisc.pl/user,f-123,new"),"Freedisc stable identity");
 Console.WriteLine("Persistent movie collections: all checks passed.");
} finally { if(Directory.Exists(folder))Directory.Delete(folder,true); }


Check(Policy.Movie("https://freedisc.pl/embed/video/8307530?match=1"),"Embed movie");
Check(!Policy.Movie("https://freedisc.pl/embed/video/8307530/evil"),"Embed path boundary");
Check(Policy.MovieUrl("https://freedisc.pl/embed/video/8307530?match=1&token=secret").EndsWith("?match=1"),"Embed match retained without tracking");
Check(MovieCollections.Key("https://freedisc.pl/u,f-8307530,test")==MovieCollections.Key("https://freedisc.pl/embed/video/8307530"),"Embed shares file identity");
Check(Policy.Google("https://consent.google.com/m"),"Google consent");
Check(!Policy.Google("https://google.com.evil.test/search")&&!Policy.Google("https://accounts.google.com/"),"Google scope");
Check(!Policy.Allowed("https://www.google.com/search?q=test"),"Google isolated from playback policy");
var plan=EpisodePlan.Parse("Serial S1E01 lektor");Check(plan?.Next().Query=="Serial S01E02","Next episode");
Check(plan?.NextSeason().Query=="Serial S02E01","Season fallback");
Check(plan?.Next().Matches("Serial S01E02 HD")==true&&!plan.Next().Matches("Inny serial S01E02"),"Episode series identity");
Check(EpisodePlan.Parse("Ronin 1998")==null,"Movie has no episode plan");
var resumeFile=Path.Combine(Path.GetTempPath(),Guid.NewGuid()+".json");
try {var library=new MovieCollections(resumeFile);var movie=new MovieCard("Film","https://freedisc.pl/u,f-8307530,title","");library.Opened(movie);library.Progress(movie.url,123,7200);library.Opened(movie with {url="https://freedisc.pl/embed/video/8307530?match=1"});var reload=new MovieCollections(resumeFile);Check(reload.Find(movie.url)?.Position==123,"Resume persists across reopen and embed URL");library.Progress(movie.url,double.NaN,7200);Check(library.Find(movie.url)?.Position==123,"Invalid progress rejected");}finally{File.Delete(resumeFile);}
Console.WriteLine("Windows embed, Google, episodes and resume: all checks passed.");
