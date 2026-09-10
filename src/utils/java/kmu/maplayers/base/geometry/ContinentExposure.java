package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/**
 * What a continent still shows the open void once its inlets are spanned.
 *
 * <p>A span laid across an inlet takes that water inside the continent, and the frontage it
 * closed off stops facing the void at all - the cells are still there, but what lies outside
 * them is now water the shape encloses. So after a laying there are two leftovers, and they
 * are two halves of one question a further pass has to ask: which stretch of cell still faces
 * open water, and which of the spans already laid stands between captured water and the open
 * void rather than between two pieces of captured water.
 *
 * <p><b>Answered per continent, because a continent is the unit a span belongs to.</b> A span
 * joins two cells of one outline and rounds that outline up; leftovers pooled across the
 * sector would say how open the SECTOR is, which is not a thing any pass can act on.
 *
 * <p><b>Read at the cells' own reach, on the discs the coast was traced against.</b> The
 * shaping a pocket is DRAWN at moves the reach, and moving the reach moves every crossing
 * between two cells - so it moves the ends of every arc. Measured there, a stretch of coast
 * would be called open or closed by a number that exists to leave a gap under a fill. Which
 * border faces the void is a fact about the map rather than about the drawing of it, so it is
 * read at the one reach that defines void.
 *
 * <p><b>A wall's mouth closes border as surely as its water does.</b> A span keeps a channel,
 * so where it meets a cell it takes a mouth out of that cell's border, and the captured water
 * beside it stops at the near edge of that mouth rather than at the span itself. Counting only
 * the water therefore leaves a sliver of border open at every anchor - and two anchors close
 * together leave a sliver that looks like a stretch, offering a further pass somewhere to
 * build in the one place a wall is already standing. The mouth belongs to the wall, which is
 * to say it belongs to neither side.
 */
public final class ContinentExposure {

    // The shaping every reading here is taken at: the void's own extent, which walks the very
    // discs the coast walk ran on. See the class note - the channel is presentation, and none
    // of this is a question about presentation.
    private static final VoidPockets.PocketShaping AT_THE_COASTS_OWN_REACH =
        VoidPockets.PocketShaping.AT_TRUE_EXTENT;

    // How many of a span's two sides have captured water against them for it to stand on the
    // edge of the open void. Both sides makes it an interior span, laid between two pieces of
    // water something else already closed.
    private static final int SIDES_CLOSED_ON_AN_EDGE_SPAN = 1;

    // What a span no captured water names closed: nothing. Either it was one of a chain, which
    // strings cells together without ringing anything, or its mouth was buried and it was
    // never laid at all.
    private static final int NO_SIDES_CLOSED = 0;

    // Fewest points a surviving run needs before it is a stretch of coast. One point is a place
    // rather than a stretch: nothing can be anchored along it and nothing can be drawn of it.
    private static final int MIN_POINTS_IN_A_STRETCH = 2;

    // Where a mouth's two numbers sit in the pair it comes back as.
    private static final int MOUTH_START = 0;
    private static final int MOUTH_WIDTH = 1;

    private ContinentExposure() {
    }

    /**
     * One run of a cell's frontage that no span has closed off.
     *
     * @param cell   whose frontage it is, which is what anything anchoring on it has to know:
     *               a stretch is somewhere to build from, and what it is built between is
     *               cells
     * @param points the run, in coast order, as points of the traced line - the same points a
     *               span anchors on, so a further pass measures against the line rather than
     *               near it
     */
    public record ExposedStretch(
        int cell,
        List<double[]> points) {
    }

    /**
     * What one continent has left facing the open void.
     *
     * @param continent        which continent, numbered as the silhouettes are
     * @param exposedStretches every stretch of its frontage still facing the open void
     * @param edgeSpans        those of its spans that hold captured water on one side and the
     *                         open void on the other
     */
    public record ExposedContinent(
        int continent,
        List<ExposedStretch> exposedStretches,
        List<CellGap> edgeSpans) {
    }

    /**
     * What each continent still shows the open void.
     *
     * <p>The spans are handed in rather than laid here, for the reason the shore is named
     * rather than chosen: a reading taken against spans laid on some other trace, or under
     * other rules, would compile and describe a map nobody drew.
     *
     * @param traced     the continent coasts, as they were traced without bridges
     * @param spans      the spans laid on the EXTERIOR shore of that same trace
     * @param parameters the knobs the cells are built under
     * @return one entry per continent, in the silhouettes' own order, including the continents
     *         with nothing left exposed - a continent the spans closed up entirely is an
     *         answer rather than an absence
     */
    public static List<ExposedContinent> findContinentExposure(
            Coastlines.TracedCoasts traced,
            List<CellGap> spans,
            SectorGeometryParameters parameters) {

        var captured = VoidBridgePockets.findBridgeWalledHoles(
            traced.union().sites(), spans, parameters, AT_THE_COASTS_OWN_REACH);

        // Built for the mouths alone, which is how wide a bite the walls take out of a cell.
        // Asked of the walls rather than read off the knobs here, so that the rule about what
        // a bridge wall keeps stays in the one place that states it.
        var walls = VoidBridgePockets.buildBridgeWalls(spans, parameters);

        var continentOf = Coastlines.mapCellsToContinents(traced);
        var continents = traced.silhouettes().size();

        var stretches = gatherByContinent(
            findExposedStretches(traced, captured, walls),
            ExposedStretch::cell,
            continentOf,
            continents);

        // Either end names the continent: the laying offers a span only between two cells of
        // one outline, so its two ends cannot disagree about which.
        var edges = gatherByContinent(
            findEdgeSpans(spans, captured), CellGap::fromSite, continentOf, continents);

        var exposure = new ArrayList<ExposedContinent>(continents);

        for (var continent = 0; continent < continents; continent++) {

            exposure.add(new ExposedContinent(
                continent,
                List.copyOf(stretches.get(continent)),
                List.copyOf(edges.get(continent))));
        }
        return List.copyOf(exposure);
    }

    // Every stretch of frontage still facing the void, over the whole trace. Cell by cell,
    // because that is how a frontage is offered and how both a piece of water and a wall's
    // mouth name the border they take - the three only meet on a cell.
    private static List<ExposedStretch> findExposedStretches(
            Coastlines.TracedCoasts traced,
            List<VoidHole> captured,
            DiscUnionBoundary.Walls walls) {

        var closed = gatherClosedArcsByCell(captured, traced.union(), walls);
        var sites = traced.union().sites();
        var exposed = new ArrayList<ExposedStretch>();

        for (var entry : CoastFrontages.Shore.EXTERIOR.collectFrontages(traced).entrySet()) {

            var cell = entry.getKey();
            var arcs = closed.getOrDefault(cell, List.of());

            for (var frontage : entry.getValue()) {
                for (var stretch : splitOffClosedBorder(frontage, sites.get(cell), arcs)) {

                    exposed.add(new ExposedStretch(cell, stretch));
                }
            }
        }
        return exposed;
    }

    // One frontage cut into the runs that survive, dropping every point the spans closed off.
    // Cut into runs rather than measured as a share, because what a further pass wants is
    // somewhere to anchor - and an anchor is a point of the line, not a fraction of an arc.
    private static List<List<double[]>> splitOffClosedBorder(
            List<double[]> frontage,
            double[] site,
            List<ClosedArc> arcs) {

        var stretches = new ArrayList<List<double[]>>();
        var run = new ArrayList<double[]>();

        for (var point : frontage) {

            if (isBorderClosed(point, site, arcs)) {

                addStretch(stretches, run);
                run = new ArrayList<>();
            } else {
                run.add(point);
            }
        }
        addStretch(stretches, run);

        return stretches;
    }

    // Whether one point of frontage is border the spans closed off.
    //
    // Asked as an angle about the cell's own site rather than as a point inside an outline.
    // The frontage, the captured water's arcs and the mouths are all stretches of the SAME
    // circle read at the same reach, so which of them a point falls in is a comparison of two
    // angles and nothing else - where the same point tested against the water's sampled
    // outline would be answered by whichever side of a flattening chord it happened to land on.
    private static boolean isBorderClosed(
            double[] point,
            double[] site,
            List<ClosedArc> arcs) {

        var angle = Math.atan2(point[1] - site[1], point[0] - site[0]);

        for (var arc : arcs) {

            // Moved into the arc's own turn first. An arc runs past a full turn where it
            // straddles the angle the circle is cut at, so an angle taken raw from atan2 falls
            // short of an arc that in fact holds it.
            if (Angles.placeAfter(angle, arc.fromAngle()) <= arc.toAngle()) {
                return true;
            }
        }
        return false;
    }

    // Every stretch of border the spans took, by the cell it sits on: the arcs the captured
    // water runs along, and the mouths the walls that closed it open on their two cells. A
    // list per cell rather than one arc, because a cell between two captured pockets faces
    // both and gives up a stretch to each.
    //
    // The mouths of walls that closed NOTHING are left out. Such a wall has open void on both
    // sides of it, and the border under it goes on facing the void whatever is standing there.
    private static Map<Integer, List<ClosedArc>> gatherClosedArcsByCell(
            List<VoidHole> captured,
            DiscUnion union,
            DiscUnionBoundary.Walls walls) {

        var arcs = new LinkedHashMap<Integer, List<ClosedArc>>();

        for (var water : captured) {

            for (var mark : water.marks()) {

                addArc(arcs, mark.circle(), new ClosedArc(mark.fromAngle(), mark.toAngle()));
            }

            for (var wall : water.walledBy()) {

                addMouthOf(arcs, wall, wall.fromCircle(), union, walls);
                addMouthOf(arcs, wall, wall.toCircle(), union, walls);
            }
        }
        return arcs;
    }

    // The stretch one wall takes out of one of its cells' border.
    private static void addMouthOf(
            Map<Integer, List<ClosedArc>> arcs,
            DiscUnionBoundary.Chord wall,
            int cell,
            DiscUnion union,
            DiscUnionBoundary.Walls walls) {

        var mouth = WallMouths.measureMouth(union, wall, cell, walls.channelOn(cell));

        // Absent only where a wall opens no mouth on a circle at all, which a wall the walk
        // laid does not. Guarded rather than assumed, since nothing downstream would notice
        // the difference between a missing mouth and a mouth of no width.
        if (mouth != null) {

            addArc(arcs, cell, new ClosedArc(
                mouth[MOUTH_START], mouth[MOUTH_START] + mouth[MOUTH_WIDTH]));
        }
    }

    private static void addArc(Map<Integer, List<ClosedArc>> arcs, int cell, ClosedArc arc) {

        arcs.computeIfAbsent(cell, whichever -> new ArrayList<>()).add(arc);
    }

    // The spans standing between captured water and the open void.
    //
    // Counted off the walk's own record of what closed each piece of water. A span is laid as
    // a wall with two sides, and each side is walked as part of whatever encloses it: a side
    // facing captured water belongs to that water's cycle, while a side facing the open void
    // belongs to no cycle at all, the unbounded outside being no hole. So the number of
    // captured pieces naming a span is the number of its sides that face inward.
    //
    // Matched back to the span by the cells it joins. A chord carries its two cells and nothing
    // else of the span it was built from, so matching by identity would mean building the chords
    // a second time and trusting two builds of one line to come out equal.
    private static List<CellGap> findEdgeSpans(List<CellGap> spans, List<VoidHole> captured) {

        var closedSides = new LinkedHashMap<SpannedCells, Integer>();

        for (var water : captured) {
            for (var wall : water.walledBy()) {

                closedSides.merge(
                    SpannedCells.buildFromCells(wall.fromCircle(), wall.toCircle()),
                    1,
                    Integer::sum);
            }
        }

        var edges = new ArrayList<CellGap>();

        for (var span : spans) {

            var sides = closedSides.getOrDefault(
                SpannedCells.buildFromCells(span.fromSite(), span.toSite()), NO_SIDES_CLOSED);

            if (sides == SIDES_CLOSED_ON_AN_EDGE_SPAN) {
                edges.add(span);
            }
        }
        return edges;
    }

    // One continent's share of a list, for every continent, filed by the cell each entry sits
    // on. Written once for both readings: a stretch and a span are gathered the same way and
    // differ only in where the cell is read off, so a copy per reading is a second place for a
    // cell on no continent to be handled differently.
    private static <T> List<List<T>> gatherByContinent(
            List<T> entries,
            ToIntFunction<T> cellOf,
            Map<Integer, Integer> continentOf,
            int continents) {

        var gathered = new ArrayList<List<T>>(continents);

        for (var continent = 0; continent < continents; continent++) {
            gathered.add(new ArrayList<T>());
        }

        for (var entry : entries) {

            var continent = continentOf.get(cellOf.applyAsInt(entry));

            // A cell the outer walk never touched sits on no continent, and there is nothing
            // for its leftovers to be left over FROM.
            if (continent != null) {
                gathered.get(continent).add(entry);
            }
        }
        return gathered;
    }

    // Keeps a run only where enough of it survived to be a stretch, and starts the next one.
    private static void addStretch(List<List<double[]>> stretches, List<double[]> run) {

        if (run.size() >= MIN_POINTS_IN_A_STRETCH) {
            stretches.add(List.copyOf(run));
        }
    }

    /**
     * A stretch of one cell's border that no longer faces the void.
     *
     * <p>Either water a span shut in runs along it or a span's own mouth attaches there. One
     * type for the two, because what a frontage point is asked is whether anything closed the
     * border under it - and a point does not care which of the two did.
     *
     * @param fromAngle where it begins, about that cell's site
     * @param toAngle   where it ends, which may run past a full turn where the stretch
     *                  straddles the angle the circle is cut at
     */
    private record ClosedArc(
        double fromAngle,
        double toAngle) {
    }
}
