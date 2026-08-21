package kmu.starsector.listeners;

import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.function.Supplier;

/**
 * The two shapes a listener is registered with the sector in, so every installer picks one rather
 * than writing the walk itself.
 *
 * <p>Both register transient - the engine's second parameter keeps a listener out of the save - so
 * nothing registered here enters one, and skipping an install is the whole of removing a listener.
 * What the two shapes differ on is a repeated install within one session: whether the registration
 * already there may be kept, or has to be replaced by a fresh instance. Naming the two here is what
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
     * Adds one listener to the sector unless a listener of that class is already registered, so a
     * repeated install cannot leave two of them running every one of that listener's reactions
     * twice.
     *
     * <p>The remove-then-add shape is the opposite call, and is
     * {@link #installTransientListener}: it is for the listeners where keeping the one already
     * registered is not good enough.
     *
     * @param sector        the sector to register with; null is a no-op
     * @param listenerClass the class the presence check is made against
     * @param buildListener called only once the registration is going ahead, so a skipped install
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
     * Clears every registration of one listener class, adding nothing back - what a feature
     * switched off asks for, so a registration a save carried does not go on reacting for a
     * feature the player has turned off.
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

    /**
     * Adds one listener to the sector, clearing any registration of that class first, so a repeated
     * install can never leave two of them - two of these do visible damage, two boxes over one cell
     * or a key flipped twice per press and so apparently dead - and so the one that runs is always
     * fresh, these holding cached GL text or live view state from whatever went before.
     *
     * <p>The has-check shape is the opposite call, and is {@link #installListenerOnce}: it is for
     * the listeners a kept registration serves just as well.
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
