package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.ui.controls.ControlSpec;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.FilterSelectionBinder;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
import kmu.maplayers.politicalmap.base.render.PoliticalMapLayerRenderer;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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
    // The id the one bloc-offering view registers under, and the scope the picker must therefore be
    // built for - shared between the registration helper and the assertion so the two cannot drift.
    private static final String PICKER_VIEW_ID = "picker-view";

    private static final Color MARKER_COLOUR = Color.WHITE;
    private static final ControlSpec SHARED_MARKER = buildMarker("shared");
    private static final ControlSpec SELECTOR_MARKER = buildMarker("selector");
    private static final ControlSpec VIEW_MARKER = buildMarker("view");
    private static final ControlSpec PICKER_MARKER = buildMarker("picker");

    // Two sectors' installed machinery, since what this tab now answers turns on which of them it
    // is asked about. The body cases below use neither: a tab's controls are the same wherever it
    // is drawn.
    private final MapLayerInstallation installation = new MapLayerInstallation();
    private final MapLayerInstallation otherInstallation = new MapLayerInstallation();

    private final PoliticalMapView viewWithControlsMock = mock(PoliticalMapView.class);
    private final PoliticalMapView viewWithoutControlsMock = mock(PoliticalMapView.class);

    @Nested
    class ResolveRenderer {

        @Test
        void resolveRendererYieldsTheOneRendererThatInstallationKeeps() {
            // The counterpart to No Layer's null: this tab is the one that draws, and it hands the map
            // surface one view-neutral renderer rather than branching on the view roster. Twice for
            // one sector is once, since the surface resolves it every frame and the hover box again
            // in the pass after.
            assertThat(PoliticalMapLayer.INSTANCE.resolveRenderer(installation))
                .isInstanceOf(PoliticalMapLayerRenderer.class)
                .isSameAs(PoliticalMapLayer.INSTANCE.resolveRenderer(installation));
        }

        @Test
        void resolveRendererYieldsARendererPerInstallationSoOneSectorsDrawingIsNotAnothers() {
            // This tab is registered once for the process while everything behind its renderer - the
            // cut cells, the territories, the fitted names - is one sector's, so the same tab has to
            // answer for two sectors with two renderers.
            assertThat(PoliticalMapLayer.INSTANCE.resolveRenderer(installation))
                .isNotSameAs(PoliticalMapLayer.INSTANCE.resolveRenderer(otherInstallation));
        }

        @Test
        void resolveRendererLeavesNothingBehindOnTheInstallationItWasReleasedWith() {
            // The renderer goes with the sector's machinery, so the sector installed on after it
            // draws through one of its own rather than through the previous sector's cached cells.
            var renderer = PoliticalMapLayer.INSTANCE.resolveRenderer(installation);

            installation.disposeMachinery();

            assertThat(PoliticalMapLayer.INSTANCE.resolveRenderer(installation))
                .isNotSameAs(renderer);
        }
    }

    @Nested
    class GetBodyControls {

        @Test
        void getBodyControlsAppendsTheSelectedViewsControlsAfterTheSelector() {

            when(viewWithControlsMock.getViewBodyControls())
                .thenReturn(List.of(VIEW_MARKER));

            registerDefaultView(viewWithControlsMock);

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

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

            when(viewWithControlsMock.getViewBodyControls())
                .thenReturn(List.of(VIEW_MARKER));

            registerDefaultView(viewWithControlsMock);

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class);
                    var pickerMock = mockStatic(FilterSelectionBinder.class)) {

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

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

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

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

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

            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class)) {

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

        @Test
        void getBodyControlsScopesThePickerToTheSelectedViewsId() {
            // The scope this layer hands over is what makes each view remember its own spotlight and
            // its own sort: the stores partition by whatever id they are given, so a layer passing a
            // constant would still read and write consistently and every store-level test would stay
            // green while all three views shared one slot. Pinned here because this is the only place
            // the id is chosen.
            registerViewWithOneBloc(viewWithoutControlsMock);
            try (var globalMock = mockStatic(Global.class);
                    var controlsMock = mockStatic(PoliticalMapBodyControls.class);
                    var pickerMock = mockStatic(FilterSelectionBinder.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(null);

                // The recede paired with the sort selector reads the engine's text tone off the live
                // settings, so even a stubbed picker needs a settings proxy that answers a colour.
                var settingsMock = buildSettingsAnsweringColours();
                globalMock
                    .when(Global::getSettings)
                    .thenReturn(settingsMock);

                stubSharedControlsAndSelector(controlsMock);

                PoliticalMapLayer.INSTANCE.getBodyControls();

                pickerMock.verify(
                    () -> FilterSelectionBinder.buildPicker(
                        eq(PICKER_VIEW_ID),
                        any(),
                        any(),
                        any()));
            }
        }
    }

    // Registers a view offering one spotlightable bloc, under an id and revision of its own so the
    // shared picker memo misses on it rather than serving the empty list the composition tests leave
    // cached. The bloc's contents do not matter - what matters is that the picker builds at all,
    // since an empty list contributes none.
    private static void registerViewWithOneBloc(PoliticalMapView view) {

        when(view.getId())
            .thenReturn(PICKER_VIEW_ID);

        when(view.getContentRevision())
            .thenReturn(1);

        when(view.getViewBodyControls())
            .thenReturn(List.of());

        // Stubbed through doReturn because the seam answers a wildcarded picker, whose captured item
        // type a when() stub would have to name.
        doReturn(new ListPicker<>(
                List.of(new RankedBloc<>(
                    new SelectableBloc("hegemony", "Hegemony", null),
                    DominanceStats.EMPTY)),
                DominanceSortMode.MODES))
            .when(view)
            .resolveBlocPicker(any());

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
    // stubs the view's identity and empty picker, since the body build now reads its picker options
    // through the memo (keyed on the view id and its content revision) rather than off the view
    // directly - an empty picker contributes no controls, keeping these composition assertions about
    // where the picker sits, not what it holds.
    private static void registerDefaultView(PoliticalMapView view) {

        when(view.getId())
            .thenReturn("selected-view");
        when(view.getContentRevision())
            .thenReturn(0);

        doReturn(ListPicker.empty())
            .when(view)
            .resolveBlocPicker(any());

        var hostTabMock = mock(MapLayer.class);

        when(hostTabMock.getId())
            .thenReturn("host");

        PoliticalMapViewRegistry.registerViews(List.of(view), view, hostTabMock);
        MapLayerRegistry.registerLayers(List.of(hostTabMock), hostTabMock);
    }
}
