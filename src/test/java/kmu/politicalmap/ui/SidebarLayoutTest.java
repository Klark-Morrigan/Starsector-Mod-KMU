package kmu.politicalmap.ui;

import kmu.politicalmap.layer.FactionsLayer;
import kmu.politicalmap.layer.NoLayer;
import kmu.politicalmap.layer.PoliticalMapLayer;
import kmu.settings.SidebarAnchorChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pins {@link SidebarLayout}: each anchor lands the bar against the right screen edges with a
 * uniform margin, the tabs lay left to right across the top strip, and the caption is the
 * bottom strip - so the tabs the renderer draws are the tabs the click hit-test reads.
 */
final class SidebarLayoutTest {
    private static final float SCREEN_WIDTH = 1920f;
    private static final float SCREEN_HEIGHT = 1080f;
    private static final float TOLERANCE = 0.01f;

    // Two layers, so the row is two tabs wide - the shipped registry's shape, stated
    // explicitly here so the geometry math does not depend on the registry's contents.
    private static final List<PoliticalMapLayer> LAYERS =
            List.of(new NoLayer(), new FactionsLayer());
    private static final float STRIP_WIDTH = SidebarLayout.TAB_WIDTH * LAYERS.size();
    private static final float STRIP_HEIGHT = SidebarLayout.TAB_HEIGHT + SidebarLayout.CAPTION_HEIGHT;

    // The safe region's low corner: one edge margin in from the left, and the bottom chrome
    // band plus margin up from the bottom, since the bar must clear the filter row and nav bar.
    private static final float NEAR_X = SidebarLayout.EDGE_MARGIN;
    private static final float NEAR_Y = SidebarLayout.EDGE_MARGIN + SidebarLayout.BOTTOM_CHROME_BAND;
    // The safe region's far corner: margin in from the right, and margin plus the top chrome
    // band down from the top so the bar's top edge clears the tab strip.
    private static final float FAR_X = SCREEN_WIDTH - STRIP_WIDTH - SidebarLayout.EDGE_MARGIN;
    private static final float FAR_Y =
            SCREEN_HEIGHT - SidebarLayout.EDGE_MARGIN - SidebarLayout.TOP_CHROME_BAND - STRIP_HEIGHT;
    private static final float MID_X = (NEAR_X + FAR_X) / 2f;
    private static final float MID_Y = (NEAR_Y + FAR_Y) / 2f;

    @Nested
    class ComputePlacement {

        @Test
        void computePlacementPinsEachCornerAgainstItsSafeRegionEdges() {
            assertBoxOrigin(SidebarAnchorChoice.TOP_LEFT, NEAR_X, FAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.TOP_RIGHT, FAR_X, FAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.BOTTOM_LEFT, NEAR_X, NEAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.BOTTOM_RIGHT, FAR_X, NEAR_Y);
        }

        @Test
        void computePlacementCentersEdgeMidpointsOnTheirFreeAxis() {
            assertBoxOrigin(SidebarAnchorChoice.TOP_CENTER, MID_X, FAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.BOTTOM_CENTER, MID_X, NEAR_Y);
            assertBoxOrigin(SidebarAnchorChoice.LEFT_CENTER, NEAR_X, MID_Y);
            assertBoxOrigin(SidebarAnchorChoice.RIGHT_CENTER, FAR_X, MID_Y);
        }

        @Test
        void computePlacementKeepsEveryAnchorClearOfTheMapChromeBands() {
            // Whatever the player picks, the whole bar stays out of the top tab-strip band and
            // the bottom filter/nav band, so it never covers the map's own controls.
            for (var anchor : SidebarAnchorChoice.values()) {
                var box = SidebarLayout
                        .computePlacement(SCREEN_WIDTH, SCREEN_HEIGHT, anchor, LAYERS)
                        .box();
                assertThat(box.y())
                        .as("bottom edge for %s clears the bottom chrome band", anchor)
                        .isGreaterThanOrEqualTo(SidebarLayout.BOTTOM_CHROME_BAND - TOLERANCE);
                assertThat(box.y() + box.height())
                        .as("top edge for %s clears the top chrome band", anchor)
                        .isLessThanOrEqualTo(
                                SCREEN_HEIGHT - SidebarLayout.TOP_CHROME_BAND + TOLERANCE);
            }
        }

        @Test
        void computePlacementLaysEqualTabsLeftToRightAcrossTheTopStrip() {
            var placement = placeTopLeft();
            var box = placement.box();
            var tabs = placement.tabs();
            assertThat(tabs).hasSize(LAYERS.size());
            for (var index = 0; index < tabs.size(); index++) {
                var bounds = tabs.get(index).bounds();
                assertThat(bounds.x())
                        .isCloseTo(box.x() + index * SidebarLayout.TAB_WIDTH, within(TOLERANCE));
                assertThat(bounds.width()).isEqualTo(SidebarLayout.TAB_WIDTH);
                assertThat(bounds.height()).isEqualTo(SidebarLayout.TAB_HEIGHT);
                // The tab row caps the bar: each tab's top edge is the bar's top edge.
                assertThat(bounds.y() + bounds.height())
                        .isCloseTo(box.y() + box.height(), within(TOLERANCE));
            }
        }

        @Test
        void computePlacementPairsEachTabWithItsLayerInOrder() {
            var tabs = placeTopLeft().tabs();
            for (var index = 0; index < LAYERS.size(); index++) {
                assertThat(tabs.get(index).layer()).isSameAs(LAYERS.get(index));
            }
        }

        @Test
        void computePlacementKeepsTheCaptionAsTheBottomStrip() {
            var placement = placeTopLeft();
            var box = placement.box();
            var caption = placement.caption();
            assertThat(caption.x()).isEqualTo(box.x());
            assertThat(caption.y()).isEqualTo(box.y());
            assertThat(caption.width()).isCloseTo(STRIP_WIDTH, within(TOLERANCE));
            assertThat(caption.height()).isEqualTo(SidebarLayout.CAPTION_HEIGHT);
        }

        private SidebarPlacement placeTopLeft() {
            return SidebarLayout.computePlacement(SCREEN_WIDTH, SCREEN_HEIGHT,
                    SidebarAnchorChoice.TOP_LEFT, LAYERS);
        }

        private void assertBoxOrigin(SidebarAnchorChoice anchor, float expectedX, float expectedY) {
            var box = SidebarLayout.computePlacement(SCREEN_WIDTH, SCREEN_HEIGHT, anchor, LAYERS)
                    .box();
            assertThat(box.x()).isCloseTo(expectedX, within(TOLERANCE));
            assertThat(box.y()).isCloseTo(expectedY, within(TOLERANCE));
        }
    }
}
