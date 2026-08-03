package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.LabelledRow;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.starsector.ui.widgets.lists.SortDirection;

import kmu.starsector.StarsectorSettingsFake;
import kmu.util.KmuStrings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the picker's body controls, top to bottom: a section rule, the columns selector, a row
 * pairing the sort selector beside the caller's own trailing controls, and the vertical icon-radio
 * list of selectable items ranked by the active sort mode. Also pins the click wiring: an unlit
 * option spotlights its item, the lit option clears the filter.
 *
 * <p>Run throughout over the foreign {@link Hazard} item and {@link HazardSortMode} vocabulary,
 * which no part of the political map declares - the proof that the picker draws, ranks, and stores
 * a caller's own type without opening it. Strings and the filter selection are stubbed, so this
 * pins the picker's shape and wiring alone, not how a string resolves or how a selection persists.
 */
final class FilterPickerControlTest {

    // The scope whose slot a pick or clear writes into; the picker is built for this scope.
    private static final String SCOPE_ID = "hazards";

    // The foreign sort vocabulary the picker ranks and lays its selector rows out from.
    private static final ListSortModes<Hazard> MODES =
        new ListSortModes<>(List.of(HazardSortMode.values()), HazardSortMode.ALPHA);

    // The two items the picker lists in every test: a crested one that is harsher and wider and a
    // crestless one, so the null-crest path is exercised. Their ids differ from their labels, so a
    // row lit by id cannot be passing by matching a label.
    private static final Hazard STORM = new Hazard("storm_1", "Storm", "crest_storm", 9, 8);
    private static final Hazard DRIFT = new Hazard("drift_1", "Drift", null, 2, 3);

    private static final List<Hazard> HAZARDS = List.of(STORM, DRIFT);

    // The trailing controls the caller pairs with the sort selector; a plain label stands in for
    // whatever a layer actually pairs there, since the picker only places what it is handed. Its tone
    // is arbitrary - nothing under test reads what colour a placed control draws in.
    private static final ControlSpec TRAILING_MARKER =
        ControlSpec.Label.createLabel(new TextSpan("trailing", Color.WHITE));
    private static final List<ControlSpec> TRAILING = List.of(TRAILING_MARKER);

    // The block is a fixed four rows: the section rule, the columns selector, the paired sort row,
    // then the list.
    private static final int DIVIDER = 0;
    private static final int COLUMNS_SELECTOR = 1;
    private static final int SORT_ROW = 2;

    // The engine tone the picker's rows carry, stood in for so the rows can be built without the live
    // palette in reach.
    private static final Color TEXT = Color.LIGHT_GRAY;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();
        
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(TEXT);
    }

    @AfterEach
    void clearColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildControls {

        @Test
        void buildControlsReturnsNothingWhenNoItemsAreSelectable() {
            // A scope with nothing to spotlight contributes no picker at all, so the body carries no
            // empty list widget.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {

                assertThat(build(List.of(), null, HazardSortMode.ALPHA))
                    .isEmpty();
            }
        }

        @Test
        void buildControlsHeadsWithADivider() {
            // The section rule heads the block with no text, parting the controls above from the
            // picker below.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var divider = build(HAZARDS, null, HazardSortMode.ALPHA)
                    .get(DIVIDER);

                assertThat(divider)
                    .isInstanceOf(ControlSpec.Divider.class);
            }
        }

        @Test
        void buildControlsPlacesTheColumnsSelectorUnderTheDivider() {
            // The columns selector rides directly under the rule, so the column count is chosen for
            // the block as a whole: a two-segment horizontal radio.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var columnsSelector = build(HAZARDS, null, HazardSortMode.ALPHA)
                    .get(COLUMNS_SELECTOR);

                assertThat(columnsSelector)
                    .isInstanceOf(ControlSpec.HorizontalRadio.class);
                assertThat(columnsSelector.labels())
                    .hasSize(2);
            }
        }

        @Test
        void buildControlsPairsTheSortSelectorBesideTheCallersTrailingControls() {
            // The sort selector holds the row's left half (a vertical, re-firing radio lit on the
            // active mode's row) and whatever the caller handed over fills the right, so the metric
            // reads side by side with the layer's own knobs above the list.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var pair = sortRowOf(build(HAZARDS, null, HazardSortMode.SEVERITY));
                var sortSelector = (ControlSpec.VerticalTable) pair.leftColumn()
                    .get(0);

                assertThat(pair.leftColumn())
                    .hasSize(1);

                // A sort is always active, so a re-pick re-fires to flip; the lit row is the active
                // mode.
                assertThat(sortSelector.reselect())
                    .isEqualTo(ReselectBehaviour.REFIRE);
                assertThat(sortSelector.selectedIndex())
                    .isEqualTo(List.of(HazardSortMode.values()).indexOf(HazardSortMode.SEVERITY));

                // The right half is the caller's, placed verbatim.
                assertThat(pair.rightColumn())
                    .containsExactly(TRAILING_MARKER);
            }
        }

        @Test
        void buildControlsDrawsTheSortSelectorAloneWhenNothingIsPairedWithIt() {
            // A layer with nothing to pair passes no trailing controls, which leaves the sort
            // selector alone on its row rather than forcing a stand-in widget into the right half.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var pair = sortRowOf(buildPicker(
                    HAZARDS,
                    null,
                    sortOf(HazardSortMode.ALPHA),
                    ListColumns.ONE,
                    List.of()));

                assertThat(pair.leftColumn())
                    .hasSize(1);
                assertThat(pair.rightColumn())
                    .isEmpty();
            }
        }

        @Test
        void buildControlsIsAFixedFourRowBlock() {
            // The rule, the columns selector, the paired sort row, and the list: four rows, fixed
            // regardless of whether anything is spotlighted.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                assertThat(build(HAZARDS, null, HazardSortMode.ALPHA))
                    .hasSize(4);
                assertThat(build(HAZARDS, "storm_1", HazardSortMode.ALPHA))
                    .hasSize(4);
            }
        }

        @Test
        void buildControlsBuildsAVerticalDeselectableIconListOfTheItems() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, null, HazardSortMode.ALPHA));

                // A vertical table by type; re-picking the lit row clears the spotlight (DESELECT).
                assertThat(picker.reselect())
                    .isEqualTo(ReselectBehaviour.DESELECT);

                // The list is the body's scrolling cluster, so a long list scrolls within the capped
                // body while the controls above and below it stay pinned.
                assertThat(picker.scrolls())
                    .isTrue();

                // Each row carries its own name and its own crest, so a crestless item leads with the
                // empty slot rather than dropping out of a parallel column. Alpha-sorted, so Drift
                // leads.
                assertThat(picker.labels())
                    .containsExactly("Drift", "Storm");
                assertThat(readLeadingRowSlots(picker))
                    .containsExactly(RowSlot.EMPTY, new RowSlot.Image("crest_storm"));
            }
        }

        @Test
        void buildControlsRanksTheListByTheSortMode() {
            // The list is ordered by the chosen metric, and the trailing value is that metric, so the
            // rows read as a table sorted by the number shown.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                // A third item that trails on severity but leads on radius, so a radius sort visibly
                // reorders the list rather than just relabelling it.
                var squall = new Hazard("squall_1", "Squall", "crest_squall", 1, 12);

                var picker = pickerOf(build(
                    List.of(STORM, DRIFT, squall),
                    null,
                    HazardSortMode.RADIUS));

                assertThat(picker.labels())
                    .containsExactly("Squall", "Storm", "Drift");
                assertThat(readTrailingRowSlots(picker))
                    .containsExactly(valueRowSlot("12"), valueRowSlot("8"), valueRowSlot("3"));
            }
        }

        @Test
        void buildControlsRanksTheListInTheGivenDirection() {
            // The direction flows through to the ordering: radius ascending reverses the default
            // descending list. The trailing values still read the radius, only their order flips.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(buildPicker(
                    HAZARDS,
                    null,
                    new ListSort<>(HazardSortMode.RADIUS, SortDirection.ASCENDING),
                    ListColumns.ONE,
                    TRAILING));

                assertThat(picker.labels())
                    .containsExactly("Drift", "Storm");
                assertThat(readTrailingRowSlots(picker))
                    .containsExactly(valueRowSlot("3"), valueRowSlot("8"));
            }
        }

        @Test
        void buildControlsLeavesTheValuesBlankUnderAModeThatShowsNone() {
            // A mode that declares no trailing value leaves every row blank, so the list reads as a
            // plain one rather than a ranked table.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, null, HazardSortMode.ALPHA));

                assertThat(readTrailingRowSlots(picker))
                    .containsExactly(valueRowSlot(""), valueRowSlot(""));
            }
        }

        @Test
        void buildControlsLabelsANamelessItemAsAnEmptyRow() {
            // An item whose name did not resolve draws as an unlabelled row, not a null the width
            // measurer would choke on.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var nameless = new Hazard("ghost_1", null, null, 4, 4);

                var picker = pickerOf(build(List.of(nameless), null, HazardSortMode.SEVERITY));

                assertThat(picker.labels())
                    .containsExactly("");
            }
        }

        @Test
        void buildControlsLightsTheSpotlightedItemsRow() {
            // The lit row is resolved by the stored id, not by the label the row draws, so an id
            // that matches no label still lights its own item's row wherever the ranking put it.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, "storm_1", HazardSortMode.ALPHA));

                // Alpha-sorted, Storm is the second row.
                assertThat(picker.selectedIndex())
                    .isEqualTo(1);
            }
        }

        @Test
        void buildControlsLightsNoRowWhenTheStoredItemIsNotInTheList() {
            // A stale stored id (the load heal has not run, or the item lapsed mid-session) lights
            // nothing, so the list still shows every real option to pick from.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, "vanished", HazardSortMode.ALPHA));

                assertThat(picker.selectedIndex())
                    .isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsLaysTheListAcrossTheChosenColumnCount() {
            // The chosen column count reaches the list widget's geometry: a two-column choice builds
            // a two-column list, a single-column choice a one-column list, so the layout wraps the
            // rows exactly as the selector says.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubLabels(stringsMock);

                var oneColumn = pickerOf(buildPicker(
                    HAZARDS, null, sortOf(HazardSortMode.ALPHA), ListColumns.ONE, TRAILING));
                var twoColumn = pickerOf(buildPicker(
                    HAZARDS, null, sortOf(HazardSortMode.ALPHA), ListColumns.TWO, TRAILING));

                assertThat(oneColumn.columnCount())
                    .isEqualTo(1);
                assertThat(twoColumn.columnCount())
                    .isEqualTo(2);
            }
        }

        @Test
        void buildControlsBindsAColumnsSegmentPickToTheColumnStore() {
            // The columns selector reports its pick rather than storing one, so this is the only
            // place the report is joined to the save - a binding that silently came unwired would
            // leave the segment lighting up and the count never persisting.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<ColumnSelection> selectionMock =
                        mockStatic(ColumnSelection.class)) {
                stubLabels(stringsMock);

                var columnsSelector = (ControlSpec.HorizontalRadio)
                    build(HAZARDS, null, HazardSortMode.ALPHA)
                        .get(COLUMNS_SELECTOR);

                columnsSelector.action().activateCell(
                    List.of(ListColumns.values()).indexOf(ListColumns.TWO));

                selectionMock.verify(
                    () -> ColumnSelection.selectColumnCount(ListColumns.TWO.persistenceKey()));
            }
        }

        @Test
        void buildControlsBindsASortRowPickToTheSortStore() {
            // The same join for the other selector: a click on an unlit mode's row stores that mode
            // and its own default direction, both keys written as one pick.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                stubLabels(stringsMock);

                var sortSelector = (ControlSpec.VerticalTable) sortRowOf(
                        build(HAZARDS, null, HazardSortMode.ALPHA))
                    .leftColumn()
                    .get(0);

                sortSelector.action().activateCell(
                    List.of(HazardSortMode.values()).indexOf(HazardSortMode.SEVERITY));

                selectionMock.verify(
                    () -> SortSelection.selectSortMode(HazardSortMode.SEVERITY.persistenceKey()));
                selectionMock.verify(
                    () -> SortSelection.selectSortDirection(
                        HazardSortMode.SEVERITY.defaultDirection().persistenceKey()));
            }
        }
    }

    @Nested
    class PickItem {

        @Test
        void clickingAnUnlitOptionSpotlightsThatItem() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, null, HazardSortMode.ALPHA));

                picker.action().activateCell(0);

                // The item's own id is what is stored, not its label or its row index.
                selectionMock.verify(
                    () -> FilterSelection.selectId(SCOPE_ID, "drift_1"));
            }
        }

        @Test
        void clickingTheLitOptionClearsTheFilter() {
            // The list is deselectable, so a press on the spotlighted row reaches the action with its
            // own index; re-picking it stops the spotlight rather than re-selecting it.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, "drift_1", HazardSortMode.ALPHA));

                picker.action().activateCell(0);

                selectionMock.verify(
                    () -> FilterSelection.clearSelection(SCOPE_ID));
            }
        }

        @Test
        void clickingAnotherOptionWhileFilteringSpotlightsTheNewItem() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, "drift_1", HazardSortMode.ALPHA));

                picker.action().activateCell(1);

                selectionMock.verify(
                    () -> FilterSelection.selectId(SCOPE_ID, "storm_1"));
            }
        }

        @Test
        void clickingOutsideTheListChangesNothing() {
            // A stray hit - an index past the rows, or a negative one - neither spotlights nor
            // clears, so a click that lands outside the options leaves the filter as it was.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(build(HAZARDS, null, HazardSortMode.ALPHA));

                picker.action().activateCell(HAZARDS.size());
                picker.action().activateCell(-1);

                selectionMock.verifyNoInteractions();
            }
        }
    }

    // The one call into the picker every test in the suite goes through, so the scope and the mode
    // vocabulary are named once and a test spells out only the input it is actually varying.
    private static List<ControlSpec> buildPicker(
            List<Hazard> hazards,
            String selectedItemId,
            ListSort<Hazard> sort,
            ListColumns columns,
            List<ControlSpec> trailingControls) {

        return FilterPickerControl.buildControls(
            SCOPE_ID,
            hazards,
            selectedItemId,
            sort,
            MODES,
            columns,
            trailingControls);
    }

    // Builds the picker in the sort mode's own default direction, the default single-column layout,
    // and one stand-in trailing control - the state every test that does not exercise a flip, a
    // column change, or an empty pairing assumes, so those call sites read the mode alone.
    private static List<ControlSpec> build(
            List<Hazard> hazards,
            String selectedItemId,
            HazardSortMode mode) {

        return buildPicker(
            hazards,
            selectedItemId,
            sortOf(mode),
            ListColumns.ONE,
            TRAILING);
    }

    // A mode in its own natural direction - the state a save that has never flipped the sort reads.
    private static ListSort<Hazard> sortOf(HazardSortMode mode) {
        return new ListSort<>(mode, mode.defaultDirection());
    }

    // The picker list is always the block's last row, so a test reads it from the tail. Read as the
    // vertical table it is, so a test reads its rows, its scroll flag, and its re-pick behaviour.
    private static ControlSpec.VerticalTable pickerOf(List<ControlSpec> controls) {
        return (ControlSpec.VerticalTable) controls.get(controls.size() - 1);
    }

    // What each row leads with, top to bottom - an item's crest, or the empty slot for an item with
    // none.
    private static List<RowSlot> readLeadingRowSlots(ControlSpec.VerticalTable picker) {
        return picker.labelledRows().stream()
            .map(LabelledRow::leadingRowSlot)
            .toList();
    }

    // What each row trails with, top to bottom - the sort metric's value for that item.
    private static List<RowSlot> readTrailingRowSlots(ControlSpec.VerticalTable picker) {
        return picker.labelledRows().stream()
            .map(LabelledRow::trailingRowSlot)
            .toList();
    }

    // The trailing slot a row carrying this value holds, in the tone the picker resolves - what an
    // assertion spells out to say "this row's value column reads that".
    private static RowSlot valueRowSlot(String value) {
        return new RowSlot.Text(new TextSpan(value, TEXT));
    }

    // The paired sort row, read as the side-by-side group it is so a test reads its left column (the
    // sort selector) and its right column (the caller's trailing controls) separately.
    private static ControlSpec.SideBySide sortRowOf(List<ControlSpec> controls) {
        return (ControlSpec.SideBySide) controls.get(SORT_ROW);
    }

    // Stubs the text the picker resolves through this mod's strings table, so the assertions read
    // the wiring without the live table. A control's labels are copied and reject a null option
    // name, so every sort row must resolve to real text; the constant names stand in for the drawn
    // labels, which no assertion here reads. The columns segments need no stub - their labels are
    // the counts themselves - but the caption beside them is prose and is resolved here.
    private static void stubLabels(MockedStatic<KmuStrings> stringsMock) {
        for (var mode : HazardSortMode.values()) {
            stringsMock
                .when(() -> KmuStrings.get(mode.labelKey()))
                .thenReturn(mode.name());
        }
        stringsMock
            .when(() -> KmuStrings.get(KmuStrings.MAP_LAYER_CTL_COLUMNS_CAPTION))
            .thenReturn("Columns");
    }
}
