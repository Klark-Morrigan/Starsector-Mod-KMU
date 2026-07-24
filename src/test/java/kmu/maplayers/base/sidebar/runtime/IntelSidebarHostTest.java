package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.render.gl.BoxEdge;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which frame edges the intel overlay strokes: it drops the borders it shares with the visor - the
 * left always (flush against the visor's left edge) and the bottom only when the box reaches the visor's
 * bottom - and keeps the top and right, which sit inside the visor.
 */
final class IntelSidebarHostTest {
    // A visor with its bottom edge at y = 50, so a box bottom at or within a pixel of 50 is flush with it.
    private static final Rectangle VISOR = new Rectangle(100f, 50f, 800f, 600f);

    @Nested
    class DecideBorderEdges {

        @Test
        void decideBorderEdgesAlwaysDropsTheLeftEdge() {
            var floatingBox = IntelSidebarHost.decideBorderEdges(400f, VISOR);
            var flushBox = IntelSidebarHost.decideBorderEdges(VISOR.y(), VISOR);

            assertThat(floatingBox).doesNotContain(BoxEdge.LEFT);
            assertThat(flushBox).doesNotContain(BoxEdge.LEFT);
        }

        @Test
        void decideBorderEdgesAlwaysKeepsTheTopAndRightEdges() {
            var edges = IntelSidebarHost.decideBorderEdges(400f, VISOR);

            assertThat(edges).contains(BoxEdge.TOP, BoxEdge.RIGHT);
        }

        @Test
        void decideBorderEdgesDropsTheBottomEdgeWhenTheBoxSitsOnTheVisorBottom() {
            var edges = IntelSidebarHost.decideBorderEdges(VISOR.y(), VISOR);

            assertThat(edges).doesNotContain(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesDropsTheBottomEdgeWhenTheBoxIsWithinTheFlushTolerance() {
            // One pixel above the visor bottom still counts as flush, absorbing the padding's rounding.
            var edges = IntelSidebarHost.decideBorderEdges(VISOR.y() + 1f, VISOR);

            assertThat(edges).doesNotContain(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesKeepsTheBottomEdgeWhenTheBoxFloatsClearOfTheVisorBottom() {
            // Ten pixels above the visor bottom: the box does not reach it, so the bottom border shows.
            var edges = IntelSidebarHost.decideBorderEdges(VISOR.y() + 10f, VISOR);

            assertThat(edges).contains(BoxEdge.BOTTOM);
        }

        @Test
        void decideBorderEdgesKeepsTheBottomEdgeWhenThereIsNoVisor() {
            var edges = IntelSidebarHost.decideBorderEdges(400f, null);

            assertThat(edges).contains(BoxEdge.BOTTOM);
        }
    }
}
