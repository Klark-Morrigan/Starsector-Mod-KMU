package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Segments;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static kmu.maplayers.base.geometry.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.SectorPipeline.loadFixture;
import static kmu.maplayers.base.geometry.SectorPipeline.traceLinkedCoasts;
import static kmu.maplayers.base.geometry.SectorPipeline.traceLinkedShores;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the water behind the coastline the links added, over real sectors.
 *
 * <p>The construction is the coast-pocket walk over the second trace plus one keep-rule, and the
 * keep-rule is the whole of what can be wrong. The walk hands back every pocket that trace's
 * reaches close, which is most of the sector; what makes one of them this layer's is that a
 * stretch the map actually strokes as linked shore runs along it.
 *
 * <p>So the checks ask where a kept pocket SITS rather than how it was built. A rule that sounds
 * equivalent - keep a pocket whose closing reach is new to the second trace - passes every test
 * about shape and fails this one: it kept fifty-five pockets on one fixture, a dozen of them more
 * than ten thousand units from any stroked run, which is water painted behind a line nobody draws.
 *
 * <p>Asked at both shapings, because the channel is what a pocket gives up against the line that
 * closed it, and the rule is stated in those terms.
 */
class LinkedShorePocketsIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    // How far a stroked run may pass from a pocket the layer kept. One channel is what a pocket
    // gives up against the wall that closed it, and the rule admits a pocket at exactly that - so
    // a little over it is the bound a correct keep-rule cannot exceed, and is well inside the ten
    // thousand the rejected rule reached.
    private static final double NEAREST_RUN_BOUND = 2 * 150.0;

    @Nested
    class FindLinkedShorePockets {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void keepsWaterOnEverySectorThatHasALinkedShore(String sector) {

            assertThat(traceLinkedShores(sector))
                .as("no linked shore is stroked, so there is nothing for this layer to be about")
                .isNotEmpty();

            assertThat(findPockets(sector, VoidPockets.PocketShaping.WITH_CHANNEL))
                .as("a stroked linked shore with no water behind any of it")
                .isNotEmpty();
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void keepsOnlyPocketsAStrokedRunRunsAlong(String sector) {

            var runs = traceLinkedShores(sector);

            for (var shaping : VoidPockets.PocketShaping.values()) {
                for (var pocket : findPockets(sector, shaping)) {

                    assertThat(measureNearestRun(pocket, runs))
                        .as("a pocket kept at %s with no stroked run along it", shaping)
                        .isLessThanOrEqualTo(NEAREST_RUN_BOUND);
                }
            }
        }
    }

    private static List<List<double[]>> findPockets(
            String sector, VoidPockets.PocketShaping shaping) {

        return LinkedShorePockets.findLinkedShorePockets(
            traceLinkedCoasts(sector),
            traceLinkedShores(sector),
            CoastPockets.markEverySiteUnowned(loadFixture(sector).getSites()),
            new VoidPockets.PocketRules(PARAMETERS, shaping));
    }

    // How near the closest stroked run passes to a pocket's outline. Against the runs' segments
    // rather than their sampled points, the way the rule itself asks: a coarsely sampled run
    // passes close to a pocket while its nearest POINT sits far along the line.
    private static double measureNearestRun(
            List<double[]> pocket, List<List<double[]>> runs) {

        var nearest = Double.MAX_VALUE;

        for (var point : pocket) {
            for (var run : runs) {
                for (var step = 1; step < run.size(); step++) {

                    nearest = Math.min(nearest, Segments.computeDistanceToPoint(
                        run.get(step - 1), run.get(step), point));
                }
            }
        }
        return nearest;
    }
}
