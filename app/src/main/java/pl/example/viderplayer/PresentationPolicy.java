package pl.example.viderplayer;

/** Per-document presentation choice; a user leaving fullscreen must never be forced back. */
final class PresentationPolicy {
    private enum State { PENDING, AUTOMATIC, USER }
    private State state=State.PENDING;
    void navigation(){state=State.PENDING;}
    boolean shouldEnter(){return state==State.PENDING;}
    void automaticallyEntered(){if(state==State.PENDING)state=State.AUTOMATIC;}
    void userControlled(){state=State.USER;}
    void accessBlocked(){if(state==State.AUTOMATIC)state=State.PENDING;}
}
