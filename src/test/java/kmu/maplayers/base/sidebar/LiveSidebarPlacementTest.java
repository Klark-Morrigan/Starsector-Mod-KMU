package kmu.maplayers.base.sidebar;

import kmlib.math.geometry.Rectangle;

import kmu.maplayers.base.layer.MapLayer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the intel overlay's anchor math: the box hangs from the visor's top-left corner (UI origin
 * bottom-left, so the visor's top edge is its y plus its height), pushed down by the top padding, flush to
 * the visor's left, and capped to the visor's bottom - all expressed as screen padding for the
 * top-left-anchored layout. The tab band each panel stands its row in is its host's, and pinned there.
 *
 * <p>And where the tab row's letters come from: each layer's own answer, taken as drawn text, so a layer
 * shipped by another mod letters its tab out of its own bundle.
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

            assertThat(padding.top())
                .isEqualTo(590);
        }

        @Test
        void computeIntelPaddingHangsTheBoxAtTheVisorTopWhenTheTopPaddingIsZero() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 0);

            assertThat(padding.top())
                .isEqualTo(550);
        }

        @Test
        void computeIntelPaddingSitsFlushAgainstTheVisorLeftEdgeAndGrowsRightward() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.left())
                .isEqualTo(100);
            assertThat(padding.right())
                .isZero();
        }

        @Test
        void computeIntelPaddingCapsTheBodyToTheVisorBottom() {

            var padding = LiveSidebarPlacement.computeIntelPadding(MAP_VISOR, 1200f, 40);

            assertThat(padding.bottom())
                .isEqualTo(50);
        }
    }

    @Nested
    class ResolveTabLabels {

        @Test
        void resolveTabLabelsLettersEachTabFromItsOwnLayersAnswer() {
            // The inversion this pins: the bar draws what the layer hands back and resolves no key of its
            // own, which is what lets a layer from another mod letter its tab out of a bundle KMU has no
            // reader for. Two layers, so a bar reading one fixed source would show one of them twice.
            var firstLayerMock = mock(MapLayer.class);
            var secondLayerMock = mock(MapLayer.class);

            when(firstLayerMock.resolveTabLabelText())
                .thenReturn("No Layer");
            when(secondLayerMock.resolveTabLabelText())
                .thenReturn("Trade Routes");

            assertThat(LiveSidebarPlacement.resolveTabLabels(List.of(firstLayerMock, secondLayerMock)))
                .containsExactly("No Layer", "Trade Routes");
        }

        @Test
        void resolveTabLabelsKeepsATabForALayerAnsweringABlankLabel() {
            // A layer whose text resolves to nothing - a missing bundle entry, or a player-set name
            // cleared - keeps its place in the row. Dropping it would take the tab away with it, leaving
            // the player no way back to a layer they can still be holding as their pick.
            var blankLabelLayerMock = mock(MapLayer.class);
            var labelledLayerMock = mock(MapLayer.class);

            when(blankLabelLayerMock.resolveTabLabelText())
                .thenReturn("");
            when(labelledLayerMock.resolveTabLabelText())
                .thenReturn("Political Map");

            assertThat(LiveSidebarPlacement.resolveTabLabels(List.of(blankLabelLayerMock, labelledLayerMock)))
                .containsExactly("", "Political Map");
        }
    }
}
