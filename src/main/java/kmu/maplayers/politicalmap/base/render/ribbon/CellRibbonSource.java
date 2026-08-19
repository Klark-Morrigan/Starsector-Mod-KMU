package kmu.maplayers.politicalmap.base.render.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.math.geometry.RingPath;
import kmlib.starsector.systems.SectorStarSystems;

import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanInputs;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanRules;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;
import kmu.maplayers.politicalmap.base.ribbon.SystemRibbonPlanner;
import kmu.maplayers.politicalmap.base.ribbon.UncontestedRibbonRuns;
import kmu.settings.KmuPoliticalMapSettings;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One rebuild's band source: everything a cell's band is settled from, sampled once, so asking
 * for a cell's band is a call rather than a fresh set of reads.
 *
 * <p>What it holds is what must not vary across a pass. The planner is the mechanic the active
 * view paints by, resolved once so no cell is counted by a different one; the sizes are one read
 * of the player's proportions, so a slider moved mid-pass cannot leave two cells drawn to
 * different designs; the inhabited systems are the pass's own, so the cells that get a band are
 * exactly the cells something stands in; and the names' boxes are one reading of where the map's
 * names ended up, so no two cells keep clear of different placements of the same name. Sampling
 * them here mirrors how the rest of a rebuild is driven -
 * one snapshot, then a per-item call over it - and is what lets the incremental re-shape bake a
 * band identical to the one the full rebuild would have.
 *
 * <p>Inhabitation rather than this layer's holding, because that is the question a band is actually
 * about: a system splits whether or not the mechanic painting the map gives it to anybody, and a
 * layer's holding can leave a settled system unaccounted for - see
 * {@link kmu.maplayers.politicalmap.base.politics.holders} for why the claims layer routinely
 * does. Every held system is inhabited, a bloc holding one only by having a colony in it, so the
 * wider set can only add cells.
 *
 * <p>The inhabited set is also the cost gate, and the reason it is held rather than looked up per
 * call site. Most of the sector is empty space, and asking for a band walks the system's colonies -
 * and on the claims layer settles a whole contest over them; without the gate, every empty cell in
 * the sector would pay for a contest nobody is contesting. Being the wider set, it gates more
 * loosely than the holding did, and what that costs is weighed in this package's README.
 *
 * <p>Which cells get a band at all is settled here, in three refusals read in one place: a cell
 * nothing lives in or with nowhere to start from, a system the sector no longer lists, and a cell
 * whose plan came back empty. The last of them is the one that has to be answered before the
 * geometry rather than inside it, since what it saves is the ring walk - the single-holder cell
 * is most of the sector, and it costs a bake nothing.
 *
 * <p>The ring a band runs along outlives the bake that traced it. A bake runs whenever a name may
 * have moved, while a ring moves only when its cell is re-shaped, so the path is asked of
 * {@link CellRingPathCache} before it is traced and kept there once it is - which leaves a re-bake
 * walking rings only for the cells a change actually re-shaped.
 *
 * <p>The diagnostic overlay's paths are traced through the same pass, from the same sizes and the
 * same gate, for the reason the overlay exists at all: it is worth looking at only while it is the
 * cells' own paths it is drawing. A pass with the bands switched off holds nothing to lay them out
 * from, so it traces nothing either - there is no band design in play for an overlay to report on.
 * That trace is taken fresh rather than read from the cache above, because the overlay walks its
 * own inset ladder: the cells it exists to explain are exactly the ones the band pass refused a
 * path.
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
    private final Set<String> inhabitedSystemIds;
    private final Map<String, StarSystemAPI> systemById;
    private final Map<String, double[]> siteBySystemId;
    private final List<List<double[]>> nameBoxes;
    private final CellRingPathCache ringPathCache;

    private CellRibbonSource(
            SystemRibbonPlanner planner,
            RibbonStyle style,
            Set<String> inhabitedSystemIds,
            Map<String, StarSystemAPI> systemById,
            Map<String, double[]> siteBySystemId,
            List<List<double[]>> nameBoxes,
            CellRingPathCache ringPathCache) {

        this.planner = planner;
        this.style = style;
        this.inhabitedSystemIds = inhabitedSystemIds;
        this.systemById = systemById;
        this.siteBySystemId = siteBySystemId;
        this.nameBoxes = nameBoxes;
        this.ringPathCache = ringPathCache;
    }

    /**
     * Samples everything one pass's bands are settled from, or nothing at all where the player has
     * the bands switched off - in which case every cell is answered "no band" without a count, a
     * size read, or a ring traced.
     *
     * @param sector             the sector the counts are read from
     * @param viewGrouping       the active view and the grouping it resolved, the pair the pass
     *                           already carries: the view supplies the mechanic its cells are
     *                           counted by - the same one they were painted by - and the grouping
     *                           folds factions into blocs exactly as the fill did
     * @param inhabitedSystemIds every system something stands in this pass, the gate deciding
     *                           which cells are asked for a band at all. The pass's own scan
     *                           rather than its holding, so a settled system this layer gives to
     *                           nobody is still counted
     * @param siteBySystemId     each system's own site, the point a band's start is found above.
     *                           Taken as the one map this reads rather than as the geometry cache
     *                           holding it, so what a band is laid out from is stated in the
     *                           signature rather than reachable through it
     * @param nameBoxes          the room the drawn cluster names take up, which every cell's band
     *                           keeps out of; the whole map's, since a name reaches into cells its
     *                           own cluster does not hold
     * @param ringPathCache      the rings already traced inside the cells' current shapes, asked
     *                           before a cell's ring is walked and written back when one is. Taken
     *                           from whatever holds those shapes, so its lifetime is theirs and a
     *                           path can never be served against a shape it was not traced inside
     * @return the source the pass bakes its bands through
     */
    public static CellRibbonSource createForPass(
            SectorAPI sector,
            ViewGrouping viewGrouping,
            Set<String> inhabitedSystemIds,
            Map<String, double[]> siteBySystemId,
            List<List<double[]>> nameBoxes,
            CellRingPathCache ringPathCache) {

        if (!KmuPoliticalMapSettings.shouldDrawPoliticalMapRibbons()) {
            return createBandlessPass();
        }
        var style = RibbonStyleReader.readRibbonStyle();

        // The bake's own reading of the sector, opened here so both mechanics of a composed planner
        // count off one walk of each system, under one sampling of the dev reveal - a band read
        // under a reveal the player moved mid-bake would count out colonies the fills are hiding.
        //
        // Its own pass rather than the rebuild's, because a bake runs whenever a cluster name may
        // have moved and a rebuild's pass is a snapshot of the moment that rebuild began: re-baking
        // through it would count a sector as it stood some flips ago.
        var pass = HolderPass.readFromLunaSettings(sector, viewGrouping.grouping());

        // The colour source and the laying rules are sampled here, once, and handed to whatever
        // planner the view resolves - so both mechanics read a bloc's shades through one object
        // and lay their cells by one rule.
        var inputs = RibbonPlanInputs.createForPass(
            pass,
            new RibbonPlanRules(
                style.lengths(),
                UncontestedRibbonRuns.readFromLunaSettings()));

        return new CellRibbonSource(
            viewGrouping.view().resolveRibbonPlanner(inputs),
            style,
            inhabitedSystemIds,
            SectorStarSystems.indexById(sector),
            siteBySystemId,
            nameBoxes,
            ringPathCache);
    }

    /**
     * Bakes the band of one cell.
     *
     * @param cellId        the cell being baked, the key its traced ring is kept under
     * @param drawnSystemId the system the cell draws as, or null for a cell with no star of its
     *                      own - which nothing paints, so it carries no band
     * @param fillPolygon   the cell's painted outline, the ring the band runs inside
     * @param timings       the pass's running totals, which this cell's count and the geometry
     *                      that follows it are charged to
     * @return the cell's baked band, or {@link CellRibbon#NONE} where it draws none
     */
    public CellRibbon buildCellRibbon(
            String cellId,
            String drawnSystemId,
            List<double[]> fillPolygon,
            RibbonBakeTimings timings) {

        var site = resolveBandLayoutSite(drawnSystemId);
        var system = site == null ? null : systemById.get(drawnSystemId);

        // A system the sector no longer lists leaves the band with nothing to count, which is the
        // same answer as an unpainted cell: no band, rather than one counted off a stand-in.
        if (system == null) {
            return CellRibbon.NONE;
        }

        // Charged apart from the geometry that follows it because it is the one phase of a bake
        // that grows with what the systems hold rather than with the cells: the first ask about a
        // system walks its colonies, so a sector's colonies move this and the cells' own ring work
        // by different factors.
        var planStart = System.nanoTime();
        var plan = planner.planSystemRibbon(system);
        timings.addPlanNanos(System.nanoTime() - planStart);

        // The single-holder cell, and most of the sector: nothing was planned, so nothing is laid
        // out. Answered here rather than by the geometry because what it saves is the ring walk -
        // a cell with nothing to say must not pay for a path nothing would be laid on.
        if (plan.sumLengthUnits() <= 0) {
            return CellRibbon.NONE;
        }
        return CellRibbonBuilder.buildCellRibbon(
            findOrTraceRingPath(cellId, fillPolygon, site, timings),
            plan,
            style,
            nameBoxes,
            timings);
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

    // The ring this cell's band runs along: the one already traced inside the shape the cell holds
    // now, or a fresh trace where none stands.
    //
    // Asked of the cache first because a bake is repeated far more often than a cell is re-shaped.
    // A bake runs whenever a cluster name may have moved - which is every colony flip, since a
    // re-fit can place a name on a cell the flip never touched - while the ring itself is settled
    // by which of a cell's edges are same-owner seams, so it moves only when the cell is cut again.
    // Between the two, every cell in the sector would otherwise re-trace to arrive at the path it
    // discarded a moment earlier.
    //
    // Charged to the pass only on the trace, so the phase's number is what walking rings cost this
    // bake rather than what walking them would have cost - which is the difference the cache is
    // there to make and the one worth being able to read.
    private RingPath findOrTraceRingPath(
            String cellId,
            List<double[]> fillPolygon,
            double[] site,
            RibbonBakeTimings timings) {

        var standingPath = ringPathCache.findRingPathOf(cellId);

        if (standingPath != null) {
            return standingPath;
        }
        var traceStart = System.nanoTime();
        var tracedPath = RibbonPathTracer.traceLaidRibbonPath(fillPolygon, site, style);
        timings.addTraceNanos(System.nanoTime() - traceStart);

        ringPathCache.putRingPath(cellId, tracedPath);

        return tracedPath;
    }

    // The point a cell's band is laid out from, or null on a cell no band is laid on at all.
    //
    // Two refusals in one answer, since both are the same fact about the cell rather than about
    // the band. Nobody lives in it, so there are no holdings for a band to report how a system
    // splits between - a band over empty space would be a readout of nothing; or it has no recorded
    // site, leaving the band nowhere to start from rather than starting it somewhere arbitrary.
    //
    // Shared by the two calls above so the overlay covers exactly the cells the band pass
    // considered: a diagnostic answering for a wider set than the pass it reports on would show
    // paths where no band was ever going to be laid.
    private double[] resolveBandLayoutSite(String drawnSystemId) {

        if (drawnSystemId == null || !inhabitedSystemIds.contains(drawnSystemId)) {
            return null;
        }
        return siteBySystemId.get(drawnSystemId);
    }

    // A pass with the bands switched off, which the empty inhabited set states outright: that set
    // is the gate every cell is answered by, so an empty one answers "no band" for the whole
    // sector. Nothing is sampled for it - no planner resolved, no system index built, no sizes
    // read, and the cells' own traced rings left where they are rather than reached for - so the
    // switch takes the counting off the rebuild as well as the bands off the map, which is the half
    // of it a player cannot see and the half that costs.
    private static CellRibbonSource createBandlessPass() {
        return new CellRibbonSource(
            system -> RibbonPlan.NONE,
            BANDLESS_STYLE,
            Set.of(),
            Map.of(),
            Map.of(),
            List.of(),
            new CellRingPathCache());
    }
}
