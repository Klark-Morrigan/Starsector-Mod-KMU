package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.scripts.SectorScripts;

import kmu.maplayers.base.layer.MapLayerScreens;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the two passes that keep the map layers' controls honest: the tick box on the game's
 * own filter row, and the heal that keeps each screen's pick on a tab its bar still offers.
 *
 * <p>Its own installer rather than a line inside the sidebar's, because what it stands up is not the
 * sidebar: the box goes on a widget the game built, outlives every frame the sidebar draws on, and is
 * the one thing able to bring the sidebar back once the player has switched the layers off. An
 * installer per half of the feature is also what lets a failure here cost the box alone.
 *
 * <p>A guarded step each, and the heal after the box, so a sector that refuses one pass still gets
 * the other. Their frame order is the same way round and for a reason: standing a box is what
 * withholds a tab from that screen, so a box going up is healed against on the frame it goes up
 * rather than the one after.
 *
 * <p>Both go on per sector through {@link SectorScripts}, which owns the transience and the
 * clear-then-add: two boxes over one pick, or two heals writing one answer twice, are what a second
 * registration buys.
 *
 * <p>The take-back stops the passes and nothing else. A box already standing stays where it is, the
 * game's row offering no way to take one off again - it goes on moving a pick nothing reads while
 * the layers are off, and the next row the player opens is bare. That the row is rebuilt on every
 * open is the same fact the pass itself rests on, and is what keeps a feature switched off and on
 * again from putting a second box beside the first.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class MapChromeInstaller {

    private MapChromeInstaller() {
        // utility class, no instances.
    }

    /**
     * Registers the pass that maintains the tick box and the pass that heals the screens' picks, each
     * behind its own failure boundary.
     *
     * @param sector the loaded sector; null leaves both unattempted rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        runGuardedStep(
            () -> installMapLayerToggleUpkeep(sector),
            "Failed to install KMU map layer filter row control");

        runGuardedStep(
            () -> SectorScripts.installTransientScript(sector, MapLayerPickUpkeep::new),
            "Failed to install KMU map layer pick heal");
    }

    /**
     * Stops both passes, for a player switching the map layers off.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> SectorScripts.removeTransientScripts(sector, MapLayerToggleUpkeep.class),
            "Failed to remove KMU map layer filter row control");

        runGuardedStep(
            () -> SectorScripts.removeTransientScripts(sector, MapLayerPickUpkeep.class),
            "Failed to remove KMU map layer pick heal");
    }

    // A step of its own rather than the bare registration the heal is, because standing the box up
    // carries one thing the registration does not: what the screens believe about having one.
    static void installMapLayerToggleUpkeep(SectorAPI sector) {

        // A campaign just loaded has no box standing on any row, whatever an earlier one in this
        // session managed to attach. The word that a screen has one is held for the process while the
        // pick it governs reads the loaded sector's memory, so nothing else takes it back - and this
        // campaign's stored hide would otherwise be acted on from the first frame, on the strength of
        // a control the previous campaign put up. Before the registration, since a load with no sector
        // to install into is a load with no box either.
        MapLayerScreens.forgetControlsAttached();

        SectorScripts.installTransientScript(sector, MapLayerToggleUpkeep::new);
    }
}
