package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.SegmentSizing;

import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the columns selector: a two-segment horizontal radio with one segment per column choice in
 * the choice's own order, lit on the active count, trailed by the "Columns" caption. Clicking a
 * segment persists that count. Strings and the column selection are stubbed so this pins the
 * selector's shape and wiring alone.
 */
final class ColumnsSelectorControlTest {

    // The choice segments in the order the selector lays them out, so a test maps a segment index
    // back to a choice.
    private static final List<ListColumns> CHOICES = List.of(ListColumns.values());

    @Nested
    class BuildSelector {

        @Test
        void buildSelectorBuildsATwoSegmentHorizontalRadio() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubColumnLabels(stringsMock);

                var selector = ColumnsSelectorControl.buildSelector(ListColumns.ONE);

                // A horizontal radio by type; its even-cell segments read the default UNIFORM sizing.
                assertThat(selector.segmentSizing())
                    .isEqualTo(SegmentSizing.UNIFORM);
                assertThat(selector.labels())
                    .containsExactly("1", "2");
            }
        }

        @Test
        void buildSelectorLightsTheActiveChoicesSegment() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubColumnLabels(stringsMock);

                var selector = ColumnsSelectorControl.buildSelector(ListColumns.TWO);

                assertThat(selector.selectedIndex())
                    .isEqualTo(CHOICES.indexOf(ListColumns.TWO));
            }
        }
    }

    @Nested
    class ApplySelection {

        @Test
        void clickingASegmentPersistsThatColumnCount() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<ColumnSelection> selectionMock = mockStatic(ColumnSelection.class)) {
                stubColumnLabels(stringsMock);
                var selector = ColumnsSelectorControl.buildSelector(ListColumns.ONE);
                var twoSegment = CHOICES.indexOf(ListColumns.TWO);

                selector.action().activateCell(twoSegment);

                selectionMock
                    .verify(() -> ColumnSelection.selectColumnCount(ListColumns.TWO.persistenceKey()));
            }
        }

        @Test
        void clickingOutsideTheSegmentsChangesNothing() {
            // A stray hit past the last segment names no choice, so it is ignored rather than
            // persisting a phantom count.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<ColumnSelection> selectionMock = mockStatic(ColumnSelection.class)) {
                stubColumnLabels(stringsMock);
                var selector = ColumnsSelectorControl.buildSelector(ListColumns.ONE);

                selector.action().activateCell(CHOICES.size());

                selectionMock
                    .verifyNoInteractions();
            }
        }
    }

    // Stubs the caption and segment-label strings the selector draws, so the assertions read the
    // wiring without the live strings table.
    private static void stubColumnLabels(MockedStatic<KmuStrings> stringsMock) {

        stringsMock
            .when(() -> KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_CAPTION))
            .thenReturn("Columns");

        stringsMock
            .when(() -> KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_ONE))
            .thenReturn("1");

        stringsMock
            .when(() -> KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_TWO))
            .thenReturn("2");
    }
}
