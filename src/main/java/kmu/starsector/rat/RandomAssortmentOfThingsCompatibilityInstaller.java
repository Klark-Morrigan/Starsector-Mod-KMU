package kmu.starsector.rat;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.rat.RandomAssortmentOfThingsPresence;
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
 * <p>Named for the mod rather than for the widget, because nothing here is a rule about minimaps in
 * general. The generic half is KMLib's: {@code OffScreenWidgetSuppressor} reads any widget's box
 * against the screen and writes an opacity, and would read a minimap docked by the next mod exactly
 * the same way. What cannot be stated generally is whose widget this mod may write into, and that
 * is the whole of what this package is for.
 *
 * <p><b>Three conditions govern the suppression, and they are answered at three different
 * layers</b>, because they do not change on the same clock:
 *
 * <ul>
 *   <li><b>Whether that mod is installed</b> is fixed for the launch, so it gates the install here.
 *       Without it there is no minimap that could ever be parked, and a script registered anyway
 *       would walk the widget tree for the rest of the campaign to reach that conclusion.</li>
 *   <li><b>Whether the player wants this mod adapting to that one</b> is a switch of ours, and one
 *       we are told about, so it stands the whole feature up or takes it back where it is flipped.
 *       That belongs to the composition root, which owns every such switch.</li>
 *   <li><b>Whether that mod's own minimap switch is on</b> is a live setting of somebody else's,
 *       and nothing announces it to us. So it stays a per-frame read inside the suppression, where
 *       being wrong for one frame costs a frame rather than a session.</li>
 * </ul>
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state beyond the
 * script it is holding on the sector's behalf.
 */
public final class RandomAssortmentOfThingsCompatibilityInstaller {

    // The script this installs, held so it can be taken out again by instance rather than by class.
    // OffScreenWidgetSuppressor is KMLib's and another mod may be running its own over the same
    // sector - and it hands the widget it silenced its opacity back as it goes, so a removal by
    // class would write into a widget this mod never touched. Per process rather than per sector: a
    // reference to a script from a sector since left is inert, removing it from another sector
    // doing nothing, and the next install replaces it.
    private static OffScreenWidgetSuppressor installedSuppressor;

    private RandomAssortmentOfThingsCompatibilityInstaller() {
        // utility class, no instances.
    }

    /**
     * Installs what the compatibility needs when that mod is enabled, and does nothing otherwise.
     *
     * <p>Transient: pure runtime logic that must not enter a save, so it is re-added fresh each
     * load. A fresh permission per load with it, so the widget walk behind it starts on this save's
     * tree rather than holding the previous one's.
     *
     * @param sector the loaded sector; null leaves it uninstalled rather than throwing
     */
    public static void installIfPresent(SectorAPI sector) {

        if (!RandomAssortmentOfThingsPresence.isModEnabled()) {
            return;
        }

        runGuardedStep(
            () -> installParkedMinimapSuppressor(sector),
            "Failed to install KMU parked minimap suppressor");
    }

    /**
     * Takes back what the compatibility stood up.
     *
     * <p>Ungated, where the install is not: a player who disables the mod between sessions leaves a
     * sector still holding the script, and a removal that first asked whether the mod was there
     * would decline to clean up in exactly that case. Removing what was never installed is free.
     *
     * @param sector the loaded sector; null leaves the save untouched rather than throwing
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> removeParkedMinimapSuppressor(sector),
            "Failed to remove KMU parked minimap suppressor");
    }

    static void installParkedMinimapSuppressor(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        var minimapSuppression = RandomAssortmentOfThingsMinimapSuppression.createForLiveScreen();

        installedSuppressor = new OffScreenWidgetSuppressor(
            minimapSuppression::resolveSuppressibleMinimap,
            VanillaScreen::resolveScreenBox);

        sector.addTransientScript(installedSuppressor);
    }

    static void removeParkedMinimapSuppressor(SectorAPI sector) {

        if (sector == null || installedSuppressor == null) {
            return;
        }
        sector.removeTransientScript(installedSuppressor);

        installedSuppressor = null;
    }
}
