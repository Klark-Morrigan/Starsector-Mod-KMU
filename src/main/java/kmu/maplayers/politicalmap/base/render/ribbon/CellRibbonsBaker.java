package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.labels.LabelLineBoxes;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterNameBoxes;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.settings.KmuPoliticalMapSettings;

import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Bakes the presence bands over cells that have already been shaped and named.
 *
 * <p>Its own pass, run after the rest of a rebuild rather than inside the cell loop, because a
 * band needs two things settled that no single cell knows about: the shape it runs inside, and
 * where every cluster name on the map ended up. The names are fitted after the cells are shaped -
 * a name is placed inside the border its cluster's cells trace - so a band baked as its cell was
 * shaped would be laid before any name had a place, and the name would then be drawn across it.
 * Baking last is what lets the band keep out of the names' way instead - which is the shipped
 * answer rather than the only one, a player being free to hand the room back to the band and have
 * the name draw across it after all.
 *
 * <p>The bands are read out of the cells' own recorded shapes rather than off a shaping pass, so
 * the incremental refresh re-bakes the cells it disturbed through the very same call the full
 * rebuild bakes all of them through - which is what keeps an incrementally-updated band identical
 * to the one a full rebuild would lay.
 *
 * <p>Sampled once per pass and then asked, like the band source it holds: what a pass bakes from -
 * the cells, their geometry, and the room the names took - must not vary between the cells of one
 * pass, and holding it is what makes baking every cell and baking a handful the same operation
 * over the same snapshot rather than two calls that have to be handed matching inputs.
 */
public final class CellRibbonsBaker {

    private static final Logger LOG = Global.getLogger(CellRibbonsBaker.class);

    private final PoliticalMapTerritories territories;
    private final Map<String, String> systemIdByCellId;
    private final CellRibbonSource ribbonSource;

    private CellRibbonsBaker(
            PoliticalMapTerritories territories,
            Map<String, String> systemIdByCellId,
            CellRibbonSource ribbonSource) {

        this.territories = territories;
        this.systemIdByCellId = systemIdByCellId;
        this.ribbonSource = ribbonSource;
    }

    /**
     * Samples everything one pass's bands are baked from.
     *
     * @param territories    the built cells, read for their shapes and written back with their
     *                       bands
     * @param geometryCache  the cells' geometry, for the system each draws as and its site
     * @param sector         the sector the counts are read from
     * @param clusterAnchors the cluster names' placements, whose boxes the bands keep out of
     * @return the pass, ready to bake whichever cells the caller names
     */
    public static CellRibbonsBaker createForPass(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            SectorAPI sector,
            List<ClusterAnchor> clusterAnchors) {

        // Both geometry reads are taken here, once, rather than per cell inside the loop: each
        // hands back a fresh unmodifiable view over the live cells, so asking per cell would mint
        // a wrapper per cell to answer one lookup. Taking them apart here is also what lets each
        // consumer state the one map it reads instead of holding the cache both live in.
        return new CellRibbonsBaker(
            territories,
            geometryCache.getSystemIdByCellId(),
            CellRibbonSource.createForPass(
                sector,
                territories.getViewGrouping(),
                territories.getHolderBySystemId(),
                geometryCache.getSiteBySystemId(),
                resolveNameBoxes(clusterAnchors)));
    }

    /** Bakes the band of every drawn cell, replacing whatever each was carrying. */
    public void bakeAllCellRibbons() {
        bakeCellRibbonsOf(territories.getFillPolygonByCellId().keySet());
    }

    /**
     * Bakes the bands of the named cells alone, leaving every other cell's as it stands.
     *
     * <p>For the incremental refresh, whose cells are the handful a colony change disturbed. A
     * cell that draws nothing is skipped rather than being an error: the caller names the cells
     * something happened to, and one of them losing its last colony is one of the things that
     * can have happened.
     *
     * @param cellIds the cells to re-bake
     */
    public void bakeCellRibbonsOf(Collection<String> cellIds) {

        var bakeStart = System.nanoTime();

        // A band's count walks a system's markets - the claim mechanic's walks all of them - so
        // this is the one part of a rebuild that could rival the known label-fit stall, and it is
        // profiled and timed on its own so a rebuild that slows down says which half slowed.
        var bakedCells = KmuProfiling
            .getProfiler()
            .measure("politicalMap.bakeRibbons", () -> bakeCellRibbons(cellIds));

        LOG.debug("Political map presence bands baked; cells="
            + cellIds.size()
            + " banded=" + bakedCells
            + " took=" + Timings.formatMillis(System.nanoTime() - bakeStart));
    }

    // The room the names take up, or none at all for either of two reasons, answered side by side
    // so they read in one place: the player has the names switched off, in which case there is
    // nothing on the map for a band to be interrupted by, whatever placements the anchor overlay
    // may still be holding; or the player would rather the bands ran whole beneath the names.
    // Nothing downstream branches on why - the builder takes the boxes and carves what it is
    // handed, which is what keeps the carve testable on hand-built rings.
    //
    // How much room a name is then taken to need is the player's too, and the two readings differ
    // by more than they sound: a placement's fitted box is the chord the search accepted, which
    // overhangs the words by whatever it beat them by, while the drawn lines are what the reader
    // sees a name occupying. Both come back as world boxes, so the choice reaches no further than
    // this call.
    private static List<List<double[]>> resolveNameBoxes(List<ClusterAnchor> clusterAnchors) {

        var isBandKeptClearOfNames = NameFormatPreference.getSelectedNameFormat().areNamesDrawn()
            && KmuPoliticalMapSettings.shouldKeepPoliticalMapRibbonsClearOfNames();

        if (!isBandKeptClearOfNames) {
            return List.of();
        }
        return switch (KmuPoliticalMapSettings.getPoliticalMapRibbonNameClearance()) {
            case FITTED_BOX -> ClusterNameBoxes.listNameBoxes(clusterAnchors);
            case WORDS -> LabelLineBoxes.listLineBoxes(clusterAnchors);
        };
    }

    // Bakes each named cell's band inside the shape that cell already records, reporting how many
    // of them came back with anything to draw.
    private int bakeCellRibbons(Collection<String> cellIds) {

        var bakedCells = 0;

        for (var cellId : cellIds) {

            var fillPolygon = territories.getFillPolygonByCellId().get(cellId);
            if (fillPolygon == null) {
                continue;
            }
            var ribbon = ribbonSource.buildCellRibbon(
                systemIdByCellId.get(cellId),
                fillPolygon);

            territories.putCellRibbon(cellId, ribbon);
            bakedCells += ribbon.isEmpty() ? 0 : 1;
        }
        return bakedCells;
    }
}
