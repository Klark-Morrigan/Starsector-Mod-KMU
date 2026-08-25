package kmu.maplayers.base.sidebar.runtime;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.map.probes.VanillaMapTooltipProbe;
import kmlib.starsector.ui.widgets.tabs.TabPanelPlacement;
import kmlib.testfixtures.starsector.ui.coreui.CoreUiComponentRepainterFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the two decisions this pass makes that a GL context is not needed to reach: when it treats a panel's
 * fold as settled, which is what decides whether a fold is offered to the host's selection at all, and what
 * it does about a vanilla tooltip its own panel would otherwise bury.
 */
final class SidebarRendererTest {

    private static final float FULLY_DOCKED = 1f;
    private static final float FULLY_EXPANDED = 0f;

    // A drawn panel: a tab row standing on the body box, with the collapse handle past the box's right
    // edge. Chosen so each piece reaches past the others on some side, which is what makes the union of
    // the three a rect no single piece could have supplied.
    private static final Rectangle DRAWN_HEADER_BAND = new Rectangle(100f, 600f, 300f, 20f);
    private static final Rectangle BODY_BOX = new Rectangle(100f, 200f, 300f, 400f);
    private static final Rectangle NOTCH = new Rectangle(400f, 300f, 20f, 40f);

    @Nested
    class ResolveSettledFold {

        @Test
        void resolveSettledFoldReportsDockedOnceTheBodyReachesTheRail() {
            assertThat(SidebarRenderer.resolveSettledFold(FULLY_DOCKED, false))
                .isTrue();
        }

        @Test
        void resolveSettledFoldReportsOpenWhenTheBodyRestsFullyExpanded() {
            assertThat(SidebarRenderer.resolveSettledFold(FULLY_EXPANDED, true))
                .isFalse();
        }

        @Test
        void resolveSettledFoldReportsNothingMidFold() {
            // Halfway between the ends and resting at neither, there is no choice to record yet.
            assertThat(SidebarRenderer.resolveSettledFold(0.5f, false))
                .isNull();
        }

        @Test
        void resolveSettledFoldReportsNothingOnTheFirstFrameOfACollapse() {
            // The handle has just turned an open panel toward the rail: the fraction is still zero but the
            // panel no longer rests open, so recording here would write back the fold being left behind.
            assertThat(SidebarRenderer.resolveSettledFold(FULLY_EXPANDED, false))
                .isNull();
        }
    }

    @Nested
    class RepaintVanillaTooltipOverPanel {

        @Test
        void repaintVanillaTooltipOverPanelDrawsTheShownTooltipAgain() {

            var tooltipFake = new Object();
            var repainterFake = new CoreUiComponentRepainterFake();

            buildRenderer(tooltipFake, repainterFake)
                .repaintVanillaTooltipOverPanel(placeDrawnSidebar());

            assertThat(repainterFake.getRepaintedComponents())
                .containsExactly(tooltipFake);
        }

        @Test
        void repaintVanillaTooltipOverPanelClipsToTheWholePanelFootprint() {
            // The clip is the panel's outer bound, not the tooltip's own box: the part of the tooltip
            // outside the panel keeps vanilla's single draw, so nothing is composited twice. The rect
            // spans the row's top edge down to the box's bottom and out to the handle's far side.
            var repainterFake = new CoreUiComponentRepainterFake();

            buildRenderer(new Object(), repainterFake)
                .repaintVanillaTooltipOverPanel(placeDrawnSidebar());

            assertThat(repainterFake.getRepaintedRegions())
                .containsExactly(new Rectangle(100f, 200f, 320f, 420f));
        }

        @Test
        void repaintVanillaTooltipOverPanelDrawsNothingWithNoTooltipUp() {
            // The probe answers null both for "no tooltip" and for a read that broke, so this is also
            // what a failed probe costs: the panel draws as it always did and nothing is lifted.
            var repainterFake = new CoreUiComponentRepainterFake();

            buildRenderer(null, repainterFake)
                .repaintVanillaTooltipOverPanel(placeDrawnSidebar());

            assertThat(repainterFake.getRepaintedComponents())
                .isEmpty();
        }

        @Test
        void repaintVanillaTooltipOverPanelKeepsDrawingAfterAFailedRepaint() {
            // A repaint that throws must neither escape into the render pass - which would take the whole
            // panel away - nor latch the lift off for the session: the draw entry point can fail on one
            // frame's state, so the next frame tries again and only the log is silenced after the first.
            var repainterFake = new CoreUiComponentRepainterFake(new IllegalStateException("no draw"));
            var renderer = buildRenderer(new Object(), repainterFake);

            assertThatCode(() -> {
                renderer.repaintVanillaTooltipOverPanel(placeDrawnSidebar());
                renderer.repaintVanillaTooltipOverPanel(placeDrawnSidebar());
            }).doesNotThrowAnyException();

            assertThat(repainterFake.getRepaintedComponents())
                .hasSize(2);
        }

        // A renderer whose probe reports the given tooltip - null for none up - drawing through the given
        // repainter. The host plays no part in the repaint, so it is a bare mock.
        private SidebarRenderer buildRenderer(Object shownTooltip, CoreUiComponentRepainterFake repainterFake) {

            var vanillaMapTooltipProbeMock = mock(VanillaMapTooltipProbe.class);

            when(vanillaMapTooltipProbeMock.findShownTooltip())
                .thenReturn(shownTooltip);

            return new SidebarRenderer(mock(SidebarHost.class), vanillaMapTooltipProbeMock, repainterFake);
        }

        private TabPanelPlacement placeDrawnSidebar() {
            return SidebarPlacements.placeSidebarUnderDrawnRow(DRAWN_HEADER_BAND, BODY_BOX, NOTCH);
        }
    }
}
