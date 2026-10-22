package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.ui.label.AspectLabelLengthEstimator;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a cluster's shade and its name measurement are looked up under the one owner key its
 * identity names. Two owners answer differently on both counts here, so a subject that took either
 * half from the other owner reads as a different value.
 */
final class ClusterLabelResolversTests {

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";

    // Two shapes no two owners share, so an estimator taken from the wrong owner is unequal.
    private static final AspectLabelLengthEstimator HEGEMONY_ESTIMATOR = new AspectLabelLengthEstimator(4.0);
    private static final AspectLabelLengthEstimator TRITACHYON_ESTIMATOR = new AspectLabelLengthEstimator(9.0);

    @Nested
    class ResolveLabelSubjectFor {

        @Test
        void shadesAndMeasuresTheClusterByItsOwnersKey() {

            var resolvers = new ClusterLabelResolvers(
                Map.of(HEGEMONY, Color.RED, TRITACHYON, Color.BLUE)::get,
                Map.of(HEGEMONY, HEGEMONY_ESTIMATOR, TRITACHYON, TRITACHYON_ESTIMATOR)::get);

            var identity = new ClusterIdentity(HEGEMONY, Set.of(buildCellKey("corvus")));
            var expectedSubject = new ClusterLabelSubject(identity, Color.RED, HEGEMONY_ESTIMATOR);
            var actualSubject = resolvers.resolveLabelSubjectFor(identity);

            assertThat(actualSubject)
                .isEqualTo(expectedSubject);
        }
    }
}
