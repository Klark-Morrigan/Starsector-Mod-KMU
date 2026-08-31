package kmu.maplayers.base.installation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The index from a sector to its installed map machinery, and the one place a seam that is handed
 * no sector resolves one.
 *
 * <p>Installing replaces whatever that sector already had, so asking twice cannot leave two
 * installations answering for one sector; removing releases and forgets. A load discards every
 * installation before the loaded sector's is made, which is what makes a leak impossible without
 * anything having to notice that a sector went away - the index holds each sector by reference, and
 * nobody is in a position to tell it that one is gone.
 *
 * <p>Resolution takes three forms because the seams do. A caller already holding a sector asks for
 * that sector's; a seam vanilla hands no sector at all - its map render hook is passed a fade factor
 * and nothing else - asks for the live sector's, which is the single place a global read stands in
 * for a sector nobody passed down; and a render surface, which is terrain and so reaches a
 * {@link LocationAPI} rather than a sector, asks by the location it sits in.
 *
 * <p>A sector with nothing installed resolves to a detached installation rather than to null. The
 * map layers sit behind a switch a player can leave off, so an uninstalled sector is an ordinary
 * state; answering null would make every seam below branch on a case that means "draw as you always
 * did".
 *
 * <p>Concurrent throughout, for the reason the refresh signals are: installing and removing happen
 * on the campaign thread while a frame resolves on the render thread.
 *
 * <p>Final class with a private constructor: process-wide index, no instances.
 */
public final class MapLayerInstallations {

    private static final Logger LOG = Global.getLogger(MapLayerInstallations.class);

    // What a resolution falls back on when the sector it was asked about has none: a real
    // installation that nothing indexes and nothing releases. Shared, so every caller that reaches
    // it meets one holder rather than one each - which is the arrangement a sector-less caller has
    // always had, back when each holder was a static of its own.
    //
    // Made over no sector, which is the honest answer: a caller that reached it named none. A stage
    // that reads the sector therefore finds nothing to read rather than falling through to the
    // running game's, which would have it draw a sector nobody asked it about.
    private static final MapLayerInstallation DETACHED_INSTALLATION =
        new MapLayerInstallation(null);

    // One installation per sector, keyed by the sector itself rather than by any id it carries: a
    // sector is the thing being installed on, and two sectors are two objects whether or not
    // anything about their contents differs.
    private static final Map<SectorAPI, MapLayerInstallation> installationsBySector =
        new ConcurrentHashMap<>();

    private MapLayerInstallations() {
        // process-wide index, no instances.
    }

    /**
     * Makes {@code sector} a fresh installation, releasing any it already had.
     *
     * <p>Replacing rather than reusing is what keeps a second install from inheriting the first
     * one's cached drawing: the two are separated by the install, not by whatever each holder
     * happens to notice has changed.
     *
     * @param sector the sector the map layers are being installed on; null installs nothing and
     *               yields the detached installation, since the switch can be flipped with no game
     *               loaded
     * @return the installation that sector's machinery is now held in
     */
    public static MapLayerInstallation installMachineryOn(SectorAPI sector) {

        if (sector == null) {
            return DETACHED_INSTALLATION;
        }

        // Computed in one pass so a replacement cannot be observed as an absence: a frame resolving
        // between a removal and a re-insertion would otherwise draw through the detached
        // installation for that one frame.
        var installation = installationsBySector.compute(sector, (key, replaced) -> {

            if (replaced != null) {
                replaced.disposeMachinery();
            }
            return new MapLayerInstallation(sector);
        });

        LOG.debug("Map layer machinery installed; installations=" + installationsBySector.size());
        return installation;
    }

    /**
     * Releases {@code sector}'s installation and forgets it, so a later resolution for that sector
     * answers with the detached installation rather than with a drawing nothing maintains.
     *
     * @param sector the sector the map layers are being removed from; null and an uninstalled
     *               sector are both left alone
     */
    public static void uninstallMachineryFrom(SectorAPI sector) {

        if (sector == null) {
            return;
        }

        var removed = installationsBySector.remove(sector);

        if (removed == null) {
            return;
        }
        removed.disposeMachinery();

        LOG.debug("Map layer machinery uninstalled; installations=" + installationsBySector.size());
    }

    /**
     * Releases every installation there is.
     *
     * <p>What a load runs before installing on the sector it loaded. Every installation standing at
     * that point belongs to a sector the load has already replaced, and no other seam is told that
     * a sector went away - so discarding all of them is what keeps a previous save's drawing from
     * outliving it.
     */
    public static void disposeEveryInstallation() {

        installationsBySector.values().forEach(MapLayerInstallation::disposeMachinery);
        installationsBySector.clear();

        LOG.debug("Map layer machinery discarded for every installed sector");
    }

    /**
     * @param sector the sector being asked about; null is an uninstalled sector
     * @return {@code sector}'s installation, or the detached one where it has none
     */
    public static MapLayerInstallation resolveInstallationFor(SectorAPI sector) {

        if (sector == null) {
            return DETACHED_INSTALLATION;
        }
        return installationsBySector.getOrDefault(sector, DETACHED_INSTALLATION);
    }

    /**
     * The installation of the sector whose hyperspace {@code location} is, for a caller that has a
     * location and no sector - which is every render surface, terrain reaching a containing location
     * and nothing above it.
     *
     * <p>Answered by walking the installed sectors rather than from a location index of its own.
     * There is one installed sector, so the walk is the cheaper half of the trade; what it buys is
     * that a location can never disagree with the sector it belongs to. A second index would have to
     * be written after the sector one - leaving a window in which a frame resolved the installation
     * a reinstall had just released - and kept in step through every removal.
     *
     * <p>The comparison rests on {@code sector.getHyperspace()} being the same object as the
     * surface's {@code entity.getContainingLocation()}. It is: the terrain is added to that
     * hyperspace and the engine seats an added entity in the location it was added to. Stated rather
     * than implied because a comparison that missed would take the whole overlay off screen instead
     * of degrading.
     *
     * <p>Answers null where nothing is installed in that location, and this is the one resolution
     * that does. A surface exists because an installation put its terrain there, so a location with
     * no installation is a surface belonging to a sector nothing is drawing; handing back the
     * detached installation would have it paint through the holder every sector-less caller shares,
     * which is a drawing of no sector at all rather than a fallback.
     *
     * @param location the location being asked about, typically a surface's containing one; null is
     *                 a surface not in any location yet
     * @return that location's installation, or null where it has none
     */
    public static MapLayerInstallation resolveInstallationIn(LocationAPI location) {

        if (location == null) {
            return null;
        }

        for (var installed : installationsBySector.entrySet()) {

            if (installed.getKey().getHyperspace() == location) {
                return installed.getValue();
            }
        }
        return null;
    }

    /**
     * @return the installation of the sector the game is running, or the detached one where that
     *         sector has none and where no game is loaded at all. For the seams vanilla drives
     *         without naming a sector, which is where the one global read this index exists to
     *         contain belongs
     */
    public static MapLayerInstallation resolveInstallationForLiveSector() {
        return resolveInstallationFor(Global.getSector());
    }
}
