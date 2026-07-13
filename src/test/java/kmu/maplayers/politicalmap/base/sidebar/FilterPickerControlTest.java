package kmu.maplayers.politicalmap.base.sidebar;

import kmlib.starsector.ui.controls.ControlKind;
import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.RadioAlignment;

import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.SelectableBloc;
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
 * Pins the spotlight picker's body controls, top to bottom: a section rule, the shared recede control
 * only while a bloc is spotlighted, and the vertical icon-radio list of selectable blocs. Also pins
 * the click wiring: an unlit option spotlights its bloc, the lit option clears the filter. Strings,
 * the recede preferences, and the filter selection are stubbed so this pins the picker's shape and
 * wiring alone, not how a string resolves or how the selection persists.
 */
final class FilterPickerControlTest {
    // The two blocs the picker lists in every test: a crested faction and a crestless one (an
    // alliance, or a faction with no authored crest), so the null-crest path is exercised too.
    private static final SelectableBloc HEGEMONY =
            new SelectableBloc("hegemony", "Hegemony", "crest_heg");
    private static final SelectableBloc TRADERS =
            new SelectableBloc("free_traders", "Free Traders", null);
    private static final List<SelectableBloc> BLOCS = List.of(HEGEMONY, TRADERS);

    // The section rule always heads the block. The list is always the last row - its offset shifts by
    // the recede rows when a bloc is spotlighted - so tests read it from the tail rather than a fixed
    // index.
    private static final int DIVIDER = 0;

    @Nested
    class BuildControls {

        @Test
        void buildControlsReturnsNothingWhenNoBlocsAreSelectable() {
            // A view with no visible weighted bloc contributes no picker at all, so the body carries
            // no empty list widget.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                assertThat(FilterPickerControl.buildControls(List.of(), null)).isEmpty();
            }
        }

        @Test
        void buildControlsHeadsWithADivider() {
            // The section rule replaces the old caption string: it heads the block with no text,
            // parting the view-level controls above from the picker below.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var divider = FilterPickerControl.buildControls(BLOCS, null).get(DIVIDER);

                assertThat(divider.kind()).isEqualTo(ControlKind.DIVIDER);
            }
        }

        @Test
        void buildControlsBuildsAVerticalDeselectableIconListOfTheBlocs() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(FilterPickerControl.buildControls(BLOCS, null));

                assertThat(picker.kind()).isEqualTo(ControlKind.RADIO);
                assertThat(picker.alignment()).isEqualTo(RadioAlignment.VERTICAL);
                assertThat(picker.canDeselect()).isTrue();
                // Labels are the bloc names, the icons the crests, aligned index for index so a
                // crestless bloc rides as a null entry rather than dropping a row.
                assertThat(picker.labels()).containsExactly("Hegemony", "Free Traders");
                assertThat(picker.iconPaths()).containsExactly("crest_heg", null);
            }
        }

        @Test
        void buildControlsDrawsEachBlocsDominationCountAsItsTrailingValue() {
            // The list reads as a ranked table: each row's trailing value is the count of systems the
            // bloc dominates, kept aligned index for index so a bloc that dominates nothing shows "0"
            // rather than dropping the column.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);
                var hegemony = new SelectableBloc("hegemony", "Hegemony", "crest_heg",
                        new BlocStats(5, 8, 40, 12));
                var traders = new SelectableBloc("free_traders", "Free Traders", null,
                        new BlocStats(0, 3, 6, 4));

                var picker = pickerOf(
                        FilterPickerControl.buildControls(List.of(hegemony, traders), null));

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

                var picker = pickerOf(FilterPickerControl.buildControls(List.of(nameless), null));

                assertThat(picker.labels()).containsExactly("");
            }
        }

        @Test
        void buildControlsLightsTheSpotlightedBlocsRow() {
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(FilterPickerControl.buildControls(BLOCS, "free_traders"));

                assertThat(picker.selectedIndex()).isEqualTo(1);
            }
        }

        @Test
        void buildControlsLightsNoRowWhenTheStoredBlocIsNotInTheList() {
            // A stale stored id (the load heal has not run, or the bloc's market vanished mid-session)
            // lights nothing, so the list still shows every real option to pick from.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                var picker = pickerOf(FilterPickerControl.buildControls(BLOCS, "vanished"));

                assertThat(picker.selectedIndex()).isEqualTo(ControlSpec.NO_SELECTION);
            }
        }

        @Test
        void buildControlsOmitsTheRecedeControlWhenNoBlocIsSpotlighted() {
            // With no filter the sector draws normally, so there is nothing to recede - the block is
            // just the divider and the list.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubCaptions(stringsMock);

                assertThat(FilterPickerControl.buildControls(BLOCS, null)).hasSize(2);
            }
        }

        @Test
        void buildControlsInsertsTheRecedeControlAboveTheListWhenABlocIsSpotlighted() {
            // While filtering the recede control rides between the divider and the list: its caption
            // then the Mute and Desaturate checkboxes, so the block is divider + three recede rows +
            // list.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<RecedePreferences> preferencesMock =
                            mockStatic(RecedePreferences.class)) {
                stubCaptions(stringsMock);

                var controls = FilterPickerControl.buildControls(BLOCS, "hegemony");

                assertThat(controls).hasSize(5);
                assertThat(controls.get(1).kind()).isEqualTo(ControlKind.LABEL);
                assertThat(controls.get(1).labels()).containsExactly("Rest of the sector is");
                assertThat(controls.get(2).kind()).isEqualTo(ControlKind.CHECKBOX);
                assertThat(controls.get(3).kind()).isEqualTo(ControlKind.CHECKBOX);
                assertThat(controls.get(4).kind()).isEqualTo(ControlKind.RADIO);
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
                var picker = pickerOf(FilterPickerControl.buildControls(BLOCS, null));

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
                var picker = pickerOf(FilterPickerControl.buildControls(BLOCS, "hegemony"));

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
                var picker = pickerOf(FilterPickerControl.buildControls(BLOCS, "hegemony"));

                picker.action().activateCell(1);

                selectionMock.verify(() -> FilterSelection.selectBloc("free_traders"));
            }
        }
    }

    // The picker list is always the block's last row, so a test reads it from the tail rather than a
    // fixed index that would shift with the recede rows.
    private static ControlSpec pickerOf(List<ControlSpec> controls) {
        return controls.get(controls.size() - 1);
    }

    // Stubs the caption strings the picker heads its rows with, so the assertions read the wiring
    // without the live strings table. The recede checkbox labels are only reached in tests that mock
    // RecedePreferences, which stub them there.
    private static void stubCaptions(MockedStatic<KmuStrings> stringsMock) {
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION))
                .thenReturn("Rest of the sector is");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_MUTED)).thenReturn("Muted");
        stringsMock.when(() -> KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_DESATURATED))
                .thenReturn("Desaturated");
    }
}
