package kmu.starsector.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.function.Supplier;

/**
 * The two shapes a listener is registered with the sector in, so every installer picks one rather
 * than writing the walk itself.
 *
 * <p>Which shape a listener wants turns on one question: whether a registration a reloaded save
 * carries back is wanted or is damage. A listener meant to survive into the save must not be
 * cleared out from under the copy already there; one holding cached GL text or live view state must
 * be replaced outright, since two of them do visible damage. Naming the two shapes here is what
 * keeps that choice a decision each installer states rather than a walk it improvises.
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
     * Adds one listener to the sector unless a listener of that class is already registered, which
     * a reloaded save is what makes possible: these are persistent registrations, so a save carries
     * them back and adding a second would run every one of that listener's reactions twice.
     *
     * <p>The remove-then-add shape is the opposite call, and is
     * {@link #installTransientListener}: it is for the listeners that must not survive into a save.
     *
     * @param sector        the sector to register with; null is a no-op
     * @param listenerClass the class the presence check is made against
     * @param buildListener called only once the registration is going ahead, so a reloaded save
     *                      does not construct a listener it is about to discard
     */
    public static void installListenerOnce(
            SectorAPI sector,
            Class<?> listenerClass,
            Supplier<?> buildListener) {

        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null || listenerManager.hasListenerOfClass(listenerClass)) {
            return;
        }

        listenerManager.addListener(buildListener.get(), true);
    }

    /**
     * Adds one listener to the sector, clearing any registration of that class first, which a
     * reloaded save is what makes possible: a save that captured one would restore it alongside the
     * one added on load, and two of these do visible damage - two boxes over one cell, or a key
     * flipped twice per press and so apparently dead.
     *
     * <p>The has-check shape is the opposite call, and is {@link #installListenerOnce}: it is for
     * the listeners meant to survive into the save, which must not be cleared out from under it.
     *
     * @param sector        the sector to register with; null is a no-op
     * @param listenerClass the class every existing registration of is cleared first
     * @param buildListener called for the fresh registration, which is always made
     */
    public static void installTransientListener(
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
}
