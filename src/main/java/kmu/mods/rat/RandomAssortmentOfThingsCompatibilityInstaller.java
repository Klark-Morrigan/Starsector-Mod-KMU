package kmu.mods.rat;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.scripts.InstalledTransientScript;
import kmlib.starsector.ui.screen.VanillaScreen;
import kmlib.starsector.ui.suppression.OffScreenWidgetSuppressor;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * What adapting to Random Assortment of Things needs standing up on a loaded sector.
 *
 * <p>One script today: the one that stops that mod's minimap rendering while it is parked off
 * screen. A minimap nobody can see still renders a whole sector map and drives every terrain pass
 * in the sector, so the frame a player opened a vanilla map on carries a second transform - and
 * which of the two a cursor read resolves through is the engine's child order rather than a
 * contract.
 *
 * <p>Named for the mod rather than for the widget: nothing here is a rule about minimaps in
 * general. The generic half is KMLib's {@code OffScreenWidgetSuppressor}, and whose widget this
 * mod may write into is {@link RandomAssortmentOfThingsMinimapSuppression}'s.
 *
 * <p><b>Three conditions govern the suppression, and the switch that drives this installer
 * composes all of them</b>: {@link RandomAssortmentOfThingsCompatibilityMode#isEngaged()} is the
 * player's compatibility toggle, and that mod present with its own minimap switch on. The
 * composition root applies it on load and re-applies it whenever either mod's settings are saved -
 * LunaLib announces every mod's saves, so the other mod's switch is as observable as ours - and
 * presence, the one condition that cannot move within a run, is settled by the load that first
 * applies it. So nothing is installed here that the mode does not currently want, and this class
 * carries no gate of its own.
 *
 * <p>The suppression still reads {@code isEngaged()} per frame. That is the frame-level truth
 * between announcements, and being wrong for a frame costs a frame - where this installer being
 * wrong would cost a session of a script walking the widget tree for nothing.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state beyond the
 * script it is holding on the sector's behalf.
 */
public final class RandomAssortmentOfThingsCompatibilityInstaller {

    // The script this installs, held so it can be taken off again by instance rather than by class.
    // OffScreenWidgetSuppressor is KMLib's, another mod may be running its own over the same sector,
    // and it hands the widget it silenced its opacity back as it goes - so a removal by class would
    // write into a widget this mod never touched. The slot states that rule; this says which script
    // is held under it.
    private static final InstalledTransientScript<OffScreenWidgetSuppressor> installedSuppressor =
        new InstalledTransientScript<>();

    private RandomAssortmentOfThingsCompatibilityInstaller() {
        // utility class, no instances.
    }

    /**
     * Installs what the compatibility needs, each step behind its own failure boundary.
     *
     * <p>Transient: pure runtime logic that must not enter a save, so it is re-added fresh each
     * load. A fresh permission per load with it, so the widget walk behind it starts on this save's
     * tree rather than holding the previous one's.
     *
     * @param sector the loaded sector; null leaves it uninstalled rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        runGuardedStep(
            () -> installParkedMinimapSuppressor(sector),
            "Failed to install KMU parked minimap suppressor");
    }

    /**
     * Takes back what the compatibility stood up.
     *
     * <p>Runs whenever the composed switch reads off, whichever of its conditions turned it -
     * including the mod being disabled between sessions, where a removal that asked after the mod
     * first would decline to clean up. Removing what was never installed is free.
     *
     * @param sector the loaded sector; null leaves the save untouched rather than throwing
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> removeParkedMinimapSuppressor(sector),
            "Failed to remove KMU parked minimap suppressor");
    }

    static void installParkedMinimapSuppressor(SectorAPI sector) {

        installedSuppressor.installOn(
            sector,
            () -> {

                var minimapSuppression =
                    RandomAssortmentOfThingsMinimapSuppression.createForLiveScreen();

                return new OffScreenWidgetSuppressor(
                    minimapSuppression::resolveSuppressibleMinimap,
                    VanillaScreen::resolveScreenBox);
            });
    }

    static void removeParkedMinimapSuppressor(SectorAPI sector) {
        installedSuppressor.removeFrom(sector);
    }
}
