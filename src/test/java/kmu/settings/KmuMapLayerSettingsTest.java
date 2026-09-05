package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the knobs in this class that do something with what was stored rather than hand it straight back:
 * the sidebar's background opacity, which the table keeps as a percentage and the paint pass takes as a
 * fraction, and its scrollbar thickness, which is held inside the range the panel is willing to draw. Every
 * other getter here is a read and its fallback, and those are held by the walk that reads the shipped table
 * against the constants beside each getter - a walk that never calls one, so it cannot see a conversion or
 * a clamp.
 *
 * <p>Both ends of each clamp are pinned, because they answer a value the slider cannot produce: a settings
 * file edited by hand, or a row whose bounds were widened without the clamp following. What each clamp is
 * for is stated where it is declared - a body faded past half leaves opaque text standing on the map behind
 * it, a bar at no width is a control gone from the panel - and neither is something a player can see the
 * cause of once it has happened.
 */
final class KmuMapLayerSettingsTest {

    // The live field ids, as the shipped table spells them. A rename in the reader would otherwise read a
    // row the table never writes, leaving the panel on its fallback with nothing to say why.
    private static final String OPACITY_FIELD = "kmu_map_visuals_sidebar_opacity";
    private static final String SCROLLBAR_THICKNESS_FIELD = "kmu_map_visuals_sidebar_scrollbarThickness";

    @Nested
    class GetMapSidebarBackgroundOpacity {

        @Test
        void getMapSidebarBackgroundOpacityConvertsTheStoredPercentageToAFraction() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(OPACITY_FIELD), anyInt()))
                    .thenReturn(80);

                assertThat(KmuMapLayerSettings.getMapSidebarBackgroundOpacity())
                    .isEqualTo(0.8f);
            }
        }

        @Test
        void getMapSidebarBackgroundOpacityLiftsAPercentageUnderTheFloor() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(OPACITY_FIELD), anyInt()))
                    .thenReturn(10);

                assertThat(KmuMapLayerSettings.getMapSidebarBackgroundOpacity())
                    .isEqualTo(0.5f);
            }
        }

        @Test
        void getMapSidebarBackgroundOpacityHoldsAPercentageOverTheCeiling() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(OPACITY_FIELD), anyInt()))
                    .thenReturn(140);

                assertThat(KmuMapLayerSettings.getMapSidebarBackgroundOpacity())
                    .isEqualTo(1f);
            }
        }

        @Test
        void getMapSidebarBackgroundOpacityConvertsItsOwnFallbackWhileNothingIsStored() {
            // Stood in as a substrate that answers with whatever fallback it is handed, which is what an
            // unset row gets. The conversion runs on that answer like any other, so the panel before the
            // settings load is the panel the shipped row draws rather than a raw percentage read as an
            // alpha - which at 80 would be an opacity of 80 rather than of 0.8, and clip to fully opaque.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(anyString(), anyInt()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

                assertThat(KmuMapLayerSettings.getMapSidebarBackgroundOpacity())
                    .isEqualTo(0.8f);
            }
        }
    }

    @Nested
    class GetMapSidebarScrollbarThickness {

        @Test
        void getMapSidebarScrollbarThicknessHandsBackAWidthInsideTheRange() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(SCROLLBAR_THICKNESS_FIELD), anyInt()))
                    .thenReturn(7);

                assertThat(KmuMapLayerSettings.getMapSidebarScrollbarThickness())
                    .isEqualTo(7);
            }
        }

        @Test
        void getMapSidebarScrollbarThicknessLiftsAWidthUnderTheFloor() {
            // The widget library reads a width of nothing as no bar at all, which is the one state this
            // panel never wants: the slider cannot reach it, and neither can a settings file that names it.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(SCROLLBAR_THICKNESS_FIELD), anyInt()))
                    .thenReturn(0);

                assertThat(KmuMapLayerSettings.getMapSidebarScrollbarThickness())
                    .isEqualTo(1);
            }
        }

        @Test
        void getMapSidebarScrollbarThicknessHoldsAWidthOverTheCeiling() {
            // A bar past the ceiling widens the panel by whatever it overruns the gutter by, so an absurd
            // stored width would stand an absurd panel rather than an absurd bar.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(eq(SCROLLBAR_THICKNESS_FIELD), anyInt()))
                    .thenReturn(400);

                assertThat(KmuMapLayerSettings.getMapSidebarScrollbarThickness())
                    .isEqualTo(12);
            }
        }

        @Test
        void getMapSidebarScrollbarThicknessClampsItsOwnFallbackWhileNothingIsStored() {
            // Stood in as a substrate answering with whatever fallback it is handed, which is what an unset
            // row gets. The fallback passes the clamp untouched, so the panel before the settings load is
            // the panel the shipped row draws.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                settingsMock
                    .when(() -> KmuLunaSettings.readInt(anyString(), anyInt()))
                    .thenAnswer(invocation -> invocation.getArgument(1));

                assertThat(KmuMapLayerSettings.getMapSidebarScrollbarThickness())
                    .isEqualTo(3);
            }
        }
    }
}
