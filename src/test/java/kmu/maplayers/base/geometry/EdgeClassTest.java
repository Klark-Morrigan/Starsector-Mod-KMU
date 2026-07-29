package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link EdgeClass#isBoundary()}: the fold that lets a consumer which only cares
 * seam-or-border treat an open frontier exactly like a plain boundary, so the finer
 * OPEN_FRONTIER class stays inert until a consumer acts on it.
 */
final class EdgeClassTest {

    @Nested
    class IsBoundary {

        @Test
        void isBoundaryIsFalseForAnInteriorSeam() {
            assertThat(EdgeClass.INTERIOR_SEAM.isBoundary()).isFalse();
        }

        @Test
        void isBoundaryIsTrueForAPlainBoundary() {
            assertThat(EdgeClass.BOUNDARY.isBoundary()).isTrue();
        }

        @Test
        void isBoundaryIsTrueForAnOpenFrontier() {
            // An open frontier still draws as a border, so a seam-or-border consumer
            // folds it in with BOUNDARY.
            assertThat(EdgeClass.OPEN_FRONTIER.isBoundary()).isTrue();
        }
    }
}
