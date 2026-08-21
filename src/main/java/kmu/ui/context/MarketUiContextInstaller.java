package kmu.ui.context;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.starsector.listeners.SectorListeners;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the tracker that remembers which market the player has a UI open on, per save.
 *
 * <p>An installer of one step, named beside the tracker rather than listed at the entry point for
 * the reason every other installer is: what a piece needs on load is that piece's own knowledge,
 * and an entry point that carried it would be the one file every feature had to be edited into.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class MarketUiContextInstaller {

    private MarketUiContextInstaller() {
        // utility class, no instances.
    }

    /**
     * Registers the tracker with the sector, each step behind its own failure boundary.
     *
     * @param sector the loaded sector; null leaves the tracker uninstalled rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        runGuardedStep(
            () -> installMarketUiContextTracker(sector),
            "Failed to install KMU market UI context tracker");
    }

    static void installMarketUiContextTracker(SectorAPI sector) {
        SectorListeners.installListenerOnce(
            sector,
            StarsectorMarketUiContextTracker.class,
            StarsectorMarketUiContextTracker::new);
    }
}
