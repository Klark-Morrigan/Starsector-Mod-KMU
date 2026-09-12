package kmu.maplayers.base.geometry;

import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segments;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.base.geometry.SectorPipeline.fillWater;
import static kmu.maplayers.base.geometry.SectorPipeline.traceLinkedShores;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the water the links enclose that no other layer paints.
 *
 * <p>The construction is the coast-pocket walk over the second trace plus one keep-rule, and the
 * keep-rule is the whole of what can be wrong. The walk hands back every pocket that trace's
 * reaches close, which is most of the sector; a pocket is this layer's only where nothing above
 * already fills it.
 *
 * <p>So the check asks what a kept pocket touches rather than how it was built. Two rules that
 * sounded necessary are not, and both were dropped on measurement: keeping a pocket whose closing
 * reach is new to the second trace kept water behind lines nobody draws, and holding a pocket
 * within a channel of a stroked line turned down pockets standing exactly one channel off one -
 * a threshold sitting where the geometry does, decided by floating point.
 *
 * <p>The disjointness check is the one that constrains the design rather than the arithmetic.
 * Every other layer overlaps its neighbours by construction, because no wall can be laid to keep
 * two apart; this one is chosen rather than walled, so it can be made to overlap nothing, and a
 * pocket that doubles another layer's water is dropped whole rather than trimmed.
 */
class LinkedSectorPocketsIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    @Nested
    class CollectLinkedSectorWater {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void keepsWaterOnEverySectorThatHasLinks(String sector) {

            assertThat(traceLinkedShores(sector))
                .as("no link reshaped the sector, so there is nothing for this layer to be about")
                .isNotEmpty();

            assertThat(keptPockets(sector, VoidPockets.PocketShaping.WITH_CHANNEL))
                .as("links reshaped the sector and nothing was found enclosed by it")
                .isNotEmpty();
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void overlapsNoOtherLayer(String sector) {

            for (var shaping : VoidPockets.PocketShaping.values()) {

                var others = gatherOtherLayers(sector, shaping);

                for (var pocket : keptPockets(sector, shaping)) {
                    for (var ring : others) {

                        assertThat(isOverlapping(pocket, ring))
                            .as("a pocket kept at %s doubles water another layer paints", shaping)
                            .isFalse();
                    }
                }
            }
        }
    }

    private static List<List<double[]>> keptPockets(
            String sector, VoidPockets.PocketShaping shaping) {

        return fillWater(sector, shaping).collectLinkedSectorWater();
    }

    private static List<List<double[]>> gatherOtherLayers(
            String sector, VoidPockets.PocketShaping shaping) {

        var water = fillWater(sector, shaping);
        var rings = new ArrayList<List<double[]>>();

        rings.addAll(water.collectShoreWater());
        rings.addAll(water.collectInletWater());
        rings.addAll(water.collectLakeWater());
        rings.addAll(water.collectPuddleWater());
        rings.addAll(water.collectLinkWater());

        return rings;
    }

    // Whether two rings share any area: either holding a point of the other, or crossing it with
    // no point held at all - which is how two shapes meet in a lens between four crossings.
    private static boolean isOverlapping(List<double[]> one, List<double[]> other) {

        for (var point : other) {

            if (PolygonRegions.isPointInsideRing(one, point[0], point[1])) {
                return true;
            }
        }

        for (var point : one) {

            if (PolygonRegions.isPointInsideRing(other, point[0], point[1])) {
                return true;
            }
        }

        for (var here = 1; here < one.size(); here++) {
            for (var there = 1; there < other.size(); there++) {

                if (Segments.intersectSegments(
                        one.get(here - 1), one.get(here),
                        other.get(there - 1), other.get(there)) != null) {

                    return true;
                }
            }
        }
        return false;
    }

}
