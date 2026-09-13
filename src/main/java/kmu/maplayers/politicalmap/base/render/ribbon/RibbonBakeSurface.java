package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.labels.LabelLineBoxes;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterNameBoxes;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.settings.KmuPoliticalMapRibbonSettings;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The drawn map one bake lays its bands on: which cells are offered a band at all, where each band
 * starts, the room it keeps clear of, and the rings already traced inside the cells' current
 * shapes.
 *
 * <p>The four travel together because each is one reading of a map that has already been shaped and
 * named, and none may vary between the cells of a single bake: two cells laid against two
 * placements of one name would keep clear of different rooms, and two laid against two readings of
 * the cells would disagree about which of them is even settled. Carried as one value, baking every
 * cell and baking the handful a colony flip disturbed are the same operation over the same map
 * rather than two calls that have to be handed matching pieces.
 *
 * <p>The inhabited set sits with the sites because the two answer one refusal between them: nobody
 * lives in the cell, so there are no holdings for a band to report, or it has no recorded site,
 * leaving the band nowhere to start from.
 *
 * <p>The ring store is the cells' own rather than a copy. A bake runs whenever a name may have
 * moved, while a ring moves only when its cell is re-shaped, so the rings outlive the bake that
 * traced them - and holding the cells' store is what drops a stale path with the shape it was
 * traced inside rather than through anything a bake has to remember to do.
 *
 * @param inhabitedSystemKeys every system something stands in this bake, the gate deciding which
 *                            cells are asked for a band at all
 * @param siteBySystemKey     each system's own site, the point a band's start is found above
 * @param nameBoxes           the room the drawn cluster names take up, the whole map's, since a
 *                            name reaches into cells its own cluster does not hold
 * @param ringPathCache       the rings already traced inside the cells' current shapes, asked
 *                            before a cell's ring is walked and written back when one is
 */
public record RibbonBakeSurface(
    Set<SystemKey> inhabitedSystemKeys,
    Map<SystemKey, double[]> siteBySystemKey,
    List<List<double[]>> nameBoxes,
    CellRingPathCache ringPathCache) {

    /**
     * Reads the map one bake lays its bands on, off the cells it has already shaped and named.
     *
     * @param territories    the built cells, read for which systems are settled and for the store
     *                       their traced rings are kept in
     * @param geometryCache  the cells' geometry, read for the site each system draws at
     * @param clusterAnchors the cluster names' placements, whose boxes the bands keep out of
     * @return the map this bake is laid against
     */
    public static RibbonBakeSurface createForPass(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            List<ClusterAnchor> clusterAnchors) {

        return new RibbonBakeSurface(
            // The pass's inhabitation scan rather than its holding, so a settled system this layer
            // gives to nobody - an unclaimed pirate haven on the claims layer - is still offered a
            // band. It is the same set the factionless cell beneath it is classified from, so a
            // cell drawn as settled and a cell offered a band are one set.
            territories.getInhabitedSystemKeys(),
            // Taken here, once, rather than per cell inside a loop: the read hands back a fresh
            // unmodifiable view over the live cells, so asking per cell would mint a wrapper per
            // cell to answer one lookup.
            geometryCache.getSiteBySystemKey(),
            // The name format off the build's own sampling rather than off the stored preference:
            // the boxes a band keeps clear of are the boxes those very names were fitted into, so
            // a second reading could carve the bands around names the map is not drawing.
            resolveNameBoxes(clusterAnchors, territories.getContentInputs().nameFormat()),
            territories.getPaintedCells().getRingPathCache());
    }

    // The room the names take up, or none at all for either of two reasons, answered side by side
    // so they read in one place: the player has the names switched off, in which case there is
    // nothing on the map for a band to be interrupted by, whatever placements the anchor overlay
    // may still be holding; or the player would rather the bands ran whole beneath the names.
    // Nothing downstream branches on why - the builder takes the boxes and carves what it is
    // handed, which is what keeps the carve a matter of the boxes and nothing else.
    //
    // How much room a name is then taken to need is the player's too, and the two readings differ
    // by more than they sound: a placement's fitted box is the chord the search accepted, which
    // overhangs the words by whatever it beat them by, while the drawn lines are what the reader
    // sees a name occupying. Both come back as world boxes, so the choice reaches no further than
    // this call.
    private static List<List<double[]>> resolveNameBoxes(
            List<ClusterAnchor> clusterAnchors,
            FactionNameFormatChoice nameFormat) {

        var isBandKeptClearOfNames = nameFormat.areNamesDrawn()
            && KmuPoliticalMapRibbonSettings.shouldKeepPoliticalMapRibbonsClearOfNames();

        if (!isBandKeptClearOfNames) {
            return List.of();
        }
        return switch (KmuPoliticalMapRibbonSettings.getPoliticalMapRibbonNameClearance()) {
            case FITTED_BOX -> ClusterNameBoxes.listNameBoxes(clusterAnchors);
            case WORDS -> LabelLineBoxes.listLineBoxes(clusterAnchors);
        };
    }
}
