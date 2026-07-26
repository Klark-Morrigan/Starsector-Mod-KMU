package kmu.maplayers.base.sidebar.runtime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins when the renderer treats a panel's fold as settled, which is what decides whether a fold is offered
 * to the host's selection at all. Only the two resting ends count: while the panel is folding, the fold it
 * is heading for is not yet the player's committed choice.
 */
final class SidebarRendererTest {
    private static final float FULLY_DOCKED = 1f;
    private static final float FULLY_EXPANDED = 0f;

    @Nested
    class ResolveSettledFold {

        @Test
        void resolveSettledFoldReportsDockedOnceTheBodyReachesTheRail() {
            assertThat(SidebarRenderer.resolveSettledFold(FULLY_DOCKED, false)).isTrue();
        }

        @Test
        void resolveSettledFoldReportsOpenWhenTheBodyRestsFullyExpanded() {
            assertThat(SidebarRenderer.resolveSettledFold(FULLY_EXPANDED, true)).isFalse();
        }

        @Test
        void resolveSettledFoldReportsNothingMidFold() {
            // Halfway between the ends and resting at neither, there is no choice to record yet.
            assertThat(SidebarRenderer.resolveSettledFold(0.5f, false)).isNull();
        }

        @Test
        void resolveSettledFoldReportsNothingOnTheFirstFrameOfACollapse() {
            // The handle has just turned an open panel toward the rail: the fraction is still zero but the
            // panel no longer rests open, so recording here would write back the fold being left behind.
            assertThat(SidebarRenderer.resolveSettledFold(FULLY_EXPANDED, false)).isNull();
        }
    }
}
