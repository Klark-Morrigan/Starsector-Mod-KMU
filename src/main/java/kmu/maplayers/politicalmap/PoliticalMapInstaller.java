package kmu.maplayers.politicalmap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.alliances.AllianceSource;
import kmlib.starsector.listeners.SectorListeners;
import kmlib.starsector.scripts.SectorScripts;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.ownermap.FilterSelectionHeal;
import kmu.maplayers.ownermap.MapLayerViewRegistry;
import kmu.maplayers.politicalmap.refresh.PoliticalMapSectorWatcher;
import kmu.maplayers.politicalmap.refresh.PoliticalMapStalenessSource;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapColonisationListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapDecivListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapDiscoveryListener;
import kmu.maplayers.politicalmap.refresh.listeners.PoliticalMapMarketTransferListener;

import java.util.List;
import java.util.function.Function;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing the political map up against a loaded save: healing a spotlight the save holds that its
 * active view does not offer, and registering everything that repaints the overlay while the campaign
 * runs.
 *
 * <p>The listeners cover the changes something fires an event for - the engine, or the relay that
 * forwards Nexerelin's colony transfers to every {@code MarketTransferListener}; the watcher covers
 * the ones nothing announces. Between them they are why the overlay updates live rather than only on reload, which is
 * what makes them one subject rather than a list of unrelated registrations.
 *
 * <p>Every listener below is built with the sector it is installed on. Each marks a system stale on
 * that sector's own refresh board, so one reading the running game instead would mark whichever
 * sector the player has loaded for an event belonging to another.
 *
 * <p>Installed before the render surfaces, because the save heal has to run before the terrain
 * reads what it repairs.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class PoliticalMapInstaller {

    private static final String INSTALL_FAILURE_PREFIX = "Failed to install KMU political map ";
    private static final String REMOVAL_FAILURE_PREFIX = "Failed to remove KMU political map ";

    // Every listener that repaints the overlay, held as one table that both directions walk, so a
    // listener added here is removed by the same entry that installs it.
    private static final List<RefreshListener<?>> REFRESH_LISTENERS = List.of(
        // The player discovering a map-relevant entity: a market, a jump point, or a gate.
        new RefreshListener<>(
            PoliticalMapDiscoveryListener.class,
            PoliticalMapDiscoveryListener::new,
            "discovery listener"),
        // A colony resizing, which can flip the dominant faction.
        new RefreshListener<>(
            PoliticalMapColonySizeListener.class,
            PoliticalMapColonySizeListener::new,
            "colony size listener"),
        // A colony decivilising, which sheds its faction colour.
        new RefreshListener<>(
            PoliticalMapDecivListener.class,
            PoliticalMapDecivListener::new,
            "deciv listener"),
        // The player founding or abandoning a colony.
        new RefreshListener<>(
            PoliticalMapColonisationListener.class,
            PoliticalMapColonisationListener::new,
            "colonisation listener"),
        // A conquered colony changing hands, which vanilla reports nothing for. Whatever reports
        // transfers on this sector reaches it through the listener manager; on an install where
        // nothing does, it is registered and never called.
        new RefreshListener<>(
            PoliticalMapMarketTransferListener.class,
            PoliticalMapMarketTransferListener::new,
            "market-transfer listener"));

    private PoliticalMapInstaller() {
        // utility class, no instances.
    }

    /**
     * Heals the loaded save and registers every live repaint, each step behind its own failure
     * boundary.
     *
     * <p>Nothing of the previous save is dropped here. The overlay's derived state belongs to the
     * sector it was built from and goes when that sector's machinery does, so this sector's is new
     * by the time anything below runs.
     *
     * @param sector         the loaded sector; null leaves the listeners unregistered rather than
     *                       throwing
     * @param viewRegistry   the political map's views, whose spotlights the save heal judges
     * @param allianceSource the alliances standing, which the poll compares between passes
     */
    public static void installAll(
            SectorAPI sector,
            MapLayerViewRegistry viewRegistry,
            AllianceSource allianceSource) {

        // Clear a spotlight the loaded save's active view does not offer, before the terrain reads
        // it - a bloc can lapse while a save sits unopened (a faction removed by a mod change, an
        // alliance dissolved), and a dangling spotlight would recede the sector behind a bloc the
        // player cannot unpick.
        runGuardedStep(
            () -> FilterSelectionHeal.healStaleSelectionAgainstActiveView(sector, viewRegistry),
            "Failed to heal KMU political map spotlight selection");

        for (var listener : REFRESH_LISTENERS) {

            runGuardedStep(
                () -> listener.installOn(sector),
                INSTALL_FAILURE_PREFIX + listener.listenerName());
        }

        runGuardedStep(
            () -> installMapLayerSectorWatcher(sector, allianceSource),
            INSTALL_FAILURE_PREFIX + "sector watcher");
    }

    /**
     * Clears every live repaint this registers, for a player switching the map layers off.
     *
     * <p>The save heal has nothing to take back, repairing the save rather than standing anything
     * up, so what is left is the listeners and the watcher - each of which would otherwise go on
     * marking an overlay stale that nothing is drawing.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        for (var listener : REFRESH_LISTENERS) {

            runGuardedStep(
                () -> listener.removeFrom(sector),
                REMOVAL_FAILURE_PREFIX + listener.listenerName());
        }

        runGuardedStep(
            () -> removeMapLayerSectorWatcher(sector),
            REMOVAL_FAILURE_PREFIX + "sector watcher");
    }

    // Stops the per-frame watcher polling. By class, which is safe because the class is this
    // layer's own: no other layer's poll, and no sibling mod's, is installed under it.
    static void removeMapLayerSectorWatcher(SectorAPI sector) {
        SectorScripts.removeTransientScripts(sector, PoliticalMapSectorWatcher.class);
    }

    // Registers the per-frame watcher that refreshes the political map when a
    // change the engine fires no event for slips past the listeners - the set of
    // drawn systems shifting (a gate activating, a jump point established), a drawn
    // system changing hands (an AI colony founded in a system already on the map),
    // or a mobile system drifting to a new position. The watcher owns only the
    // cadence, so which of those count as a change is handed in as the political
    // map's own staleness source. Transient and cleared before it is added, so it
    // is fresh on every load and never doubles up within a session either.
    static void installMapLayerSectorWatcher(SectorAPI sector, AllianceSource allianceSource) {

        // A fresh source per load, so the baselines it diffs against start empty rather
        // than carrying the previous save's last read into this one. Built against this
        // sector's installed machinery, for the same reason every listener above is built
        // against this sector: what it stages there is this sector's alone.
        SectorScripts.installTransientScript(
            sector,
            () -> new PoliticalMapSectorWatcher(
                new PoliticalMapStalenessSource(
                    SectorMapMachineryIndex.resolveMachineryFor(sector),
                    allianceSource)));
    }

    // One listener that marks a system stale so the overlay repaints live rather than only on
    // reload: the class it is installed and removed by, how it is built for a sector, and what a
    // failure names it as.
    private record RefreshListener<T>(
        Class<T> listenerClass,
        Function<SectorAPI, T> createListener,
        String listenerName) {

        void installOn(SectorAPI sector) {
            SectorListeners.installListener(sector, listenerClass, () -> createListener.apply(sector));
        }

        void removeFrom(SectorAPI sector) {
            SectorListeners.removeListener(sector, listenerClass);
        }
    }
}
