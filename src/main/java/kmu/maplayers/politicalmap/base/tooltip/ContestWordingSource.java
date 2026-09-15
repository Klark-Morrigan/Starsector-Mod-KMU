package kmu.maplayers.politicalmap.base.tooltip;

/**
 * Source port for a {@link ContestWording} - what a box holds when it needs the wording the install
 * calls for at the moment it draws, rather than one fixed when the box was built.
 *
 * <p>A source rather than the wording itself for a reason particular to these boxes: they are shared
 * instances standing in static fields, so they are built when their class is first touched, and that
 * can fall before the game has stood its mod set up. A wording resolved there would read an empty mod
 * set, settle on the install being plain vanilla, and go on heading every hover that way for the rest
 * of the session - on a Nexerelin game included. There is no wording to keep, only the means of taking
 * one when there is a box to head.
 *
 * <p>A port rather than a direct call on the gate, for the usual reason - a box depending on this can
 * be handed a wording with no running game behind it, and the mod the answer turns on stays named
 * where the binding is made rather than where the heading is drawn.
 */
@FunctionalInterface
public interface ContestWordingSource {

    /**
     * Takes the wording the install calls for as this call is made.
     *
     * @return the live wording
     */
    ContestWording resolveWording();
}
