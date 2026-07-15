package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;

import kmu.maplayers.politicalmap.base.BlocListColumns;
import kmu.maplayers.politicalmap.base.BlocSortMode;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.SelectableBloc;
import kmu.maplayers.politicalmap.base.SortDirection;
import kmu.maplayers.politicalmap.base.politics.BlocStats;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the spotlight picker's body controls, top to bottom: a section rule, the sort selector, the
 * shared recede control only while a bloc is spotlighted, and the vertical icon-radio list of
 * selectable blocs ranked by the active sort mode. Also pins the click wiring: an unlit option
 * spotlights its bloc, the lit option clears the filter. Strings, the recede preferences, and the
 * filter selection are stubbed so this pins the picker's shape and wiring alone, not how a string
 * resolves or how the selection persists.
 */
final class FilterPickerControlTest {
    // The two blocs the picker lists in every test: a crested faction that dominates more and a
    // crestless one (an alliance, or a faction with no authored crest) that dominates less, so the
    // null-crest path is exercised and the default domination sort keeps them in this order.
    private static final SelectableBloc HEGEMONY =
            new SelectableBloc("hegemony", "Hegemony", "crest_heg", new BlocStats(5, 8, 40, 12));
    private static final SelectableBloc TRADERS =
            new SelectableBloc("free_traders", "Free Traders", null, new BlocStats(2, 3, 6, 4));
    private static final List<SelectableBloc> BLOCS = List.of(HEGEMONY, TRADERS);

    // The section rule always heads the block, and the sort selector always rides directly under it.
    // The list is always the last row - its offset shifts by the recede rows when a bloc is
    // spotlighted - so tests read it from the tail rather than a fixed index.
    private static final int DIVIDER = 0;
    private static final int SORT_SELECTOR = 1;

    @Nested
    class BuildControls {

        @Test
        void buildControlsReturnsNothingWhenNoBlocsAreSelectable() {
            // A view with no visible weighted bloc contributes no picker at all, so the body carries
            // no empty list widget.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                assertThat(build(List.of(), null, BlocSortMode.DEFAULT))
                        .isEmpty();
            }
        }

        @Test
        void buildControlsHeadsWithADivider() {
            // The section rule replaces the old caption string: it heads the block with no text,
            // parting the view-level controls above from the picker below.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var divider =
                        build(BLOCS, null, BlocSortMode.DEFAULT).get(DIVIDER);

                assertThat(divider).isInstanceOf(ControlSpec.Divider.class);
            }
        }

        @Test
        void buildControlsPlacesTheSortSelectorUnderTheDivider() {
            // The sort selector rides directly under the rule, so the metric is chosen right above the
            // list it orders: a vertical, re-firing radio lit on the active mode's row.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var selector = (ControlSpec.VerticalTable) build(BLOCS, null, BlocSortMode.PRESENCE)
                        .get(SORT_SELECTOR);

                // A vertical table by type; a sort is always active, so a re-pick re-fires to flip.
                assertThat(selector.reselect()).isEqualTo(ReselectBehaviour.REFIRE);
                assertThat(selector.selectedIndex())
                        .isEqualTo(List.of(BlocSortMode.values()).indexOf(BlocSortMode.PRESENCE));
            }
        }

        @Test
        void buildControlsBuildsAVerticalDeselectableIconListOfTheBlocs() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(build(BLOCS, null, BlocSortMode.DEFAULT));

                // A vertical table by type; re-picking the lit row clears the spotlight (DESELECT).
                assertThat(picker.reselect()).isEqualTo(ReselectBehaviour.DESELECT);
                // The list is the body's scrolling region, so a long bloc list scrolls within the
                // capped body while the controls above and below it stay pinned.
                assertThat(picker.scrolls()).isTrue();
                // Labels are the bloc names, the icons the crests, aligned index for index so a
                // crestless bloc rides as a null entry rather than dropping a row. Domination-sorted,
                // so the higher-dominating Hegemony leads.
                assertThat(picker.labels()).containsExactly("Hegemony", "Free Traders");
                assertThat(picker.iconPaths()).containsExactly("crest_heg", null);
            }
        }

        @Test
        void buildControlsRanksTheListByTheSortMode() {
            // The list is ordered by the chosen metric, high to low; the trailing value is that metric,
            // so the rows read as a table sorted by the number shown. Under presence Traders and
            // Hegemony keep their order (8 > 3) but the values switch to the presence counts.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);
                // A third bloc that trails on domination but leads on market size, so a market-size
                // sort visibly reorders the list rather than just relabelling it.
                var tritachyon = new SelectableBloc("tritachyon", "Tri-Tachyon", "crest_tt",
                        new BlocStats(1, 9, 30, 99));

                var picker = pickerOf(build(
                        List.of(HEGEMONY, TRADERS, tritachyon), null, BlocSortMode.MARKET_SIZE));

                assertThat(picker.labels()).containsExactly("Tri-Tachyon", "Hegemony", "Free Traders");
                assertThat(picker.trailingLabels()).containsExactly("99", "12", "4");
            }
        }

        @Test
        void buildControlsRanksTheListInTheGivenDirection() {
            // The direction flows through to the ordering: domination ascending reverses the default
            // descending list, so the lower-dominating Free Traders leads. The trailing values still
            // read the domination metric, only their order flips.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(FilterPickerControl.buildControls(
                        BLOCS, null, BlocSortMode.DOMINATION, SortDirection.ASCENDING,
                        BlocListColumns.ONE));

                assertThat(picker.labels()).containsExactly("Free Traders", "Hegemony");
                assertThat(picker.trailingLabels()).containsExactly("2", "5");
            }
        }

        @Test
        void buildControlsSortsByNameWithBlankValuesUnderTheNameMode() {
            // The name mode ranks the labels alphabetically and shows no numeric value, so the rows
            // read as a plain A-to-Z list rather than a ranked table.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(build(BLOCS, null, BlocSortMode.NAME));

                assertThat(picker.labels()).containsExactly("Free Traders", "Hegemony");
                assertThat(picker.trailingLabels()).containsExactly("", "");
            }
        }

        @Test
        void buildControlsDrawsEachBlocsSortMetricAsItsTrailingValue() {
            // The list reads as a ranked table: each row's trailing value is the active sort metric for
            // the bloc, kept aligned index for index so a bloc with a zero metric shows "0" rather than
            // dropping the column.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);
                var hegemony = new SelectableBloc("hegemony", "Hegemony", "crest_heg",
                        new BlocStats(5, 8, 40, 12));
                var traders = new SelectableBloc("free_traders", "Free Traders", null,
                        new BlocStats(0, 3, 6, 4));

                var picker = pickerOf(build(
                        List.of(hegemony, traders), null, BlocSortMode.DOMINATION));

                assertThat(picker.trailingLabels()).containsExactly("5", "0");
            }
        }

        @Test
        void buildControlsLabelsANamelessBlocAsAnEmptyRow() {
            // A bloc whose name did not resolve draws as an unlabelled row, not a null the width
            // measurer would choke on.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);
                var nameless = new SelectableBloc("ghost", null, null);

                var picker = pickerOf(
                        build(List.of(nameless), null, BlocSortMode.DEFAULT));

                assertThat(picker.labels()).containsExactly("");
            }
        }

        @Test
        void buildControlsLightsTheSpotlightedBlocsRow() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(
                        build(BLOCS, "free_traders", BlocSortMode.DEFAULT));

                // Domination-sorted, Free Traders is the second row.
                assertThat(picker.selectedIndex()).isEqualTo(1);
            }
        }

        @Test
        void buildControlsLightsNoRowWhenTheStoredBlocIsNotInTheList() {
            // A stale stored id (the load heal has not run, or the bloc's market vanished mid-session)
            // lights nothing, so the list still shows every real option to pick from.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(
                        build(BLOCS, "vanished", BlocSortMode.DEFAULT));

                assertThat(picker.selectedIndex()).isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsOmitsTheRecedeControlWhenNoBlocIsSpotlighted() {
            // With no filter the sector draws normally, so there is nothing to recede - the block is
            // just the divider, the sort selector, the columns selector, and the list.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                assertThat(build(BLOCS, null, BlocSortMode.DEFAULT))
                        .hasSize(4);
            }
        }

        @Test
        void buildControlsInsertsTheRecedeControlAboveTheColumnsSelectorWhenABlocIsSpotlighted() {
            // While filtering the recede control rides between the sort selector and the columns
            // selector: its caption then the Mute and Desaturate checkboxes, so the block is divider +
            // sort selector + three recede rows + columns selector + list.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class)) {
                stubCaptions(stringsMock);

                var controls = build(BLOCS, "hegemony", BlocSortMode.DEFAULT);

                assertThat(controls).hasSize(7);
                assertThat(controls.get(2)).isInstanceOf(ControlSpec.Label.class);
                assertThat(controls.get(2).labels()).containsExactly("Rest of the sector is");
                assertThat(controls.get(3)).isInstanceOf(ControlSpec.Checkbox.class);
                assertThat(controls.get(4)).isInstanceOf(ControlSpec.Checkbox.class);
                // The columns selector then the list are the last two rows: the horizontal columns radio
                // sits directly above the vertical list it lays out.
                assertThat(controls.get(5)).isInstanceOf(ControlSpec.HorizontalRadio.class);
                assertThat(controls.get(6)).isInstanceOf(ControlSpec.VerticalTable.class);
            }
        }

        @Test
        void buildControlsPlacesTheColumnsSelectorDirectlyAboveTheList() {
            // The columns selector rides right above the list, so how many columns the list wraps
            // across is chosen by the list it lays out: a two-segment horizontal radio.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var controls = build(BLOCS, null, BlocSortMode.DEFAULT);
                var columnsSelector = controls.get(controls.size() - 2);

                assertThat(columnsSelector).isInstanceOf(ControlSpec.HorizontalRadio.class);
                assertThat(columnsSelector.labels()).hasSize(2);
            }
        }

        @Test
        void buildControlsLaysTheListAcrossTheChosenColumnCount() {
            // The chosen column count reaches the list widget's geometry: a two-column choice builds a
            // two-column list, a single-column choice a one-column list, so the layout wraps the rows
            // exactly as the selector says.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var oneColumn = pickerOf(FilterPickerControl.buildControls(BLOCS, null,
                        BlocSortMode.DEFAULT, BlocSortMode.DEFAULT.defaultDirection(),
                        BlocListColumns.ONE));
                var twoColumn = pickerOf(FilterPickerControl.buildControls(BLOCS, null,
                        BlocSortMode.DEFAULT, BlocSortMode.DEFAULT.defaultDirection(),
                        BlocListColumns.TWO));

                assertThat(oneColumn.columnCount()).isEqualTo(1);
                assertThat(twoColumn.columnCount()).isEqualTo(2);
            }
        }
    }

    @Nested
    class PickBloc {

        @Test
        void clickingAnUnlitOptionSpotlightsThatBloc() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubCaptions(stringsMock);
                var picker = pickerOf(build(BLOCS, null, BlocSortMode.DEFAULT));

                picker.action().activateCell(0);

                selectionMock.verify(() -> FilterSelection.selectBloc("hegemony"));
            }
        }

        @Test
        void clickingTheLitOptionClearsTheFilter() {
            // The list is deselectable, so a press on the spotlighted row reaches the action with its
            // own index; re-picking it stops the spotlight rather than re-selecting it.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubCaptions(stringsMock);
                var picker = pickerOf(
                        build(BLOCS, "hegemony", BlocSortMode.DEFAULT));

                picker.action().activateCell(0);

                selectionMock.verify(FilterSelection::clearSelection);
            }
        }

        @Test
        void clickingAnotherOptionWhileFilteringSpotlightsTheNewBloc() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubCaptions(stringsMock);
                var picker = pickerOf(
                        build(BLOCS, "hegemony", BlocSortMode.DEFAULT));

                picker.action().activateCell(1);

                selectionMock.verify(() -> FilterSelection.selectBloc("free_traders"));
            }
        }
    }

    // Builds the picker in the sort mode's own default direction and the default single-column layout,
    // the state every test that does not exercise a flip or a column change assumes, so the call sites
    // read the mode alone without spelling out its default direction or column count. A test that flips
    // the direction calls the full builder directly.
    private static List<ControlSpec> build(List<SelectableBloc> blocs, String selectedBlocId,
            BlocSortMode mode) {
        return FilterPickerControl.buildControls(blocs, selectedBlocId, mode, mode.defaultDirection(),
                BlocListColumns.ONE);
    }

    // The picker list is always the block's last row, so a test reads it from the tail rather than a
    // fixed index that would shift with the recede rows. Read as the vertical table it is, so a test
    // reads its icon and value columns, its scroll flag, and its re-pick behaviour.
    private static ControlSpec.VerticalTable pickerOf(List<ControlSpec> controls) {
        return (ControlSpec.VerticalTable) controls.get(controls.size() - 1);
    }

    // Stubs the caption and sort-label strings the picker heads its rows with, so the assertions read
    // the wiring without the live strings table. The recede checkbox labels are only reached in tests
    // that mock RecedePreferences, which stub them there.
    private static void stubCaptions(MockedStatic<KmuStrings> stringsMock) {
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION))
                .thenReturn("Rest of the sector is");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED)).thenReturn("Muted");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED))
                .thenReturn("Desaturated");
        // The picker builds the sort selector, which reads every sort-mode label.
        SortLabelStubs.stubSortLabels(stringsMock);
        // The picker also builds the columns selector, whose segment labels must resolve to real text
        // rather than a null, since a control's labels are copied and reject a null option name.
        for (var columns : BlocListColumns.values()) {
            stringsMock.when(() -> KmuStrings.get(columns.labelKey())).thenReturn(columns.name());
        }
    }
}
