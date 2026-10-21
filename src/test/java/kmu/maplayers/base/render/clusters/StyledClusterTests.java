package kmu.maplayers.base.render.clusters;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the boundary a cluster reports as one set: the outer loop first and each enclave after it,
 * and nothing at all when the outer loop is empty - enclaves included, since a cluster that strokes
 * nothing has no boundary to hit-test or measure.
 */
final class StyledClusterTests {

    private static final float[] NO_FILL = {};
    private static final float[] NO_HATCH = {};
    private static final float[] NO_OUTER_LOOP = {};

    private static final float[] OUTER_LOOP = {0f, 0f, 10f, 0f, 10f, 10f, 0f, 10f};
    private static final float[] ENCLAVE_LOOP = {4f, 4f, 6f, 4f, 6f, 6f, 4f, 6f};

    @Nested
    class ListLoops {

        @Test
        void returnsTheOuterLoopFirstThenEachEnclave() {

            var cluster = new StyledCluster(NO_FILL, NO_HATCH, OUTER_LOOP, List.of(ENCLAVE_LOOP));

            assertThat(cluster.listLoops())
                .containsExactly(OUTER_LOOP, ENCLAVE_LOOP);
        }

        @Test
        void returnsTheOuterLoopAloneForAClusterWithNoEnclave() {

            var cluster = new StyledCluster(NO_FILL, NO_HATCH, OUTER_LOOP, List.of());

            assertThat(cluster.listLoops())
                .containsExactly(OUTER_LOOP);
        }

        @Test
        void returnsNothingWhenTheOuterLoopIsEmpty() {

            // An empty outer loop means nothing strokes, so enclave loops left beside it do not
            // stand in for a boundary of their own.
            var cluster = new StyledCluster(NO_FILL, NO_HATCH, NO_OUTER_LOOP, List.of(ENCLAVE_LOOP));

            assertThat(cluster.listLoops())
                .isEmpty();
        }
    }
}
