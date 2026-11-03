package pl.example.viderplayer;

/** One automatic return to the selected movie after the service redirects to its home page. */
public final class MovieNavigation {
    private String target;
    private boolean retried;
    public void select(String url) { target = UrlPolicy.isMovie(url) ? url : null; retried = false; }
    public String target() { return target; }
    public void restore(String url, boolean retryUsed) { select(url); retried = retryUsed; }
    public boolean retryUsed() { return retried; }
    public String retryFrom(String current, boolean consentVisible) {
        if (target == null || retried || consentVisible || !UrlPolicy.isHome(current)) return null;
        retried = true;
        return target;
    }
}
