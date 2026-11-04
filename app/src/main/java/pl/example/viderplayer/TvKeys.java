package pl.example.viderplayer;

/** Android key codes kept here so remote behavior can be tested without a device. */
final class TvKeys {
    private TvKeys() {}
    static String media(int key) {
        switch(key){case 85:return "toggle";case 126:return "play";case 127:return "pause";
            case 89:return "back";case 90:return "forward";default:return null;}
    }
    static String fullscreen(int key) {
        switch(key){case 21:return "back";case 22:return "forward";case 23:case 66:return "toggle";
            case 19:case 20:return "controls";default:return null;}
    }
}
