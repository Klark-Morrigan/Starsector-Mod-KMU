package kmu.maplayers.base.faces;

import java.util.Set;

/**
 * A kind of text a face is settled against: what a text drawn in that face may say, gathered once from
 * the sector and the mod rather than read off each line as it is drawn.
 *
 * <p>A face is settled against the kinds its text is made of, not against the text of one frame, so a
 * name that arrives later - a colony founded mid-session - is drawn in the face its kind already settled
 * on. Kinds rather than one pool, because texts differ in what they draw: a text naming factions and
 * nothing else, held to every string KMU ships, would move for text it never shows.
 */
public enum ProbedText {

    /** Every faction's short and long display name, a faction being named by either form. */
    FACTION_NAMES,

    /** Every star system's and colony's name, a place being named by either. */
    PLACE_NAMES,

    /** Every string KMU ships, as the game merged them for the running build. */
    MOD_STRINGS;

    /** Every kind at once, for a text naming factions and places among KMU's own words. */
    public static final Set<ProbedText> EVERY_KIND = Set.of(values());
}
