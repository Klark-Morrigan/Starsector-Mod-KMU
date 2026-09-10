package kmu.maplayers.base.layer;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.installation.MapLayerInstallations;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Brings each registered layer's standing on a sector into line with the bar the player arranged: a
 * tab they took off stops costing, and one they put back starts paying again.
 *
 * <p>Registration and standing part company here. A hidden layer stays registered - the arranging
 * dialog has to list it to offer it back, and a stored pick naming it must not read as an id from a
 * build that dropped it - but it loses its wiring on every sector, so its listeners stop firing and
 * its polls stop sweeping the sector for a picture nobody can reach.
 *
 * <p><b>Driven by the hidden set, not by the order.</b> An id entering the hidden list stands one
 * layer down and an id leaving it stands one up; a reorder moves no id between the two and so stands
 * nothing up or down. Which is why each layer is diffed against what it was last applied as rather
 * than acted on wholesale: the alternative has a tab dragged one place up tearing a layer's
 * listeners down and building them again.
 *
 * <p>Asked on load, so a layer hidden in the store never stands up on a sector at all, and again
 * whenever the arrangement is written. Per sector throughout - standing up is against one sector,
 * and {@link MapLayerInstallations} is what says which sectors there are to stand on.
 *
 * <p>Every layer is acted on behind its own failure boundary. An install carrying another mod's
 * layer calls a stranger's code here, and a layer that throws on the way up is not a reason for the
 * layers after it in the row to go unwired.
 *
 * <p>Final class with a private constructor: process-wide driver, no instances.
 */
public final class MapLayerStandings {

    // The news a failing half carries. One message for both directions: what the player loses either
    // way is that one tab's map is not keeping itself up to date.
    private static final String STANDING_UNCHANGED_WARNING =
        "Failed to bring a KMU map layer's standing into line with the arranged bar";

    private MapLayerStandings() {
        // process-wide driver, no instances.
    }

    /**
     * Brings one sector's layers into line with the arrangement: every layer the player has on the
     * bar standing, every layer they took off it not.
     *
     * <p>What the entry point runs as the layers are installed on a loaded sector. A sector with no
     * installation - the switch flipped with no game loaded - has nothing to stand on and is left
     * alone.
     *
     * @param sector the sector being brought into line
     */
    public static void applyArrangementTo(SectorAPI sector) {
        applyArrangementToInstallation(MapLayerInstallations.resolveInstallationFor(sector));
    }

    /**
     * The same over every sector the map layers are installed on, for a change to the arrangement
     * itself rather than to which sector is loaded.
     *
     * <p>Every sector rather than the live one because the arrangement is one preference shared by
     * all of them: a layer taken off the bar is off it wherever the player goes, so a sector left
     * unvisited would otherwise keep the wiring of a tab that is no longer there.
     */
    public static void applyArrangementWhereverInstalled() {

        for (var installation : MapLayerInstallations.getEveryInstallation()) {
            applyArrangementToInstallation(installation);
        }
    }

    /**
     * Takes every standing layer on one sector back down, whatever the arrangement says.
     *
     * <p>What the switch that turns the map layers off runs, and the path a player uninstalls the
     * mod from a save through. It is the arrangement that decides which layers pay while the feature
     * is on; this decides nothing and simply ends all of it.
     *
     * @param sector the sector being cleared
     */
    public static void standEveryLayerDownFrom(SectorAPI sector) {

        var installation = MapLayerInstallations.resolveInstallationFor(sector);
        var installedSector = installation.resolveSector();

        if (installedSector == null) {
            return;
        }
        var standingLayers = resolveStandingLayersIn(installation);

        for (var layer : MapLayerRegistry.getLayers()) {
            runGuardedStep(
                () -> applyLayerStanding(layer, false, installedSector, standingLayers),
                STANDING_UNCHANGED_WARNING);
        }
    }

    // One sector's layers against the arrangement standing at this moment. The arrangement is read
    // once for the whole walk rather than per layer: it is one answer about the bar, and a store
    // read per layer would let the row shift under the walk that is acting on it.
    //
    // A sector-less installation is the detached one, which is nobody's sector: there is nothing for
    // a layer to be stood up on, and recording a standing against it would have the next real sector
    // inherit a reading of a sector that never existed.
    private static void applyArrangementToInstallation(MapLayerInstallation installation) {

        var sector = installation.resolveSector();

        if (sector == null) {
            return;
        }
        var arrangement = LiveMapLayerArrangement.resolveArrangement();
        var standingLayers = resolveStandingLayersIn(installation);

        for (var layer : MapLayerRegistry.getLayers()) {
            runGuardedStep(
                () -> applyLayerStanding(
                    layer,
                    !arrangement.isLayerHidden(layer.getId()),
                    sector,
                    standingLayers),
                STANDING_UNCHANGED_WARNING);
        }
    }

    // One layer brought to where it should be, and left alone where it is already there. The diff is
    // what keeps a reorder - which writes the whole arrangement back - from tearing down and
    // rebuilding every layer on the bar.
    //
    // A layer that states no pair has no sector wiring to stand up or take back, so it is simply
    // always standing and neither half has anything to do. Nothing is recorded for it either: a
    // standing nobody stood is not a standing to take back.
    private static void applyLayerStanding(
            MapLayer layer,
            boolean isWanted,
            SectorAPI sector,
            StandingLayers standingLayers) {

        var layerId = layer.getId();

        if (standingLayers.isLayerStanding(layerId) == isWanted) {
            return;
        }
        var standing = layer.resolveStanding();

        if (standing == null) {
            return;
        }
        standingLayers.recordLayerStanding(layerId, isWanted);

        if (isWanted) {
            standing.standLayerUpOn(sector);
        } else {
            standing.standLayerDownFrom(sector);
        }
    }

    // What this sector remembers of which layers are up on it, made on the first ask and released
    // with the installation holding it.
    private static StandingLayers resolveStandingLayersIn(MapLayerInstallation installation) {
        return installation.resolveMachinery(StandingLayers.class, StandingLayers::new);
    }
}
