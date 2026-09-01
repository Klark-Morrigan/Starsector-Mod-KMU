package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.CornerRounding;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.PolygonSmoothing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The outer edge of settled space, smoothed: one line through the middle of every cell's
 * frontage on the open void, instead of the scallop of arcs the cells actually make.
 *
 * <p>The silhouette a run of connected cells traces is a chain of circular arcs, and at the
 * scale a sector is read at that reads as a row of bites taken out of the edge rather than as
 * a coast. What the eye wants is the shape those cells collectively occupy, which is the line
 * joining the middle of each cell's frontage - so that is what is drawn.
 *
 * <p><b>One reach, for the walk and the line alike.</b> Which cells face the open void, and
 * which void the cells have closed around, is a fact about the map: void is what is further
 * than a cell radius from every site, so that radius is where the walk is decided. Walked a
 * channel short of it, rings of cells that close in fact come apart and the coast wanders down
 * into a pocket every other shape on the map draws as enclosed.
 *
 * <p>And DRAWN at that same radius, because a coast is a border. Everything that stops short
 * of one has to inset from the line the map actually draws, so a second reach here is a
 * licence for a fill to sit outside the edge that defines it - which is exactly what happened
 * while there were two: pockets measured from the true border, a line drawn a channel inside
 * it, and the gap between them showing as fill spilling past the coast.
 *
 * <p><b>Nothing is searched for.</b> The silhouettes come out of {@link DiscUnionBoundary} as
 * stretches of coast in walk order, so "the next cell along the coast" is the next element of
 * the run. There is no nearest-neighbour matching and no ordering to get wrong, and a cell
 * facing the void on two separate stretches - a strait, or the inside of a C - contributes one
 * stretch per frontage without needing a rule for it.
 *
 * <p><b>A coast may never pass through a cell.</b> Joining the middles of two frontages with a
 * straight line does exactly that wherever the coast turns inward, which is at every waist
 * between two cells - so where the line would cut a cell, the point gives way instead of the
 * line, sliding along that cell's own border. How far it slides, and what happens when both
 * ends give way at once, is {@link StraightRuns}' whole subject; what stays here is why a cell
 * contributes an arrival point and a departure point rather than one. The line coming in and
 * the line going out can want it slid in opposite directions, which is precisely what happens
 * at the inward turns this exists to fix; joined along the cell's own border, the two can
 * disagree without the coast leaving the boundary. Where nothing blocks, both land on the
 * middle and it is one point again.
 *
 * <p><b>Why cells get dropped.</b> A run of cells packed tightly along a coast contributes a
 * stretch every few hundred units, and joining all of them reproduces the scallop at a
 * slightly smaller amplitude rather than smoothing it. What turns the chain into a line is
 * dropping the cells that barely face the void at all: one that peeks out by a few degrees
 * contributes a notch the width of a rounding error, and the coast is drawn all the way in
 * and back out for it.
 *
 * <p><b>One rule, asked of the stretch alone.</b> How much of its own border a cell offers
 * the void is a fact about that cell, so the answer does not depend on where the walk began
 * or on which of its neighbours happened to be kept. Judging instead by distance from the
 * last point kept - which this did until the share replaced it - made the same cell kept or
 * dropped according to walk order, and needed a second rule capping how many could go in a
 * row to stop a dense run vanishing wholesale. Neither the order-dependence nor the cap
 * survives a measure that is intrinsic to the cell.
 *
 * <p>Per STRETCH rather than per cell, since a cell facing the void on two frontages can
 * offer a crack on one side and half its border on the other, and only the crack is worth
 * losing.
 *
 * <p>Two things bound it. <b>A cell a bridge attaches to is never dropped</b>, because a
 * bridge's wall is boundary the coast has to stay outside of, and cutting the corner across
 * one puts the coast on the wrong side of a shape already drawn. And a drop is provisional:
 * a cell nothing else knows about can sit in the gap a jump opens up, so every jump is tested
 * against every cell, and whichever one blocks it is put back.
 */
public final class Coastlines {

    // A skip put back changes the jump either side of it, which can expose a different cell.
    // Bounded rather than run to a fixed point, because each pass keeps strictly more cells
    // and the worst case - every cell kept - is the unsmoothed coast rather than a wrong one.
    private static final int REPAIR_PASSES = 4;

    // No stretch stands between two kept ones, or none of those that do is in anything's way.
    private static final int NOTHING_BLOCKING = -1;

    // How far apart two cells may sit and still be walled together, centre to centre, in cell
    // radii. Four is the width at which a whole further cell would fit in the gap, which is
    // the point past which the void between two cells stops being theirs.
    private static final double DEFAULT_BRIDGE_REACH_MULTIPLE = 4;

    // Five percent of a cell's border - about eighteen degrees of arc.
    //
    // What a cell has to show of itself to be worth drawing the coast in and back out for.
    // Below this it contributes a notch a few pixels wide at the zoom a sector is read at,
    // bought at the price of two straight runs and a fillet.
    //
    // Not zero, because zero is the rule switched off: every stretch kept, and the scalloped
    // silhouette reproduced exactly rather than smoothed. Since this is now the only thing
    // deciding what a coast passes through, an opening value of zero would be no smoothing at
    // all.
    private static final double DEFAULT_MIN_FRONTAGE_SHARE = 0.05;

    // Below this a lake is a puddle: half a percent of one cell is a few pixels of water at
    // the zoom a sector is read at, bought at the price of a shore, a margin and a fill.
    //
    // Judged on the TRUE hole rather than on anything the smoothing made of it, because
    // whether a lake is worth drawing is a fact about the map - and a threshold read off the
    // drawn shore would move whenever a smoothing knob did.
    private static final double DEFAULT_MIN_LAKE_SHARE = 0.005;

    // The rounding's own numbers, named so the slider ranges beside them read against
    // something rather than against three literals.
    //
    // The radius steps back along each arm of a corner and is small against a 4000-unit
    // cell, so only the very tip of a corner moves - at these settings the drawn line goes
    // no more than about 28 units from where it was traced.
    //
    // The threshold is what makes the pass selective, and is set just under where a coast's
    // own sampled arcs begin: their joints sit at about 173 degrees, so a threshold this
    // side of that rounds every join BETWEEN runs of coast while leaving the samples along
    // one run alone. Past them the pass has nothing left to find and triples the vertex
    // count saying so.
    //
    // Nothing is ever chamfered, because what is wanted of a needle is a rounded tip rather
    // than a flat one.
    private static final double DEFAULT_ROUNDING_RADIUS = 200;
    private static final int DEFAULT_ROUNDING_SEGMENTS = 3;
    private static final double DEFAULT_ROUND_BELOW_DEGREES = 170;
    private static final double NEVER_CHAMFER = 0;

    // How the drawn coastline is rounded where it turns sharply, out of the numbers above.
    //
    // What it is FOR: a kept cell whose two cleared landings cross contributes a single
    // point instead of a fillet, and the two straight runs either side then meet in a
    // needle. Rounding the join takes the needle's tip off without moving either run.
    public static final CornerRounding DEFAULT_ROUNDING = new CornerRounding(
        DEFAULT_ROUNDING_RADIUS,
        DEFAULT_ROUNDING_SEGMENTS,
        NEVER_CHAMFER,
        Math.toRadians(DEFAULT_ROUND_BELOW_DEGREES));

    private Coastlines() {
    }

    /**
     * The knobs a coast is traced under, in the units they are set in.
     *
     * <p>Multiples of a cell radius rather than distances, because that is what they mean:
     * "closer than a cell across" is a claim about the map, where a number of units stops
     * being one the moment the reach slider moves. Converting to distances is this class's
     * job and happens once, so no caller can do it differently.
     *
     * @param bridgeReachMultiple how far apart two cells may sit and still be walled together,
     *                            centre to centre, in cell radii
     * @param minFrontageShare    how much of its own border a cell has to face the void with
     *                            to be worth passing through, as a share of the whole turn.
     *                            Zero asks nothing and drops nobody
     * @param minLakeShare        how much water a hole has to hold to be drawn as a lake, as
     *                            a share of one cell's area. The second floor, beside the
     *                            frontage's: that one judges a cell's stretch of shore, this
     *                            one a whole lake - and it is judged on the true hole, so no
     *                            smoothing knob can move it. Zero keeps every puddle
     * @param rounding            how the drawn line is rounded where it turns sharply. The
     *                            third smoothing knob, beside the floors above: the floors
     *                            decide which stretches and lakes the line passes through,
     *                            the arc sampling how finely each is drawn, and this what
     *                            becomes of the joins between them
     */
    public record CoastRules(
        double bridgeReachMultiple,
        double minFrontageShare,
        double minLakeShare,
        CornerRounding rounding) {
    }

    /**
     * What the viewer opens on, and so what every drawing of a coast describes.
     *
     * <p>Declared once here rather than beside each drawing. Three copies of these numbers is
     * how a report comes to describe a different map from the one on screen without either of
     * them saying so.
     */
    public static final CoastRules DEFAULT_RULES = new CoastRules(
        DEFAULT_BRIDGE_REACH_MULTIPLE,
        DEFAULT_MIN_FRONTAGE_SHARE,
        DEFAULT_MIN_LAKE_SHARE,
        DEFAULT_ROUNDING);

    /**
     * A traced coast and the two things it was traced against.
     *
     * <p>Handed back together because everything asked of a coast afterwards needs one of
     * them: which cells a reach crosses needs the discs, how much the smoothing took out
     * needs the stretches it started from, and laying more walls beside the coast's own needs
     * those walls. Rebuilt separately by each asker, they can be built from knobs that have
     * since moved.
     *
     * @param coasts      one coast per run of connected cells
     * @param silhouettes the stretches of coast the cells make, in walk order, before any
     *                    smoothing. Carried rather than walked again by whatever wants them:
     *                    the walk is not cheap, and a second one is a second answer that can
     *                    disagree with the coast it is supposed to describe
     * @param dropped     the stretches the smoothing chose not to pass through, over both
     *                    kinds of coast, which is the one thing about a finished line that
     *                    cannot be read back off it: a stretch the walk never offered and a
     *                    stretch a rule threw away are both simply absent from it
     * @param union       the discs it was walked and drawn against, which are the same discs:
     *                     a coast is a border, and everything measured from a border has to
     *                     be measured from the one the map draws
     * @param walls        the bridges it was walled by, and the channel they were laid at.
     *                    Carried for whatever lays more walls alongside them: found again
     *                    from the knobs, they are a second answer that can differ from the
     *                    one the coast was actually walked against
     * @param islands     the cells the walk found alone in the void, which are on no silhouette
     *                    because they have no coast: a cell touching nothing draws its own
     *                    border and encloses nothing further. Handed on rather than forgotten,
     *                    since a shape with no coast is still a shape a span can reach - and
     *                    read off the walk that dropped them rather than worked out again, so
     *                    nothing can come to disagree with it about what is alone
     * @param lakes       the void the cells closed around unaided - inland water, ringed by
     *                    land the whole way round. Out of the same walk and the same
     *                    smoothing as the coasts above, because a lake shore and an outer
     *                    shore are the same kind of line looked at from opposite sides
     * @param puddles     the holes the floor judged too small to be lakes. Kept rather than
     *                    discarded, because being too small for a shore does not stop them
     *                    being water: what a puddle earns instead is being filled up, the
     *                    way the settled construction fills the void its bridges capture
     */
    public record TracedCoasts(
        List<Coast> coasts,
        List<List<DiscUnionBoundary.CoastMark>> silhouettes,
        List<Integer> islands,
        List<DiscUnionBoundary.CoastMark> dropped,
        DiscUnion union,
        DiscUnionBoundary.Walls walls,
        List<Lake> lakes,
        List<Puddle> puddles) {
    }

    /**
     * One smoothed coast, in the two forms everything downstream asks it for.
     *
     * <p>The pair travels together because they are one line described twice and only mean
     * anything against each other. Held as parallel lists they line up by index alone, which
     * is a correspondence nothing checks and the smoothing can break: a coast dropped for
     * coming out degenerate leaves the two lists a place apart from there on.
     *
     * @param vertices  the line as points that each name the cell they sit on, which is what
     *                  anything reasoning about WHERE a coast runs needs
     * @param drawnRing the same line as the map draws it: rounded where it turns sharply, and
     *                  so no longer attributable to the cells the vertices name. Rounded once
     *                  at the trace rather than at each reader, because every one of them -
     *                  the paint, the SVG, the inside-the-coast test - has to be looking at
     *                  the one line, and because a pass repeated per frame is paid for per
     *                  frame
     */
    public record Coast(
        List<CoastVertex> vertices,
        List<double[]> drawnRing) {
    }

    /**
     * One inland lake: a hole the cells closed around unaided, drawn the way a coast is.
     *
     * <p>Built ON a coast rather than beside one, because that is what a lake shore is - the
     * same smoothing over the same kind of marks, read from the water's side. What a lake
     * adds is the edge that water actually runs to.
     *
     * @param shore     the lake's coast, smoothed and drawn like any other
     * @param waterEdge the water's true edge: the cells' own arcs around the hole, sampled.
     *                  What the fill runs against - the lake's margin is the water between
     *                  the drawn shore and this edge, and the open water inside the shore is
     *                  left to the backdrop the way the open void outside the sector is
     * @param ringCells the cells whose borders close the water in, as a puddle carries its
     *                  own. What anything laid ACROSS the lake is laid between - and the one
     *                  honest source for it, since a cell can ring a lake without ever
     *                  touching the outer coast, and such a cell appears nowhere else at all
     */
    public record Lake(
        Coast shore,
        List<double[]> waterEdge,
        Set<Integer> ringCells) {
    }

    /**
     * One puddle: a hole the floor judged too small to be a lake.
     *
     * <p>No shore. What makes a puddle a puddle is exactly that a smoothed shoreline is more
     * drawing than its water deserves, so it carries only what filling it up needs: the water
     * itself, and the cells that ring it - which is what a bridge across it is laid between.
     *
     * @param waterEdge the water, as the cells' own arcs around the hole, sampled
     * @param ringCells the cells whose borders make that edge. A set, because the only thing
     *                  ever asked of it is whether a given cell is in it - a bridge belongs to
     *                  a puddle when both its cells ring that puddle - and the walk order it
     *                  would otherwise carry is order nothing reads
     */
    public record Puddle(
        List<double[]> waterEdge,
        Set<Integer> ringCells) {
    }

    /**
     * The stretches the smoothing left out, as lines along the borders they sit on.
     *
     * <p>Drawn where the coast WOULD have run had it passed through them, which is what makes
     * them legible beside the line that replaced them: the gap between the two is exactly what
     * the rule bought. Sampled rather than chorded, so a wide frontage reads as the arc of
     * border it is rather than as a straight line cutting through its own cell.
     *
     * @param traced      the coast
     * @param arcSegments how finely a half-turn of arc is sampled
     * @return one open run of points per dropped stretch
     */
    public static List<List<double[]>> collectDroppedRuns(TracedCoasts traced, int arcSegments) {

        var runs = new ArrayList<List<double[]>>(traced.dropped().size());

        for (var mark : traced.dropped()) {
            runs.add(sampleMarkArc(traced.union(), mark, arcSegments));
        }
        return List.copyOf(runs);
    }

    /**
     * Which continent each cell belongs to.
     *
     * <p>Read off the silhouettes rather than worked out again: the coast walk already
     * separated the cells into runs of touching neighbours - one run per continent - and that
     * separation is precisely what a continent IS here. Grouping them a second way would be a
     * second answer, and everything drawn from the two would stop describing one map.
     *
     * <p>Keyed off the SILHOUETTES rather than off the finished coasts, which is what makes a
     * continent's number mean the same thing to every asker. A silhouette too small to smooth
     * into a line leaves no coast behind it, so the two lists part company from there on, and a
     * number read off the coasts would name a different continent either side of the drop.
     *
     * @param traced the coast
     * @return the continent each cell sits on, by cell. A cell the outer walk never touched is
     *         absent rather than present under some sentinel - which includes every cell that
     *         faces only a lake, and is why nothing asks this about an interior shore
     */
    public static Map<Integer, Integer> mapCellsToContinents(TracedCoasts traced) {

        var continentOf = new LinkedHashMap<Integer, Integer>();

        for (var continent = 0; continent < traced.silhouettes().size(); continent++) {
            for (var mark : traced.silhouettes().get(continent)) {

                continentOf.put(mark.circle(), continent);
            }
        }
        return continentOf;
    }

    /**
     * Which shape of the sector each cell belongs to: its continent, or its own island.
     *
     * <p>The continents come off the silhouettes, as everything else reads them. The islands
     * are the cells the walk found alone, which are on no silhouette at all - a lone cell has
     * no coast, since the line round it would be its own border drawn twice.
     *
     * <p><b>Numbered after the continents rather than among them</b>, so that a number means the
     * same thing here as it does to every other reader of the trace. What this adds is names for
     * shapes those readers have none for, and it adds them where they cannot be mistaken for a
     * continent's.
     *
     * <p>Its own reading rather than {@link #mapCellsToContinents}, and deliberately
     * not an extension of it: that map is indexed against the silhouettes by everything that
     * gathers per continent, so a cell numbered past the end of them would run off those lists.
     * What is wanted here is a coarser question - is this the same SHAPE - which the sector's
     * islands are part of the answer to and the continents' own bookkeeping is not.
     *
     * @param traced the coast
     * @return the shape each cell sits on, by cell; a cell on neither is absent
     */
    public static Map<Integer, Integer> mapCellsToShapes(TracedCoasts traced) {

        var shapeOf = new LinkedHashMap<>(mapCellsToContinents(traced));
        var island = traced.silhouettes().size();

        for (var cell : traced.islands()) {

            shapeOf.put(cell, island);
            island++;
        }
        return shapeOf;
    }

    // One mark's stretch of border as points along its arc, at the density asked for. The one
    // flattening for every reader of a raw arc, so a diagnostic and a water's edge sampled on
    // the same stretch land on the same points.
    private static List<double[]> sampleMarkArc(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            int arcSegments) {

        var sweep = mark.toAngle() - mark.fromAngle();
        var steps = Math.max(
            1,
            (int) Math.ceil(arcSegments * sweep / Angles.HALF_TURN));

        var run = new ArrayList<double[]>(steps + 1);

        for (var step = 0; step <= steps; step++) {

            run.add(DiscUnionBoundary.findPointOnMark(
                union,
                mark,
                mark.fromAngle() + sweep * step / steps));
        }
        return List.copyOf(run);
    }

    /**
     * Traces a whole sector's coast: the settled construction, with the bridges laid.
     *
     * <p>All this entry decides is the wall set - find the bridges and lay them as chords at
     * the border channel. Everything else about building a coast lives once, in the shared
     * tail, where the continent entry below cannot come to differ from it.
     *
     * @param sites      the sites
     * @param parameters the knobs the cells are built under
     * @param rules      the knobs the coast is traced under
     * @return the coast, and what it was traced against
     */
    public static TracedCoasts traceSectorCoasts(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            CoastRules rules) {

        return traceCoastsAcrossWalls(
            sites,
            parameters,
            rules,
            new DiscUnionBoundary.Walls(
                DiscUnionBoundary.buildChordsFrom(VoidBridges.findVoidBridges(
                    sites,
                    parameters.cellRadius(),
                    parameters.cellRadius() * rules.bridgeReachMultiple())),
                parameters.borderInset()));
    }

    /**
     * Traces each touching-connected run of cells - each continent - as its own closed coast,
     * with no bridges laid.
     *
     * <p>A preview of the per-continent proposal: the same walk and the same smoothing as
     * {@link #traceSectorCoasts}, with the walls left out. Runs that a bridge joins into one
     * super-continent come back as separate closed lines instead - so laid over the settled
     * map, this shows exactly where those lines and the bridges would cross, which is the
     * fact the proposal turns on.
     *
     * <p>Without walls there are also no bridged cells for the smoothing to protect, so a
     * continent's coast is free to cut a corner across where a bridge lands. That is not a
     * defect of the preview - it is the very collision this exists to make visible.
     *
     * <p>A cell alone in the void still contributes nothing: the lone-island rule reads the
     * run rather than the walls, so a single-cell continent degenerates exactly as it does
     * on the settled coast.
     *
     * @param sites      the sites
     * @param parameters the knobs the cells are built under
     * @param rules      the knobs the coast is traced under; the bridge reach goes unread,
     *                   since there are no bridges to find
     * @return the coasts, one closed run of points per continent
     */
    public static TracedCoasts traceContinentCoasts(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            CoastRules rules) {

        return traceCoastsAcrossWalls(
            sites,
            parameters,
            rules,
            DiscUnionBoundary.Walls.NONE);
    }

    // The shared tail of both entries: everything about tracing a coast that does not depend
    // on where the walls came from. What differs between a sector coast and a continent coast
    // is only the wall set - bridges found from the sites, or none at all - so the walk, the
    // smoothing and the reach live once, here, and cannot drift between the two.
    private static TracedCoasts traceCoastsAcrossWalls(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            CoastRules rules,
            DiscUnionBoundary.Walls walls) {

        // ONE reach, for the walk and for the line alike.
        //
        // A coast is a BORDER: it is where settled space ends, and it is settled at the reach
        // that DEFINES void - a point is void when its nearest site is further than the cell
        // radius. Walked a channel short of that, rings of cells that close in fact come
        // apart and the silhouette runs down into a pocket every other shape draws as
        // enclosed.
        //
        // And drawn at that same reach, not a channel inside it. Anything that stops short of
        // a coast has to inset from the line the map actually draws, so a second reach here
        // is a licence for a fill to sit outside the edge that defines it - which is what
        // happened: pockets measured from the true border, a line drawn a channel inside it,
        // and the gap between them showing as fill spilling past the coast. There is no
        // reading of a border under which those are two numbers.
        var union = new DiscUnion(sites, parameters.cellRadius());

        var runs = DiscUnionBoundary.traceCoastRuns(union, walls, parameters.boundSegments());
        var silhouettes = keepJoinedRuns(runs.silhouettes());
        var islands = collectLoneIslands(runs.silhouettes());
        var bridged = findBridgedCircles(union, walls);
        var smoothingRules = new SmoothingRules(
            rules.minFrontageShare(),
            parameters.measureArcSegments());

        var smoothed = smoothSilhouettes(silhouettes, union, bridged, smoothingRules);
        var water = buildLakes(runs.lakes(), union, bridged, smoothingRules, rules);

        return new TracedCoasts(
            buildCoasts(smoothed.coasts(), rules.rounding()),
            silhouettes,
            islands,
            concatenateDropped(smoothed.dropped(), water.dropped()),
            union,
            walls,
            water.lakes(),
            water.puddles());
    }

    // The stretches both kinds of coast left out, as the one list the diagnostic draws. A
    // stretch a rule threw away is the same kind of fact on a lake shore as on the outer
    // shore, and the drawing tells them apart by the line each sits beside.
    private static List<DiscUnionBoundary.CoastMark> concatenateDropped(
            List<DiscUnionBoundary.CoastMark> fromCoasts,
            List<DiscUnionBoundary.CoastMark> fromLakes) {

        var dropped = new ArrayList<DiscUnionBoundary.CoastMark>(
            fromCoasts.size() + fromLakes.size());

        dropped.addAll(fromCoasts);
        dropped.addAll(fromLakes);

        return List.copyOf(dropped);
    }

    // The lakes, each smoothed through the SAME pipeline as the outer coasts: a lake shore is
    // the outer shore looked at from the water's side, and the pipeline reads nothing but the
    // marks and their order. No lone-island filter first - a hole cannot be ringed by one
    // cell's border alone, so the degenerate run that filter exists for cannot arrive.
    //
    // One lake at a time rather than as one batch, because a lake pairs its drawn shore with
    // its own water's edge - and the batch drops a degenerate outline on the way through, so
    // its output stops lining up with its input exactly when a sector has such a lake. What a
    // drop leaves behind is still recorded: the stretches go on the shared dropped list, where
    // the diagnostic draws them beside whichever line replaced them.
    private static TracedLakes buildLakes(
            List<List<DiscUnionBoundary.CoastMark>> lakeRuns,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules smoothingRules,
            CoastRules rules) {

        var lakes = new ArrayList<Lake>(lakeRuns.size());
        var puddles = new ArrayList<Puddle>();
        var dropped = new ArrayList<DiscUnionBoundary.CoastMark>();
        var leastWater = rules.minLakeShare() * Math.PI * union.reach() * union.reach();

        for (var run : lakeRuns) {

            var waterEdge = sampleWaterEdge(union, run, smoothingRules.arcSegments());

            // The puddle floor, taken on the water's edge BEFORE any smoothing is paid for:
            // whether a hole is a lake is decided by how much water it holds, and a puddle
            // skips the smoothing - which is the expensive half - because a shore is exactly
            // what it is too small to deserve. Not dropped: a puddle is still a piece of the
            // map, kept for the construction that fills it up instead of shoring it.
            if (Math.abs(PolygonRegions.computeSignedArea(waterEdge)) < leastWater) {

                puddles.add(new Puddle(waterEdge, collectRingCells(run)));
                continue;
            }

            var one = smoothOneCoast(run, union, bridged, smoothingRules);

            dropped.addAll(one.dropped());

            if (!one.outline().isEmpty()) {
                lakes.add(new Lake(
                    buildCoast(one.outline(), rules.rounding()),
                    waterEdge,
                    collectRingCells(run)));
            }
        }
        return new TracedLakes(
            List.copyOf(lakes), List.copyOf(puddles), List.copyOf(dropped));
    }

    // The cells a run of marks passes over. A set rather than a run, because the walk offers
    // a mark per STRETCH and a cell facing the water twice contributes two of them - so the
    // duplicates have to go, and once they have there is no order left worth keeping.
    private static Set<Integer> collectRingCells(List<DiscUnionBoundary.CoastMark> run) {

        var cells = new LinkedHashSet<Integer>();

        for (var mark : run) {
            cells.add(mark.circle());
        }
        return Set.copyOf(cells);
    }

    /**
     * The water a trace found beyond its outer coasts - the lakes, the puddles, and what the
     * lake shores left out on the way.
     *
     * <p>The drops travel with them for the reason {@link SmoothedCoasts}' do: a lake that
     * came out wrong looks the same on screen whether a rule dropped too much or the walk
     * never offered the stretch, and those are opposite faults with opposite fixes.
     *
     * @param lakes   the lakes, in the order they were traced
     * @param puddles the holes the floor judged too small to be lakes, in the same order
     * @param dropped every stretch the lake shores were not drawn through, over all of them
     */
    private record TracedLakes(
        List<Lake> lakes,
        List<Puddle> puddles,
        List<DiscUnionBoundary.CoastMark> dropped) {
    }

    // The water's true edge: every mark of the lake's ring sampled along its own arc, joined
    // in walk order into one closed outline.
    private static List<double[]> sampleWaterEdge(
            DiscUnion union,
            List<DiscUnionBoundary.CoastMark> run,
            int arcSegments) {

        var edge = new ArrayList<double[]>();

        for (var mark : run) {
            edge.addAll(sampleMarkArc(union, mark, arcSegments));
        }
        return List.copyOf(edge);
    }

    private static List<Coast> buildCoasts(
            List<List<CoastVertex>> outlines,
            CornerRounding rounding) {

        var coasts = new ArrayList<Coast>(outlines.size());

        for (var outline : outlines) {
            coasts.add(buildCoast(outline, rounding));
        }
        return List.copyOf(coasts);
    }

    // A smoothed outline paired with the line the map draws for it: the same points, rounded
    // where they turn sharply.
    //
    // The rounding is apart from the smoothing rather than folded into it, because the two
    // work on different things. The smoothing decides which stretches the coast runs along
    // and hands back vertices that each name the cell they sit on; this rounds the joins
    // BETWEEN those stretches, and the points it adds sit on no cell at all - so a rounded
    // ring can no longer answer what the vertices answer, and is kept beside them rather than
    // replacing them.
    //
    // The one place a Coast is made, so no caller can pair an outline with a ring rounded to
    // different numbers, or with a ring off some other outline entirely.
    private static Coast buildCoast(List<CoastVertex> outline, CornerRounding rounding) {

        return new Coast(
            outline,
            PolygonSmoothing.roundCorners(collectPoints(outline), rounding));
    }

    /**
     * How aggressively a coast is smoothed, in the units the smoothing works in.
     *
     * <p>One rule for what is dropped and one for how finely what survives is drawn. They do
     * not interact - the first decides which stretches the coast passes through, the second
     * how smoothly it rounds each of them.
     *
     * @param minFrontageShare how much of its own border a cell has to face the void with to
     *                         be worth passing through, as a share of the whole turn
     * @param arcSegments      how finely a half-turn of arc is sampled, which is how smooth
     *                         the fillets come out and so the second thing deciding what a
     *                         smoothed coast looks like
     */
    private record SmoothingRules(
        double minFrontageShare,
        int arcSegments) {
    }

    /**
     * One point of a smoothed coast, and the cell whose border it sits on.
     *
     * <p>The cell travels with the point because the two things drawn here are not the same
     * kind of run: a step between two points on ONE cell is a fillet along that cell's own
     * border, where a step between two cells is a straight reach across open void. Only the
     * second has to clear every cell, and a check that could not tell them apart would read
     * every fillet as a coast cutting into the cell it is drawn on.
     *
     * @param point  where the coast passes
     * @param circle whose cell's border it is on
     */
    public record CoastVertex(
        double[] point,
        int circle) {
    }

    /**
     * Traces the smoothed outer edge of every run of connected cells.
     *
     * <p>Judged on the points that came out rather than on the cells that went in. A cell
     * contributes a whole run of border rather than a single point, so two cells that touch
     * enclose the pair perfectly well. Only a run that came out too small to be a shape at all
     * is dropped, which after the winding is settled means one that could not be built. Runs
     * of one cell never arrive here at all - a lone island is not a coast.
     *
     * @param silhouettes the stretches of coast the cells make, in walk order
     * @param union       the discs to draw against
     * @param bridged     the cells a laid wall attaches to, which are never skipped
     * @param rules       how aggressively to smooth, and how finely
     * @return one closed run of points per run of connected cells
     */
    private static SmoothedCoasts smoothSilhouettes(
            List<List<DiscUnionBoundary.CoastMark>> silhouettes,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var smoothed = new ArrayList<List<CoastVertex>>();
        var dropped = new ArrayList<DiscUnionBoundary.CoastMark>();

        for (var silhouette : silhouettes) {

            var one = smoothOneCoast(silhouette, union, bridged, rules);

            dropped.addAll(one.dropped());

            if (!one.outline().isEmpty()) {
                smoothed.add(one.outline());
            }
        }
        return new SmoothedCoasts(smoothed, List.copyOf(dropped));
    }

    /**
     * The same smoothing over ONE run of coast marks, whichever kind of coast it came off.
     *
     * <p>Named on its own because two callers want it one run at a time: the silhouettes go
     * through in a batch, and a lake pairs its finished shore with its own water's edge, so
     * it has to keep hold of which run produced which. Called through the batch with a
     * one-element list, a lake would have to read the answer back out of a list that may have
     * dropped it.
     *
     * @param coast   one run of coast marks, in walk order
     * @param union   the discs to draw against
     * @param bridged the cells a laid wall attaches to, which are never skipped
     * @param rules   how aggressively to smooth, and how finely
     * @return the smoothed outline and the stretches left out of it. The outline is empty
     *         where what came out was too small to enclose anything, which is what a run that
     *         could not be built comes to
     */
    private static SmoothedCoast smoothOneCoast(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var kept = keepSmoothedMarks(coast, union, bridged, rules);
        var outline = buildClearedOutline(coast, kept, union, rules);
        var dropped = new ArrayList<DiscUnionBoundary.CoastMark>();

        for (var index = 0; index < coast.size(); index++) {

            if (!kept.contains(index)) {
                dropped.add(coast.get(index));
            }
        }

        return new SmoothedCoast(
            outline.size() >= Limits.MIN_VERTICES_TO_ENCLOSE_AREA ? outline : List.of(),
            List.copyOf(dropped));
    }

    /**
     * What the smoothing made of one run of coast, and what it left out on the way.
     *
     * @param outline the smoothed line, or empty where it came out too small to be a shape
     * @param dropped every stretch of this run the line was not drawn through
     */
    private record SmoothedCoast(
        List<CoastVertex> outline,
        List<DiscUnionBoundary.CoastMark> dropped) {
    }

    /**
     * What the smoothing made of a batch of them, and what it left out over all of them.
     *
     * <p>The stretches dropped are kept rather than discarded because they are the only record
     * of a decision the smoothing otherwise makes silently. A coast that came out wrong looks
     * the same on screen whether a rule dropped too much or the walk never offered the stretch
     * at all, and those are opposite faults with opposite fixes.
     *
     * @param coasts  one closed run of points per run of connected cells, degenerate ones
     *                left out - so this does NOT line up with what went in
     * @param dropped every stretch the coast was not drawn through, over all of them
     */
    private record SmoothedCoasts(
        List<List<CoastVertex>> coasts,
        List<DiscUnionBoundary.CoastMark> dropped) {
    }

    /**
     * One straight reach of a smoothed coast: the two vertices it runs between.
     *
     * @param from the vertex it leaves
     * @param to   the vertex it arrives at
     */
    public record CoastReach(
        CoastVertex from,
        CoastVertex to) {
    }

    /**
     * Every straight reach of a traced coast, in the order the coast is walked.
     *
     * <p>A coast alternates between reaches, which cross open void from one cell to another,
     * and fillets, which run along one cell's own border. Both ends of a fillet sit on the
     * SAME circle - so which vertex pairs are reaches is a fact about the coast rather than a
     * rule each reader should keep its own copy of.
     *
     * <p><b>Naming two circles is not enough on its own.</b> Where two circles cross, the
     * crossing point lies on BOTH of them, and a coast clamped onto it arrives on one cell and
     * leaves on the other without going anywhere: the step has no length, and which circle
     * each of its two vertices is labelled with is arbitrary. That is the coast handing over
     * between two cells, which is what a fillet's two ends do as well, and it crosses no void
     * at all. Reported as a reach it becomes a wall with nothing on either side of it, laid
     * across the one point where two cells meet.
     *
     * <p>Told apart by the channel, because that is what a reach is FOR: a wall holds its two
     * sides half a channel off its line each, so a step shorter than the channel has no room
     * between those sides and cannot separate anything. A reach that genuinely crosses void
     * runs from one cell's frontage to another's and is thousands of units long.
     *
     * <p>Wanted by everything that treats a reach as a thing in its own right: what it walls
     * off behind it, and which cells it cuts through on the way.
     *
     * @param traced the coast
     * @return one entry per reach, in walk order
     */
    static List<CoastReach> collectStraightReaches(TracedCoasts traced) {

        var reaches = new ArrayList<CoastReach>();

        for (var coast : traced.coasts()) {

            var vertices = coast.vertices();

            for (var index = 0; index < vertices.size(); index++) {

                var from = vertices.get(index);
                var to = vertices.get((index + 1) % vertices.size());

                if (from.circle() != to.circle()
                        && Points.computeDistance(from.point(), to.point())
                            >= traced.walls().channel()) {

                    reaches.add(new CoastReach(from, to));
                }
            }
        }
        return reaches;
    }

    /**
     * Every outer coast as the plain ring the map draws.
     *
     * <p>What a shape is judged against, and what the map puts on screen, are the same line:
     * void outside it is void nothing shut in, whatever any single reach's line says. So the
     * readers that only draw or test against the line ask for it here, without carrying the
     * vertices they have no use for.
     *
     * @param traced the coast
     * @return one ring per stretch of coast, in the order they were traced
     */
    public static List<List<double[]>> collectCoastRings(TracedCoasts traced) {
        return traced.coasts().stream().map(Coast::drawnRing).toList();
    }

    /**
     * Every lake shore as the plain ring the map draws, for the same readers as above.
     *
     * @param traced the coast
     * @return one ring per lake, in the order they were traced
     */
    public static List<List<double[]>> collectLakeRings(TracedCoasts traced) {
        return traced.lakes().stream().map(lake -> lake.shore().drawnRing()).toList();
    }

    /**
     * Whether a point lies inside the drawn coast.
     *
     * @param coasts the coast's rings
     * @param point  the {x, y} point to place
     * @return whether any ring holds it
     */
    static boolean isInsideCoast(List<List<double[]>> coasts, double[] point) {

        for (var coast : coasts) {

            if (PolygonRegions.isPointInsideRing(coast, point[0], point[1])) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ring on its own, for anything that only wants to draw or fill it.
     *
     * @param coast a smoothed coast
     * @return its points, in order
     */
    public static List<double[]> collectPoints(List<CoastVertex> coast) {

        var points = new ArrayList<double[]>(coast.size());

        for (var vertex : coast) {
            points.add(vertex.point());
        }
        return points;
    }

    // A cell alone in the void has no coast, so the runs that name one are not silhouettes.
    //
    // A coast is where settled space ends along a run of cells that hold something BETWEEN
    // them. A cell touching nothing, joined to nothing, holds only itself: the line traced
    // round it is the cell's own border a second time, and there is no void it encloses that
    // the cell does not already draw. Kept, it is an edge drawn twice on screen, a stretch of
    // frontage in every coast measure that no reach can ever be laid along, and a ring that
    // answers "inside the coast" for points the cell already claims.
    //
    // Read off the run itself: cells that touch, and cells a laid bridge joins, are walked
    // into ONE run - so a run naming a single circle is exactly the degenerate case, with no
    // separate test for touching or for bridges to fall out of step with the walk.
    private static List<List<DiscUnionBoundary.CoastMark>> keepJoinedRuns(
            List<List<DiscUnionBoundary.CoastMark>> silhouettes) {

        var joined = new ArrayList<List<DiscUnionBoundary.CoastMark>>(silhouettes.size());

        for (var silhouette : silhouettes) {

            if (!isLoneIsland(silhouette)) {
                joined.add(silhouette);
            }
        }
        return joined;
    }

    // The other half of the same reading: which cells those dropped runs were.
    //
    // Kept because having no coast is not the same as not being there. A cell alone in the void
    // is a shape of the sector like any other - it can be reached, and a span laid to it joins
    // it to whatever it reaches - and the only thing it lacks is a line of its own to draw.
    //
    // An empty run names no cell and is not an island; it is a walk that found nothing, and
    // there is no cell to hand on.
    private static List<Integer> collectLoneIslands(
            List<List<DiscUnionBoundary.CoastMark>> silhouettes) {

        var islands = new ArrayList<Integer>();

        for (var silhouette : silhouettes) {

            if (isLoneIsland(silhouette) && !silhouette.isEmpty()) {
                islands.add(silhouette.get(0).circle());
            }
        }
        return List.copyOf(islands);
    }

    // Whether a run of coast is one cell's own border and nothing else.
    private static boolean isLoneIsland(List<DiscUnionBoundary.CoastMark> silhouette) {

        if (silhouette.isEmpty()) {
            return true;
        }

        for (var mark : silhouette) {

            if (mark.circle() != silhouette.get(0).circle()) {
                return false;
            }
        }
        return true;
    }

    // The cells a laid wall attaches to. Asked of the laid chords rather than of every bridge
    // offered, because a bridge that was never drawn has no wall for the coast to cut across
    // and protecting its cells would only cost smoothing for nothing.
    private static Set<Integer> findBridgedCircles(
            DiscUnion union,
            DiscUnionBoundary.Walls walls) {

        var bridged = new LinkedHashSet<Integer>();

        for (var chord : DiscUnionBoundary.findAttachableChords(union, walls)) {

            bridged.add(chord.fromCircle());
            bridged.add(chord.toCircle());
        }
        return bridged;
    }

    // Which stretches the coast is drawn through: the ones too far from their neighbour to be
    // dropped, plus the ones dropping would have put a cell across the jump.
    private static List<Integer> keepSmoothedMarks(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var isKept = markExposedStretches(coast, bridged, rules);

        for (var pass = 0; pass < REPAIR_PASSES; pass++) {

            if (!restoreBlockingStretches(coast, union, isKept)) {
                break;
            }
        }

        return collectKeptPositions(isKept);
    }

    // The kept stretches as positions along the walk, which is what everything downstream
    // indexes by. The flags are how the smoothing decides; the positions are how it is read.
    private static List<Integer> collectKeptPositions(boolean[] isKept) {

        var kept = new ArrayList<Integer>();

        for (var index = 0; index < isKept.length; index++) {

            if (isKept[index]) {
                kept.add(index);
            }
        }
        return kept;
    }

    // One pass along the coast, keeping every stretch that faces the void with enough of its
    // own border to be worth drawing.
    //
    // Order-free, and that is the point of it: each stretch is judged against nothing but
    // itself, so the walk could start anywhere and drop the same set. Nothing accumulates
    // across the loop, which is why there is no state here to get wrong.
    private static boolean[] markExposedStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            Set<Integer> bridged,
            SmoothingRules rules) {

        var isKept = new boolean[coast.size()];

        for (var index = 0; index < coast.size(); index++) {
            isKept[index] = !isBarelyFacingTheVoid(coast.get(index), bridged, rules);
        }
        return isKept;
    }

    // Whether a stretch offers too little of its cell's border to be worth passing through.
    //
    // A cell that peeks out by a few degrees contributes a notch the width of a rounding
    // error, and the coast is drawn all the way in and back out for it. Dropping it is what
    // the caller asked for by setting the rule above zero.
    //
    // A cell a bridge attaches to is exempt: a bridge's wall is boundary the coast has to
    // stay OUTSIDE of, so cutting the corner across one puts the coast on the wrong side of
    // a shape already drawn. That is a matter of correctness rather than of taste, and no
    // smoothing knob may overrule it.
    //
    // Nothing here can strand the coast inside a cell. A stretch dropped from this pass is
    // put straight back by the repair pass if the jump over it turns out to cross anything -
    // so the rule can only ever remove detail that was not load-bearing.
    private static boolean isBarelyFacingTheVoid(
            DiscUnionBoundary.CoastMark mark,
            Set<Integer> bridged,
            SmoothingRules rules) {

        return mark.measureShareOfCircle() < rules.minFrontageShare()
            && !bridged.contains(mark.circle());
    }

    // Puts back any stretch a jump turned out to cross. A jump is tested against every cell,
    // not only the two it runs between, because the cell in the way is by definition one
    // neither end knows about - and if that cell is one of the stretches skipped over, keeping
    // it is what stops the jump being made at all.
    private static boolean restoreBlockingStretches(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isKept) {

        var kept = collectKeptPositions(isKept);

        if (kept.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA) {
            return false;
        }

        var restored = false;

        for (var step = 0; step < kept.size(); step++) {

            var from = kept.get(step);
            var to = kept.get((step + 1) % kept.size());
            var blocker = findBlockedStretch(coast, union, isKept, from, to);

            if (blocker >= 0) {

                isKept[blocker] = true;
                restored = true;
            }
        }
        return restored;
    }

    // Which of the stretches skipped between two kept ones to put back, or NOTHING_BLOCKING
    // when the jump is clear.
    //
    // The jump is judged first, by the one clearance test the drawing itself uses, and only a
    // jump that failed goes looking for something to blame. That ordering is what keeps one
    // definition of "in the way" in play: an inline scan with its own threshold answered the
    // same question a hair differently, and a method holding two definitions of one question
    // is how the next reader picks the wrong one.
    //
    // The blame falls on the skipped stretch actually across the jump where there is one, and
    // otherwise simply on the first skipped. A jump can cut into one of the two cells it runs
    // BETWEEN, which no skipped stretch can be blamed for - it happens where both ends block
    // and the common tangent has to be clamped back onto a frontage that does not reach it,
    // leaving a run tangent to nothing. Putting any skipped stretch back is still the remedy:
    // the one long jump becomes two short ones, and two neighbouring stretches can always fall
    // back on the boundary's own join between them, which cuts nothing by construction.
    private static int findBlockedStretch(
            List<DiscUnionBoundary.CoastMark> coast,
            DiscUnion union,
            boolean[] isKept,
            int from,
            int to) {

        var jump = new StraightRuns.StraightRun(union, coast.get(from), coast.get(to));
        var edge = StraightRuns.resolveEdge(jump);

        if (StraightRuns.isRunClearOfEveryCell(jump, edge)) {
            return NOTHING_BLOCKING;
        }

        var firstSkipped = NOTHING_BLOCKING;

        for (var step = 1; step < coast.size() && !isKept[(from + step) % coast.size()]; step++) {

            var index = (from + step) % coast.size();

            if (firstSkipped < 0) {
                firstSkipped = index;
            }
            if (jump.isRunBetween(coast.get(index).circle())) {
                continue;
            }
            if (jump.measureIncursion(edge, coast.get(index).circle()) > DiscUnion.TOUCHING_TOLERANCE) {
                return index;
            }
        }
        return firstSkipped;
    }

    // The kept stretches turned into a closed run of points: a fillet along each cell's own
    // border from where the coast arrives to where it leaves, and a straight reach from there
    // to the next cell.
    private static List<CoastVertex> buildClearedOutline(
            List<DiscUnionBoundary.CoastMark> coast,
            List<Integer> kept,
            DiscUnion union,
            SmoothingRules rules) {

        // A run of one has no reach to any other cell, so there is nothing to clamp against
        // and its whole frontage is the coast. That is a cell alone in the void, whose coast
        // is its own border - drawn over the top of it and so invisible, which is right.
        if (kept.size() == 1) {

            var only = coast.get(kept.get(0));

            return buildVertices(
                only,
                sampleFillet(union, only, only.fromAngle(),
                only.toAngle(),
                rules));
        }

        var arriveAngles = new double[kept.size()];
        var departAngles = new double[kept.size()];

        for (var index = 0; index < kept.size(); index++) {

            var next = (index + 1) % kept.size();

            var edge = StraightRuns.findClearEdge(
                new StraightRuns.StraightRun(
                    union,
                    coast.get(kept.get(index)),
                    coast.get(kept.get(next))),
                kept.get(next) == (kept.get(index) + 1) % coast.size());

            departAngles[index] = edge.departAngle();
            arriveAngles[next] = edge.arriveAngle();
        }

        var outline = new ArrayList<CoastVertex>();

        for (var index = 0; index < kept.size(); index++) {

            var mark = coast.get(kept.get(index));

            outline.addAll(buildVertices(
                mark,
                sampleFillet(
                    union,
                    mark,
                    arriveAngles[index],
                    departAngles[index],
                    rules)));
        }
        return outline;
    }

    // One stretch of coast as the points the line passes through: the sampled fillet running
    // along the cell's own border, each carrying the cell it belongs to so a later pass can
    // tell which stretch a point came off without matching coordinates back to a circle.
    private static List<CoastVertex> buildVertices(
            DiscUnionBoundary.CoastMark mark,
            List<double[]> points) {

        var vertices = new ArrayList<CoastVertex>(points.size());

        for (var point : points) {
            vertices.add(new CoastVertex(point, mark.circle()));
        }
        return vertices;
    }

    // The run of a cell's own border the coast keeps to, from where it arrives to where it
    // leaves. Sampled rather than cut straight across, so that a cell whose two neighbours
    // pull it far apart is still traced around rather than through.
    //
    // Bounded by the cell's OWN frontage rather than by any fixed sweep. The last cell of a
    // chain faces void nearly the whole way round, so its coast has to wrap most of a turn to
    // come back down the other side - and a rule that stopped at a half turn collapsed
    // exactly those cells to a point, which is what put a spike on every chain end and drove
    // the two runs either side of it straight through the cell.
    //
    // Both ends are already clamped into that frontage, so the arrival cannot sit past the
    // departure unless the cell's two neighbours pulled each beyond the other. There is no
    // run of border between them then, and the point they are least far from is the only
    // answer left.
    private static List<double[]> sampleFillet(
            DiscUnion union,
            DiscUnionBoundary.CoastMark mark,
            double arriveAngle,
            double departAngle,
            SmoothingRules rules) {

        var sweep = departAngle - arriveAngle;

        if (sweep <= 0) {

            return List.of(DiscUnionBoundary.findPointOnMark(
                union,
                mark,
                (arriveAngle + departAngle) / 2));
        }

        var steps = Math.max(
            1,
            (int) Math.ceil(rules.arcSegments() * sweep / Angles.HALF_TURN));

        var points = new ArrayList<double[]>(steps + 1);

        for (var step = 0; step <= steps; step++) {

            points.add(DiscUnionBoundary.findPointOnMark(
                union,
                mark,
                arriveAngle + sweep * step / steps));
        }
        return points;
    }

}
