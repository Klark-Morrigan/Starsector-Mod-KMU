package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.probes.VanillaMapTooltipProbe;

import kmu.maplayers.base.hover.MapHoverExpirer;
import kmu.maplayers.base.hover.MapHoverPermission;
import kmu.maplayers.base.installation.MapLayerInstallations;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevelState;
import kmu.starsector.listeners.SectorListeners;
import kmu.starsector.ui.ShownMapSurface;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the box that follows the cursor over the map: the pass that draws it, the key that
 * switches its detail, and the discard that stops the previous save's choice opening this one.
 *
 * <p>Three registrations rather than one, because each claims a different half of the same feature
 * and can only be made where that half lives - a render pass gets no events to consume, an input
 * pass gets no GL context, and the tick that lets a hover go has to run on the frames neither of
 * them does. So a failure to install any of them costs only its own half.
 *
 * <p>Installed after the surfaces, whose frame preparation claim the box asks for a frame.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class MapHoverInstaller {

    private MapHoverInstaller() {
        // utility class, no instances.
    }

    /**
     * Registers the hover box's render and input halves and drops the previous save's detail level,
     * each step behind its own failure boundary.
     *
     * @param sector the loaded sector; null leaves the box uninstalled rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        // The holder is a process-lifetime singleton, so a save left at a deeper detail level would
        // otherwise open the next one on it. The level is a live view preference and enters no save,
        // so load is the only point it can be dropped.
        runGuardedStep(
            HoverTooltipDetailLevelState.getInstance()::discardLevelFromPreviousSave,
            "Failed to discard KMU hover tooltip detail level from the previous save");

        runGuardedStep(
            () -> installMapLayerHoverTooltip(sector),
            "Failed to install KMU map layer hover tooltip");

        runGuardedStep(
            () -> installHoverTooltipDetailLevelInput(sector),
            "Failed to install KMU hover tooltip detail level input");

        runGuardedStep(
            () -> installMapHoverExpirer(sector),
            "Failed to install KMU map hover expirer");
    }

    /**
     * Clears both halves of the hover box, for a player switching the map layers off.
     *
     * <p>The detail level itself is left alone: it is a live view preference held in a
     * process-lifetime singleton, so there is nothing registered to take back and nothing in a save
     * to tidy.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> {
                SectorListeners.removeListener(sector, MapLayerCellTooltip.class);
                SectorListeners.removeListener(sector, HoverTooltipDetailLevelInput.class);
            },
            "Failed to remove KMU map layer hover tooltip");

        runGuardedStep(
            () -> removeMapHoverExpirer(sector),
            "Failed to remove KMU map hover expirer");
    }

    // Registers the hover-tooltip dispatcher - the render listener that draws whichever tooltip the
    // active map layer injects for the star system under the cursor. Transient, remove-then-add: it
    // draws only, holds no save-relevant state, and its cached GL text must never enter a save, so it
    // is re-added fresh each load and never duplicates. Mirrors the sidebar's contract so exactly one
    // renders.
    static void installMapLayerHoverTooltip(SectorAPI sector) {
        // Both live reads are supplied rather than built by the dispatcher, so its two decisions -
        // the step-aside and the on-screen gate - turn on what it is handed. The map read is
        // host-blind: the box draws wherever the layer paints, which is the sector map and the intel
        // screen's map visor alike, and look-blind: some terrain surface paints the layers in either
        // look, so the Starscape filter changes what is under the box rather than whether there is one.
        //
        // The step-aside is rooted at the shown surface rather than at the core tab, because the box
        // now draws over a map another mod docked as well - and the frames such a panel is up are
        // frames where no tab is up at all, so a walk fixed at the tab could never reach the tooltip
        // it has to stand aside for.
        SectorListeners.installListener(
            sector,
            MapLayerCellTooltip.class,
            () -> new MapLayerCellTooltip(
                new VanillaMapTooltipProbe(ShownMapSurface::resolveShownMapSurface),
                MapHoverPermission.createForLiveScreen()));
    }

    // Registers the per-frame tick that closes each frame's hover window, so a hover the map has
    // stopped resolving is let go of rather than named for the rest of the session. A script rather
    // than a listener because the frames it answers for are the ones no map pass runs on - the map
    // screen closed with nothing else drawing one - which is exactly when a pass-borne park cannot
    // happen. Transient, and removed by class: this script is this mod's own, so no sibling mod's
    // instance can be taken out with it.
    static void installMapHoverExpirer(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        removeMapHoverExpirer(sector);

        // This sector's own holder, since the script runs on this sector's frames: one kept from an
        // earlier load would go on expiring the hover of the sector it was built against while the
        // loaded one's stood forever.
        sector.addTransientScript(new MapHoverExpirer(
            MapLayerInstallations
                .resolveInstallationFor(sector)
                .resolveHoverState()));
    }

    static void removeMapHoverExpirer(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        sector.removeTransientScriptsOfClass(MapHoverExpirer.class);
    }

    // Registers the input listener that reads the hover box's detail-level cycle key. Transient,
    // remove-then-add, for the dispatcher's reasons: it holds no save-relevant state, and a second
    // registration alongside the first would advance the level twice per press - every press would
    // skip a depth the player never saw.
    static void installHoverTooltipDetailLevelInput(SectorAPI sector) {
        // Handed the same permission the dispatcher is, so the key is claimed on exactly the screens
        // and looks the box it switches can draw on - which is the whole of what makes the key
        // honest. A permission each rather than one shared instance: it holds no state, both are
        // built from the same factory, and a listener reaching for another's field would outlive it.
        SectorListeners.installListener(
            sector,
            HoverTooltipDetailLevelInput.class,
            () -> new HoverTooltipDetailLevelInput(MapHoverPermission.createForLiveScreen()));
    }
}
