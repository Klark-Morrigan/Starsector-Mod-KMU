package kmu.maplayers.base.sidebar;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.controls.ControlHoverReport;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.ListSort;
import kmlib.starsector.ui.widgets.lists.ListSortModes;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Pins the join between KMLib's picker and the calling mod's save slots, which is the whole of what the
 * binder does: the spotlighted id and the stored sort are read off the shared stores for the slot on
 * the way in, and each of the picker's three picks reaches the store that keeps it on the way out. The
 * picker's own shape and click rules are KMLib's and are pinned there; the stores are mocked, so this
 * reads the wiring alone.
 *
 * <p>The slot is pinned as the one the picker was built for rather than one resolved when a report
 * lands, since a report can land after the player has moved: a pick belongs to the panel it was
 * clicked on.
 *
 * <p>Each item pick is verified against the board of the installation the picker was built over,
 * and each hover read back off that same installation's slot. Both are what fails if the store ever
 * resolved either when the report landed: the spotlight would repaint whichever sector was running
 * rather than the one the picker was listed for, and the preview would light that sector's map.
 *
 * <p>Run over the foreign {@link Hazard} item and {@link HazardSortMode} vocabulary, since the
 * binder is no more one layer's than the picker it binds. That is also what proves the wildcard
 * capture is shaped by no layer in particular: these types stand in for a second layer's list, so a
 * capture that only worked over the first layer's own item type would not compile here.
 */
final class FilterSelectionBinderTest {

    // The slot a pick, clear or hover is read from and written into: the mod and panel this picker is
    // built for, and the list it lists. A stand-in mod on a stand-in screen, since neither which mod
    // nor which panel it is changes anything here.
    private static final MapLayerStoreNamespace NAMESPACE =
        MapLayerStoreNamespaces.createStandInNamespace();

    private static final SelectionSlot SLOT = new SelectionSlot(
        new ScreenSelectionSlot(NAMESPACE, ScreenMemoryScopes.createStandInScreen()),
        "hazards");

    // The same list on the other panel, for the case that pins a pick landing where it was clicked.
    private static final SelectionSlot OTHER_SCREEN_SLOT = new SelectionSlot(
        new ScreenSelectionSlot(NAMESPACE, ScreenMemoryScopes.createOtherStandInScreen()),
        "hazards");

    private static final ListSortModes<Hazard> MODES =
        new ListSortModes<>(List.of(HazardSortMode.values()), HazardSortMode.ALPHA);

    // The two items the picker lists, alpha-ordered as Drift then Storm, so a row index maps back to
    // a known id.
    private static final Hazard STORM = new Hazard("storm_1", "Storm", "crest_storm", 9, 8);
    private static final Hazard DRIFT = new Hazard("drift_1", "Drift", null, 2, 3);

    private static final ListPicker<Hazard> HAZARD_PICKER =
        new ListPicker<>(List.of(STORM, DRIFT), MODES);

    // The rows the picker lays out, so a test names the widget it clicks rather than an index into
    // the block.
    private static final int COLUMNS_SELECTOR = 1;
    private static final int SORT_ROW = 2;

    // The sector's machinery the picker is built over, which is where both of its writers come from:
    // the board an item pick repaints through and the slot a hovered row is recorded in. One per
    // case, so neither a spotlight nor a hover left standing is one the next case starts from.
    private final MapLayerInstallation installation = new MapLayerInstallation(null);

    // Those two, taken the way the binder takes them. Named here rather than resolved at each
    // assertion so a case reads as "this sector's board" and "this sector's slot" - and so a binder
    // that reached past its installation for either shows up as a write nothing observed.
    private final MapLayerRefreshBoard builtBoard = installation.resolveRefreshBoard();
    private final FilterHoverSlot builtHoverSlot = FilterHoverSlot.resolveHoverSlotIn(installation);

    private MockedStatic<Misc> miscMock;

    // Mocked for every test, since the binder now resolves the stored sort itself: left live it
    // would read a sector memory no test JVM has. Stubbed to the alpha mode in its own direction,
    // which is what a save that has never picked a sort reads.
    private MockedStatic<SortSelectionBinder> sortBinderMock;

    @BeforeEach
    void installColours() {
        // Settings first, then the Misc statics: Misc's class initialiser reads the settings, so
        // mocking it against an uninstalled settings proxy would fail on class load.
        StarsectorSettingsFake.installSettings();

        // Both row tones, since the picker resolves the receded one whether or not a row uses it -
        // an unlisted stand-in fails on the read rather than on anything under test here.
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(Color.LIGHT_GRAY);
        miscMock
            .when(Misc::getGrayColor)
            .thenReturn(Color.DARK_GRAY);

        sortBinderMock = Mockito.mockStatic(SortSelectionBinder.class);
        sortBinderMock
            .when(() -> SortSelectionBinder.resolveStoredSort(SLOT, MODES))
            .thenReturn(sortOf(HazardSortMode.ALPHA));
    }

    @AfterEach
    void clearColours() {

        sortBinderMock.close();
        miscMock.close();

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class BuildPicker {

        @Test
        void buildPickerLightsTheRowTheSlotsStoredIdNames() {
            // The id the picker lights comes from this slot, which is the read half of the
            // binding - a picker handed nothing would light no row whatever the save holds.
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                stubLabels(stringsMock);

                selectionMock
                    .when(() -> FilterSelection.getSelectedIdOf(SLOT))
                    .thenReturn("storm_1");

                var picker = buildPickerFor(buildPicker());

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
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                stubLabels(stringsMock);

                var columnsSelector = (ControlSpec.HorizontalRadio) buildPicker()
                    .get(COLUMNS_SELECTOR);

                assertThat(columnsSelector.trailingLabel())
                    .isEqualTo("Columns");
            }
        }

        @Test
        void buildPickerWritesAnItemPickIntoTheSlot() {
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                stubLabels(stringsMock);

                var picker = buildPickerFor(buildPicker());
                picker.action().activateCell(0);

                selectionMock.verify(
                    () -> FilterSelection.selectId(SLOT, "drift_1", builtBoard));
            }
        }

        @Test
        void buildPickerWritesAnItemPickUnderTheSlotItWasBuiltFor() {
            // The slot is captured at the build, so a picker stood on one panel files its pick there
            // whatever screen is up when the click is handled.
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                stubLabels(stringsMock);

                sortBinderMock
                    .when(() -> SortSelectionBinder.resolveStoredSort(OTHER_SCREEN_SLOT, MODES))
                    .thenReturn(sortOf(HazardSortMode.ALPHA));

                var picker = buildPickerFor(FilterSelectionBinder.buildPicker(
                    OTHER_SCREEN_SLOT,
                    HAZARD_PICKER,
                    ListColumns.ONE,
                    List.of(),
                    installation));
                picker.action().activateCell(0);

                selectionMock.verify(
                    () -> FilterSelection.selectId(OTHER_SCREEN_SLOT, "drift_1", builtBoard));
            }
        }

        @Test
        void buildPickerClearsTheSlotOnARePick() {
            // The picker reports a clear rather than a pick when the lit row is re-clicked, and the
            // clear lands on this slot alone.
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                stubLabels(stringsMock);

                selectionMock
                    .when(() -> FilterSelection.getSelectedIdOf(SLOT))
                    .thenReturn("drift_1");

                var picker = buildPickerFor(buildPicker());
                picker.action().activateCell(0);

                selectionMock.verify(
                    () -> FilterSelection.clearSelection(SLOT, builtBoard));
            }
        }

        @Test
        void buildPickerRecordsAHoveredRowInTheSlotsScopesHoverSlot() {
            // The pointer's row reaches the sector's own slot, which is what a layer previews the
            // spotlight from. Written into the scope the picks use, so a layer cannot end up
            // previewing one scope's row while filtering by another's.
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                stubLabels(stringsMock);

                var picker = buildPickerFor(buildPicker());
                picker.hoverReport().reportHoveredCell(0);

                assertThat(builtHoverSlot.getHoveredIdOf(PickerScope.resolveScopeOf(SLOT)))
                    .isEqualTo("drift_1");
            }
        }

        @Test
        void buildPickerClearsTheHoverSlotWhenThePointerLeavesTheList() {
            // The leave is the one reading a stream never says out loud, so it arrives as a report
            // of its own - swallowed here, a layer would go on previewing the last row the pointer
            // crossed while the pointer is somewhere else entirely.
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                stubLabels(stringsMock);

                var picker = buildPickerFor(buildPicker());

                picker.hoverReport().reportHoveredCell(0);
                picker.hoverReport().reportHoveredCell(ControlHoverReport.NO_CELL_HOVERED);

                assertThat(builtHoverSlot.getHoveredIdOf(PickerScope.resolveScopeOf(SLOT)))
                    .isNull();
            }
        }

        @Test
        void buildPickerRoutesAColumnsPickToTheColumnBinder() {
            // The other two picks are handed to the binders that already own those slots. Asserted
            // at the binder rather than at the sector-memory key behind it: what this class decides
            // is which binder a report goes to, and the binder's own suite pins the write.
            try (var stringsMock = mockStatic(KmuStrings.class);
                    var binderMock = mockStatic(ColumnSelectionBinder.class)) {

                stubLabels(stringsMock);

                var columnsSelector = (ControlSpec.HorizontalRadio) buildPicker()
                    .get(COLUMNS_SELECTOR);

                columnsSelector.action().activateCell(
                    List.of(ListColumns.values()).indexOf(ListColumns.TWO));

                binderMock.verify(
                    () -> ColumnSelectionBinder.storeColumns(SLOT.screenSlot(), ListColumns.TWO));
            }
        }

        @Test
        void buildPickerRoutesASortPickToTheSortBinder() {
            try (var stringsMock = mockStatic(KmuStrings.class)) {

                stubLabels(stringsMock);

                var sortSelector = (ControlSpec.VerticalTable)
                    ((ControlSpec.SideBySide) buildPicker().get(SORT_ROW))
                        .leftColumn()
                        .get(0);

                sortSelector.action().activateCell(
                    List.of(HazardSortMode.values()).indexOf(HazardSortMode.SEVERITY));

                sortBinderMock.verify(
                    () -> SortSelectionBinder.storeSort(SLOT, sortOf(HazardSortMode.SEVERITY)));
            }
        }

        @Test
        void buildPickerRanksTheListByTheVocabularyTheCallersPickerCarries() {
            // The read half of the same tie: the stored sort is resolved against the vocabulary
            // that arrived bundled with the items, not against one this class names, which is what
            // lets two layers holding different vocabularies share the one binder. Read under the
            // scope the item pick uses, so a layer cannot bind its filter and its sort to different
            // slots.
            try (var stringsMock = mockStatic(KmuStrings.class)) {

                stubLabels(stringsMock);
                buildPicker();

                sortBinderMock.verify(
                    () -> SortSelectionBinder.resolveStoredSort(SLOT, MODES));
            }
        }

        @Test
        void buildPickerContributesNothingForAnOfferNothingPickerWithoutReadingItsVocabulary() {
            // An empty picker carries no fallback mode, so the item list has to be found empty
            // before any stored sort is resolved - resolving first would land on nothing. Both
            // halves are asserted, since returning no controls while still reading the vocabulary
            // would fail only once a caller actually handed over an empty picker in play.
            try (var stringsMock = mockStatic(KmuStrings.class)) {

                stubLabels(stringsMock);

                assertThat(FilterSelectionBinder.buildPicker(
                        SLOT,
                        ListPicker.empty(),
                        ListColumns.ONE,
                        List.of(),
                        installation))
                    .isEmpty();

                sortBinderMock.verify(
                    () -> SortSelectionBinder.resolveStoredSort(any(), any()),
                    never());
            }
        }
    }

    // The picker list is always the block's last row, so a test reads it from the tail.
    private static ControlSpec.VerticalTable buildPickerFor(List<ControlSpec> controls) {
        return (ControlSpec.VerticalTable) controls.get(controls.size() - 1);
    }

    // The one call into the binder every test goes through: the two items with their own
    // vocabulary, a single column, and nothing paired beside the sort, since none of those is what
    // this suite varies.
    private List<ControlSpec> buildPicker() {
        return FilterSelectionBinder.buildPicker(
            SLOT,
            HAZARD_PICKER,
            ListColumns.ONE,
            List.of(),
            installation);
    }

    // A mode in its own natural direction over the foreign vocabulary - what a save that has never
    // flipped the sort reads, and what a click on that mode's row reports back.
    private static ListSort<Hazard> sortOf(HazardSortMode mode) {
        return new ListSort<>(mode, mode.defaultDirection(), MODES);
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
