package kmu.maplayers.base.sidebar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins the sidebar's scroll-position holder: {@link SidebarScrollState#scrollBy} accumulates a raw
 * request and {@link SidebarScrollState#clampTo} settles it into the list's real range each frame, so a
 * wheel past the bottom or a list that shrank cannot leave the stored offset drifting outside what can be
 * scrolled. The shared static is reset before each test so the cases do not leak into one another.
 */
final class SidebarScrollStateTest {
    private static final float TOLERANCE = 0.01f;

    @BeforeEach
    void resetSharedState() {
        SidebarScrollState.reset();
    }

    @Nested
    class ScrollBy {

        @Test
        void scrollByAddsToTheOffset() {
            SidebarScrollState.scrollBy(30f);
            assertThat(SidebarScrollState.getOffset()).isCloseTo(30f, within(TOLERANCE));
        }

        @Test
        void scrollByAccumulatesAcrossCalls() {
            // A run of notches sums, so repeated scrolling walks the list rather than jumping to a
            // single position.
            SidebarScrollState.scrollBy(30f);
            SidebarScrollState.scrollBy(-10f);
            assertThat(SidebarScrollState.getOffset()).isCloseTo(20f, within(TOLERANCE));
        }
    }

    @Nested
    class ClampTo {

        @Test
        void clampToHoldsTheOffsetWithinTheOverflow() {
            // A request past the list's bottom settles at the overflow, so the stored value tracks what
            // can be scrolled rather than drifting far below the last row.
            SidebarScrollState.scrollBy(500f);
            SidebarScrollState.clampTo(100f);
            assertThat(SidebarScrollState.getOffset()).isCloseTo(100f, within(TOLERANCE));
        }

        @Test
        void clampToFloorsANegativeOffsetAtTheTop() {
            SidebarScrollState.scrollBy(-50f);
            SidebarScrollState.clampTo(100f);
            assertThat(SidebarScrollState.getOffset()).isZero();
        }

        @Test
        void clampToCollapsesToTheTopWhenNothingOverflows() {
            // A list that now fits (overflow 0) pulls the stored offset back to the top, so a shrunk
            // list does not stay scrolled into blank space.
            SidebarScrollState.scrollBy(40f);
            SidebarScrollState.clampTo(0f);
            assertThat(SidebarScrollState.getOffset()).isZero();
        }
    }
}
