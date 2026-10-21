package kmu.maplayers.base.geometry.walls;

/** The verdicts the walk can reach about a wall it was offered. */
public enum RefusalReason {

    /** Laid, and the boundary runs along it. */
    LAID("laid"),

    /** The wall passes too far from one of its circles to open a mouth on it at all. */
    NO_MOUTH("no mouth"),

    /** One of its sides lies inside another disc, so it is not on the boundary. */
    OFF_BOUNDARY("off the boundary"),

    /** A wall already laid holds the mouth this one would have left from or landed on. */
    CROWDED_OUT("crowded out by"),

    /** Not among the walls offered, so the walk never ruled on it. */
    NOT_OFFERED("not offered");

    private final String wording;

    RefusalReason(String wording) {
        this.wording = wording;
    }

    String describe() {
        return wording;
    }
}
