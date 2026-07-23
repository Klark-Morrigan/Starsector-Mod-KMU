package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;
import kmlib.starsector.ui.controls.TriangleDirection;

import kmu.maplayers.politicalmap.base.BlocSort;
import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.maplayers.politicalmap.base.SortDirection;
import kmu.maplayers.politicalmap.base.refresh.SortSelection;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Pins the sort selector: a vertical, re-firing radio with one row per sort mode in the mode's own
 * order, lit on the active mode, each row trailed by the direction it would sort in. Clicking a
 * different mode switches to it at its default direction; re-clicking the lit mode flips its direction.
 * Strings and the sort selection are stubbed so this pins the selector's shape and wiring alone.
 */
final class SortSelectorControlTest {
    // The mode rows in the order the selector stacks them, so a test maps a row index back to a mode.
    private static final List<BlocSortMode> MODES = List.of(BlocSortMode.values());

    @Nested
    class BuildSelector {

        @Test
        void buildSelectorBuildsAVerticalReFiringRadio() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = buildSelector(BlocSortMode.DEFAULT,
                        BlocSortMode.DEFAULT.defaultDirection());

                // A vertical table by type; a sort is always active, so it never deselects - instead a
                // re-pick re-fires so the handler can flip the direction.
                assertThat(selector.reselect()).isEqualTo(ReselectBehaviour.REFIRE);
            }
        }

        @Test
        void buildSelectorLabelsARowPerModeInModeOrder() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = buildSelector(BlocSortMode.DEFAULT,
                        BlocSortMode.DEFAULT.defaultDirection());

                assertThat(selector.labels())
                        .containsExactly("Name", "Domination", "Presence", "Score", "Market size");
            }
        }

        @Test
        void buildSelectorLightsTheActiveModesRow() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = buildSelector(BlocSortMode.MARKET_SIZE,
                        SortDirection.DESCENDING);

                assertThat(selector.selectedIndex()).isEqualTo(MODES.indexOf(BlocSortMode.MARKET_SIZE));
            }
        }

        @Test
        void buildSelectorTrailsTheActiveRowWithItsLiveDirectionAndOthersWithTheirDefaults() {
            // The lit row previews the direction the list is sorting in now (flipped to ascending, an UP
            // triangle); every other numeric row previews its own default descending DOWN triangle, and
            // the name row its default ascending UP triangle - so each row reads as "pick me and the list
            // sorts this way".
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = buildSelector(BlocSortMode.DOMINATION,
                        SortDirection.ASCENDING);

                var nameRow = MODES.indexOf(BlocSortMode.NAME);
                var dominationRow = MODES.indexOf(BlocSortMode.DOMINATION);
                var presenceRow = MODES.indexOf(BlocSortMode.PRESENCE);
                assertThat(selector.directionAt(dominationRow)).isEqualTo(TriangleDirection.UP);
                assertThat(selector.directionAt(presenceRow)).isEqualTo(TriangleDirection.DOWN);
                assertThat(selector.directionAt(nameRow)).isEqualTo(TriangleDirection.UP);
            }
        }

        @Test
        void buildSelectorDrawsNoRowIcons() {
            // The selector reuses the bloc list's table geometry with an all-null icon column, so no
            // mode row draws a crest.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);

                var selector = buildSelector(BlocSortMode.DEFAULT,
                        BlocSortMode.DEFAULT.defaultDirection());

                assertThat(selector.hasIconAt(MODES.indexOf(BlocSortMode.DOMINATION))).isFalse();
            }
        }
    }

    @Nested
    class ApplySelection {

        @Test
        void clickingADifferentModeSwitchesToItAtItsDefaultDirection() {
            // The stored mode defaults to domination; clicking presence switches to it and resets the
            // direction to presence's default (descending), so a mode switch always starts natural.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);
                var selector = buildSelector(BlocSortMode.DOMINATION,
                        SortDirection.DESCENDING);
                var presenceRow = MODES.indexOf(BlocSortMode.PRESENCE);

                selector.action().activateCell(presenceRow);

                selectionMock.verify(() -> SortSelection.selectSortMode(
                        BlocSortMode.PRESENCE.persistenceKey()));
                selectionMock.verify(() -> SortSelection.selectSortDirection(
                        BlocSortMode.PRESENCE.defaultDirection().persistenceKey()));
            }
        }

        @Test
        void reClickingTheLitModeFlipsItsDirectionWithoutSwitchingMode() {
            // Domination is stored ascending; re-clicking its row flips only the direction to
            // descending and never rewrites the mode.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);
                selectionMock.when(SortSelection::getSortModeKey)
                        .thenReturn(BlocSortMode.DOMINATION.persistenceKey());
                selectionMock.when(SortSelection::getSortDirectionKey)
                        .thenReturn(SortDirection.ASCENDING.persistenceKey());
                var selector = buildSelector(BlocSortMode.DOMINATION,
                        SortDirection.ASCENDING);
                var dominationRow = MODES.indexOf(BlocSortMode.DOMINATION);

                selector.action().activateCell(dominationRow);

                selectionMock.verify(() -> SortSelection.selectSortDirection(
                        SortDirection.DESCENDING.persistenceKey()));
                selectionMock.verify(() -> SortSelection.selectSortMode(anyString()), never());
            }
        }

        @Test
        void clickingOutsideTheModeRowsChangesNothing() {
            // A stray hit past the last row names no mode, so it is ignored rather than persisting a
            // phantom choice.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                SortLabelStubs.stubSortLabels(stringsMock);
                var selector = buildSelector(BlocSortMode.DEFAULT,
                        BlocSortMode.DEFAULT.defaultDirection());

                selector.action().activateCell(MODES.size());

                selectionMock.verifyNoInteractions();
            }
        }
    }

    // Builds the selector from a mode and direction the tests spell out as a pair, wrapping them into
    // the BlocSort the production builder now takes, so each call site reads as the mode-and-direction
    // it exercises rather than a record construction.
    private static ControlSpec.VerticalTable buildSelector(
            BlocSortMode mode, SortDirection direction) {
        return SortSelectorControl.buildSelector(new BlocSort(mode, direction));
    }
}
