package kmu.maplayers.base.hover.cover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the delegation the four flag-shaped covers share: what the reading says is what the cover
 * answers, with no cursor arranged at all. That last part is the point rather than a convenience -
 * these covers read no geometry, so whatever they stand for covers the map wherever the pointer
 * happens to be.
 *
 * <p>Pinned once here rather than once per cover. Each of the four used to carry its own pair of
 * cases proving this same line, which said nothing about the cover it was written under: what makes
 * one of them its own class is the reading it binds and why that reading is not its neighbour's, and
 * a supplier handed in by a test exercises neither.
 */
final class FlagMapCoverTest {

    @Nested
    class IsCoveringCursor {

        @Test
        void isCoveringCursorAnswersCoveredWhileTheReadingStands() {

            assertThat(new TestFlagMapCover(() -> true).isCoveringCursor())
                .isTrue();
        }

        @Test
        void isCoveringCursorAnswersUncoveredWhileItDoesNot() {
            // The ordinary case, and the one that keeps these from being hover switches: with
            // nothing standing the map hovers as it did before any of them existed.
            assertThat(new TestFlagMapCover(() -> false).isCoveringCursor())
                .isFalse();
        }

        @Test
        void isCoveringCursorAsksTheReadingEachTime() {
            // Nothing is settled at construction, so a cover built once answers a flag that moves.
            var standing = new boolean[] { false };
            var cover = new TestFlagMapCover(() -> standing[0]);

            assertThat(cover.isCoveringCursor()).isFalse();

            standing[0] = true;

            assertThat(cover.isCoveringCursor()).isTrue();
        }
    }

    // The base with nothing bound to it, there being no reading of its own to exercise.
    private static final class TestFlagMapCover extends FlagMapCover {

        private TestFlagMapCover(BooleanSupplier isCoverStanding) {
            super(isCoverStanding);
        }
    }
}
