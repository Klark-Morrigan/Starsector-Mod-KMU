package kmu.maplayers.politicalmap;

import com.fs.starfarer.api.campaign.SectorAPI;

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
     * @param sector       the loaded sector; null leaves the listeners unregistered rather than
     *                     throwing
     * @param viewRegistry the political map's views, whose spotlights the save heal judges
     */
    public static void installAll(SectorAPI sector, MapLayerViewRegistry viewRegistry) {

        // Clear a spotlight the loaded save's active view does not offer, before the terrain reads
        // it - a bloc can lapse while a save sits unopened (a faction removed by a mod change, an
        // alliance dissolved), and a dangling spotlight would recede the sector behind a bloc the
        // player cannot unpick.
        runGuardedStep(
            () -> FilterSelectionHeal.healStaleSelectionAgainstActiveView(sector, viewRegistry),
            "Failed to heal KMU political map spotlight selection");

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

        runGuardedStep(
            () -> installPoliticalMapMarketTransferListener(sector),
            "Failed to install KMU political map market-transfer listener");
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

        runGuardedStep(
            () -> {
                SectorListeners.removeListener(sector, PoliticalMapDiscoveryListener.class);
                SectorListeners.removeListener(sector, PoliticalMapColonySizeListener.class);
                SectorListeners.removeListener(sector, PoliticalMapDecivListener.class);
                SectorListeners.removeListener(sector, PoliticalMapColonisationListener.class);
                SectorListeners.removeListener(sector, PoliticalMapMarketTransferListener.class);
            },
            "Failed to remove KMU political map refresh listeners");

        runGuardedStep(
            () -> removeMapLayerSectorWatcher(sector),
            "Failed to remove KMU political map sector watcher");
    }

    // Stops the per-frame watcher polling. By class, which is safe because the class is this
    // layer's own: no other layer's poll, and no sibling mod's, is installed under it.
    static void removeMapLayerSectorWatcher(SectorAPI sector) {
        SectorScripts.removeTransientScripts(sector, PoliticalMapSectorWatcher.class);
    }

    // Registers the listener that refreshes the political map when the player
    // discovers a map-relevant entity (a market, a jump point, or a gate), so
    // the overlay updates live rather than only on reload.
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

    // Registers the listener that marks a conquered colony's system stale when it
    // changes hands, which vanilla reports nothing for. Whatever reports transfers
    // on this sector reaches it through the listener manager; on an install where
    // nothing does, it is registered and never called.
    static void installPoliticalMapMarketTransferListener(SectorAPI sector) {
        SectorListeners.installListener(
            sector,
            PoliticalMapMarketTransferListener.class,
            () -> new PoliticalMapMarketTransferListener(sector));
    }

    // Registers the per-frame watcher that refreshes the political map when a
    // change the engine fires no event for slips past the listeners - the set of
    // drawn systems shifting (a gate activating, a jump point established), a drawn
    // system changing hands (an AI colony founded in a system already on the map),
    // or a mobile system drifting to a new position. The watcher owns only the
    // cadence, so which of those count as a change is handed in as the political
    // map's own staleness source. Transient and cleared before it is added, so it
    // is fresh on every load and never doubles up within a session either.
    static void installMapLayerSectorWatcher(SectorAPI sector) {

        // A fresh source per load, so the baselines it diffs against start empty rather
        // than carrying the previous save's last read into this one. Built against this
        // sector's installed machinery, for the same reason every listener above is built
        // against this sector: what it stages there is this sector's alone.
        SectorScripts.installTransientScript(
            sector,
            () -> new PoliticalMapSectorWatcher(
                new PoliticalMapStalenessSource(
                    SectorMapMachineryIndex.resolveMachineryFor(sector))));
    }
}
