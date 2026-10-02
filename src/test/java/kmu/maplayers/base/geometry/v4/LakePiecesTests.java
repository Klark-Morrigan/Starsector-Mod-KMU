package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

import kmu.maplayers.base.geometry.SectorFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit coverage for sorting a partition's pieces into the lake tier's two kinds.
 *
 * <p>Which kind a piece is, is {@link LakeTierTests}' subject; what is pinned here is the
 * bookkeeping over a list: each piece the tier touched lands in exactly one list, a piece it
 * never touched lands in none, and every piece that lands is named. The squares are those of
 * {@link LakeTierTests} - the two halves of one square a reach runs across, and a third piece
 * on cells no line touches.
 */
class LakePiecesTests {

    // Any fixture with four cells, for the sites and system IDs a name is built from; the
    // pieces below are labelled with its first four cells.
    private static final SectorFixture FIXTURE =
        SectorPartitions.loadFixture(SectorFixture.listSectorNames().get(0));

    // A coast wall across the square's middle, running east, so the upper half is the lake.
    private static final List<LabelledWall> EASTWARD_COAST =
        List.of(new LabelledWall(new Segment(0, 50, 100, 50), LakeTier.THE_LAKE_COAST));

    private static final Face UPPER_HALF = Face.encloseFace(new LabelledRing(
        List.of(
            new double[] {0, 50},
            new double[] {100, 50},
            new double[] {100, 100},
            new double[] {0, 100}),
        new int[] {LakeTier.THE_LAKE_COAST, 1, 2, 3}));

    private static final Face LOWER_HALF = Face.encloseFace(new LabelledRing(
        List.of(
            new double[] {0, 0},
            new double[] {100, 0},
            new double[] {100, 50},
            new double[] {0, 50}),
        new int[] {0, 1, LakeTier.THE_LAKE_COAST, 3}));

    // Off to one side and on cells only, as a puddle is.
    private static final Face ELSEWHERE = Face.encloseFace(new LabelledRing(
        List.of(
            new double[] {300, 0},
            new double[] {400, 0},
            new double[] {400, 100},
            new double[] {300, 100}),
        new int[] {0, 1, 2, 1}));

    private static final List<Set<Integer>> NO_LAKES = List.of();

    @Nested
    class CollectLakePieces {

        @Test
        void eachPieceTheTierTouchedLandsInItsOwnKindsList() {

            var sorted = LakePieces.collectLakePieces(
                List.of(LOWER_HALF, ELSEWHERE, UPPER_HALF), EASTWARD_COAST, NO_LAKES, FIXTURE);

            assertThat(sorted.water())
                .containsExactly(UPPER_HALF);
            assertThat(sorted.margin())
                .containsExactly(LOWER_HALF);
        }

        @Test
        void everyPieceThatLandsIsNamedUnderItsKindsPrefix() {
            // In the order the pieces came: the margin first, then the lake; the untouched
            // piece between them has no name.
            var sorted = LakePieces.collectLakePieces(
                List.of(LOWER_HALF, ELSEWHERE, UPPER_HALF), EASTWARD_COAST, NO_LAKES, FIXTURE);

            assertThat(sorted.names())
                .hasSize(2);
            assertThat(sorted.names().get(0).name())
                .startsWith(LakeTier.Kind.MARGIN.keyPrefix() + "--");
            assertThat(sorted.names().get(1).name())
                .startsWith(LakeTier.Kind.LAKE.keyPrefix() + "--");
        }

        @Test
        void aPieceNoLineTouchesIsTheLakeWhenItsRingIsALakes() {

            var sorted = LakePieces.collectLakePieces(
                List.of(ELSEWHERE), List.of(), List.of(Set.of(0, 1, 2)), FIXTURE);

            assertThat(sorted.water())
                .containsExactly(ELSEWHERE);
        }
    }
}
