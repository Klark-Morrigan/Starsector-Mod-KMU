package kmu.ui.context;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.starsector.listeners.SectorListeners;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up and taking back the tracker that remembers which market the player has a UI open on,
 * per save.
 *
 * <p>An installer of one step each way, named beside the tracker rather than listed at the entry
 * point for the reason every other installer is: what a piece needs on load is that piece's own
 * knowledge, and an entry point that carried it would be the one file every feature had to be
 * edited into.
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

    /**
     * Clears the tracker, for a player who has the market condition manager switched off.
     *
     * <p>Nothing but that editor reads the context, so a load that leaves this uninstalled costs
     * nothing else - and a player switching the feature off mid-campaign is still running the
     * tracker this load registered, which is what makes a remove needed rather than a skipped
     * install.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> removeMarketUiContextTracker(sector),
            "Failed to remove KMU market UI context tracker");
    }

    static void installMarketUiContextTracker(SectorAPI sector) {
        SectorListeners.installListener(
            sector,
            StarsectorMarketUiContextTracker.class,
            StarsectorMarketUiContextTracker::new);
    }

    static void removeMarketUiContextTracker(SectorAPI sector) {
        SectorListeners.removeListener(sector, StarsectorMarketUiContextTracker.class);
    }
}
