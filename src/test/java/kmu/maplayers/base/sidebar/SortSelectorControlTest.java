package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;
import kmlib.starsector.ui.controls.TriangleDirection;

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
 * Pins the sort selector: a vertical, re-firing radio with one row per mode of the caller's set in
 * the caller's own order, lit on the active mode, each row trailed by the direction it would sort
 * in. Clicking a different mode switches to it at its default direction; re-clicking the lit mode
 * flips its direction. Exercised on the foreign {@link HazardSortMode} set throughout - the proof
 * the selector serves whatever modes a layer declares rather than one layer's - with strings and
 * the sort selection stubbed so this pins the selector's shape and wiring alone.
 */
final class SortSelectorControlTest {

    // The mode rows in the order the selector stacks them, so a test maps a row index back to a
    // mode; the vocabulary bundles them with the default the selector's fresh store reads resolve
    // against.
    private static final List<HazardSortMode> MODES = List.of(HazardSortMode.values());
    private static final HazardSortMode DEFAULT_MODE = HazardSortMode.ALPHA;
    private static final ListSortModes<Hazard> SORT_MODES =
        new ListSortModes<>(MODES, DEFAULT_MODE);

    @Nested
    class BuildSelector {

        @Test
        void buildSelectorBuildsAVerticalReFiringRadio() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubModeLabels(stringsMock);

                var selector = buildSelector(DEFAULT_MODE, DEFAULT_MODE.defaultDirection());

                // A vertical table by type; a sort is always active, so it never deselects -
                // instead a re-pick re-fires so the handler can flip the direction.
                assertThat(selector.reselect())
                    .isEqualTo(ReselectBehaviour.REFIRE);
            }
        }

        @Test
        void buildSelectorLabelsARowPerModeInTheCallersOrder() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubModeLabels(stringsMock);

                var selector = buildSelector(DEFAULT_MODE, DEFAULT_MODE.defaultDirection());

                assertThat(selector.labels())
                    .containsExactly("Alpha", "Severity", "Radius");
            }
        }

        @Test
        void buildSelectorLightsTheActiveModesRow() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubModeLabels(stringsMock);

                var selector = buildSelector(HazardSortMode.RADIUS, SortDirection.DESCENDING);

                assertThat(selector.selectedIndex())
                    .isEqualTo(MODES.indexOf(HazardSortMode.RADIUS));
            }
        }

        @Test
        void buildSelectorTrailsTheActiveRowWithItsLiveDirectionAndOthersWithTheirDefaults() {
            // The lit row previews the direction the list is sorting in now (flipped to ascending,
            // an UP triangle); the other numeric row previews its own default descending DOWN
            // triangle, and the alpha row its default ascending UP triangle - so each row reads as
            // "pick me and the list sorts this way".
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubModeLabels(stringsMock);

                var selector = buildSelector(HazardSortMode.SEVERITY, SortDirection.ASCENDING);

                var alphaRow = MODES.indexOf(HazardSortMode.ALPHA);
                var severityRow = MODES.indexOf(HazardSortMode.SEVERITY);
                var radiusRow = MODES.indexOf(HazardSortMode.RADIUS);

                assertThat(selector.directionAt(severityRow))
                    .isEqualTo(TriangleDirection.UP);
                assertThat(selector.directionAt(radiusRow))
                    .isEqualTo(TriangleDirection.DOWN);
                assertThat(selector.directionAt(alphaRow))
                    .isEqualTo(TriangleDirection.UP);
            }
        }

        @Test
        void buildSelectorDrawsNoRowIcons() {
            // The selector reuses the picker list's table geometry with an all-null icon column, so
            // no mode row draws an icon.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubModeLabels(stringsMock);

                var selector = buildSelector(DEFAULT_MODE, DEFAULT_MODE.defaultDirection());

                assertThat(selector.hasIconAt(MODES.indexOf(HazardSortMode.SEVERITY)))
                    .isFalse();
            }
        }
    }

    @Nested
    class ApplySelection {

        @Test
        void clickingADifferentModeSwitchesToItAtItsDefaultDirection() {
            // Nothing is stored, so the current mode reads as the caller's default (alpha);
            // clicking severity switches to it and resets the direction to severity's default
            // (descending), so a mode switch always starts natural.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                stubModeLabels(stringsMock);

                var selector = buildSelector(DEFAULT_MODE, DEFAULT_MODE.defaultDirection());
                var severityRow = MODES.indexOf(HazardSortMode.SEVERITY);

                selector.action().activateCell(severityRow);

                selectionMock.verify(
                    () -> SortSelection.selectSortMode(
                        HazardSortMode.SEVERITY.persistenceKey()));
                selectionMock.verify(
                    () -> SortSelection.selectSortDirection(
                        HazardSortMode.SEVERITY.defaultDirection().persistenceKey()));
            }
        }

        @Test
        void reClickingTheLitModeFlipsItsDirectionWithoutSwitchingMode() {
            // Severity is stored ascending; re-clicking its row flips only the direction to
            // descending and never rewrites the mode.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                stubModeLabels(stringsMock);

                selectionMock
                    .when(SortSelection::getSortModeKey)
                    .thenReturn(HazardSortMode.SEVERITY.persistenceKey());
                selectionMock
                    .when(SortSelection::getSortDirectionKey)
                    .thenReturn(SortDirection.ASCENDING.persistenceKey());

                var selector = buildSelector(HazardSortMode.SEVERITY, SortDirection.ASCENDING);
                var severityRow = MODES.indexOf(HazardSortMode.SEVERITY);

                selector.action().activateCell(severityRow);

                selectionMock.verify(
                    () -> SortSelection.selectSortDirection(
                        SortDirection.DESCENDING.persistenceKey()));
                selectionMock.verify(
                    () -> SortSelection.selectSortMode(anyString()),
                    never());
            }
        }

        @Test
        void clickingOutsideTheModeRowsChangesNothing() {
            // A stray hit past the last row names no mode, so it is ignored rather than persisting
            // a phantom choice.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                stubModeLabels(stringsMock);
                var selector = buildSelector(DEFAULT_MODE, DEFAULT_MODE.defaultDirection());

                selector.action().activateCell(MODES.size());

                selectionMock.verifyNoInteractions();
            }
        }
    }

    // Builds the selector over the foreign vocabulary from a mode and direction the tests spell
    // out as a pair, so each call site reads as the mode-and-direction it exercises rather than a
    // record construction and the shared vocabulary argument.
    private static ControlSpec.VerticalTable buildSelector(
            HazardSortMode mode,
            SortDirection direction) {
        return SortSelectorControl.buildSelector(new ListSort<>(mode, direction), SORT_MODES);
    }

    // Stubs every foreign mode's row label, so a test that builds the selector gets a full label
    // list rather than a null the label-list copy would reject.
    private static void stubModeLabels(MockedStatic<KmuStrings> stringsMock) {

        stringsMock
            .when(() -> KmuStrings.get(HazardSortMode.ALPHA.labelKey()))
            .thenReturn("Alpha");

        stringsMock
            .when(() -> KmuStrings.get(HazardSortMode.SEVERITY.labelKey()))
            .thenReturn("Severity");
            
        stringsMock
            .when(() -> KmuStrings.get(HazardSortMode.RADIUS.labelKey()))
            .thenReturn("Radius");
    }
}
