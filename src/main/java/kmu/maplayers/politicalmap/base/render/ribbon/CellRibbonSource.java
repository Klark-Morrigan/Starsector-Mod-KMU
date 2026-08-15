package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.StarSystems;

import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanRules;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedCellBands;
import kmu.settings.KmuPoliticalMapSettings;

import java.util.List;
import java.util.Map;

/**
 * One rebuild's band source: everything a cell's band is settled from, sampled once, so asking
 * for a cell's band is a call rather than a fresh set of reads.
 *
 * <p>What it holds is what must not vary across a pass. The planner is the mechanic the active
 * view paints by, resolved once so no cell is counted by a different one; the sizes are one read
 * of the player's proportions, so a slider moved mid-pass cannot leave two cells drawn to
 * different designs; the holder map is the pass's own, so the cells that get a band are exactly
 * the cells something painted; and the names' boxes are one reading of where the map's names
 * ended up, so no two cells keep clear of different placements of the same name. Sampling them
 * here mirrors how the rest of a rebuild is driven -
 * one snapshot, then a per-item call over it - and is what lets the incremental re-shape bake a
 * band identical to the one the full rebuild would have.
 *
 * <p>The holder map is also the cost gate, and the reason it is held rather than looked up per
 * call site. Most of the sector is cells nobody paints, and the claim mechanic's count walks a
 * system's whole market list; without the gate, every empty cell in the sector would pay for a
 * contest nobody is contesting.
 *
 * <p>The diagnostic overlay's paths are traced through the same pass, from the same sizes and the
 * same gate, for the reason the overlay exists at all: it is worth looking at only while it is the
 * cells' own paths it is drawing. A pass with the bands switched off holds nothing to lay them out
 * from, so it traces nothing either - there is no band design in play for an overlay to report on.
 *
 * <p>Named a source rather than a builder because the per-cell work is
 * {@link CellRibbonBuilder}'s: what this adds is the pass the work is done under, which is the
 * same split {@link kmu.maplayers.politicalmap.dominance.ribbon.HeldSystemRibbonSource} makes one
 * level up between a mechanic's answer and the rule that shapes it.
 */
public final class CellRibbonSource {

    // What a pass with the bands switched off is laid out by. Zeroes and refusals rather than the
    // player's knobs because no band is ever laid out at all: such a pass answers every cell at
    // its gate, well before a ring is traced, so reading the real answers for it would be a
    // settings read taken to settle nothing.
    private static final RibbonStyle BANDLESS_STYLE =
        new RibbonStyle(0, 0, 0, new RibbonSegmentLengths(0, 0), false);

    private final SystemRibbonPlanner planner;
    private final RibbonStyle style;
    private final Map<String, DominantHolder> holderBySystemId;
    private final Map<String, StarSystemAPI> systemById;
    private final Map<String, double[]> siteBySystemId;
    private final List<List<double[]>> nameBoxes;

    private CellRibbonSource(
            SystemRibbonPlanner planner,
            RibbonStyle style,
            Map<String, DominantHolder> holderBySystemId,
            Map<String, StarSystemAPI> systemById,
            Map<String, double[]> siteBySystemId,
            List<List<double[]>> nameBoxes) {

        this.planner = planner;
        this.style = style;
        this.holderBySystemId = holderBySystemId;
        this.systemById = systemById;
        this.siteBySystemId = siteBySystemId;
        this.nameBoxes = nameBoxes;
    }

    /**
     * Samples everything one pass's bands are settled from, or nothing at all where the player has
     * the bands switched off - in which case every cell is answered "no band" without a count, a
     * size read, or a ring traced.
     *
     * @param sector           the sector the counts are read from
     * @param viewGrouping     the active view and the grouping it resolved, the pair the pass
     *                         already carries: the view supplies the mechanic its cells are
     *                         counted by - the same one they were painted by - and the grouping
     *                         folds factions into blocs exactly as the fill did
     * @param holderBySystemId who paints each system this pass, the gate deciding which cells are
     *                         asked for a band at all
     * @param siteBySystemId   each system's own site, the point a band's start is found above.
     *                         Taken as the one map this reads rather than as the geometry cache
     *                         holding it, so what a band is laid out from is stated in the
     *                         signature rather than reachable through it
     * @param nameBoxes        the room the drawn cluster names take up, which every cell's band
     *                         keeps out of; the whole map's, since a name reaches into cells its
     *                         own cluster does not hold
     * @return the source the pass bakes its bands through
     */
    public static CellRibbonSource createForPass(
            SectorAPI sector,
            ViewGrouping viewGrouping,
            Map<String, DominantHolder> holderBySystemId,
            Map<String, double[]> siteBySystemId,
            List<List<double[]>> nameBoxes) {

        if (!KmuPoliticalMapSettings.shouldDrawPoliticalMapRibbons()) {
            return createBandlessPass();
        }
        var style = RibbonStyleReader.readRibbonStyle();

        // The colour source and the laying rules are sampled here, once, and handed to whatever
        // planner the view resolves - so both mechanics of a composed planner read a bloc's
        // shades through one object and gate their cells by one rule.
        var inputs = RibbonPlanInputs.createForSector(
            sector,
            viewGrouping.grouping(),
            new RibbonPlanRules(
                style.lengths(),
                UncontestedCellBands.readFromLunaSettings()));

        return new CellRibbonSource(
            viewGrouping.view().resolveRibbonPlanner(sector, viewGrouping.grouping(), inputs),
            style,
            holderBySystemId,
            StarSystems.indexById(sector),
            siteBySystemId,
            nameBoxes);
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

        var site = resolveBandLayoutSite(drawnSystemId);
        var system = site == null ? null : systemById.get(drawnSystemId);

        // A system the sector no longer lists leaves the band with nothing to count, which is the
        // same answer as an unpainted cell: no band, rather than one counted off a stand-in.
        if (system == null) {
            return CellRibbon.NONE;
        }
        return CellRibbonBuilder.buildCellRibbon(
            fillPolygon,
            site,
            planner.planSystemRibbon(system),
            style,
            nameBoxes);
    }

    /**
     * Traces one cell's band path for the diagnostic overlay, whether or not a band was laid on
     * it.
     *
     * <p>Over the cells the band pass considered rather than every cell on the map: a band is only
     * ever laid where a bloc paints, so tracing the rest would bury the cells the overlay is asked
     * about under a ring around every piece of empty space in the sector. What it adds to the pass
     * above is everything downstream of the ring - a cell whose plan came back empty, and one
     * refused the room to draw what it planned, both still show the path they would have used.
     *
     * @param drawnSystemId the system the cell draws as, or null for a cell with no star of its
     *                      own - which nothing paints, so no band is ever laid on it
     * @param fillPolygon   the cell's painted outline, the ring the path runs inside
     * @return the cell's traced path, or {@link CellRibbonPath#NONE} where none was traced
     */
    public CellRibbonPath traceCellRibbonPath(String drawnSystemId, List<double[]> fillPolygon) {

        var site = resolveBandLayoutSite(drawnSystemId);

        return site == null
            ? CellRibbonPath.NONE
            : RibbonPathTracer.traceInspectedRibbonPath(fillPolygon, site, style);
    }

    // The point a cell's band is laid out from, or null on a cell no band is laid on at all.
    //
    // Two refusals in one answer, since both are the same fact about the cell rather than about
    // the band. Nothing paints it, so there is no bloc for the band's gate to be stated against -
    // an uninhabited cell reports no presence because presence is what a fill is, and a band only
    // ever says what the fill beneath it leaves out; or it has no recorded site, leaving the band
    // nowhere to start from rather than starting it somewhere arbitrary.
    //
    // Shared by the two calls above so the overlay covers exactly the cells the band pass
    // considered: a diagnostic answering for a wider set than the pass it reports on would show
    // paths where no band was ever going to be laid.
    private double[] resolveBandLayoutSite(String drawnSystemId) {

        if (drawnSystemId == null || !holderBySystemId.containsKey(drawnSystemId)) {
            return null;
        }
        return siteBySystemId.get(drawnSystemId);
    }

    // A pass with the bands switched off, which the empty holding states outright: the holding is
    // the gate every cell is answered by, so an empty one answers "no band" for the whole sector.
    // Nothing is sampled for it - no planner resolved, no system index built, no sizes read - so
    // the switch takes the counting off the rebuild as well as the bands off the map, which is the
    // half of it a player cannot see and the half that costs.
    private static CellRibbonSource createBandlessPass() {
        return new CellRibbonSource(
            system -> RibbonPlan.NONE,
            BANDLESS_STYLE,
            Map.of(),
            Map.of(),
            Map.of(),
            List.of());
    }
}
