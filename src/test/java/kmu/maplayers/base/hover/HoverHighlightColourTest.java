package kmu.maplayers.base.hover;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.base.theme.HoverHighlightStyle;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the shade one hover burns in: the layer's own answer for the hovered cell under the tier's
 * selection, and nothing at all when the cursor is over no cell or the selection paints none - the
 * two ways the pass is skipped outright.
 */
final class HoverHighlightColourTest {

    // The cell the cursor rests on, which is the only one the layer is ever asked about.
    private static final SystemKey HOVERED_CELL_KEY = buildCellKey("system_a");

    // A cell in the hovered cluster but not under the cursor, so a resolve reading the cluster
    // rather than the cell would be visible.
    private static final SystemKey CLUSTER_MEMBER_CELL_KEY = buildCellKey("system_b");

    @Nested
    class ResolveColourFor {

        @Test
        void resolveColourForAnswersTheLayersShadeForTheHoveredCell() {

            var sourceMock = mock(HoverHighlightSource.class);
            var selectionMock = mock(ElementPaintSelection.class);

            when(sourceMock.resolveHighlightColourOf(HOVERED_CELL_KEY, selectionMock))
                .thenReturn(Color.RED);

            var colour = HoverHighlightColour.resolveColourFor(
                sourceMock,
                new MapHover(HOVERED_CELL_KEY, List.of(HOVERED_CELL_KEY, CLUSTER_MEMBER_CELL_KEY)),
                buildStyleSelecting(selectionMock));

            assertThat(colour)
                .isEqualTo(Color.RED);
        }

        @Test
        void resolveColourForIsNullWhenNothingIsHovered() {
            // A parked hover - which is also how a switched-off highlight reads - names no cell, so
            // there is nothing to ask the layer about and the pass is skipped.
            var sourceMock = mock(HoverHighlightSource.class);

            var colour = HoverHighlightColour.resolveColourFor(
                sourceMock,
                MapHover.NONE,
                buildStyleSelecting(mock(ElementPaintSelection.class)));

            assertThat(colour)
                .isNull();
            verify(sourceMock, never())
                .resolveHighlightColourOf(any(), any());
        }

        @Test
        void resolveColourForIsNullWhenTheSelectionPaintsNothing() {
            // The layer answers nothing for a selection that paints nothing, and that answer is
            // carried through rather than substituted for, so the pass skips on it too.
            var sourceMock = mock(HoverHighlightSource.class);
            when(sourceMock.resolveHighlightColourOf(any(), any()))
                .thenReturn(null);

            var colour = HoverHighlightColour.resolveColourFor(
                sourceMock,
                new MapHover(HOVERED_CELL_KEY, List.of(HOVERED_CELL_KEY)),
                buildStyleSelecting(null));

            assertThat(colour)
                .isNull();
        }
    }

    // The tier as the resolve reads it: the selection alone. The halo and the wash are the paint
    // pass's, and no resolve here touches them.
    private static HoverHighlightStyle buildStyleSelecting(ElementPaintSelection selection) {
        return new HoverHighlightStyle(selection, null, null);
    }
}
