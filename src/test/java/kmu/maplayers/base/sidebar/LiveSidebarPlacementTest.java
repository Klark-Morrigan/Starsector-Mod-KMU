package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the intel overlay's anchor math: the box hangs from the visor's top-left corner (UI origin
 * bottom-left, so the visor's top edge is its y plus its height), pushed down by the top padding, flush to
 * the visor's left, and capped to the visor's bottom - all expressed as screen padding for the
 * top-left-anchored layout. The anchor is the whole of what this class decides per screen; the tab band
 * each panel stands its row in is its host's, and pinned there.
 */
final class LiveSidebarPlacementTest {
    // A visor whose left edge is x = 100, bottom edge y = 50, and top edge y + height = 650.
    private static final Rectangle MAP_VISOR = new Rectangle(100f, 50f, 800f, 600f);

    @Nested
    class ComputeIntelPadding {

        @Test
        void computeIntelPaddingHangsTheBoxFromTheVisorTopPushedDownByTheTopPadding() {
            // Visor top edge = 650; screen 1200 tall; the box top sits 40px below the visor top, so its
            // distance from the screen top is (1200 - 650) + 40 = 590.
            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.top()).isEqualTo(590);
        }

        @Test
        void computeIntelPaddingHangsTheBoxAtTheVisorTopWhenTheTopPaddingIsZero() {
            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 0);

            assertThat(padding.top()).isEqualTo(550);
        }

        @Test
        void computeIntelPaddingSitsFlushAgainstTheVisorLeftEdgeAndGrowsRightward() {
            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.left()).isEqualTo(100);
            assertThat(padding.right()).isZero();
        }

        @Test
        void computeIntelPaddingCapsTheBodyToTheVisorBottom() {
            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.bottom()).isEqualTo(50);
        }
    }
}
