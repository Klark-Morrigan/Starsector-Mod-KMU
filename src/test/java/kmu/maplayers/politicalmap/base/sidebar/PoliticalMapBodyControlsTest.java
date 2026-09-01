package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.controls.ReselectBehaviour;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.FilterSelectionHeal;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * Pins the view selector's switch rule: each view remembers its own spotlight, so switching selects the
 * clicked view and heals the switched-in view's slot against its current blocs - never clearing another
 * view's stored selection. The registry, the strings, the filter selection, and the selection heal are
 * stubbed so this drives the selector's click action and pins only what it does, not how the view or the
 * filter persist.
 *
 * <p>The shared sub-options are pinned the same way: each lights off its per-save preference and writes
 * the flipped or clicked value back through it, with the preferences stubbed so the wiring is what is
 * asserted rather than how either choice persists. Each write is verified against the board the
 * controls were built with, which is what fails if either writer were handed a board resolved when
 * the click landed: the repaint would then reach whichever sector was running rather than the one
 * whose sidebar is up.
 */
final class PoliticalMapBodyControlsTest {

    // The engine tone the checkbox label carries, stood in for so the controls can be built without
    // the live palette in reach.
    private static final Color TEXT = Color.LIGHT_GRAY;

    // The board the shared controls are built against, standing in for one sector's installed
    // machinery. Held by identity rather than read for revisions: the preferences are stubbed, so
    // nothing raises on it and what each case pins is that this exact board reached the writer.
    private static final MapLayerRefreshBoard BUILT_BOARD = new MapLayerRefreshBoard();

    private final PoliticalMapView factionsViewMock = mock(PoliticalMapView.class);
    private final PoliticalMapView alliancesViewMock = mock(PoliticalMapView.class);

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
    class SelectViewSegment {

        @Test
        void switchingToAViewSelectsItThenHealsTheSwitchedInViewsSlot() {
            // The click selects the clicked view, then heals that view's own slot so a bloc it stored
            // but that has since lapsed does not spotlight an empty footprint. It does not clear - each
            // view keeps its own selection across the switch.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(1);

                registryMock.verify(() -> PoliticalMapViewRegistry.selectView(alliancesViewMock));
                healMock.verify(FilterSelectionHeal::healStaleSelectionAgainstActiveView);
            }
        }

        @Test
        void switchingViewsNeverClearsAStoredSelection() {
            // The regression this fix targets: a switch must not wipe the filter. selectViewSegment
            // touches no view's stored selection at all - the switched-in view loads its own.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class);
                    MockedStatic<FilterSelection> selectionMock =
                            mockStatic(FilterSelection.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(1);

                selectionMock.verifyNoInteractions();
            }
        }

        @Test
        void ignoresASegmentOutsideTheRegisteredViews() {
            // A stray hit past the last view neither selects a view nor heals, so it changes nothing.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(5);

                registryMock.verify(() -> PoliticalMapViewRegistry.selectView(any()), never());
                healMock.verifyNoInteractions();
            }
        }
    }

    @Nested
    class BuildViewSelector {

        @Test
        void buildsAHorizontalRadioWithOneSegmentPerView() {
            // The views lay side by side on one row (Factions | Alliances), so the selector is a
            // horizontal radio carrying a segment per registered view in registry order.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubSelectorViews(registryMock, stringsMock);

                var selector = PoliticalMapBodyControls.buildViewSelector();

                assertThat(selector).isInstanceOf(ControlSpec.HorizontalRadio.class);
                assertThat(((ControlSpec.HorizontalRadio) selector).labels())
                        .containsExactly("Factions", "Alliances");
            }
        }

        @Test
        void staysLitOnARepickSoTheViewAxisIsNeverLeftEmpty() {
            // Re-picking the lit view must not clear it: the No Layer tab is the one control that
            // turns the map off, so the selector carries INERT rather than a deselecting row.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class)) {
                stubSelectorViews(registryMock, stringsMock);

                var selector = (ControlSpec.HorizontalRadio) PoliticalMapBodyControls.buildViewSelector();

                assertThat(selector.reselect()).isEqualTo(ReselectBehaviour.INERT);
            }
        }
    }

    @Nested
    class BuildSharedControls {

        @Test
        void theUninhabitedCheckboxLightsAndFlipsThePerSaveOutlinePreference() {
            // Both toggles are per-save preferences rather than settings fields, so the checkbox has
            // to light off the preference and write the opposite back through it - the wiring that
            // keeps the sidebar the single control for the outline.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<UninhabitedOutlinePreference> preferenceMock =
                            mockStatic(UninhabitedOutlinePreference.class)) {
                stubControlLabels(stringsMock);
                preferenceMock.when(UninhabitedOutlinePreference::isOutlineDrawn).thenReturn(true);

                var checkbox = (ControlSpec.Checkbox) PoliticalMapBodyControls
                        .buildSharedControls(BUILT_BOARD)
                        .get(0);
                checkbox.action().activateCell(0);

                assertThat(checkbox.isLit()).isTrue();
                preferenceMock.verify(() -> UninhabitedOutlinePreference.setOutlineDrawn(false, BUILT_BOARD));
            }
        }

        @Test
        void theNameRadioLightsAndSelectsThroughThePerSaveNamePreference() {
            // Full is the first segment, Short the second, No the third, so the lit segment and the
            // segment a click writes must both follow that order or the radio would report and set
            // the wrong choice.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<NameFormatPreference> preferenceMock =
                            mockStatic(NameFormatPreference.class)) {
                stubControlLabels(stringsMock);
                preferenceMock.when(NameFormatPreference::getSelectedNameFormat)
                        .thenReturn(FactionNameFormatChoice.SHORT);

                var radio = (ControlSpec.HorizontalRadio) PoliticalMapBodyControls
                        .buildSharedControls(BUILT_BOARD).get(1);
                radio.action().activateCell(0);

                assertThat(radio.selectedIndex()).isEqualTo(1);
                preferenceMock.verify(() ->
                        NameFormatPreference.selectNameFormat(FactionNameFormatChoice.FULL, BUILT_BOARD));
            }
        }

        @Test
        void theNameRadiosNoSegmentTurnsTheNamesOffThroughTheSamePreference() {
            // No is a choice on the same radio rather than a control of its own, so clicking it must
            // write NONE through the one preference - the wiring that replaced the separate names
            // toggle.
            try (MockedStatic<KmuStrings> stringsMock = mockStatic(KmuStrings.class);
                    MockedStatic<NameFormatPreference> preferenceMock =
                            mockStatic(NameFormatPreference.class)) {
                stubControlLabels(stringsMock);
                preferenceMock.when(NameFormatPreference::getSelectedNameFormat)
                        .thenReturn(FactionNameFormatChoice.NONE);

                var radio = (ControlSpec.HorizontalRadio) PoliticalMapBodyControls
                        .buildSharedControls(BUILT_BOARD).get(1);
                radio.action().activateCell(2);

                assertThat(radio.selectedIndex()).isEqualTo(2);
                preferenceMock.verify(() ->
                        NameFormatPreference.selectNameFormat(FactionNameFormatChoice.NONE, BUILT_BOARD));
            }
        }
    }

    // Stubs every control caption to one placeholder, since the shared controls' specs hold their
    // resolved strings and the radio rejects a null label. What a caption reads is the strings table's
    // concern, not this class's, so one stand-in covers them all.
    private static void stubControlLabels(MockedStatic<KmuStrings> stringsMock) {
        stringsMock.when(() -> KmuStrings.get(anyString())).thenReturn("caption");
    }

    // Fires the selector's click action for the segment at the given index, the path a click on that
    // view's radio row takes - the only way to reach the private selectViewSegment the selector wires.
    private static void clickViewSegment(int segmentIndex) {
        // The selector is a horizontal radio (an Interactive control), so its click action drives the
        // private selectViewSegment the selector wires.
        var selector = (ControlSpec.Interactive) PoliticalMapBodyControls.buildViewSelector();
        selector.action().activateCell(segmentIndex);
    }

    // Stubs the two registered views and their radio labels, so building the selector and resolving a
    // clicked segment both read the same [Factions, Alliances] order without the live strings table.
    private void stubSelectorViews(MockedStatic<PoliticalMapViewRegistry> registryMock,
            MockedStatic<KmuStrings> stringsMock) {
        when(factionsViewMock.getSegmentLabelKey()).thenReturn("factions_label");
        when(alliancesViewMock.getSegmentLabelKey()).thenReturn("alliances_label");
        registryMock.when(PoliticalMapViewRegistry::getViews)
                .thenReturn(List.of(factionsViewMock, alliancesViewMock));
        registryMock.when(PoliticalMapViewRegistry::getSelectedViewIndex)
                .thenReturn(ControlSpec.NO_SELECTION);
        stringsMock.when(() -> KmuStrings.get("factions_label")).thenReturn("Factions");
        stringsMock.when(() -> KmuStrings.get("alliances_label")).thenReturn("Alliances");
    }
}
