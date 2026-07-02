package kmu.politicalmap.domain.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link EdgeClassifier}: the core rule that only a same-faction pairing is
 * an interior seam, while differing owners, an unowned side, or a frontier into
 * empty space are boundaries.
 */
final class EdgeClassifierTest {

    @Nested
    class Classify {

        @Test
        void classifyReturnsInteriorSeamWhenBothSidesShareAnOwner() {
            assertThat(EdgeClassifier.classify("hegemony", "hegemony"))
                    .isEqualTo(EdgeClass.INTERIOR_SEAM);
        }

        @Test
        void classifyReturnsBoundaryForDifferentOwners() {
            assertThat(EdgeClassifier.classify("hegemony", "tritachyon"))
                    .isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyReturnsBoundaryWhenTheNeighbourIsUnowned() {
            // A null neighbour faction is unowned space or a frontier - no owner
            // to match, so the edge is a boundary.
            assertThat(EdgeClassifier.classify("hegemony", null)).isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyReturnsBoundaryWhenThisSideIsUnowned() {
            assertThat(EdgeClassifier.classify(null, "hegemony")).isEqualTo(EdgeClass.BOUNDARY);
        }

        @Test
        void classifyReturnsBoundaryWhenBothSidesAreUnowned() {
            // Two unowned cells do not fuse into a faction interior; an
            // unowned-to-unowned edge stays a boundary.
            assertThat(EdgeClassifier.classify(null, null)).isEqualTo(EdgeClass.BOUNDARY);
        }
    }
}
