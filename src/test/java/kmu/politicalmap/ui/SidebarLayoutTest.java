package kmu.politicalmap.ui;

import kmu.settings.SidebarAnchorChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link SidebarLayout}: each anchor lands the box against the right screen edges with
 * a uniform margin, and the tab is the box's top strip - so the strip the renderer draws
 * is the strip the click hit-test reads.
 */
final class SidebarLayoutTest {
    private static final float SCREEN_WIDTH = 1920f;
    private static final float SCREEN_HEIGHT = 1080f;
    private static final float TOLERANCE = 0.01f;

    // The far origin an anchor pulls toward: the box's low corner when pushed to the high
    // edge, one margin in from the screen's far side.
    private static final float FAR_X =
            SCREEN_WIDTH - SidebarLayout.BOX_WIDTH - SidebarLayout.EDGE_MARGIN;
    private static final float FAR_Y =
            SCREEN_HEIGHT - SidebarLayout.BOX_HEIGHT - SidebarLayout.EDGE_MARGIN;
    private static final float MID_X = (SidebarLayout.EDGE_MARGIN + FAR_X) / 2f;
    private static final float MID_Y = (SidebarLayout.EDGE_MARGIN + FAR_Y) / 2f;

    @Nested
    class ComputePlacement {

        @Test
        void computePlacementPinsEachCornerAgainstItsEdges() {
            assertBoxOrigin(SidebarAnchorChoice.TOP_LEFT, SidebarLayout.EDGE_MARGIN, FAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.TOP_RIGHT, FAR_X, FAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.BOTTOM_LEFT,
                    SidebarLayout.EDGE_MARGIN, SidebarLayout.EDGE_MARGIN);
            assertBoxOrigin(SidebarAnchorChoice.BOTTOM_RIGHT, FAR_X, SidebarLayout.EDGE_MARGIN);
        }

        @Test
        void computePlacementCentersEdgeMidpointsOnTheirFreeAxis() {
            assertBoxOrigin(SidebarAnchorChoice.TOP_CENTER, MID_X, FAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.BOTTOM_CENTER, MID_X, SidebarLayout.EDGE_MARGIN);
            assertBoxOrigin(SidebarAnchorChoice.LEFT_CENTER, SidebarLayout.EDGE_MARGIN, MID_Y);
            assertBoxOrigin(SidebarAnchorChoice.RIGHT_CENTER, FAR_X, MID_Y);
        }

        @Test
        void computePlacementKeepsTheTabAsTheBoxTopStrip() {
            var placement = SidebarLayout.computePlacement(SCREEN_WIDTH, SCREEN_HEIGHT,
                    SidebarAnchorChoice.TOP_LEFT);
            var box = placement.box();
            var tab = placement.tab();
            assertThat(tab.x()).isEqualTo(box.x());
            assertThat(tab.width()).isEqualTo(box.width());
            assertThat(tab.height()).isEqualTo(SidebarLayout.TAB_HEIGHT);
            // The strip caps the box: its top edge is the box's top edge.
            assertThat(tab.y() + tab.height()).isCloseTo(box.y() + box.height(), within(TOLERANCE));
        }

        private void assertBoxOrigin(SidebarAnchorChoice anchor, float expectedX, float expectedY) {
            var box = SidebarLayout.computePlacement(SCREEN_WIDTH, SCREEN_HEIGHT, anchor).box();
            assertThat(box.x()).isCloseTo(expectedX, within(TOLERANCE));
            assertThat(box.y()).isCloseTo(expectedY, within(TOLERANCE));
        }
    }
}
