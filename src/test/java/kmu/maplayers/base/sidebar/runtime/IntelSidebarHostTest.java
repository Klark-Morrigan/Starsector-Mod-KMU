package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.BoxEdge;
import kmlib.math.geometry.Rectangle;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the intel overlay's gate and the frame edges it strokes. The gate is the map visor's rectangle
 * rather than the tab-open read, so the sidebar stays off the sub-tabs that share the intel tab. The
 * edges drop the borders shared with the visor - the left always (flush against the visor's left edge)
 * and the bottom only when the box reaches the visor's bottom - and keep the top and right, which sit
 * inside the visor.
 */
final class IntelSidebarHostTest {
    // A visor with its bottom edge at y = 50, so a box bottom at or within a pixel of 50 is flush with it.
    private static final Rectangle MAP_VISOR = new Rectangle(100f, 50f, 800f, 600f);

    @Nested
    class IsOverlayShowing {

        @Test
        void isOverlayShowingIsTrueWhileTheMapVisorIsLit() {
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(true);
            intelScreenFake.setMapVisorRect(MAP_VISOR);

            assertThat(new IntelSidebarHost(intelScreenFake).isOverlayShowing()).isTrue();
        }

        @Test
        void isOverlayShowingIsFalseWhenTheIntelTabIsUpWithNoMapVisor() {
            // The Planets and Factions sub-tabs are the same core tab carrying no visor, so the tab-open
            // read stays true while the rectangle goes away. Gating on the rectangle is what keeps the
            // sidebar off them; gating on the tab-open read would draw it over both.
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(true);
            intelScreenFake.setMapVisorRect(null);

            assertThat(new IntelSidebarHost(intelScreenFake).isOverlayShowing()).isFalse();
        }

        @Test
        void isOverlayShowingIsFalseWhenTheIntelTabIsNotShowing() {
            var intelScreenFake = new IntelScreenViewFake();
            intelScreenFake.setIntelTabOpen(false);

            assertThat(new IntelSidebarHost(intelScreenFake).isOverlayShowing()).isFalse();
        }
    }

    @Nested
    class DecideBorderEdges {

        @Test
        void decideBorderEdgesAlwaysDropsTheLeftEdge() {
            var floatingBox = IntelSidebarHost.decideBorderEdges(400f, MAP_VISOR);
            var flushBox = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y(), MAP_VISOR);

            assertThat(floatingBox).doesNotContain(BoxEdge.LEFT);
            assertThat(flushBox).doesNotContain(BoxEdge.LEFT);
        }

        @Test
        void decideBorderEdgesAlwaysKeepsTheTopAndRightEdges() {
            var edges = IntelSidebarHost.decideBorderEdges(400f, MAP_VISOR);

            assertThat(edges).contains(BoxEdge.TOP, BoxEdge.RIGHT);
        }

        @Test
        void decideBorderEdgesDropsTheBottomEdgeWhenTheBoxSitsOnTheVisorBottom() {
            var edges = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y(), MAP_VISOR);

            assertThat(edges).doesNotContain(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesDropsTheBottomEdgeWhenTheBoxIsWithinTheFlushTolerance() {
            // One pixel above the visor bottom still counts as flush, absorbing the padding's rounding.
            var edges = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y() + 1f, MAP_VISOR);

            assertThat(edges).doesNotContain(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesKeepsTheBottomEdgeWhenTheBoxFloatsClearOfTheVisorBottom() {
            // Ten pixels above the visor bottom: the box does not reach it, so the bottom border shows.
            var edges = IntelSidebarHost.decideBorderEdges(MAP_VISOR.y() + 10f, MAP_VISOR);

            assertThat(edges).contains(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesKeepsTheBottomEdgeWhenThereIsNoVisor() {
            var edges = IntelSidebarHost.decideBorderEdges(400f, null);

            assertThat(edges).contains(BoxEdge.BOTTOM);
        }
    }

    @Nested
    class LayoutBorderEdges {

        @Test
        void layoutBorderEdgesDropsTheLeftEdgeSoTheReservedStripCollapses() {
            // The box sits flush against the visor's left edge, so it reserves no left inset and the content
            // meets the visor rather than leaving a bare strip where the border would have been.
            assertThat(IntelSidebarHost.layoutBorderEdges()).doesNotContain(BoxEdge.LEFT);
        }

        @Test
        void layoutBorderEdgesKeepsTheTopRightAndBottomEdges() {
            // The top and right frame the sidebar inside the visor; the bottom keeps its reserved inset for
            // now (its stroke drops separately on flush, but collapsing the bottom strip is deferred).
            assertThat(IntelSidebarHost.layoutBorderEdges())
                    .contains(BoxEdge.TOP, BoxEdge.RIGHT, BoxEdge.BOTTOM);
        }

        @Test
        void layoutBorderEdgesDropsTheLeftEdgeInStepWithTheStroke() {
            // The reserved edges and the stroked edges must agree on the left, or the box would collapse the
            // left strip while still stroking the border there (or the reverse). Both drop it.
            assertThat(IntelSidebarHost.decideBorderEdges(400f, null))
                    .doesNotContain(BoxEdge.LEFT);
            assertThat(IntelSidebarHost.layoutBorderEdges()).doesNotContain(BoxEdge.LEFT);
        }
    }
}
