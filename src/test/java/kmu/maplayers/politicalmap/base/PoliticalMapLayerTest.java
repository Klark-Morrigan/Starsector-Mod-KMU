package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.FilterSelectionBinder;
import kmu.maplayers.politicalmap.base.render.PoliticalMapLayerRenderer;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins how the political-map tab composes its body: the shared sub-options, then the view-selector
 * radio, then the spotlight picker, then the selected view's own controls appended beneath - so a
 * view shows the filter list plus widgets specific to it (the alliances view's Mute/Desaturate
 * checkboxes) while the tab itself names no concrete view. The shared controls, the selector, and the
 * picker are stubbed to sentinels so this pins the composition order alone, not what those pieces
 * contain.
 */
final class PoliticalMapLayerTest {

    // The frozen sector-memory key the active-view selection serialises under, pinned as a literal so
    // a rename that would reset every save to the default fails here rather than shipping.
    private static final String ACTIVE_VIEW_KEY = "$kmu_political_active_view";

    // Sentinels standing in for the two view-agnostic pieces, so the assertions read the composition
    // order without depending on the real shared controls or selector contents. Their tone is
    // arbitrary - composition order is what is under test, not what colour a control draws in.
    private static final Color MARKER_COLOUR = Color.WHITE;
    private static final ControlSpec SHARED_MARKER = buildMarker("shared");
    private static final ControlSpec SELECTOR_MARKER = buildMarker("selector");
    private static final ControlSpec VIEW_MARKER = buildMarker("view");
    private static final ControlSpec PICKER_MARKER = buildMarker("picker");

    private final PoliticalMapView viewWithControlsMock = mock(PoliticalMapView.class);
    private final PoliticalMapView viewWithoutControlsMock = mock(PoliticalMapView.class);

    @Nested
    class GetMapRenderer {

        @Test
        void getMapRendererReturnsThePoliticalMapsOwnRenderer() {
            // The counterpart to No Layer's null: this tab is the one that draws, and it hands the map
            // surface one view-neutral renderer rather than branching on the view roster.
            assertThat(PoliticalMapLayer.INSTANCE.getMapRenderer())
                .isSameAs(PoliticalMapLayerRenderer.INSTANCE);
        }
    }

    @Nested
    class GetBodyControls {

        @Test
        void getBodyControlsAppendsTheSelectedViewsControlsAfterTheSelector() {
            when(viewWithControlsMock.getViewBodyControls()).thenReturn(List.of(VIEW_MARKER));
            registerDefaultView(viewWithControlsMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<PoliticalMapBodyControls> controlsMock =
                        mockStatic(PoliticalMapBodyControls.class)) {

                // No sector resolves the default view as selected, so the registered view paints.
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the picker's sort selector carries the engine's text tone,
                // which Misc reads off the live settings - so a body built here needs a settings proxy
                // that answers a colour. Stubbed before the static stubbing opens, since its own
                // stubbing would otherwise land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls();

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER, VIEW_MARKER);
            }
        }

        @Test
        void getBodyControlsPlacesTheSpotlightPickerBetweenTheSelectorAndTheViewControls() {
            when(viewWithControlsMock.getViewBodyControls()).thenReturn(List.of(VIEW_MARKER));
            registerDefaultView(viewWithControlsMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<PoliticalMapBodyControls> controlsMock =
                        mockStatic(PoliticalMapBodyControls.class);
                    MockedStatic<FilterSelectionBinder> pickerMock =
                        mockStatic(FilterSelectionBinder.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the picker's sort selector carries the engine's text tone,
                // which Misc reads off the live settings - so a body built here needs a settings proxy
                // that answers a colour. Stubbed before the static stubbing opens, since its own
                // stubbing would otherwise land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);
                pickerMock
                    .when(() -> FilterSelectionBinder.buildPicker(
                        any(),
                        any(),
                        any(),
                        any(),
                        any()))
                    .thenReturn(List.of(PICKER_MARKER));

                var body = PoliticalMapLayer.INSTANCE.getBodyControls();

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER, PICKER_MARKER, VIEW_MARKER);
            }
        }

        @Test
        void getBodyControlsOmitsViewControlsWhenTheSelectedViewAddsNone() {

            // The faction view adds no controls of its own, so the body is only the shared rows and
            // the selector - nothing trails the selector.
            when(viewWithoutControlsMock.getViewBodyControls())
                .thenReturn(List.of());

            registerDefaultView(viewWithoutControlsMock);

            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<PoliticalMapBodyControls> controlsMock =
                        mockStatic(PoliticalMapBodyControls.class)) {
                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the picker's sort selector carries the engine's text tone,
                // which Misc reads off the live settings - so a body built here needs a settings proxy
                // that answers a colour. Stubbed before the static stubbing opens, since its own
                // stubbing would otherwise land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls();

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER);
            }
        }

        @Test
        void getBodyControlsAppendsNoViewControlsWhenTheMapIsOff() {
            // The off sentinel is stored, so no view is selected; even a view that has controls
            // contributes none, since the tab is showing but the map is dark.
            registerDefaultView(viewWithControlsMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<PoliticalMapBodyControls> controlsMock =
                        mockStatic(PoliticalMapBodyControls.class)) {

                var memoryMock = mock(MemoryAPI.class);
                var sectorMock = mock(SectorAPI.class);

                when(sectorMock.getMemoryWithoutUpdate())
                    .thenReturn(memoryMock);

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                when(memoryMock.contains(ACTIVE_VIEW_KEY))
                    .thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY))
                    .thenReturn("");

                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls();

                assertThat(body)
                    .containsExactly(SHARED_MARKER, SELECTOR_MARKER);
            }
        }

        @Test
        void getBodyControlsPairsTheFilterRecedeWithThePickersSortSelector() {
            // What sits beside the sort selector is this layer's decision, not the framework
            // picker's: the political map fills that half with the filter recede - a caption and the
            // Mute and Desaturate checkboxes - so the "rest of the sector" knobs read beside the
            // metric. Built for real (no picker stub), since the pairing is the thing under test.
            registerViewWithOneBloc(viewWithoutControlsMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<PoliticalMapBodyControls> controlsMock =
                        mockStatic(PoliticalMapBodyControls.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The picker's rows carry the engine's text tone, which Misc reads off the live
                // settings - so building one for real needs a settings proxy that answers a colour.
                // Stubbed before the static stubbing opens, since its own stubbing would otherwise
                // land inside that one.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                var sortRow = findSortRow(PoliticalMapLayer.INSTANCE.getBodyControls());

                // The left half is the sort selector over this layer's own vocabulary, one row per
                // mode, so the modes reaching the picker are the political map's.
                assertThat(sortRow.leftColumn())
                    .hasSize(1);
                assertThat(sortRow.leftColumn().get(0).labels())
                    .hasSize(DominanceSortMode.values().length);

                // The right half is the recede: its caption, then the two toggles.
                assertThat(sortRow.rightColumn().get(0))
                    .isInstanceOf(ControlSpec.Label.class);
                assertThat(sortRow.rightColumn().get(1))
                    .isInstanceOf(ControlSpec.Checkbox.class);
                assertThat(sortRow.rightColumn().get(2))
                    .isInstanceOf(ControlSpec.Checkbox.class);
            }
        }
    }

    // Registers a view offering one spotlightable bloc, under an id and revision of its own so the
    // shared picker memo misses on it rather than serving the empty list the composition tests leave
    // cached. The bloc's contents do not matter - what matters is that the picker builds at all,
    // since an empty list contributes none.
    private static void registerViewWithOneBloc(PoliticalMapView view) {

        when(view.getId())
            .thenReturn("picker-view");

        when(view.getContentRevision())
            .thenReturn(1);

        when(view.getViewBodyControls())
            .thenReturn(List.of());

        when(view.resolveSelectableBlocs(any()))
            .thenReturn(List.of(new SelectableBloc("hegemony", "Hegemony", null)));

        var hostTabMock = mock(MapLayer.class);

        when(hostTabMock.getId())
            .thenReturn("host");

        PoliticalMapViewRegistry.registerViews(List.of(view), view, hostTabMock);
        MapLayerRegistry.registerLayers(List.of(hostTabMock), hostTabMock);
    }

    // One sentinel control, named so an assertion can tell the composed pieces apart. A caption is the
    // simplest control there is, which is why it stands in for whatever the layer really contributes.
    private static ControlSpec buildMarker(String markerText) {
        return ControlSpec.Label.createLabel(new TextSpan(markerText, MARKER_COLOUR));
    }

    // A settings proxy that answers every colour lookup with one tone. Which tone a row draws in is
    // not what these tests read, so one stands in for the whole palette.
    private static SettingsAPI buildSettingsAnsweringColours() {
        
        var settingsMock = mock(SettingsAPI.class);
        when(settingsMock.getColor(any()))
            .thenReturn(Color.LIGHT_GRAY);

        return settingsMock;
    }

    // The picker's paired sort row, found by type rather than by index so the assertion does not
    // re-state the framework picker's own row order, which is pinned where the picker lives.
    private static ControlSpec.SideBySide findSortRow(List<ControlSpec> body) {
        return body
            .stream()
            .filter(ControlSpec.SideBySide.class::isInstance)
            .map(ControlSpec.SideBySide.class::cast)
            .findFirst()
            .orElseThrow();
    }

    // Stubs the two view-agnostic pieces to their sentinels so a test asserts only the composition
    // order the layer imposes, not the pieces' own contents.
    private static void stubSharedControlsAndSelector(
            MockedStatic<PoliticalMapBodyControls> controlsMock) {

        controlsMock
            .when(PoliticalMapBodyControls::buildSharedControls)
            .thenReturn(List.of(SHARED_MARKER));

        controlsMock
            .when(PoliticalMapBodyControls::buildViewSelector)
            .thenReturn(SELECTOR_MARKER);
    }

    // Registers the one view as both the sole registered view and the default, with a host tab the
    // layer registry treats as active, so a sector-less read resolves this view as selected. Also
    // stubs the view's identity and empty selectable-bloc list, since the body build now reads its
    // picker options through the memo (keyed on the view id and its content revision) rather than off
    // the view directly - an empty list contributes no picker, keeping these composition assertions
    // about where the picker sits, not what it holds.
    private static void registerDefaultView(PoliticalMapView view) {

        when(view.getId())
            .thenReturn("selected-view");
        when(view.getContentRevision())
            .thenReturn(0);
        when(view.resolveSelectableBlocs(any()))
            .thenReturn(List.of());

        var hostTabMock = mock(MapLayer.class);

        when(hostTabMock.getId())
            .thenReturn("host");
            
        PoliticalMapViewRegistry.registerViews(List.of(view), view, hostTabMock);
        MapLayerRegistry.registerLayers(List.of(hostTabMock), hostTabMock);
    }
}
