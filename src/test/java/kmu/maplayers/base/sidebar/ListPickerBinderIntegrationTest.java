package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListColumns;
import kmlib.starsector.ui.widgets.lists.ListPicker;
import kmlib.starsector.ui.widgets.lists.ListSortModes;
import kmlib.testfixtures.starsector.memory.SectorMemoryFake;
import kmlib.testfixtures.starsector.ui.widgets.lists.Anomaly;
import kmlib.testfixtures.starsector.ui.widgets.lists.AnomalySortMode;

import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.starsector.StarsectorUiColoursMock;
import kmu.util.KmuStringKeys;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static kmlib.testfixtures.starsector.ui.widgets.lists.ListPickerBlockReads.readColumnsSelector;
import static kmlib.testfixtures.starsector.ui.widgets.lists.ListPickerBlockReads.readItemList;
import static kmlib.testfixtures.starsector.ui.widgets.lists.ListPickerBlockReads.readSortSelector;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the whole road a consuming mod walks: rows of its own type, a sort vocabulary of its own and one
 * address in, a laid-out picker out, and every answer it reports landing in that mod's own save keys.
 * The binder, the three store binders, the three stores and the address types all run live over a save
 * fake, which is what parts this from the binder's own suite - that one mocks the stores and pins which
 * binder a report reaches, and this one pins the key it reaches the save at.
 *
 * <p>Run over KMLib's {@link Anomaly} rows and {@link AnomalySortMode} vocabulary under a stand-in
 * namespace, since a consumer is exactly a mod this one names nowhere. The frozen keys this mod's own
 * namespace composes are asserted absent beside every write, which is the half a shared store gets
 * wrong: two mods listing under one scope string wrote one key before the namespace parted them, and
 * nothing downstream could tell.
 *
 * <p>Each case rebuilds the picker after a click, because that is what the sidebar does - a body is
 * built per frame off the live stores - and because a read resolved at one address and a write filed at
 * another only shows up once the two meet. The direction flip is the sharpest form of it: it is
 * computed from what the previous build read back.
 */
final class ListPickerBinderIntegrationTest {

    // The consumer's address: a mod of no particular identity on a stand-in screen, listing under a
    // scope string this mod also uses nowhere. All three axes of a composed key, none of them ours.
    private static final SelectionSlot CONSUMER_SLOT = new SelectionSlot(
        new ScreenSelectionSlot(
            MapLayerStoreNamespaces.createStandInNamespace(),
            ScreenMemoryScopes.createStandInScreen()),
        "anomalies");

    // The keys both sides compose, as literals: what this suite is about is which spelling a pick
    // lands at, so composing the expectation through the address under test would assert nothing. The
    // host's are this mod's frozen spellings over the very same screen and scope, so the namespace is
    // the one axis parting them.
    private static final String CONSUMER_SORT_MODE_KEY = "$test_map_sort_mode_anomalies_test";
    private static final String CONSUMER_SORT_DIRECTION_KEY = "$test_map_sort_direction_anomalies_test";
    private static final String CONSUMER_COLUMNS_KEY = "$test_map_list_columns_test";

    private static final String HOST_SORT_MODE_KEY = "$kmu_map_sort_mode_anomalies_test";
    private static final String HOST_SORT_DIRECTION_KEY = "$kmu_map_sort_direction_anomalies_test";
    private static final String HOST_COLUMNS_KEY = "$kmu_map_list_columns_test";

    private static final ListSortModes<Anomaly> MODES =
        new ListSortModes<>(List.of(AnomalySortMode.values()), AnomalySortMode.ALPHA);

    private static final ListPicker<Anomaly> ANOMALY_PICKER = new ListPicker<>(
        List.of(
            new Anomaly("storm_1", "Storm", "crest_storm", 9, 8),
            new Anomaly("drift_1", "Drift", null, 2, 3)),
        MODES);

    // The sector's machinery the picker is built over: the board an item pick repaints through and the
    // slot a hovered row is recorded in both come off it. Neither is this suite's subject, but the
    // build takes one.
    private final SectorMapMachinery machinery = new SectorMapMachinery(null);

    private SectorMemoryFake sectorMemoryFake;

    private MockedStatic<KmuStringKeys> stringsMock;

    // The engine palette the picker resolves its row tones through, installed and taken down as one.
    private StarsectorUiColoursMock uiColours;

    @BeforeEach
    void openTheSave() {

        sectorMemoryFake = new SectorMemoryFake();
        uiColours = StarsectorUiColoursMock.install();

        // The one thing here that stays mocked beside the palette: the columns caption is the
        // framework's own chrome, read from a strings table no test JVM can open. The consumer's
        // sort labels are its own and arrive already drawn.
        stringsMock = Mockito.mockStatic(KmuStringKeys.class);
        stringsMock
            .when(() -> KmuStringKeys.get(KmuStringKeys.MAP_LAYER_CTL_COLUMNS_CAPTION))
            .thenReturn("Columns");
    }

    @AfterEach
    void closeTheSave() {

        stringsMock.close();
        uiColours.close();

        sectorMemoryFake.close();
    }

    @Nested
    class SortPicks {

        @Test
        void buildPickerPersistsASortPickUnderTheConsumersOwnKeys() {
            // Both halves of the pair land, since the selector reports a whole sort rather than the
            // half that moved: the mode picked and the direction that mode ranks in by default.
            pickSortMode(AnomalySortMode.SEVERITY);

            assertThat(sectorMemoryFake.readStoredValue(CONSUMER_SORT_MODE_KEY))
                .isEqualTo("severity");
            assertThat(sectorMemoryFake.readStoredValue(CONSUMER_SORT_DIRECTION_KEY))
                .isEqualTo("desc");
        }

        @Test
        void buildPickerLeavesThisModsSortKeysAloneOnAConsumersPick() {
            // The scope string and the screen are both this mod's own; only the namespace parts the
            // two, so this is the case that fails if a store ever composed a key without it.
            pickSortMode(AnomalySortMode.SEVERITY);

            assertThat(sectorMemoryFake.hasStoredValue(HOST_SORT_MODE_KEY))
                .isFalse();
            assertThat(sectorMemoryFake.hasStoredValue(HOST_SORT_DIRECTION_KEY))
                .isFalse();
        }

        @Test
        void buildPickerFlipsTheDirectionItReadBackOnARePick() {
            // A re-pick flips the direction the build read back, so this only passes if the sort is
            // read at the key it was written to. The first pick stores severity descending; the
            // rebuild lights that row; the second pick on it asks for the opposite.
            pickSortMode(AnomalySortMode.SEVERITY);
            pickSortMode(AnomalySortMode.SEVERITY);

            assertThat(sectorMemoryFake.readStoredValue(CONSUMER_SORT_MODE_KEY))
                .isEqualTo("severity");
            assertThat(sectorMemoryFake.readStoredValue(CONSUMER_SORT_DIRECTION_KEY))
                .isEqualTo("asc");
        }

        @Test
        void buildPickerRanksTheListByTheModeStoredUnderTheConsumersKey() {
            // What the stored pair is for: the rows arrive alpha-ordered as Drift then Storm, and a
            // stored severity-descending sort puts Storm first.
            sectorMemoryFake.storeValue(CONSUMER_SORT_MODE_KEY, "severity");
            sectorMemoryFake.storeValue(CONSUMER_SORT_DIRECTION_KEY, "desc");

            assertThat(readItemRowLabels())
                .containsExactly("Storm", "Drift");
        }
    }

    @Nested
    class ColumnPicks {

        @Test
        void buildPickerPersistsAColumnsPickUnderTheConsumersOwnKey() {
            pickTwoColumns();

            assertThat(sectorMemoryFake.readStoredValue(CONSUMER_COLUMNS_KEY))
                .isEqualTo("2");
        }

        @Test
        void buildPickerLeavesThisModsColumnKeyAloneOnAConsumersPick() {
            // The column count is kept per screen rather than per scope, so the namespace is the only
            // axis parting two mods' counts: without it a consumer's pick re-wrapped this mod's list.
            pickTwoColumns();

            assertThat(sectorMemoryFake.hasStoredValue(HOST_COLUMNS_KEY))
                .isFalse();
        }

        @Test
        void buildPickerWrapsTheListAcrossTheCountStoredUnderTheConsumersKey() {
            sectorMemoryFake.storeValue(CONSUMER_COLUMNS_KEY, "2");

            assertThat(readItemList(buildPicker()).columnCount())
                .isEqualTo(ListColumns.TWO.columnCount());
        }

        @Test
        void buildPickerIgnoresACountStoredUnderThisModsOwnKey() {
            // The read half of the same partitioning, and the half a caller resolving the count for
            // itself could get wrong: a count under another namespace is another mod's layout.
            sectorMemoryFake.storeValue(HOST_COLUMNS_KEY, "2");

            assertThat(readItemList(buildPicker()).columnCount())
                .isEqualTo(ListColumns.ONE.columnCount());
        }
    }

    // The block as the consumer's own body would build it: its rows and vocabulary, its address, and
    // nothing paired beside the sort.
    private List<ControlSpec> buildPicker() {
        return ListPickerBinder.buildPicker(CONSUMER_SLOT, ANOMALY_PICKER, List.of(), machinery);
    }

    // The item rows' names in the order the block draws them, which is the order the stored sort ranks
    // them in rather than the order the picker was handed. A row's name is its label's one run, the
    // crest and the sort value riding in the slots either side of it.
    private List<String> readItemRowLabels() {
        return readItemList(buildPicker())
            .labelledRows()
            .stream()
            .map(row -> ((TextSpan) row.labelRuns().get(0)).text())
            .toList();
    }

    // A click on one sort row of a freshly built block. Fresh every time, since the direction a click
    // reports is computed from what that build read back out of the save.
    private void pickSortMode(AnomalySortMode mode) {

        var sortSelector = readSortSelector(buildPicker());
        sortSelector.action().activateCell(List.of(AnomalySortMode.values()).indexOf(mode));
    }

    // A click on the two-column segment of a freshly built block.
    private void pickTwoColumns() {

        var columnsSelector = readColumnsSelector(buildPicker());
        columnsSelector.action().activateCell(List.of(ListColumns.values()).indexOf(ListColumns.TWO));
    }
}
