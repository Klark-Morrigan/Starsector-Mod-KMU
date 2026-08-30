package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapStalenessSource;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonisationListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDecivListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDiscoveryListener;
import kmu.maplayers.politicalmap.base.render.PoliticalMapLayerRenderer;
import kmu.starsector.listeners.SectorListeners;
import kmu.starsector.nexerelin.NexerelinInvasionListenerInstaller;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing the political map up against a loaded save: healing what the save spells the old way,
 * dropping what the previous one left in a process-lifetime singleton, and registering everything
 * that repaints the overlay while the campaign runs.
 *
 * <p>The listeners cover the changes the engine fires an event for; the watcher covers the ones it
 * does not. Between them they are why the overlay updates live rather than only on reload, which is
 * what makes them one subject rather than a list of unrelated registrations.
 *
 * <p>Installed before the render surfaces, because the save heal has to run before the terrain
 * reads what it repairs.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class PoliticalMapInstaller {

    private PoliticalMapInstaller() {
        // utility class, no instances.
    }

    /**
     * Heals the loaded save, discards the previous one's overlay state and registers every live
     * repaint, each step behind its own failure boundary.
     *
     * @param sector the loaded sector; null leaves the listeners unregistered rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        // Clear a spotlight the loaded save's active view no longer offers, before the terrain reads
        // it - a bloc can lapse while a save sits unopened (a faction removed by a mod change, an
        // alliance dissolved), and a dangling spotlight would recede the sector behind a bloc the
        // player cannot unpick.
        runGuardedStep(
            FilterSelectionHeal::healStaleSelectionAgainstActiveView,
            "Failed to heal KMU political map spotlight selection");

        // The layer renderer is a process-lifetime singleton, so without this the sector just left
        // keeps painting over the one being loaded.
        runGuardedStep(
            PoliticalMapLayerRenderer.INSTANCE::discardStateFromPreviousSave,
            "Failed to discard KMU political map state from the previous save");

        runGuardedStep(
            () -> installPoliticalMapDiscoveryListener(sector),
            "Failed to install KMU political map discovery listener");

        runGuardedStep(
            () -> installMapLayerSectorWatcher(sector),
            "Failed to install KMU political map sector watcher");

        runGuardedStep(
            () -> installPoliticalMapColonySizeListener(sector),
            "Failed to install KMU political map colony size listener");

        runGuardedStep(
            () -> installPoliticalMapDecivListener(sector),
            "Failed to install KMU political map deciv listener");

        runGuardedStep(
            () -> installPoliticalMapColonisationListener(sector),
            "Failed to install KMU political map colonisation listener");

        // Nex-gated: no-op unless Nexerelin is enabled, so a Nex-free install
        // never loads the market-transfer listener's Nex-coupled class.
        runGuardedStep(
            () -> NexerelinInvasionListenerInstaller.installIfPresent(sector),
            "Failed to install KMU political map market-transfer listener");
    }

    /**
     * Clears every live repaint this registers, for a player switching the map layers off.
     *
     * <p>The save heal and the singleton discard have nothing to take back - one repairs the save
     * and the other drops derived state - so what is left is the listeners and the watcher, each of
     * which would otherwise go on marking an overlay stale that nothing is drawing.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> {
                SectorListeners.removeListener(sector, PoliticalMapDiscoveryListener.class);
                SectorListeners.removeListener(sector, PoliticalMapColonySizeListener.class);
                SectorListeners.removeListener(sector, PoliticalMapDecivListener.class);
                SectorListeners.removeListener(sector, PoliticalMapColonisationListener.class);
            },
            "Failed to remove KMU political map refresh listeners");

        runGuardedStep(
            () -> removeMapLayerSectorWatcher(sector),
            "Failed to remove KMU political map sector watcher");

        // Nex-gated on the way out as on the way in, so a Nex-free install never loads the
        // market-transfer listener's Nex-coupled class merely to say it is not registered.
        runGuardedStep(
            () -> NexerelinInvasionListenerInstaller.uninstallIfPresent(sector),
            "Failed to remove KMU political map market-transfer listener");
    }

    // Stops the per-frame watcher polling. By class, which is safe here where it would not be for a
    // library script: this one is the framework's own and no sibling mod installs it.
    static void removeMapLayerSectorWatcher(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        sector.removeTransientScriptsOfClass(MapLayerSectorWatcher.class);
    }

    // Registers the listener that refreshes the political map when the player
    // discovers a map-relevant entity (a market, a jump point, or a gate), so
    // the overlay updates live rather than only on reload.
    //
    // Built with the sector it is installed on, as every listener here is: each marks a system
    // stale on that sector's own refresh board, and one reading the running game instead would mark
    // whichever sector the player has loaded for an event belonging to another.
    static void installPoliticalMapDiscoveryListener(SectorAPI sector) {
        SectorListeners.installListener(
            sector,
            PoliticalMapDiscoveryListener.class,
            () -> new PoliticalMapDiscoveryListener(sector));
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when one of its colonies resizes, so a size change that flips the dominant
    // faction repaints live rather than only on reload.
    static void installPoliticalMapColonySizeListener(SectorAPI sector) {
        SectorListeners.installListener(
            sector,
            PoliticalMapColonySizeListener.class,
            () -> new PoliticalMapColonySizeListener(sector));
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when one of its colonies decivilises, so a dying colony sheds its faction
    // colour and repaints neutral live rather than only on reload.
    static void installPoliticalMapDecivListener(SectorAPI sector) {
        SectorListeners.installListener(
            sector,
            PoliticalMapDecivListener.class,
            () -> new PoliticalMapDecivListener(sector));
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when the player founds or abandons a colony in it, so planting or dropping
    // a colony repaints its system live rather than only on reload.
    static void installPoliticalMapColonisationListener(SectorAPI sector) {
        SectorListeners.installListener(
            sector,
            PoliticalMapColonisationListener.class,
            () -> new PoliticalMapColonisationListener(sector));
    }

    // Registers the per-frame watcher that refreshes the political map when a
    // change the engine fires no event for slips past the listeners - the set of
    // drawn systems shifting (a gate activating, a jump point established), a drawn
    // system changing hands (an AI colony founded in a system already on the map),
    // or a mobile system drifting to a new position. The watcher owns only the
    // cadence, so which of those count as a change is handed in as the political
    // map's own staleness source. Transient: not saved, so it is re-added fresh
    // each load and never duplicates across reloads.
    static void installMapLayerSectorWatcher(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        // Clear observed positions from any earlier save this app session: the shared
        // tracker outlives a single save, so a system id reused across saves would
        // otherwise be compared against the previous save's last-seen position until
        // the first poll re-seeds it.
        MovingSystems.getInstance().reset();

        // A fresh source per load, so the baselines it diffs against start empty rather
        // than carrying the previous save's last read into this one.
        sector.addTransientScript(
            new MapLayerSectorWatcher(new PoliticalMapStalenessSource()));
    }
}
