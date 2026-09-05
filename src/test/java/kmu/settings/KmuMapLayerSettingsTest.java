package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one knob in this class that computes rather than hands back what was stored: the sidebar's
 * background opacity, which the table keeps as a percentage and the paint pass takes as a fraction. Every
 * other getter here is a read and its fallback, and those are held by the walk that reads the shipped table
 * against the constants beside each getter - a walk that never calls one, so it cannot see a conversion.
 *
 * <p>Both ends of the clamp are pinned as well as the conversion, because they answer a value the slider
 * cannot produce: a settings file edited by hand, or a row whose bounds were widened without the clamp
 * following. What the clamp is for is stated where it is declared - a body faded past half leaves opaque
 * text standing on the map behind it - and a fraction escaping that floor is not something a player can
 * see the cause of.
 */
final class KmuMapLayerSettingsTest {

    // The live field id, as the shipped table spells it. A rename in the reader would otherwise read a row
    // the table never writes, leaving the panel on its fallback opacity with nothing to say why.
    private static final String OPACITY_FIELD = "kmu_map_visuals_sidebar_opacity";

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
}
