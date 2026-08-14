package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.StarSystems;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;

import java.util.List;
import java.util.Map;

/**
 * One rebuild's band source: everything a cell's band is settled from, sampled once, so asking
 * for a cell's band is a call rather than a fresh set of reads.
 *
 * <p>What it holds is what must not vary across a pass. The planner is the mechanic the active
 * view paints by, resolved once so no cell is counted by a different one; the sizes are one read
 * of the design's proportions; the holder map is the pass's own, so the cells that get a band are
 * exactly the cells something painted. Sampling them here mirrors how the rest of a rebuild is
 * driven - one snapshot, then a per-item call over it - and is what lets the incremental re-shape
 * bake a band identical to the one the full rebuild would have.
 *
 * <p>The holder map is also the cost gate, and the reason it is held rather than looked up per
 * call site. Most of the sector is cells nobody paints, and the claim mechanic's count walks a
 * system's whole market list; without the gate, every empty cell in the sector would pay for a
 * contest nobody is contesting.
 */
public final class CellRibbonsBuilder {

    private final SystemRibbonPlanner planner;
    private final RibbonStyle style;
    private final Map<String, DominantHolder> holderBySystemId;
    private final Map<String, StarSystemAPI> systemById;
    private final Map<String, double[]> siteBySystemId;

    private CellRibbonsBuilder(
            SystemRibbonPlanner planner,
            RibbonStyle style,
            Map<String, DominantHolder> holderBySystemId,
            Map<String, StarSystemAPI> systemById,
            Map<String, double[]> siteBySystemId) {
                
        this.planner = planner;
        this.style = style;
        this.holderBySystemId = holderBySystemId;
        this.systemById = systemById;
        this.siteBySystemId = siteBySystemId;
    }

    /**
     * Samples everything one pass's bands are settled from.
     *
     * @param sector           the sector the counts are read from
     * @param view             the active view, which supplies the mechanic its cells are counted
     *                         by - the same one they were painted by
     * @param grouping         the view's grouping, sampled once by the pass so a band folds
     *                         factions into blocs exactly as the fill did
     * @param holderBySystemId who paints each system this pass, the gate deciding which cells are
     *                         asked for a band at all
     * @param geometryCache    the cells' geometry, read for each system's own site - the point a
     *                         band's start is found above
     * @return the source the pass bakes its bands through
     */
    public static CellRibbonsBuilder createForPass(
            SectorAPI sector,
            PoliticalMapView view,
            HolderGrouping grouping,
            Map<String, DominantHolder> holderBySystemId,
            CellGeometryCache geometryCache) {

        var style = RibbonStyle.createAuthoredDefaults();

        return new CellRibbonsBuilder(
            view.resolveRibbonPlanner(sector, grouping, style.lengths()),
            style,
            holderBySystemId,
            StarSystems.indexById(sector),
            geometryCache.getSiteBySystemId());
    }

    /**
     * Bakes the band of one cell.
     *
     * @param drawnSystemId the system the cell draws as, or null for a cell with no star of its
     *                      own - which nothing paints, so it carries no band
     * @param fillPolygon   the cell's painted outline, the ring the band runs inside
     * @return the cell's baked band, or {@link CellRibbon#NONE} where it draws none
     */
    public CellRibbon buildCellRibbon(String drawnSystemId, List<double[]> fillPolygon) {

        // Nothing paints the cell, so there is no bloc for the band's gate to be stated against:
        // an uninhabited cell reports no presence because presence is what a fill is, and a band
        // only ever says what the fill beneath it leaves out.
        if (drawnSystemId == null || !holderBySystemId.containsKey(drawnSystemId)) {
            return CellRibbon.NONE;
        }
        var system = systemById.get(drawnSystemId);
        var site = siteBySystemId.get(drawnSystemId);

        // A system the sector no longer lists, or one with no recorded site, leaves the band with
        // nothing to count or nowhere to start from. Both are the same answer as an unpainted
        // cell: no band, rather than one laid out from a stand-in point.
        if (system == null || site == null) {
            return CellRibbon.NONE;
        }
        return CellRibbonBuilder.buildCellRibbon(
            fillPolygon,
            site,
            planner.planSystemRibbon(system),
            style);
    }
}
