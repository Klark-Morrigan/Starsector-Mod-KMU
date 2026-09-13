package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.base.geometry.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.SectorPipeline.loadFixture;

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
 * held strictly to them, since the map's other layers lie over much of the same void - which is
 * exactly how a missing coast pocket stays invisible. Water ringed by cells alone is either
 * construction's, so it takes any layer the map paints. The comparison between two settings of
 * the frontage floor
 * catches what the absolute rule cannot: a pocket that stands at one floor and not at the
 * next is a fill the map had and then lost, which is worth naming separately from void that
 * was never filled at any setting.
 *
 * <p>A pocket may legitimately disappear when the coast coarsens: a bay whose mouth the coarser
 * line no longer closes is open sea again, and open sea wants no fill. That is why that check
 * is not "no pocket is lost" but "a lost pocket's water is no longer enclosed".
 *
 * <p>The converse fault has its own check: a pocket drawn where there is no water to draw. Both
 * directions matter to a reader, and neither catches the other - missing fill leaves a hole in
 * the map, and fill out at sea colours space nothing encloses.
 */
class CoastPocketsIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

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

    @Nested
    class FindSpills {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void noPocketOutlineLiesOutsideTheCoastAtItsOwnReach(String sector) {
            // The rule the construction states about itself: a pocket is void the coast shut
            // in, so its outline is inside the coast. Asked at the reach the pocket was walked
            // at, since a pocket a channel out is walked against discs a channel wider - and
            // those close every strait narrower than two channels, where the cells' own reach
            // leaves an opening. Judged across that difference, whole inland seas read as
            // standing one channel out at sea.
            var spills = new ArrayList<String>();

            for (var shaping : VoidPockets.PocketShaping.values()) {
                for (var spill : findSpillsAt(sector, shaping)) {
                    spills.add(shaping + ": " + spill);
                }
            }

            assertThat(spills)
                .as("%s: pocket outline outside the coast that defines it", sector)
                .isEmpty();
        }
    }

    @Nested
    class FindCoastPockets {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void aPocketLostToACoarserCoastLeavesNoWaterBehindIt(String sector) {
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
        void shutInWaterInsideTheCoastLiesUnderTheFillThatOwesIt(String sector) {
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
        void everyPocketNamesTheCellsThatRingIt(String sector) {
            // What makes a pocket identifiable at all, and the first thing the cross-floor
            // check matches on. A pocket ringed by no cell could not be told from any other,
            // so that match would pair unrelated water and call a lost pocket a kept one.
            for (var shaping : VoidPockets.PocketShaping.values()) {
                for (var walled : findPocketsAt(sector, SHIPPED_FRONTAGE_FLOOR, shaping)) {
                    assertThat(walled.pocket().section().cells())
                        .as("%s, %s: a pocket with no cells around it", sector, shaping)
                        .isNotEmpty();
                }
            }
        }
    }

    // The coast's own pockets measured against the coast at the reach they were walked at.
    //
    // Off the shared laying rather than off a coast traced here, so the line judging a pocket
    // and the line the pocket was built from are the same construction at two reaches.
    private static List<CoastPocketFaults.Spill> findSpillsAt(
            String sector,
            VoidPockets.PocketShaping shaping) {
        var laying = SectorPipeline.layContinentsIn(sector);
        var rules = new VoidPockets.PocketRules(PARAMETERS, shaping);

        return CoastPocketFaults.findSpills(
            CoastPockets.findCoastPockets(
                laying.traceCoasts(),
                CoastPockets.markEverySiteUnowned(loadFixture(sector).getSites()),
                rules),
            Coastlines.collectCoastOutlines(shaping.isAtTrueExtent()
                ? laying.traceCoasts()
                : laying.traceCoastsAtDrawnReach()),
            rules);
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
        var fixture = loadFixture(sector);
        var traced = traceCoastAt(sector, SHIPPED_FRONTAGE_FLOOR);
        var union = VoidPockets.buildUnionFor(fixture.getSites(), PARAMETERS, shaping);

        var walls = DiscUnionBoundary.findAttachableChords(
            union,
            CoastPockets.layCoastWalls(
                traced, CoastPockets.buildCoastWalls(traced), PARAMETERS.borderInset()));

        var coastFill = collectFill(findPocketsAt(sector, SHIPPED_FRONTAGE_FLOOR, shaping));
        var otherFill = collectOtherFill(sector, shaping);
        var coast = measureBounds(Coastlines.collectCoastOutlines(traced));

        var unfilled = new ArrayList<String>();

        for (var water : ShutInVoidSweep.findShutInVoid(union, walls, FLOOD_STRIDE)) {
            if (water.measureArea() < measureLeastNoticeableArea()
                    || !isMostlyInsideCoast(water, coast)) {
                continue;
            }

            var isWalledIn = !water.walledBy().isEmpty();

            // Water a wall shuts in is held strictly to the coast's fill: the map's other
            // layers lie over much of the same void, and accepting one of them there is
            // exactly how a missing coast pocket stays invisible on the composed map. Water
            // the flood found ringed by cells alone takes any layer - the flood cannot tell a
            // true cell ring from a corridor whose walled mouth is narrower than its own
            // stride, and a reach running along a cell ring's edge makes such void
            // legitimately the coast's - so only the strict direction guards against masking.
            var owingFill = isWalledIn
                ? coastFill
                : concatenate(otherFill, coastFill);

            if (isAnyPointCovered(water.points(), owingFill)) {
                continue;
            }

            // Where to look comes first: a failure naming only the cells around a piece leaves
            // whoever reads it to find the place on the map before they can judge it.
            var middle = Points.computeMean(water.points());

            unfilled.add(String.format(
                "%.0f units of %s at %.0f,%.0f ringed by %s",
                water.measureArea(),
                isWalledIn ? "coast-walled water" : "inland water",
                middle[0],
                middle[1],
                water.nameRingingCells(union, fixture.getSystemIds())));
        }
        return unfilled;
    }

    // Below this a piece of void is a seam between cells that all but touch, which no reader
    // notices is unfilled. As a share of a cell rather than in units, so it still means the
    // same thing if the cell radius moves.
    private static double measureLeastNoticeableArea() {
        return NOTICEABLE_SHARE_OF_A_CELL
            * Math.PI * PARAMETERS.cellRadius() * PARAMETERS.cellRadius();
    }

    // A construction's pockets as outlines with their bounds measured once.
    private static List<BoundedOutline> collectFill(List<WalledPocket> pockets) {
        var fill = new ArrayList<BoundedOutline>(pockets.size());

        for (var walled : pockets) {
            for (var outline : walled.pocket().outlines()) {
                fill.add(BoundedOutline.measure(outline));
            }
        }
        return fill;
    }

    // Everything the map paints over the void apart from the coast's own pockets: the bays the
    // spans hold, the lakes with the bands their shores concede, the puddles, and the sea the
    // links shut in. What cell-ringed water is allowed to be covered by, since such water is
    // either construction's to fill.
    //
    // Taken from the same inventory the window draws from, so a piece excused here is a piece a
    // reader can see coloured in.
    private static List<BoundedOutline> collectOtherFill(
            String sector,
            VoidPockets.PocketShaping shaping) {
        var water = SectorPipeline.fillWater(sector, shaping);
        var rings = new ArrayList<List<double[]>>();

        rings.addAll(water.collectInletWater());
        rings.addAll(water.collectLakeWater());
        rings.addAll(water.collectLakeMargins());
        rings.addAll(water.collectPuddleWater());
        rings.addAll(water.collectLinkWater());
        rings.addAll(water.collectLinkedSectorWater());

        return measureBounds(rings);
    }

    private static List<BoundedOutline> concatenate(
            List<BoundedOutline> first, List<BoundedOutline> second) {
        var both = new ArrayList<BoundedOutline>(first.size() + second.size());

        both.addAll(first);
        both.addAll(second);

        return both;
    }

    // Void shut in by walls can still be open sea: a wall closes the gap between two cells
    // wherever it is laid, seaward of the coast as readily as landward of it. Only water inside
    // the drawn coast is water a fill is owed.
    //
    // Stops as soon as the majority is settled either way. A piece of water carries thousands
    // of points and nearly every one of them agrees with the rest, so counting the remainder
    // out is the bulk of the work and none of the answer.
    private static boolean isMostlyInsideCoast(
            ShutInVoidSweep.ShutInVoid water,
            List<BoundedOutline> coast) {
        var counted = water.points().size();
        var majority = counted / 2;

        var inside = 0;
        var outside = 0;

        for (var point : water.points()) {
            if (isPointCovered(point, coast)) {
                inside++;
            } else {
                outside++;
            }

            if (inside > majority) {
                return true;
            }
            if (outside >= counted - majority) {
                return false;
            }
        }
        return inside > majority;
    }

    // Rings with their bounds measured once, so the point tests below reject the far-away ones
    // on four comparisons instead of walking them.
    private static List<BoundedOutline> measureBounds(List<List<double[]>> rings) {
        var bounded = new ArrayList<BoundedOutline>(rings.size());

        for (var ring : rings) {
            bounded.add(BoundedOutline.measure(ring));
        }
        return bounded;
    }

    // Whether a fill covers any of these points. One point is enough: the question asked of
    // it is whether a construction accounted for a piece of void at all, not how closely its
    // outline hugs it - a pocket is inset from the wall that closed it and never covers the
    // water whole.
    private static boolean isAnyPointCovered(
            List<double[]> points,
            List<BoundedOutline> fill) {
        for (var point : points) {
            if (isPointCovered(point, fill)) {
                return true;
            }
        }
        return false;
    }

    // Whether any one of a set of outlines holds a point.
    private static boolean isPointCovered(double[] point, List<BoundedOutline> outlines) {
        for (var outline : outlines) {
            if (outline.holds(point)) {
                return true;
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
        var coarserFill = collectFill(atCoarser);

        var coarserCoast = Coastlines.collectCoastOutlines(
            traceCoastAt(sector, COARSER_FRONTAGE_FLOOR));

        var stranded = new ArrayList<String>();

        for (var walled : atShipped) {
            // The same water under the coarser floor is found by ring equality first and by
            // overlap second. Equality catches the pocket the coarser floor left alone.
            // Overlap catches the reshaped one: raising the floor drops cells out of a
            // pocket's ring and moves its walls while the water stays where it was, so an
            // exact ring match alone reports a reshaped pocket as a lost one - and no
            // distance tells a moved pocket from a different one.
            if (hasSameRing(walled, atCoarser)
                    || isAnyPointCovered(collectNudgedCorners(walled), coarserFill)) {
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

    // A pocket's outline corners, each nudged a step towards its own outline's mean point.
    //
    // Nudged because a corner sits ON the water's edge, where a fill bounded by that same
    // wall or rim answers false for a point lying exactly on its boundary. A step inward
    // puts the question where the answer is not a coin toss.
    private static List<double[]> collectNudgedCorners(WalledPocket walled) {
        var nudged = new ArrayList<double[]>();

        for (var outline : walled.pocket().outlines()) {
            var mean = Points.computeMean(outline);

            for (var corner : outline) {
                nudged.add(new double[] {
                    corner[0] + CORNER_NUDGE_SHARE * (mean[0] - corner[0]),
                    corner[1] + CORNER_NUDGE_SHARE * (mean[1] - corner[1])});
            }
        }
        return nudged;
    }

    private static List<WalledPocket> findPocketsAt(
            String sector,
            double frontageFloor,
            VoidPockets.PocketShaping shaping) {
        return CoastPockets.findCoastPockets(
            traceCoastAt(sector, frontageFloor),
            loadFixture(sector).getOwnerBySite(),
            new VoidPockets.PocketRules(PARAMETERS, shaping));
    }

    private static Coastlines.TracedCoasts traceCoastAt(String sector, double frontageFloor) {
        return Coastlines.traceContinentCoasts(
            loadFixture(sector).getSites(),
            PARAMETERS,
            new Coastlines.CoastRules(
                Coastlines.DEFAULT_RULES.bridgeReachMultiple(),
                frontageFloor,
                Coastlines.DEFAULT_RULES.minLakeShare(),
                Coastlines.DEFAULT_ROUNDING,
                Coastlines.DEFAULT_RULES.reachAnchor()));
    }
}
