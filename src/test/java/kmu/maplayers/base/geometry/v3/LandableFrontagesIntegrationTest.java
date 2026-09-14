package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Points;

import kmu.maplayers.base.geometry.DiscUnion;
import kmu.maplayers.base.geometry.DiscUnionBoundary;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static kmu.maplayers.base.geometry.v3.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.v3.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for where a straight line could arrive on a sector's border, over real
 * sectors.
 *
 * <p>The construction answers one question of every sampled angle, so what can be wrong with it
 * is the answer being vacuous. The checks are aimed at that: that it finds something, that it
 * finds LESS than every stretch it was offered, and that it does not lose the shapes a link most
 * needs to reach.
 */
class LandableFrontagesIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    // How much of a cell's radius a point may sit inside the border it is drawn on, in map
    // units. A sampled arc is chorded, so a point between two samples sits off the true circle
    // by the sagitta - and every point here is a sample rather than a chord midpoint, so this
    // only has to absorb the arithmetic.
    private static final double ON_THE_BORDER = 1;

    // How far outside the stretch it was cut from a sub-stretch may reach, in radians. A
    // hundredth of a degree: the ends are the walk's own angles arrived at by multiplication, so
    // this only has to absorb the arithmetic and not any real width.
    private static final double WITHIN_THE_STRETCH = Math.toRadians(0.01);

    // How many directions the cruder reading tries from an island's centre, and how finely it
    // walks each. Five degrees apart and an eighth of a reach at a time: coarse enough to be
    // plainly a different measurement from the construction's, fine enough that a gap a whole
    // cell wide cannot slip between two rays.
    private static final int RAYS_FROM_CENTRE = 72;
    private static final int RAY_STEPS_PER_REACH = 8;

    private static final Map<String, List<DiscUnionBoundary.CoastMark>> LANDABLE =
        new ConcurrentHashMap<>();

    @Nested
    class CollectLandableFrontages {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void aSectorHasBorderAStraightLineCouldArriveAt(String sector) {
            // Asked first and alone, because every other claim below is satisfied by an empty
            // answer. A sector whose whole border is walled in from every direction is not a
            // sector anything could ever be laid across.
            assertThat(landableOf(sector))
                .as("%s: no stretch of the sector's border can be arrived at at all", sector)
                .isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void someExposedBorderCannotBeArrivedAt(String sector) {
            // What makes this a rule rather than a pass-through. Facing the void and being
            // reachable across it are different things - the difference is the notches, whose
            // water is open sea by a path and by no straight line - so a sector that came back
            // with every stretch it was offered would be one where the sum found nothing.
            var offered = measureExposedArc(sector);
            var landable = measureLandableArc(sector);

            assertThat(landable)
                .as("%s: every stretch of exposed border came back reachable", sector)
                .isLessThan(offered);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyStretchLiesWithinTheOneItWasCutFrom(String sector) {
            // A landable stretch is a sub-arc of a stretch the walk found, so it names that
            // cell and runs between two angles of it. One outside would be border the walk
            // never offered - a stretch facing another cell rather than the void.
            var offered = collectExposedByCell(sector);
            var strayed = new ArrayList<String>();

            for (var stretch : landableOf(sector)) {
                if (!isWithinAny(stretch, offered.get(stretch.circle()))) {
                    strayed.add(String.format(
                        "cell %d from %.3f to %.3f",
                        stretch.circle(), stretch.fromAngle(), stretch.toAngle()));
                }
            }

            assertThat(strayed)
                .as("%s: a reachable stretch outside every stretch the walk offered", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void anIslandOpenFromItsCentreCanBeArrivedAtOnItsRim(String sector) {
            // The shapes this exists to keep. A cell alone in the void is on no silhouette and
            // has no coast, and it is exactly what a link is laid to reach - so the one way the
            // answer could fail them is by finding nowhere on a rim that plainly faces open sea.
            //
            // Judged by a second, cruder reading that shares none of the construction's
            // arithmetic: rays marched out from the island's centre. The centre sees a subset
            // of what the rim sees - a point on the rim is a reach nearer whatever gap the
            // centre is looking past - so a rim that comes back walled while its centre finds
            // open sky is the construction refusing what a simpler test allows. The converse
            // is no fault: a rim can thread a gap its centre cannot, and some do.
            //
            // Not every island is on the sector's outside. One ringed by several continents
            // together sits in the very water the inter-continental bridges wall in, and no
            // straight line from open sea reaches it; those are correctly reported walled, and
            // this check leaves them alone.
            var traced = traceContinentCoast(sector);

            assertThat(traced.islands())
                .as("%s: no islands, so this check asks nothing", sector)
                .isNotEmpty();

            var reachable = collectCellsWithLandableBorder(sector);
            var refused = new ArrayList<String>();

            for (var island : traced.islands()) {
                if (isOpenFromCentre(traced.union(), island) && !reachable.contains(island)) {
                    refused.add(String.format("island %d", island));
                }
            }

            assertThat(refused)
                .as("%s: an island open to the sky from its centre with nowhere on its rim to be "
                    + "arrived at", sector)
                .isEmpty();
        }
    }

    @Nested
    class CollectLandableRuns {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void noRunPassesInsideACell(String sector) {
            // A stretch of border drawn on the border it sits on. A point inside any cell would
            // mean the sampling had left the boundary, which is the one way these runs could be
            // drawn somewhere they say nothing about.
            var union = traceContinentCoast(sector).union();
            var buried = new ArrayList<String>();

            for (var run : runsOf(sector)) {
                for (var point : run) {
                    for (var site = 0; site < union.sites().size(); site++) {
                        var separation = Points.computeDistance(union.sites().get(site), point);

                        if (separation < union.reach() - ON_THE_BORDER) {
                            buried.add(String.format(
                                "cell %d at %.0f, inside %.0f",
                                site, separation, union.reach()));
                        }
                    }
                }
            }

            assertThat(buried)
                .as("%s: reachable border drawn inside a cell", sector)
                .isEmpty();
        }
    }

    // Whether any ray marched out from a cell's centre leaves the map without entering another
    // cell. Marched from just past the cell's own reach, so its own disc is already behind the
    // ray and only the rest of the map can stop it.
    private static boolean isOpenFromCentre(DiscUnion union, int cell) {
        var centre = union.sites().get(cell);
        var edge = measureMapEdge(union);

        for (var ray = 0; ray < RAYS_FROM_CENTRE; ray++) {
            var angle = Angles.FULL_TURN * ray / RAYS_FROM_CENTRE;
            var blocked = false;

            for (var along = union.reach() + ON_THE_BORDER;
                    along < edge && !blocked;
                    along += union.reach() / RAY_STEPS_PER_REACH) {
                blocked = union.isPointInside(new double[] {
                    centre[0] + along * Math.cos(angle),
                    centre[1] + along * Math.sin(angle)});
            }
            if (!blocked) {
                return true;
            }
        }
        return false;
    }

    // Beyond every cell from anywhere on the map, so a ray marched this far has left it.
    private static double measureMapEdge(DiscUnion union) {
        var furthest = 0.0;

        for (var site : union.sites()) {
            furthest = Math.max(furthest, Math.hypot(site[0], site[1]));
        }
        return furthest * 2 + union.reach();
    }

    // Whether a stretch runs entirely within one of the stretches offered on its own cell.
    private static boolean isWithinAny(
            DiscUnionBoundary.CoastMark stretch,
            List<DiscUnionBoundary.CoastMark> offered) {
        if (offered == null) {
            return false;
        }

        for (var mark : offered) {
            if (stretch.fromAngle() >= mark.fromAngle() - WITHIN_THE_STRETCH
                    && stretch.toAngle() <= mark.toAngle() + WITHIN_THE_STRETCH) {
                return true;
            }
        }
        return false;
    }

    // Every stretch of border the walk found, by the cell it sits on. The islands go in whole,
    // as the construction takes them: a lone cell is on no silhouette and faces the void the
    // entire way round.
    private static Map<Integer, List<DiscUnionBoundary.CoastMark>> collectExposedByCell(
            String sector) {
        var traced = traceContinentCoast(sector);
        var byCell = new LinkedHashMap<Integer, List<DiscUnionBoundary.CoastMark>>();

        for (var silhouette : traced.silhouettes()) {
            for (var mark : silhouette) {
                byCell.computeIfAbsent(mark.circle(), whichever -> new ArrayList<>()).add(mark);
            }
        }
        for (var island : traced.islands()) {
            byCell
                .computeIfAbsent(island, whichever -> new ArrayList<>())
                .add(new DiscUnionBoundary.CoastMark(island, 0, Angles.FULL_TURN));
        }
        return byCell;
    }

    private static List<Integer> collectCellsWithLandableBorder(String sector) {
        var cells = new ArrayList<Integer>();

        for (var stretch : landableOf(sector)) {
            if (!cells.contains(stretch.circle())) {
                cells.add(stretch.circle());
            }
        }
        return cells;
    }

    private static double measureLandableArc(String sector) {
        var total = 0.0;

        for (var stretch : landableOf(sector)) {
            total += stretch.toAngle() - stretch.fromAngle();
        }
        return total;
    }

    private static double measureExposedArc(String sector) {
        var total = 0.0;

        for (var byCell : collectExposedByCell(sector).values()) {
            for (var mark : byCell) {
                total += mark.toAngle() - mark.fromAngle();
            }
        }
        return total;
    }

    private static List<List<double[]>> runsOf(String sector) {
        return LandableFrontages.collectLandableRuns(
            traceContinentCoast(sector), PARAMETERS.measureArcSegments());
    }

    private static List<DiscUnionBoundary.CoastMark> landableOf(String sector) {
        return LANDABLE.computeIfAbsent(sector, named ->
            LandableFrontages.collectLandableFrontages(
                traceContinentCoast(named), PARAMETERS.measureArcSegments()));
    }
}
