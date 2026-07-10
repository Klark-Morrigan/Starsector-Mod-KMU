package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins how the political-map tab composes its body: the shared sub-options, then the view-selector
 * radio, then the selected view's own controls appended beneath - so a view shows widgets specific
 * to it (the alliances view's Mute/Desaturate checkboxes) while the tab itself names no concrete
 * view. The shared controls and the selector are stubbed to sentinels so this pins the composition
 * order alone, not what those two pieces contain.
 */
final class PoliticalMapLayerTest {
    // The frozen sector-memory key the active-view selection serialises under, pinned as a literal so
    // a rename that would reset every save to the default fails here rather than shipping.
    private static final String ACTIVE_VIEW_KEY = "$kmu_political_active_view";

    // Sentinels standing in for the two view-agnostic pieces, so the assertions read the composition
    // order without depending on the real shared controls or selector contents.
    private static final SidebarControlSpec SHARED_MARKER = new SidebarControlSpec(
            SidebarControlKind.CHECKBOX, List.of("shared"), "", SidebarControlSpec.NO_SELECTION);
    private static final SidebarControlSpec SELECTOR_MARKER = new SidebarControlSpec(
            SidebarControlKind.RADIO, List.of("selector"), "", SidebarControlSpec.NO_SELECTION);
    private static final SidebarControlSpec VIEW_MARKER = new SidebarControlSpec(
            SidebarControlKind.CHECKBOX, List.of("view"), "", SidebarControlSpec.NO_SELECTION);

    private final PoliticalMapView viewWithControlsMock = mock(PoliticalMapView.class);
    private final PoliticalMapView viewWithoutControlsMock = mock(PoliticalMapView.class);

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
                globalMock.when(Global::getSector).thenReturn(null);
                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls();

                assertThat(body).containsExactly(SHARED_MARKER, SELECTOR_MARKER, VIEW_MARKER);
            }
        }

        @Test
        void getBodyControlsOmitsViewControlsWhenTheSelectedViewAddsNone() {
            // The faction view adds no controls of its own, so the body is only the shared rows and
            // the selector - nothing trails the selector.
            when(viewWithoutControlsMock.getViewBodyControls()).thenReturn(List.of());
            registerDefaultView(viewWithoutControlsMock);
            try (MockedStatic<Global> globalMock = mockStatic(Global.class);
                    MockedStatic<PoliticalMapBodyControls> controlsMock =
                            mockStatic(PoliticalMapBodyControls.class)) {
                globalMock.when(Global::getSector).thenReturn(null);
                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls();

                assertThat(body).containsExactly(SHARED_MARKER, SELECTOR_MARKER);
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
                when(sectorMock.getMemoryWithoutUpdate()).thenReturn(memoryMock);
                globalMock.when(Global::getSector).thenReturn(sectorMock);
                when(memoryMock.contains(ACTIVE_VIEW_KEY)).thenReturn(true);
                when(memoryMock.getString(ACTIVE_VIEW_KEY)).thenReturn("");
                stubSharedControlsAndSelector(controlsMock);

                var body = PoliticalMapLayer.INSTANCE.getBodyControls();

                assertThat(body).containsExactly(SHARED_MARKER, SELECTOR_MARKER);
            }
        }
    }

    // Stubs the two view-agnostic pieces to their sentinels so a test asserts only the composition
    // order the layer imposes, not the pieces' own contents.
    private static void stubSharedControlsAndSelector(
            MockedStatic<PoliticalMapBodyControls> controlsMock) {
        controlsMock.when(PoliticalMapBodyControls::buildSharedControls)
                .thenReturn(List.of(SHARED_MARKER));
        controlsMock.when(PoliticalMapBodyControls::buildViewSelector).thenReturn(SELECTOR_MARKER);
    }

    // Registers the one view as both the sole registered view and the default, with a host tab the
    // layer registry treats as active, so a sector-less read resolves this view as selected.
    private static void registerDefaultView(PoliticalMapView view) {
        var hostTabMock = mock(MapLayer.class);
        when(hostTabMock.getId()).thenReturn("host");
        PoliticalMapViewRegistry.registerViews(List.of(view), view, hostTabMock);
        MapLayerRegistry.registerLayers(List.of(hostTabMock), hostTabMock);
    }
}
