package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.scripts.SectorScripts;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing the substrate's own poll up on a loaded sector, and taking it back when the map layers
 * are switched off.
 *
 * <p>Beside the machinery rather than beside a layer, because what the poll keeps up to date is
 * shared by every layer there is - {@link MapSubstrateStalenessSource} says why that has to outlive
 * any one of them.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class MapSubstrateRefreshInstaller {

    private MapSubstrateRefreshInstaller() {
        // utility class, no instances.
    }

    /**
     * Registers the substrate's poll, behind its own failure boundary.
     *
     * @param sector the loaded sector; null leaves the poll unregistered rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        runGuardedStep(
            () -> installMapSubstrateSectorWatcher(sector),
            "Failed to install KMU map substrate sector watcher");
    }

    /**
     * Clears the substrate's poll, so it does not go on sweeping a sector for a feature the player
     * has switched off.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> removeMapSubstrateSectorWatcher(sector),
            "Failed to remove KMU map substrate sector watcher");
    }

    // Registers the per-frame poll that records what each system's own inhabitants can see. A fresh
    // source per load, built against this sector: the observations it writes are that sector's, and
    // one kept from an earlier load would go on writing into the sector it was made over.
    //
    // Transient and cleared before it is added, so it is fresh on every load and never doubles up
    // within a session either.
    static void installMapSubstrateSectorWatcher(SectorAPI sector) {
        SectorScripts.installTransientScript(
            sector,
            () -> new MapSubstrateSectorWatcher(new MapSubstrateStalenessSource(sector)));
    }

    // Stops the poll. By class, which is safe here where it would not be for a library script: this
    // one is the framework's own and no sibling mod installs it. The engine clears by exact class,
    // so this reaches the substrate's poll and leaves every layer's standing.
    static void removeMapSubstrateSectorWatcher(SectorAPI sector) {
        SectorScripts.removeTransientScripts(sector, MapSubstrateSectorWatcher.class);
    }
}
