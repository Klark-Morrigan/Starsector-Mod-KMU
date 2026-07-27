package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the intel overlay's anchor math: the box hangs from the visor's top-left corner (UI origin
 * bottom-left, so the visor's top edge is its y plus its height), pushed down by the top padding, flush to
 * the visor's left, and capped to the visor's bottom - all expressed as screen padding for the
 * top-left-anchored layout. Also pins that the two screens keep distinct tab bands, the divergence the
 * whole injected-style seam exists to carry.
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

    @Nested
    class TabBandHeights {

        @Test
        void tabBandHeightsStandTheIntelBandShorterThanTheMapBand() {
            // The intel sidebar overlays the visor beneath the vanilla map toggles and reads tighter than the
            // on-map one, which floats free beside the Sector/System tabs. Wiring both screens to one height
            // would lay out and draw without complaint, so the divergence is pinned here rather than left to
            // be noticed on screen.
            assertThat(LiveSidebarPlacement.INTEL_HEADER_BAND_HEIGHT)
                    .isLessThan(LiveSidebarPlacement.MAP_HEADER_BAND_HEIGHT);
        }

        @Test
        void tabBandHeightsStandBothBandsTallEnoughToDraw() {
            // A band clamped to nothing would leave a panel with no tab row and no way to switch layer, so
            // neither screen may be configured down to a bandless header.
            assertThat(LiveSidebarPlacement.MAP_HEADER_BAND_HEIGHT).isPositive();
            assertThat(LiveSidebarPlacement.INTEL_HEADER_BAND_HEIGHT).isPositive();
        }
    }
}
