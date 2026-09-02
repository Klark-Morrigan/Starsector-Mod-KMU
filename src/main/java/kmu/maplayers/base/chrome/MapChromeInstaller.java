package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.layer.MapLayerScreens;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the pass that keeps the map layers' tick box on the game's own filter row.
 *
 * <p>One registration, so one guarded step - but its own installer rather than a line inside the
 * sidebar's, because what it stands up is not the sidebar: the box goes on a widget the game built,
 * outlives every frame the sidebar draws on, and is the one thing able to bring the sidebar back
 * once the player has switched the layers off. An installer per half of the feature is also what
 * lets a failure here cost the box alone.
 *
 * <p>Registered per sector, the pass being a script the sector ticks, and transient like the rest of
 * the feature's wiring: a script that entered the save would be restored beside the one each load
 * adds, and two of them would append two boxes to one row.
 *
 * <p>The take-back stops the pass and nothing else. A box already standing stays where it is, the
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
     * Registers the pass that maintains the tick box, behind its own failure boundary.
     *
     * @param sector the loaded sector; null leaves the box unattempted rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        runGuardedStep(
            () -> installMapLayerToggleUpkeep(sector),
            "Failed to install KMU map layer filter row control");
    }

    /**
     * Stops the pass, for a player switching the map layers off.
     *
     * @param sector the loaded sector; null is a no-op
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> removeMapLayerToggleUpkeep(sector),
            "Failed to remove KMU map layer filter row control");
    }

    // Registers the standing pass as this sector's own transient script, clearing whatever is
    // already registered first: two of them would each find the row bare of their own box and append
    // one, leaving the player two controls over a single pick. Removed by class, which is safe
    // because the script is this mod's own - no sibling mod runs one to be taken out with it.
    //
    // The same start-clean rule reaches past the script to what the screens believe, below.
    static void installMapLayerToggleUpkeep(SectorAPI sector) {

        // A campaign just loaded has no box standing on any row, whatever an earlier one in this
        // session managed to attach. The word that a screen has one is held for the process while the
        // pick it governs reads the loaded sector's memory, so nothing else takes it back - and this
        // campaign's stored hide would otherwise be acted on from the first frame, on the strength of
        // a control the previous campaign put up. Before the null check, since a load with no sector
        // to install into is a load with no box either.
        MapLayerScreens.forgetControlsAttached();

        if (sector == null) {
            return;
        }
        removeMapLayerToggleUpkeep(sector);

        sector.addTransientScript(new MapLayerToggleUpkeep());
    }

    static void removeMapLayerToggleUpkeep(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        sector.removeTransientScriptsOfClass(MapLayerToggleUpkeep.class);
    }
}
