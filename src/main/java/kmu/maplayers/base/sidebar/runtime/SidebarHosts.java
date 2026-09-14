package kmu.maplayers.base.sidebar.runtime;

import java.util.List;

/**
 * The registered sidebar hosts asked as a set, for a caller that needs an answer about "the sidebar"
 * without knowing which screen is up. The panel is one widget shown on two screens by two hosts, so
 * a question like "is the cursor over it" has no single host to put it to: the asking code is reached
 * through hooks that never name the screen that invoked them, and the map layers draw on both the
 * sector map and the intel screen's map visor through the same terrain pass.
 *
 * <p>Each host answers for its own screen and a host whose sidebar is not live answers no, so the
 * disjunction needs no screen test of its own and stays true if a third host is ever added. That is
 * also the limit of what it can answer: it says a sidebar is under the point, never which host drew
 * it. A question about one host - whether to draw into it, or where - is that host's to answer.
 *
 * <p>The roster here is the whole of "every host": the load-time reseed and the listener registration
 * walk it too, so a new screen is added once and is reseeded, registered, and answered for from that
 * one edit rather than from four that can be made three of.
 */
public final class SidebarHosts {

    // Every host the mod registers a sidebar for. A constant rather than a mutable registration
    // list: the hosts are process-lifetime singletons named at compile time, so there is no point in
    // the session at which the roster is not yet known.
    private static final List<SidebarHost> REGISTERED_HOSTS =
        List.of(MapSidebarHost.INSTANCE, IntelSidebarHost.INSTANCE);

    private SidebarHosts() {
    }

    /**
     * @param uiX the point's x in UI coordinates, the coordinates a placement is laid out in
     * @param uiY the point's y in UI coordinates
     * @return whether any registered host's sidebar is live and covers the point
     */
    public static boolean isPointOverAnySidebar(float uiX, float uiY) {
        return isPointOverAnySidebarOf(getRegisteredHosts(), uiX, uiY);
    }

    /**
     * @return every host a sidebar is registered for, in registration order - the roster to reseed on
     *         load, to register listeners for, and to put a host-blind question to, so a new screen is
     *         added here and nowhere else
     */
    public static List<SidebarHost> getRegisteredHosts() {
        return REGISTERED_HOSTS;
    }

    // The disjunction over a given roster, stated apart from the roster the game supplies: those
    // hosts are singletons that read the running game, so the rule is written over whatever roster
    // it is handed rather than over that one.
    static boolean isPointOverAnySidebarOf(List<SidebarHost> hosts, float uiX, float uiY) {
        for (var host : hosts) {
            if (isPointOverSidebarOf(host, uiX, uiY)) {
                return true;
            }
        }
        return false;
    }

    // Whether one host's sidebar covers the point. Gated the way the render and input passes are - the
    // host's own "is the sidebar live" answer first, then the panel its draw published - because a host
    // that is not showing can still have a box to report: only the intel host's anchor is the thing that
    // goes missing off its screen, while the on-map host hangs its panel from the screen corner.
    //
    // The drawn panel rather than a fresh layout: this asks what the pointer is over, and what it is over
    // is what is painted. Laying one out to answer that would also spend a whole panel's measurement on a
    // question asked every frame the cursor moves.
    private static boolean isPointOverSidebarOf(SidebarHost host, float uiX, float uiY) {
        if (!host.isOverlayShowing()) {
            return false;
        }
        var placement = host.getDrawnPlacement();
        return placement != null && placement.containsPoint(uiX, uiY);
    }
}
