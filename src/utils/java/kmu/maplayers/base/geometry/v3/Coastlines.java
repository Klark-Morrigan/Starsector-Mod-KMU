package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.CornerRounding;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import kmu.maplayers.base.geometry.CoastMark;
import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.SectorGeometryParameters;
import kmu.maplayers.base.geometry.walls.DiscUnionBoundary;
import kmu.maplayers.base.geometry.walls.Walls;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
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
 * <p><b>Three stages, and only the last is cosmetic.</b> SELECTION chooses which stretches the
 * border visits - the frontage floor and the clearance repair below. PLACEMENT decides where on
 * each of them the line lands and how it crosses between them, which is {@link StraightRuns}'
 * subject and is load-bearing geometry: it is what holds the line outside every cell, so it IS
 * the border rather than a tidying of one. ROUNDING takes the tips off the joins the other two
 * leave, and is the only one of the three that could be switched off and still leave a border
 * to read - which is why it is not here at all but in {@link CoastRounding}, run over a whole
 * finished trace.
 *
 * <p>Named apart because they have different freedoms, and a rule put in the wrong one cannot be
 * satisfied. Selection can still choose: dropping a stretch is available to it, and the repair
 * pass is there to overrule it where the drop would strand the line inside a cell. By the time
 * placement runs, the stretches the line visits are settled, and holding clear of every cell
 * uses up what freedom remains - so a constraint on WHERE the line may land has to be asked of
 * selection, which can still act on it, rather than of placement, which can only refuse.
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
 * last point kept makes the same cell kept or dropped according to walk order, and needs a
 * second rule capping how many can go in a row to stop a dense run vanishing wholesale.
 * Neither the order-dependence nor the cap survives a measure that is intrinsic to the cell.
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
    // Not zero, because zero is the rule switched off: every stretch visited, and the scalloped
    // silhouette reproduced exactly rather than smoothed. Since this is now the only thing
    // deciding what a coast passes through, an opening value of zero would be selection
    // switched off.
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
    // What it is FOR: a visited cell whose two cleared landings cross contributes a single
    // point instead of a fillet, and the two straight runs either side then meet in a
    // needle. Rounding the join takes the needle's tip off without moving either run.
    public static final CornerRounding DEFAULT_ROUNDING = new CornerRounding(
        DEFAULT_ROUNDING_RADIUS,
        DEFAULT_ROUNDING_SEGMENTS,
        NEVER_CHAMFER,
        Math.toRadians(DEFAULT_ROUND_BELOW_DEGREES));

    private Coastlines() {
    }

    // The window read on the turn the stretch begins, which is the answer that draws the better
    // map - not the arithmetically correct one. What each of the two costs is measured on
    // StraightRuns.ReachAnchor, and that is the note to read before moving this.
    //
    // Named rather than written into the record below, so the map's answer is in one place and
    // reads as a choice that was weighed rather than as whichever value was to hand.
    private static final StraightRuns.ReachAnchor DEFAULT_REACH_ANCHOR =
        StraightRuns.ReachAnchor.AT_THE_STRETCH_START;

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
        DEFAULT_ROUNDING,
        DEFAULT_REACH_ANCHOR);

    // One mark's stretch of border as points along its arc, at the density asked for. The one
    // flattening for every reader of a raw arc, so a diagnostic and a water's edge sampled on
    // the same stretch land on the same points.
    static List<double[]> sampleMarkArc(
            DiscUnion union,
            CoastMark mark,
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
     * Traces each touching-connected run of cells - each continent - as its own closed coast,
     * with no bridges laid.
     *
     * <p>The entry the map is drawn from. What makes a continent a continent is touching: a run
     * of cells that reach each other is one shape, and a gap between two runs is water however
     * narrow it is. So the spans that cross those gaps are laid AFTERWARDS, against coasts that
     * already exist, rather than being boundary the walk traces over.
     *
     * <p>Which is why no walls are laid here, and the whole of what this entry decides. Walls
     * laid first join what they touch into one shape and the water between disappears into the
     * interior; laid after, the same water keeps a shore on both sides and a span standing over
     * it, and the map can say which span holds which water.
     *
     * <p>Without walls there are also no bridged cells for selection to protect, so a
     * continent's coast is free to cut a corner across where a span will later land. That is a
     * real collision rather than an artefact - a span is chosen against the line, so the line
     * gets to be drawn first.
     *
     * <p>A cell alone in the void still contributes nothing: the lone-island rule reads the run
     * rather than the walls, so a single-cell continent degenerates the way any other lone cell
     * does.
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
            Walls.NONE);
    }

    /**
     * Traces the coasts against walls a caller has already built.
     *
     * <p>The shared tail of the continent entry above, and the one to call when the coast has
     * walls to keep - spans a caller has laid, and which cells they are pinched on. Everything
     * about tracing a coast that does not depend on where the walls came from lives here, so
     * the walk, the three stages and the reach are one and cannot drift between the entries.
     *
     * @param sites      the sites
     * @param parameters the knobs the cells are built under
     * @param rules      the knobs the coast is traced under
     * @param walls      the walls to lay, exactly as the walk should keep them
     * @return the coasts, and what they were traced against
     */
    public static TracedCoasts traceCoastsAcrossWalls(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            CoastRules rules,
            Walls walls) {

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
        var silhouettes = CoastPlacement.keepJoinedRuns(runs.silhouettes());
        var islands = CoastPlacement.collectLoneIslands(runs.silhouettes());
        var bridged = CoastPlacement.collectBridgedCircles(runs.laid());
        // The two shores are placed under different rules about walls, and the difference is
        // which side of a wall the water is on. An OUTER coast has every wall inside it: a run
        // cut seaward across a wall's side leaves the wall on the land side of the line, which
        // is the rounding-up the coast is for. An INTERIOR shore is the edge of water a wall
        // bounds: a run cut across that wall's side puts the shore inside the wall, on the
        // wrong side of a shape already drawn. So the shores take a wall's own side wherever
        // the walk joined two stretches by one, and the outer coast places every run freely -
        // except through a mouth that has closed to a point, which is the one place a wall
        // offers the coast to touch it and is touched rather than cut across.
        var outerRules = new CoastPlacement.BorderRules(
            rules.minFrontageShare(),
            parameters.measureArcSegments(),
            walls,
            CoastPlacement.WallPlacement.TOUCH_POINT_MOUTHS,
            rules.reachAnchor());
        var shoreRules = new CoastPlacement.BorderRules(
            rules.minFrontageShare(),
            parameters.measureArcSegments(),
            walls,
            CoastPlacement.WallPlacement.HUG_SIDES,
            rules.reachAnchor());

        var outerPlacement = new CoastPlacement(union, bridged, outerRules);
        var shorePlacement = new CoastPlacement(union, bridged, shoreRules);
        var arcSegments = parameters.measureArcSegments();
        var leastWater = measureLeastWater(union, rules);

        var placed = outerPlacement.buildSilhouetteBorders(silhouettes);
        var water = buildLakes(runs.lakes(), union, shorePlacement, arcSegments, leastWater);
        var walledShores = buildWalledShores(
            runs.walled(), union, shorePlacement, arcSegments, leastWater);

        return new TracedCoasts(
            buildCoasts(placed.coasts()),
            silhouettes,
            islands,
            concatenateDropped(placed.dropped(), water.dropped()),
            union,
            walls,
            water.lakes(),
            water.puddles(),
            walledShores);
    }

    /**
     * The straight reaches of any set of coasts, at a stated channel.
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
     * <p>A lake shore is the outer shore looked at from the water's side, and its reaches wall
     * water off exactly as an outer coast's do - so whatever lays one kind has to be able to lay
     * the other from the same rule. The channel is handed in because it is what tells a reach
     * from a handover, and a shore does not carry one of its own.
     *
     * @param coasts  the coasts to read, of either kind
     * @param channel the width below which a step is a handover between touching cells rather
     *                than a reach across void
     * @return one entry per reach, in walk order
     */
    public static List<CoastReach> collectStraightReaches(List<Coast> coasts, double channel) {

        var reaches = new ArrayList<CoastReach>();

        for (var coast : coasts) {

            var vertices = coast.vertices();

            for (var index = 0; index < vertices.size(); index++) {

                var from = vertices.get(index);
                var to = vertices.get((index + 1) % vertices.size());

                if (from.circle() != to.circle()
                        && Points.computeDistance(from.point(), to.point()) >= channel) {

                    reaches.add(new CoastReach(from, to));
                }
            }
        }
        return reaches;
    }

    // The shores of the holes a wall closed, smoothed through the same pipeline as the lakes -
    // because a shore is a shore, and the pipeline reads nothing but the marks and their order.
    //
    // Held to the same floor as a lake for the same reason: water too small to deserve a
    // shoreline is too small whatever closed it. What differs is only what becomes of the ones
    // refused - a lake too small is kept as a puddle to be filled, while this water is already
    // the pockets' subject and needs nothing recorded of it here.
    //
    // What these shores leave out does NOT go on the shared dropped list. A dropped stretch is
    // drawn where the coast would have run had it passed through, so that a reader can see what
    // the rule bought against the line that replaced it - and the constructions that read that
    // list draw the outer coasts and the lake shores, not these. Put there, the marks would be
    // read against a line nobody drew.
    private static List<Coast> buildWalledShores(
            List<List<CoastMark>> walledRuns,
            DiscUnion union,
            CoastPlacement placement,
            int arcSegments,
            double leastWater) {

        var shores = new ArrayList<Coast>(walledRuns.size());

        for (var run : walledRuns) {

            placeShoreAboveFloor(
                run, sampleWaterEdge(union, run, arcSegments), leastWater, placement)
                .ifPresent(one -> shores.add(new Coast(one.outline())));
        }
        return List.copyOf(shores);
    }

    // How much water a hole has to hold to be worth a shoreline, as an area. One reading for
    // every kind of hole, so the floor means the same thing wherever it is applied.
    private static double measureLeastWater(DiscUnion union, CoastRules rules) {
        return rules.minLakeShare() * Math.PI * union.reach() * union.reach();
    }

    // One hole's shore, where the hole holds enough water to deserve one and a line could be
    // placed round it. Empty otherwise: what the caller does with water that got no shore is
    // the one thing the lakes and the walled holes differ on.
    //
    // The floor is taken on the water's edge BEFORE any smoothing is paid for: whether a hole
    // deserves a shore is decided by how much water it holds, and the smoothing is the
    // expensive half. A shore the placement could not draw leaves the same water the floor
    // refuses - a hole with no line to put round it - so the two come back as one answer.
    private static Optional<CoastPlacement.PlacedCoast> placeShoreAboveFloor(
            List<CoastMark> run,
            List<double[]> waterEdge,
            double leastWater,
            CoastPlacement placement) {

        if (Math.abs(PolygonRegions.computeSignedArea(waterEdge)) < leastWater) {
            return Optional.empty();
        }

        var one = placement.buildOneBorder(run);

        return one.outline().isEmpty() ? Optional.empty() : Optional.of(one);
    }

    // The stretches both kinds of coast left out, as the one list the diagnostic draws. A
    // stretch a rule threw away is the same kind of fact on a lake shore as on the outer
    // shore, and the drawing tells them apart by the line each sits beside.
    private static List<CoastMark> concatenateDropped(
            List<CoastMark> fromCoasts,
            List<CoastMark> fromLakes) {

        var dropped = new ArrayList<CoastMark>(
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
    //
    // Water that gets no shore becomes a puddle, whichever of the two reasons left it without
    // one: a puddle is still a piece of the map, kept for the construction that fills it up
    // instead of shoring it. Dropped instead, it is a piece of map that no lake, no puddle and
    // no fill has any record of, which is a patch nothing can even report as missing. Its
    // left-out stretches are not recorded either: a dropped stretch is drawn where the coast
    // WOULD have run, to be read against the line that replaced it, and a run that produced no
    // line has nothing to read them against.
    private static TracedLakes buildLakes(
            List<List<CoastMark>> lakeRuns,
            DiscUnion union,
            CoastPlacement placement,
            int arcSegments,
            double leastWater) {

        var lakes = new ArrayList<Lake>(lakeRuns.size());
        var puddles = new ArrayList<Puddle>();
        var dropped = new ArrayList<CoastMark>();

        for (var run : lakeRuns) {

            var waterEdge = sampleWaterEdge(union, run, arcSegments);
            var shore = placeShoreAboveFloor(run, waterEdge, leastWater, placement);

            if (shore.isEmpty()) {

                puddles.add(new Puddle(waterEdge, collectRingCells(run)));
                continue;
            }

            dropped.addAll(shore.get().dropped());

            lakes.add(new Lake(
                new Coast(shore.get().outline()),
                waterEdge,
                collectRingCells(run)));
        }
        return new TracedLakes(
            List.copyOf(lakes), List.copyOf(puddles), List.copyOf(dropped));
    }

    // The cells a run of marks passes over. A set rather than a run, because the walk offers
    // a mark per STRETCH and a cell facing the water twice contributes two of them - so the
    // duplicates have to go, and once they have there is no order left worth keeping.
    private static Set<Integer> collectRingCells(List<CoastMark> run) {

        var cells = new LinkedHashSet<Integer>();

        for (var mark : run) {
            cells.add(mark.circle());
        }
        return Set.copyOf(cells);
    }

    // The water's true edge: every mark of the lake's ring sampled along its own arc, joined
    // in walk order into one closed outline.
    private static List<double[]> sampleWaterEdge(
            DiscUnion union,
            List<CoastMark> run,
            int arcSegments) {

        var edge = new ArrayList<double[]>();

        for (var mark : run) {
            edge.addAll(sampleMarkArc(union, mark, arcSegments));
        }
        return List.copyOf(edge);
    }

    // The placed outlines as coasts. A wrapper rather than the lists themselves, so that
    // what a later stage is handed says which of the several point lists a trace holds it is.
    private static List<Coast> buildCoasts(List<List<CoastVertex>> outlines) {

        var coasts = new ArrayList<Coast>(outlines.size());

        for (var outline : outlines) {
            coasts.add(new Coast(outline));
        }
        return List.copyOf(coasts);
    }

    /**
     * The water a trace found beyond its outer coasts - the lakes, the puddles, and what the
     * lake shores left out on the way.
     *
     * <p>The drops travel with them for the reason {@link CoastPlacement.PlacedCoasts}' do: a
     * lake that came out wrong looks the same on screen whether a rule dropped too much or the
     * walk never offered the stretch, and those are opposite faults with opposite fixes.
     *
     * @param lakes   the lakes, in the order they were traced
     * @param puddles the holes the floor judged too small to be lakes, in the same order
     * @param dropped every stretch the lake shores were not drawn through, over all of them
     */
    private record TracedLakes(
        List<Lake> lakes,
        List<Puddle> puddles,
        List<CoastMark> dropped) {
    }
}
