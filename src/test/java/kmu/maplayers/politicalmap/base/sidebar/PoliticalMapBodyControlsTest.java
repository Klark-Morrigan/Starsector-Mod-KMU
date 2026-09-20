package kmu.maplayers.politicalmap.base.sidebar;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.controls.specs.CheckboxSpec;
import kmlib.starsector.ui.controls.specs.ControlSpec;
import kmlib.starsector.ui.controls.specs.HorizontalRadioSpec;
import kmlib.starsector.ui.controls.specs.InteractiveSpec;
import kmlib.starsector.ui.controls.specs.ReselectBehaviour;
import kmlib.testfixtures.starsector.ui.intel.IntelScreenViewFake;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.FilterSelectionHeal;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry;
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;
import kmu.starsector.StarsectorSettingsFake;
import kmu.util.KmuStringKeys;

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
 * clicked view on the panel the radio sits on and heals the switched-in view's slot against its current
 * blocs - never clearing another view's stored selection. The registry, the strings, the filter
 * selection, and the selection heal are stubbed so this drives the selector's click action and pins only
 * what it does, not how the view or the filter persist.
 *
 * <p>The shared sub-options are pinned the same way: each lights off its per-save preference and writes
 * the flipped or clicked value back through it, with the preferences stubbed so the wiring is what is
 * asserted rather than how either choice persists. Each write is verified against the board and the
 * screen the controls were built with, which is what fails if either writer were handed one resolved
 * when the click landed: the repaint would then reach whichever sector was running rather than the one
 * whose sidebar is up, and the write would land on whichever panel was showing rather than the one the
 * control sits on. The intel screen is posed open throughout, so the built screen is never the live
 * one and a writer resolving its own could not pass by luck.
 */
final class PoliticalMapBodyControlsTest {

    // The engine tone the checkbox label carries, stood in for so the controls can be built without
    // the live palette in reach.
    private static final Color TEXT = Color.LIGHT_GRAY;

    // The board the shared controls are built against, standing in for one sector's installed
    // machinery. Held by identity rather than read for revisions: the preferences are stubbed, so
    // nothing raises on it and what each case pins is that this exact board reached the writer.
    private static final MapLayerRefreshBoard BUILT_BOARD = new MapLayerRefreshBoard();

    // The screen whose panel these controls were built on. A stand-in rather than one of the two live
    // screens, since what a control does with the screen it was built under is the same on either.
    private static final ScreenMemoryScope BUILT_SCREEN = ScreenMemoryScopes.createStandInScreen();

    // The panel the shared controls are built on, pairing the two above the way the tab's body build
    // does.
    private static final BodyControlTarget BUILT_TARGET =
        new BodyControlTarget(BUILT_BOARD, BUILT_SCREEN);

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

        // The intel screen posed open, so the live screen is a screen these controls were not built
        // on. The holder is static, so a fresh fake per test also keeps a neighbour's wiring out.
        var intelScreenFake = new IntelScreenViewFake();
        intelScreenFake.setIntelTabOpen(true);
        MapLayerScreens.registerIntelScreen(intelScreenFake);
    }

    @AfterEach
    void clearColours() {
        MapLayerScreens.registerIntelScreen(null);
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class SelectViewSegment {

        @Test
        void switchingToAViewSelectsItThenHealsTheSwitchedInViewsSlot() {
            // The click selects the clicked view on the panel the radio sits on, then heals that view's
            // own slot so a bloc it stored but that has since lapsed does not spotlight an empty
            // footprint. It does not clear - each view keeps its own selection across the switch. The
            // screen is asserted here rather than in a case of its own: the intel screen is posed open
            // throughout, so a selector resolving the showing screen files the switch under the visor
            // and fails this.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(1);

                registryMock.verify(() ->
                        PoliticalMapViewRegistry.selectView(BUILT_SCREEN, alliancesViewMock));
                healMock.verify(FilterSelectionHeal::healStaleSelectionAgainstActiveView);
            }
        }

        @Test
        void switchingViewsNeverClearsAStoredSelection() {
            // The regression this fix targets: a switch must not wipe the filter. selectViewSegment
            // touches no view's stored selection at all - the switched-in view loads its own.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class);
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
                    MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class);
                    MockedStatic<FilterSelectionHeal> healMock =
                            mockStatic(FilterSelectionHeal.class)) {
                stubSelectorViews(registryMock, stringsMock);

                clickViewSegment(5);

                registryMock.verify(
                        () -> PoliticalMapViewRegistry.selectView(any(), any()),
                        never());
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
                    MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class)) {
                stubSelectorViews(registryMock, stringsMock);

                var selector = PoliticalMapBodyControls.buildViewSelector(BUILT_SCREEN);

                assertThat(selector).isInstanceOf(HorizontalRadioSpec.class);
                assertThat(((HorizontalRadioSpec) selector).labels())
                        .containsExactly("Factions", "Alliances");
            }
        }

        @Test
        void staysLitOnARepickSoTheViewAxisIsNeverLeftEmpty() {
            // Re-picking the lit view must not clear it: the No Layer tab is the one control that
            // turns the map off, so the selector carries INERT rather than a deselecting row.
            try (MockedStatic<PoliticalMapViewRegistry> registryMock =
                            mockStatic(PoliticalMapViewRegistry.class);
                    MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class)) {
                stubSelectorViews(registryMock, stringsMock);

                var selector = (HorizontalRadioSpec) PoliticalMapBodyControls.buildViewSelector(BUILT_SCREEN);

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
            // keeps the sidebar the single control for the outline. Both halves go through the screen
            // the body was built on, so the box reports and sets its own panel's outline.
            try (MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class);
                    MockedStatic<UninhabitedOutlinePreference> preferenceMock =
                            mockStatic(UninhabitedOutlinePreference.class)) {
                stubControlLabels(stringsMock);
                preferenceMock
                        .when(() -> UninhabitedOutlinePreference.isOutlineDrawn(BUILT_SCREEN))
                        .thenReturn(true);

                var checkbox = (CheckboxSpec) PoliticalMapBodyControls
                        .buildSharedControls(BUILT_TARGET)
                        .get(0);
                checkbox.action().activateCell(0);

                assertThat(checkbox.isLit()).isTrue();
                preferenceMock.verify(() ->
                        UninhabitedOutlinePreference.setOutlineDrawn(BUILT_SCREEN, false, BUILT_BOARD));
            }
        }

        @Test
        void theNameRadioLightsAndSelectsThroughThePerSaveNamePreference() {
            // Full is the first segment, Short the second, No the third, so the lit segment and the
            // segment a click writes must both follow that order or the radio would report and set
            // the wrong choice.
            try (MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class);
                    MockedStatic<NameFormatPreference> preferenceMock =
                            mockStatic(NameFormatPreference.class)) {
                stubControlLabels(stringsMock);
                preferenceMock
                        .when(() -> NameFormatPreference.getSelectedNameFormat(BUILT_SCREEN))
                        .thenReturn(FactionNameFormatChoice.SHORT);

                var radio = (HorizontalRadioSpec) PoliticalMapBodyControls
                        .buildSharedControls(BUILT_TARGET).get(1);
                radio.action().activateCell(0);

                assertThat(radio.selectedIndex()).isEqualTo(1);
                preferenceMock.verify(() -> NameFormatPreference.selectNameFormat(
                        BUILT_SCREEN,
                        FactionNameFormatChoice.FULL,
                        BUILT_BOARD));
            }
        }

        @Test
        void theNameRadiosNoSegmentTurnsTheNamesOffThroughTheSamePreference() {
            // No is a choice on the same radio rather than a control of its own, so clicking it must
            // write NONE through the one preference - the wiring that replaced the separate names
            // toggle.
            try (MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class);
                    MockedStatic<NameFormatPreference> preferenceMock =
                            mockStatic(NameFormatPreference.class)) {
                stubControlLabels(stringsMock);
                preferenceMock
                        .when(() -> NameFormatPreference.getSelectedNameFormat(BUILT_SCREEN))
                        .thenReturn(FactionNameFormatChoice.NONE);

                var radio = (HorizontalRadioSpec) PoliticalMapBodyControls
                        .buildSharedControls(BUILT_TARGET).get(1);
                radio.action().activateCell(2);

                assertThat(radio.selectedIndex()).isEqualTo(2);
                preferenceMock.verify(() -> NameFormatPreference.selectNameFormat(
                        BUILT_SCREEN,
                        FactionNameFormatChoice.NONE,
                        BUILT_BOARD));
            }
        }

        @Test
        void theSubOptionsWriteTheScreenTheyWereBuiltOnRatherThanTheLiveOne() {
            // The panel a control sits on is settled when the body is built, not when the click lands:
            // a writer resolving the showing screen instead would file a map-panel flip under the intel
            // screen, which is what the intel tab being posed open here would let through. Both
            // sub-options are driven, since either one resolving its own screen is the same fault.
            try (MockedStatic<KmuStringKeys> stringsMock = mockStatic(KmuStringKeys.class);
                    MockedStatic<UninhabitedOutlinePreference> outlineMock =
                            mockStatic(UninhabitedOutlinePreference.class);
                    MockedStatic<NameFormatPreference> nameFormatMock =
                            mockStatic(NameFormatPreference.class)) {
                stubControlLabels(stringsMock);
                nameFormatMock
                        .when(() -> NameFormatPreference.getSelectedNameFormat(BUILT_SCREEN))
                        .thenReturn(FactionNameFormatChoice.FULL);

                var controls = PoliticalMapBodyControls.buildSharedControls(BUILT_TARGET);
                ((InteractiveSpec) controls.get(0)).action().activateCell(0);
                ((InteractiveSpec) controls.get(1)).action().activateCell(1);

                outlineMock.verify(() ->
                        UninhabitedOutlinePreference.setOutlineDrawn(BUILT_SCREEN, true, BUILT_BOARD));
                nameFormatMock.verify(() -> NameFormatPreference.selectNameFormat(
                        BUILT_SCREEN,
                        FactionNameFormatChoice.SHORT,
                        BUILT_BOARD));
            }
        }
    }

    // Stubs every control caption to one placeholder, since the shared controls' specs hold their
    // resolved strings and the radio rejects a null label. What a caption reads is the strings table's
    // concern, not this class's, so one stand-in covers them all.
    private static void stubControlLabels(MockedStatic<KmuStringKeys> stringsMock) {
        stringsMock.when(() -> KmuStringKeys.get(anyString())).thenReturn("caption");
    }

    // Fires the selector's click action for the segment at the given index, the path a click on that
    // view's radio row takes - the only way to reach the private selectViewSegment the selector wires.
    private static void clickViewSegment(int segmentIndex) {
        // The selector is a horizontal radio (an Interactive control), so its click action drives the
        // private selectViewSegment the selector wires.
        var selector = (InteractiveSpec) PoliticalMapBodyControls.buildViewSelector(BUILT_SCREEN);
        selector.action().activateCell(segmentIndex);
    }

    // Stubs the two registered views and their radio labels, so building the selector and resolving a
    // clicked segment both read the same [Factions, Alliances] order without the live strings table.
    private void stubSelectorViews(MockedStatic<PoliticalMapViewRegistry> registryMock,
            MockedStatic<KmuStringKeys> stringsMock) {
        when(factionsViewMock.getSegmentLabelKey()).thenReturn("factions_label");
        when(alliancesViewMock.getSegmentLabelKey()).thenReturn("alliances_label");
        registryMock.when(PoliticalMapViewRegistry::getViews)
                .thenReturn(List.of(factionsViewMock, alliancesViewMock));
        registryMock.when(() -> PoliticalMapViewRegistry.getSelectedViewIndex(BUILT_SCREEN))
                .thenReturn(ControlSpec.NO_SELECTION);
        stringsMock.when(() -> KmuStringKeys.get("factions_label")).thenReturn("Factions");
        stringsMock.when(() -> KmuStringKeys.get("alliances_label")).thenReturn("Alliances");
    }
}
