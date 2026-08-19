package kmu.maplayers.base.hover.cover;

/**
 * Whether a sidebar this mod drew is over a point, in the UI units a placement is laid out in.
 *
 * <p>Declared here rather than beside the hosts because this end is what needs it: the live roster
 * is a constant of process-lifetime singletons that read the running game, so a cover reaching it
 * directly could only be exercised by the game itself - which is where a containment rule is
 * hardest to see failing.
 *
 * <p>A point rather than the roster behind it, so a case can state the answer it is about without
 * standing up a host. What counts as "over a sidebar" - a host whose panel is not live answering no,
 * two hosts asked as one - stays with the hosts, this end needing only the verdict.
 */
@FunctionalInterface
public interface SidebarPresence {

    /**
     * @param uiX the point's x in UI coordinates, measured from the left edge
     * @param uiY the point's y in UI coordinates, measured from the bottom edge
     * @return whether any sidebar is live and covers that point
     */
    boolean isAnySidebarOverPoint(float uiX, float uiY);
}
