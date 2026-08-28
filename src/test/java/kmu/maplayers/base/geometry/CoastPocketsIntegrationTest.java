package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the void the continent coasts shut in, over real sectors.
 *
 * <p>What a pocket is FOR is being drawn: a reader looking at the map sees water inside a
 * coastline and expects it filled. So the property worth pinning is not how a pocket is built
 * but that the construction and the coastline agree about the same map - wherever the coast
 * closes water off, something accounts for it.
 *
 * <p>Pinned two ways, because the two catch different faults. The absolute rule - water shut in
 * inside the coast lies under the fill that owes it - is the property a reader checks by
 * looking, and it is asked of a flood that shares no code with the trace, so void the trace
 * never considered still counts against it. Which fill owes which water follows the
 * constructions' own split: water a laid wall shuts in belongs to the coast's pockets and is
 * held strictly to them, since on the composed map the bridge fill lies over the coast's
 * gaps - which is exactly how a missing coast pocket stays invisible. Water ringed by cells
 * alone takes either construction's fill. The comparison between two settings of the
 * frontage floor
 * catches what the absolute rule cannot: a pocket that stands at one floor and not at the
 * next is a fill the map had and then lost, which is worth naming separately from void that
 * was never filled at any setting.
 *
 * <p>A pocket may legitimately disappear when the coast coarsens: a bay whose mouth the coarser
 * line no longer closes is open sea again, and open sea wants no fill. That is why that check
 * is not "no pocket is lost" but "a lost pocket's water is no longer enclosed".
 */
class CoastPocketsIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.CoastPocketsIntegrationTest"
            + "#provideSectorNames";

    // The floor the coast is traced at everywhere else, and one coarser setting to raise it
    // to. A pair is enough to pin the property: the fault is that raising the floor at all
    // strands water, so any two settings either side of a dropped stretch show it, and
    // sweeping more of them multiplies a slow construction for no more coverage.
    private static final double SHIPPED_FRONTAGE_FLOOR =
        Coastlines.DEFAULT_RULES.minFrontageShare();

    // Twice the shipped floor. It has to sit ABOVE the shipped one for the comparison to mean
    // anything, since two equal floors trace one coast and the check then passes by describing
    // no change at all. Expressed as a multiple rather than as a number of its own so that it
    // stays coarser than the shipped floor wherever that is set.
    private static final double COARSER_FRONTAGE_FLOOR = 2 * SHIPPED_FRONTAGE_FLOOR;

    // How small a piece of void stops being worth a fill, as a share of one cell's area.
    // Cells all but touching leave seams a few hundred units across all over a sector, and a
    // reader notices none of them; expressed against a cell rather than in units so that it
    // still means the same thing if the cell radius moves.
    private static final double NOTICEABLE_SHARE_OF_A_CELL = 0.005;

    // How coarsely the flood walks. Fine enough that the pieces it reports are the same ones,
    // of the same size, as at half this stride - which is the only thing that makes a grid
    // flood's answer trustworthy - and coarse enough to sweep a sector in seconds.
    private static final double FLOOD_STRIDE = 50;

    // How far towards its outline's mean point a corner is nudged before a coverage test,
    // as a share of the way there. Enough to step off the boundary the corner sits on, and
    // little enough to stay in the corner's own neighbourhood of the water.
    private static final double CORNER_NUDGE_SHARE = 0.05;

    // Built once per sector and shared: tracing a coast is O(n^2) in a sector's systems, and
    // this suite asks for four of them per fixture.
    private static final Map<String, SectorFixture> FIXTURES = new ConcurrentHashMap<>();

    static List<String> provideSectorNames() {

        var names = SectorFixture.listSectorNames();

        assertThat(names)
            .as("no sector fixtures on the classpath")
            .isNotEmpty();

        return names;
    }

    @Nested
    class FindCoastPockets {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_pocket_lost_to_a_coarser_coast_leaves_no_water_behind_it(String sector) {
            // Raising the frontage floor stops the coast passing along the narrowest
            // frontages. Where that closes a bay off rather than opening it, the water is
            // still inside the coastline and still wants a fill - so a pocket that vanishes
            // while its water stays enclosed is a hole in the map, not a coarser map.
            for (var shaping : VoidPockets.PocketShaping.values()) {

                var stranded = findStrandedPockets(sector, shaping);

                assertThat(stranded)
                    .as(
                        "%s, %s: water still inside the coast with no pocket over it",
                        sector,
                        shaping)
                    .isEmpty();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void shut_in_water_inside_the_coast_lies_under_the_fill_that_owes_it(String sector) {
            // The property a reader checks by looking: water they cannot sail out of, drawn
            // inside a coastline, is coloured in. Asked of the flood rather than of the trace,
            // so that a piece of void the trace never considered still counts against it.
            // Both shapings gathered before asserting, rather than one assertion per shaping.
            // A pocket missing at one shaping is usually missing at the other for the same
            // reason, and failing on the first hides half of what has to be fixed.
            var unfilled = new ArrayList<String>();

            for (var shaping : VoidPockets.PocketShaping.values()) {
                for (var water : findUnfilledWater(sector, shaping)) {
                    unfilled.add(shaping + ": " + water);
                }
            }

            assertThat(unfilled)
                .as("%s: water shut in inside the coast with no fill over it", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_pocket_names_the_cells_that_ring_it(String sector) {
            // What makes a pocket identifiable at all, and what the check above matches on.
            // A pocket ringed by no cell could not be told from any other, so the comparison
            // between two floors would silently pair unrelated water.
            for (var shaping : VoidPockets.PocketShaping.values()) {
                for (var walled : findPocketsAt(sector, SHIPPED_FRONTAGE_FLOOR, shaping)) {

                    assertThat(walled.pocket().section().cells())
                        .as("%s, %s: a pocket with no cells around it", sector, shaping)
                        .isNotEmpty();
                }
            }
        }
    }

    // Every piece of shut-in water inside the coast that the fill owing it does not cover,
    // named by the cells around it so a failure says which piece of the map went blank.
    //
    // A piece the flood could not leave without crossing a laid wall is water the coast shut
    // in, and wants a coast pocket - the bridge fill does not excuse it. A piece the flood
    // found enclosed by cells alone may be either construction's, and takes either fill.
    private static List<String> findUnfilledWater(
            String sector,
            VoidPockets.PocketShaping shaping) {

        var fixture = buildFixtureFor(sector);
        var parameters = SectorGeometryParameters.createDefaults();
        var traced = traceCoastAt(sector, SHIPPED_FRONTAGE_FLOOR);
        var union = VoidPockets.buildUnionFor(fixture.getSites(), parameters, shaping);

        var walls = DiscUnionBoundary.findAttachableChords(
            union,
            CoastPockets.layCoastWalls(
                traced, CoastPockets.buildCoastWalls(traced), parameters.borderInset()));

        var coastFill = new ArrayList<BoundedOutline>();

        for (var walled : findPocketsAt(sector, SHIPPED_FRONTAGE_FLOOR, shaping)) {
            for (var outline : walled.pocket().outlines()) {
                coastFill.add(BoundedOutline.measure(outline));
            }
        }

        var bridgeFill = collectBridgeFill(fixture, parameters, shaping);
        var coast = Coastlines.collectCoastRings(traced);
        var leastNoticeable = NOTICEABLE_SHARE_OF_A_CELL
            * Math.PI * parameters.cellRadius() * parameters.cellRadius();

        var unfilled = new ArrayList<String>();

        for (var water : ShutInVoidSweep.findShutInVoid(
                fixture.getSites(), union, walls, FLOOD_STRIDE)) {

            if (water.measureArea() < leastNoticeable || !isMostlyInsideCoast(water, coast)) {
                continue;
            }

            var isWalledIn = !water.walledBy().isEmpty();

            // Water a wall shuts in is held strictly to the coast's fill: the bridge fill
            // lies over much of the same void, and accepting it there is exactly how a
            // missing coast pocket stays invisible on the composed map. Water the flood
            // found ringed by cells alone takes either fill - the flood cannot tell a true
            // cell ring from a corridor whose walled mouth is narrower than its own stride,
            // and a reach running along a cell ring's edge makes such void legitimately the
            // coast's - so only the strict direction guards against masking.
            var isCovered = isWalledIn
                ? isAnyPointCovered(water, coastFill)
                : isAnyPointCovered(water, bridgeFill)
                    || isAnyPointCovered(water, coastFill);

            if (isCovered) {
                continue;
            }

            unfilled.add(String.format(
                "%.0f units of %s ringed by %s",
                water.measureArea(),
                isWalledIn ? "coast-walled water" : "inland water",
                water.nameRingingCells(
                    fixture.getSites(), union, fixture.getSystemIds())));
        }
        return unfilled;
    }

    // The bridge construction's fill, as outlines with their bounds measured once - what the
    // viewer paints as the inland fill, built the way the overlay builds it.
    private static List<BoundedOutline> collectBridgeFill(
            SectorFixture fixture,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        var captured = VoidBridgePockets.findCapturedPockets(
            fixture.getSites(),
            VoidBridges.findVoidBridges(
                fixture.getSites(),
                parameters.cellRadius(),
                parameters.cellRadius() * Coastlines.DEFAULT_RULES.bridgeReachMultiple()),
            parameters,
            shaping);

        var outlines = new ArrayList<BoundedOutline>(captured.size());

        for (var outline : captured) {
            outlines.add(BoundedOutline.measure(outline));
        }
        return outlines;
    }

    // Void shut in by walls can still be open sea: a wall closes the gap between two cells
    // wherever it is laid, seaward of the coast as readily as landward of it. Only water inside
    // the drawn coast is water a fill is owed.
    private static boolean isMostlyInsideCoast(
            ShutInVoidSweep.ShutInVoid water,
            List<List<double[]>> coast) {

        var inside = 0;

        for (var point : water.points()) {
            if (Coastlines.isInsideCoast(coast, point)) {
                inside++;
            }
        }
        return inside > water.points().size() / 2;
    }

    // Whether any pocket covers any part of this water. One point is enough: the question is
    // whether the trace accounted for this piece of void at all, not how closely its outline
    // hugs it - a pocket is inset from the wall that closed it and never covers the water whole.
    private static boolean isAnyPointCovered(
            ShutInVoidSweep.ShutInVoid water,
            List<BoundedOutline> pockets) {

        for (var point : water.points()) {
            for (var outline : pockets) {
                if (outline.holds(point)) {
                    return true;
                }
            }
        }
        return false;
    }

    // A pocket outline with its bounds measured once.
    //
    // A sector carries hundreds of pockets and its largest pieces of water tens of thousands of
    // points; testing every pair properly is minutes rather than seconds, and measuring the
    // bounds inside that loop is no cheaper than the test it was meant to avoid.
    private record BoundedOutline(
        List<double[]> outline,
        double leastX,
        double leastY,
        double mostX,
        double mostY) {

        static BoundedOutline measure(List<double[]> outline) {

            var leastX = Double.MAX_VALUE;
            var leastY = Double.MAX_VALUE;
            var mostX = -Double.MAX_VALUE;
            var mostY = -Double.MAX_VALUE;

            for (var corner : outline) {
                leastX = Math.min(leastX, corner[0]);
                leastY = Math.min(leastY, corner[1]);
                mostX = Math.max(mostX, corner[0]);
                mostY = Math.max(mostY, corner[1]);
            }
            return new BoundedOutline(outline, leastX, leastY, mostX, mostY);
        }

        boolean holds(double[] point) {

            return point[0] >= leastX && point[0] <= mostX
                && point[1] >= leastY && point[1] <= mostY
                && PolygonRegions.isPointInsideRing(outline, point[0], point[1]);
        }
    }

    // Every pocket the coarser coast drops whose water it nonetheless still encloses, named by
    // where it sat so a failure says which piece of the map went blank.
    private static List<String> findStrandedPockets(
            String sector,
            VoidPockets.PocketShaping shaping) {

        var atShipped = findPocketsAt(sector, SHIPPED_FRONTAGE_FLOOR, shaping);
        var atCoarser = findPocketsAt(sector, COARSER_FRONTAGE_FLOOR, shaping);
        var coarserFill = new ArrayList<BoundedOutline>();

        for (var walled : atCoarser) {
            for (var outline : walled.pocket().outlines()) {
                coarserFill.add(BoundedOutline.measure(outline));
            }
        }

        var coarserCoast = Coastlines.collectCoastRings(
            traceCoastAt(sector, COARSER_FRONTAGE_FLOOR));

        var stranded = new ArrayList<String>();

        for (var walled : atShipped) {

            // The same water under the coarser floor is found by ring equality first and by
            // overlap second. Equality catches the pocket the coarser floor left alone.
            // Overlap catches the reshaped one: raising the floor drops cells out of a
            // pocket's ring and moves its walls while the water stays where it was, so an
            // exact ring match alone reports a reshaped pocket as a lost one - and no
            // distance tells a moved pocket from a different one.
            if (hasSameRing(walled, atCoarser) || isAnyCornerCovered(walled, coarserFill)) {
                continue;
            }

            var centre = walled.pocket().centre();

            if (Coastlines.isInsideCoast(coarserCoast, centre)) {
                stranded.add(String.format(
                    "%.0f, %.0f (ringed by %s)",
                    centre[0],
                    centre[1],
                    walled.pocket().section().cells()));
            }
        }
        return stranded;
    }

    // Whether some pocket in the list runs on exactly the same cells - which is the pocket
    // the coarser floor left alone, outline and all.
    private static boolean hasSameRing(WalledPocket pocket, List<WalledPocket> among) {

        for (var other : among) {
            if (pocket.pocket().section().cells().equals(other.pocket().section().cells())) {
                return true;
            }
        }
        return false;
    }

    // Whether any corner of a pocket's outline lies inside the given fill - the outline is
    // where the pocket's water certainly is, so a fill covering a corner of it covers some
    // of the same water.
    //
    // Each corner is nudged a step towards its outline's mean point before being asked
    // about: a corner sits ON the water's edge, where a fill bounded by the same wall or rim
    // answers false for lying exactly on its own boundary.
    private static boolean isAnyCornerCovered(
            WalledPocket walled, List<BoundedOutline> fill) {

        for (var outline : walled.pocket().outlines()) {

            var mean = Points.computeMean(outline);

            for (var corner : outline) {

                var nudged = new double[] {
                    corner[0] + CORNER_NUDGE_SHARE * (mean[0] - corner[0]),
                    corner[1] + CORNER_NUDGE_SHARE * (mean[1] - corner[1])};

                for (var candidate : fill) {
                    if (candidate.holds(nudged)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static List<WalledPocket> findPocketsAt(
            String sector,
            double frontageFloor,
            VoidPockets.PocketShaping shaping) {

        return CoastPockets.findCoastPockets(
            traceCoastAt(sector, frontageFloor),
            buildFixtureFor(sector).getOwnerBySite(),
            new VoidPockets.PocketRules(
                SectorGeometryParameters.createDefaults(),
                shaping));
    }

    private static Coastlines.TracedCoasts traceCoastAt(String sector, double frontageFloor) {

        return Coastlines.traceContinentCoasts(
            buildFixtureFor(sector).getSites(),
            SectorGeometryParameters.createDefaults(),
            new Coastlines.CoastRules(
                Coastlines.DEFAULT_RULES.bridgeReachMultiple(),
                frontageFloor));
    }

    private static SectorFixture buildFixtureFor(String sector) {
        return FIXTURES.computeIfAbsent(sector, SectorFixture::loadSector);
    }
}
