package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Bounds;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.geometry.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the water a continent trace finds inside its own coasts.
 *
 * <p>The walk hands the trace one run of marks per hole the cells close around, and every one of
 * them has to come back out as something the map knows about: a lake, which gets a drawn shore,
 * or a puddle, which gets filled whole. Those are the only two kinds, and between them they are
 * exhaustive by construction - water too small for a shore is a puddle, and so is water with no
 * room to place one.
 *
 * <p><b>The fault this exists to catch is water that becomes neither.</b> It is the worst shape a
 * missing fill can take, because nothing downstream can report it: a piece of map with no lake,
 * no puddle and no fill appears in no count at all, so every layer's own tally reads correct
 * while the map has a hole in it. Only a check against what the walk OFFERED can see it.
 *
 * <p>Asked of the walk directly rather than of any figure the trace keeps, since the two lists
 * have to come from different code for the comparison to mean anything.
 *
 * <p>The other half is what becomes of that water once it is drawn. A lake earns a shoreline and
 * concedes its middle to the pockets its own spans cut from it, so a lake no span crosses can end
 * up as a stroked ring with nothing inside it - a shape that tells a reader "water here" and then
 * shows them the backdrop.
 */
class CoastWaterIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    // How finely a lake's middle is sampled when asking whether anything paints it. Well inside
    // the smallest lake on either fixture, since what is being asked is whether the middle is
    // painted at all rather than how much of it is.
    private static final double SAMPLE_STEP = 200;

    @Nested
    class TraceContinentCoasts {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyRunOfWaterComesBackAsALakeOrAPuddle(String sector) {
            // Keyed on the cells each run passes over, which is the one thing a run and what it
            // became still have in common once the shore has been smoothed and the water edge
            // sampled.
            var traced = traceContinentCoast(sector);
            var kept = new ArrayList<Set<Integer>>();

            for (var lake : traced.lakes()) {
                kept.add(lake.ringCells());
            }
            for (var puddle : traced.puddles()) {
                kept.add(puddle.ringCells());
            }

            var lost = new ArrayList<Set<Integer>>();

            for (var run : offerWaterRuns(traced)) {
                if (!kept.contains(run)) {
                    lost.add(run);
                }
            }

            assertThat(lost)
                .as("%s: water the walk found that the trace kept neither kind of record of",
                    sector)
                .isEmpty();
        }
    }

    @Nested
    class CollectLakeWater {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyLakeHasSomethingPaintedInsideItsShore(String sector) {
            // Not how much of the middle is painted - the bands a fill gives up against the
            // shore and against each span are bare by construction, and counting those would
            // be measuring the channel. A lake with NOTHING inside its shore is the fault:
            // that is a drawn shoreline round a piece of backdrop.
            //
            // Both shapings, since a lake filled at one reach and empty at the other is
            // exactly the case a single reading hides.
            var empty = new ArrayList<String>();

            for (var shaping : VoidPockets.PocketShaping.values()) {
                for (var lake : findLakesWithNothingInside(sector, shaping)) {
                    empty.add(shaping + ": " + lake);
                }
            }

            assertThat(empty)
                .as("%s: a drawn lake shore with no water painted inside it", sector)
                .isEmpty();
        }
    }

    // Every lake whose drawn shore holds no painted water at all, named by where it sits.
    //
    // The lake margins are not among the layers asked, and cannot be: a margin is the band
    // between the drawn shore and the cells' edge, so it lies entirely OUTSIDE the ring being
    // sampled and would answer about water that is not the middle.
    private static List<String> findLakesWithNothingInside(
            String sector,
            VoidPockets.PocketShaping shaping) {
        var laying = SectorPipeline.layContinentsIn(sector);
        var painted = collectPaintedWater(SectorPipeline.fillWater(sector, shaping));
        var empty = new ArrayList<String>();

        for (var shore : laying.roundCoasts().lakes()) {
            var inside = 0;
            var covered = 0;

            for (var at : sampleInside(shore)) {
                inside++;

                if (isInsideAny(painted, at)) {
                    covered++;
                }
            }

            if (inside > 0 && covered == 0) {
                var middle = Points.computeMean(shore);

                empty.add(String.format(
                    "shore at %.0f,%.0f, %d samples inside it and none painted",
                    middle[0],
                    middle[1],
                    inside));
            }
        }
        return empty;
    }

    private static List<List<double[]>> collectPaintedWater(FilledWater water) {
        var rings = new ArrayList<List<double[]>>();

        rings.addAll(water.collectShoreWater());
        rings.addAll(water.collectInletWater());
        rings.addAll(water.collectLakeWater());
        rings.addAll(water.collectPuddleWater());
        rings.addAll(water.collectLinkWater());
        rings.addAll(water.collectLinkedSectorWater());

        return rings;
    }

    // The points of a grid that fall inside one ring, which is how a shape is asked about by
    // area rather than at its centroid - a lake bent round a cell has its mean point outside
    // itself.
    private static List<double[]> sampleInside(List<double[]> ring) {
        var bounds = Bounds.computeEnclosingBounds(ring);
        var inside = new ArrayList<double[]>();

        for (var x = bounds.minX(); x <= bounds.maxX(); x += SAMPLE_STEP) {
            for (var y = bounds.minY(); y <= bounds.maxY(); y += SAMPLE_STEP) {
                if (PolygonRegions.isPointInsideRing(ring, x, y)) {
                    inside.add(new double[] {x, y});
                }
            }
        }
        return inside;
    }

    private static boolean isInsideAny(List<List<double[]>> rings, double[] at) {
        for (var ring : rings) {
            if (PolygonRegions.isPointInsideRing(ring, at[0], at[1])) {
                return true;
            }
        }
        return false;
    }

    // The cells each run of water passes over, off the walk the trace itself runs. Walked again
    // here rather than read off the trace, because what is being asked is whether the trace kept
    // everything it was handed - and a list taken from its output cannot answer that.
    private static List<Set<Integer>> offerWaterRuns(Coastlines.TracedCoasts traced) {
        var runs = new ArrayList<Set<Integer>>();

        for (var run : DiscUnionBoundary.traceCoastRuns(
                traced.union(),
                DiscUnionBoundary.Walls.NONE,
                PARAMETERS.boundSegments()).lakes()) {

            var cells = new LinkedHashSet<Integer>();

            for (var mark : run) {
                cells.add(mark.circle());
            }
            runs.add(cells);
        }
        return runs;
    }
}
