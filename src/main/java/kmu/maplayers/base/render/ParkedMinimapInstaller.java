package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.screen.VanillaScreen;
import kmlib.starsector.ui.suppression.OffScreenWidgetSuppressor;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the script that stops a docked minimap rendering while its owner has it parked off
 * screen.
 *
 * <p>Its own installer rather than one of the map's surfaces, because it is not one: nothing here
 * draws, nothing here is drawn into, and the map layers neither need it nor are needed by it. What
 * it is is a compatibility behaviour aimed at another mod's widget, so it stands or falls with the
 * compatibility mode the player answers that question with, and not with whether this mod is
 * drawing an overlay of its own.
 *
 * <p>A minimap nobody can see still renders a whole sector map and drives every terrain pass in the
 * sector, so the frame a player opened a vanilla map on carries a second transform - and which of
 * the two a cursor read resolves through is the engine's child order rather than a contract.
 *
 * <p>The split under it is the same one the compatibility mode is built on. What parked means and
 * how a widget is switched off are stated over any widget at all and are KMLib's; whether writing
 * into somebody else's panel is wanted is a per-mod question, and the mode is where the player
 * answers it. The screen the widget is compared against is a live read for the same reason the box
 * is: a window resized mid-session moves both.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state beyond the
 * script it is holding on the sector's behalf.
 */
public final class ParkedMinimapInstaller {

    // The script this installed, held so it can be taken out again by instance rather than by
    // class. OffScreenWidgetSuppressor is KMLib's, so a sibling KM mod may be running its own over
    // the same sector - and removing by class would hand a widget this mod never touched its
    // opacity back. Per process rather than per sector: a reference to a script from a sector since
    // left is inert, removing it from another sector doing nothing, and the next install replaces
    // it.
    private static OffScreenWidgetSuppressor installedSuppressor;

    private ParkedMinimapInstaller() {
        // utility class, no instances.
    }

    /**
     * Installs the suppressor, behind its own failure boundary.
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
     * Stops the suppressor, behind its own failure boundary.
     *
     * <p>Within a session this is what switching the compatibility mode off has to do: the script
     * is already registered and would go on writing into the widget until the next load, where
     * being transient it would simply not come back.
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
