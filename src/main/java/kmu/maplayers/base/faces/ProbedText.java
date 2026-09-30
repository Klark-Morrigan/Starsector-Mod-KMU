package kmu.maplayers.base.faces;

/**
 * A kind of text a face is settled against: what a text drawn in that face may say, gathered once from
 * the sector and the mod rather than read off each line as it is drawn.
 *
 * <p>A face is settled against the kinds its text is made of, not against the text of one frame, so a
 * name that arrives later - a colony founded mid-session - is drawn in the face its kind already settled
 * on. Kinds rather than one pool, because the faces differ in what they draw: a map label names factions
 * and nothing else, and holding it to every string KMU ships would move it for text it never shows.
 */
public enum ProbedText {

    /** Every faction's short and long display name, both forms being what a label or a row may name it by. */
    FACTION_NAMES,

    /** Every star system's and colony's name, which the hover box and the sidebar rows name places by. */
    PLACE_NAMES,

    /** Every string KMU ships in the running build's locale. */
    MOD_STRINGS
}
