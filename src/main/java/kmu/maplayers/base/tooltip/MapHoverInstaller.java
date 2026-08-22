package kmu.maplayers.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.probes.VanillaMapTooltipProbe;

import kmu.maplayers.base.hover.MapHoverPermission;
import kmu.starsector.listeners.SectorListeners;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the box that follows the cursor over the map: the pass that draws it, the key that
 * switches its detail, and the discard that stops the previous save's choice opening this one.
 *
 * <p>Two registrations rather than one, because the two are different listener kinds claiming
 * different halves of the same feature - a render pass gets no events to consume and an input pass
 * gets no GL context - so a failure to install either must cost only its own half.
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
     * Registers the hover box's render and input halves and drops the previous save's detail mode,
     * each step behind its own failure boundary.
     *
     * @param sector the loaded sector; null leaves the box uninstalled rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        // The holder is a process-lifetime singleton, so a save left showing the expanded box would
        // otherwise open the next one on it. The mode is a live view preference and enters no save,
        // so load is the only point it can be dropped.
        runGuardedStep(
            HoverTooltipDetailModeState.getInstance()::discardModeFromPreviousSave,
            "Failed to discard KMU hover tooltip detail mode from the previous save");

        runGuardedStep(
            () -> installMapLayerHoverTooltip(sector),
            "Failed to install KMU map layer hover tooltip");

        runGuardedStep(
            () -> installHoverTooltipDetailModeInput(sector),
            "Failed to install KMU hover tooltip detail mode input");
    }

    /**
     * Clears both halves of the hover box, for a player switching the map layers off.
     *
     * <p>The detail mode itself is left alone: it is a live view preference held in a
     * process-lifetime singleton, so there is nothing registered to take back and nothing in a save
     * to tidy.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> {
                SectorListeners.removeListener(sector, MapLayerCellTooltip.class);
                SectorListeners.removeListener(sector, HoverTooltipDetailModeInput.class);
            },
            "Failed to remove KMU map layer hover tooltip");
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
        SectorListeners.installListener(
            sector,
            MapLayerCellTooltip.class,
            () -> new MapLayerCellTooltip(
                new VanillaMapTooltipProbe(),
                MapHoverPermission.createForLiveScreen()));
    }

    // Registers the input listener that reads the hover box's detail-mode toggle key. Transient,
    // remove-then-add, for the dispatcher's reasons: it holds no save-relevant state, and a second
    // registration alongside the first would flip the mode twice per press - leaving it where it
    // started, so the key would look dead.
    static void installHoverTooltipDetailModeInput(SectorAPI sector) {
        // Handed the same permission the dispatcher is, so the key is claimed on exactly the screens
        // and looks the box it switches can draw on - which is the whole of what makes the toggle
        // honest. A permission each rather than one shared instance: it holds no state, both are
        // built from the same factory, and a listener reaching for another's field would outlive it.
        SectorListeners.installListener(
            sector,
            HoverTooltipDetailModeInput.class,
            () -> new HoverTooltipDetailModeInput(MapHoverPermission.createForLiveScreen()));
    }
}
