package kmu.starsector.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.function.Supplier;

/**
 * Registering and clearing the sector's listeners, so every installer states what it wants rather
 * than writing the walk itself.
 *
 * <p>Everything here registers transient - the engine's second parameter keeps a listener out of
 * the save - so nothing this mod registers enters one, and a load that never installs is a load
 * without the listener. That is what leaves {@link #removeListener} needed only where a feature can
 * be switched off mid-session: across a load there is nothing left to remove.
 *
 * <p>One install shape rather than two. A presence check that kept the registration already there
 * would be no safer than replacing it - both leave exactly one - and would be worse for a listener
 * holding anything, since the one kept is the one built against whatever came before. So an install
 * always clears its class first and adds fresh: idempotent, never two, and never a stale instance.
 *
 * <p>Null-defensive at both hops - a sector that has none, and a sector whose listener manager is
 * not up - because a start-up step runs against whatever the engine has built so far, and a missing
 * registration must cost that listener rather than the load.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class SectorListeners {

    private SectorListeners() {
        // utility class, no instances.
    }

    /**
     * Adds one listener to the sector, clearing any registration of its class first.
     *
     * <p>Two of these do visible damage - two boxes over one cell, or a key flipped twice per press
     * and so apparently dead - and the fresh one is wanted anyway, a listener being liable to hold
     * cached GL text, live view state, or the sector it was built against.
     *
     * @param sector        the sector to register with; null is a no-op
     * @param listenerClass the class every existing registration of is cleared first
     * @param buildListener called only once there is a sector and a manager to add to, so a load
     *                      that cannot register does not construct a listener it would discard
     */
    public static void installListener(
            SectorAPI sector,
            Class<?> listenerClass,
            Supplier<?> buildListener) {

        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(listenerClass);
        listenerManager.addListener(buildListener.get(), true);
    }

    /**
     * Clears every registration of one listener class, adding nothing back - what a feature
     * switched off asks for, so a listener does not go on reacting for a feature the player has
     * turned off.
     *
     * @param sector        the sector to clear on; null is a no-op
     * @param listenerClass the class every registration of is cleared
     */
    public static void removeListener(SectorAPI sector, Class<?> listenerClass) {

        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(listenerClass);
    }
}
