package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

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
 * Pins the join between KMLib's picker and this mod's save slots, which is the whole of what the
 * binder does: the spotlighted id is read off {@link FilterSelection} for the scope on the way in,
 * and each of the picker's three picks reaches the slot that keeps it on the way out. The picker's
 * own shape and click rules are KMLib's and are pinned there; the stores are mocked, so this reads
 * the wiring alone.
 *
 * <p>Run over the foreign {@link Hazard} item and {@link HazardSortMode} vocabulary, since the
 * binder is no more the political map's than the picker it binds.
 */
final class FilterSelectionBinderTest {

    // The scope whose slot a pick or clear is read from and written into.
    private static final String SCOPE_ID = "hazards";

    private static final ListSortModes<Hazard> MODES =
        new ListSortModes<>(List.of(HazardSortMode.values()), HazardSortMode.ALPHA);

    // The two items the picker lists, alpha-ordered as Drift then Storm, so a row index maps back to
    // a known id.
    private static final Hazard STORM = new Hazard("storm_1", "Storm", "crest_storm", 9, 8);
    private static final Hazard DRIFT = new Hazard("drift_1", "Drift", null, 2, 3);

    private static final List<Hazard> HAZARDS = List.of(STORM, DRIFT);

    // The rows the picker lays out, so a test names the widget it clicks rather than an index into
    // the block.
    private static final int COLUMNS_SELECTOR = 1;
    private static final int SORT_ROW = 2;

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(Color.LIGHT_GRAY);
    }

    @AfterEach
    void clearColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildPicker {

        @Test
        void buildPickerLightsTheRowTheScopesStoredIdNames() {
            // The id the picker lights comes from this scope's slot, which is the read half of the
            // binding - a picker handed nothing would light no row whatever the save holds.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                selectionMock
                    .when(() -> FilterSelection.getSelectedIdOf(SCOPE_ID))
                    .thenReturn("storm_1");

                var picker = pickerOf(buildPicker());

                // Alpha-sorted, Storm is the second row.
                assertThat(picker.selectedIndex())
                    .isEqualTo(1);
            }
        }

        @Test
        void buildPickerCaptionsTheColumnsSelectorFromThisModsStrings() {
            // The picker takes its caption as drawn text, so resolving it out of this mod's table is
            // the binder's - a caller left to pass it would be naming a string key the picker has no
            // business knowing.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                var columnsSelector = (ControlSpec.HorizontalRadio) buildPicker()
                    .get(COLUMNS_SELECTOR);

                assertThat(columnsSelector.trailingLabel())
                    .isEqualTo("Columns");
            }
        }

        @Test
        void buildPickerWritesAnItemPickIntoTheScopesSlot() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                var picker = pickerOf(buildPicker());

                picker.action().activateCell(0);

                selectionMock.verify(
                    () -> FilterSelection.selectId(SCOPE_ID, "drift_1"));
            }
        }

        @Test
        void buildPickerClearsTheScopesSlotOnARePick() {
            // The picker reports a clear rather than a pick when the lit row is re-clicked, and the
            // clear lands on this scope's slot alone.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelection> selectionMock =
                        mockStatic(FilterSelection.class)) {
                stubLabels(stringsMock);

                selectionMock
                    .when(() -> FilterSelection.getSelectedIdOf(SCOPE_ID))
                    .thenReturn("drift_1");

                var picker = pickerOf(buildPicker());

                picker.action().activateCell(0);

                selectionMock.verify(
                    () -> FilterSelection.clearSelection(SCOPE_ID));
            }
        }

        @Test
        void buildPickerWritesAColumnsPickThroughTheColumnStore() {
            // The other two picks route to the binders that already own their slots, so a wiring
            // that silently came undone would leave the segment lighting up and the count never
            // persisting.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<ColumnSelection> selectionMock =
                        mockStatic(ColumnSelection.class)) {
                stubLabels(stringsMock);

                var columnsSelector = (ControlSpec.HorizontalRadio) buildPicker()
                    .get(COLUMNS_SELECTOR);

                columnsSelector.action().activateCell(
                    List.of(ListColumns.values()).indexOf(ListColumns.TWO));

                selectionMock.verify(
                    () -> ColumnSelection.selectColumnCount(ListColumns.TWO.persistenceKey()));
            }
        }

        @Test
        void buildPickerWritesASortPickThroughTheSortStore() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<SortSelection> selectionMock = mockStatic(SortSelection.class)) {
                stubLabels(stringsMock);

                var sortSelector = (ControlSpec.VerticalTable)
                    ((ControlSpec.SideBySide) buildPicker().get(SORT_ROW))
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

    // The picker list is always the block's last row, so a test reads it from the tail.
    private static ControlSpec.VerticalTable pickerOf(List<ControlSpec> controls) {
        return (ControlSpec.VerticalTable) controls.get(controls.size() - 1);
    }

    // The one call into the binder every test goes through: the two items in the alpha mode's own
    // direction, a single column, and nothing paired beside the sort, since none of those is what
    // this suite varies.
    private static List<ControlSpec> buildPicker() {
        return FilterSelectionBinder.buildPicker(
            SCOPE_ID,
            HAZARDS,
            new ListSort<>(HazardSortMode.ALPHA, HazardSortMode.ALPHA.defaultDirection()),
            MODES,
            ListColumns.ONE,
            List.of());
    }

    // Stubs the text the binder resolves through this mod's strings table, so the assertions read
    // the wiring without the live table. A control's labels are copied and reject a null option
    // name, so every sort row must resolve to real text; the constant names stand in for the drawn
    // labels, which no assertion here reads.
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
