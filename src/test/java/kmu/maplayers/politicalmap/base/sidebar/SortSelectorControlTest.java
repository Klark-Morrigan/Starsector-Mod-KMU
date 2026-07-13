package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlKind;
import kmlib.starsector.ui.controls.RadioAlignment;

import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.maplayers.politicalmap.base.refresh.SortSelection;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the sort selector: a vertical, always-selected radio with one row per sort mode in the mode's
 * own order, lit on the active mode, each click persisting the mode its row names. Strings and the
 * sort selection are stubbed so this pins the selector's shape and wiring alone.
 */
final class SortSelectorControlTest {
    // The mode rows in the order the selector stacks them, so a test maps a row index back to a mode.
    private static final List<BlocSortMode> MODES = List.of(BlocSortMode.values());

    @Nested
    class BuildSelector {

        @Test
        void buildSelectorBuildsAVerticalAlwaysSelectedRadio() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = SortSelectorControl.buildSelector(BlocSortMode.DEFAULT);

                assertThat(selector.kind()).isEqualTo(ControlKind.RADIO);
                assertThat(selector.alignment()).isEqualTo(RadioAlignment.VERTICAL);
                // A sort is always active, so the radio never deselects to an unsorted state.
                assertThat(selector.canDeselect()).isFalse();
            }
        }

        @Test
        void buildSelectorLabelsARowPerModeInModeOrder() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = SortSelectorControl.buildSelector(BlocSortMode.DEFAULT);

                assertThat(selector.labels())
                        .containsExactly("Name", "Domination", "Presence", "Score", "Market size");
            }
        }

        @Test
        void buildSelectorLightsTheActiveModesRow() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = SortSelectorControl.buildSelector(BlocSortMode.MARKET_SIZE);

                assertThat(selector.selectedIndex()).isEqualTo(MODES.indexOf(BlocSortMode.MARKET_SIZE));
            }
        }
    }

    @Nested
    class SelectMode {

        @Test
        void clickingARowPersistsThatRowsMode() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);
                var selector = SortSelectorControl.buildSelector(BlocSortMode.DEFAULT);
                var presenceRow = MODES.indexOf(BlocSortMode.PRESENCE);

                selector.action().activateCell(presenceRow);

                selectionMock.verify(() -> SortSelection.selectSortMode(
                        BlocSortMode.PRESENCE.persistenceKey()));
            }
        }

        @Test
        void clickingOutsideTheModeRowsChangesNothing() {
            // A stray hit past the last row names no mode, so it is ignored rather than persisting a
            // phantom choice.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);
                var selector = SortSelectorControl.buildSelector(BlocSortMode.DEFAULT);

                selector.action().activateCell(MODES.size());

                selectionMock.verifyNoInteractions();
            }
        }
    }

}
