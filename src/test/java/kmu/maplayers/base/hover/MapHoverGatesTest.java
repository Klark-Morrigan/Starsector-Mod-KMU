package kmu.maplayers.base.hover;

import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the two tiers of hover switching that answer for every map layer: that the master takes both
 * kinds of feedback down with it, and that the pair under it takes down one kind each without
 * touching the other. The second is the case a single switch could not express - a player who wants
 * the box without the map lighting up, or the reverse - and it is what a layer's own pair hangs off.
 */
final class MapHoverGatesTest {

    @Nested
    class IsHoverEffectsEnabled {

        @Test
        void isHoverEffectsEnabledIsTrueWithBothTiersOn() {
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverEffectsEnabled).thenReturn(true);

                assertThat(MapHoverGates.isHoverEffectsEnabled()).isTrue();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheHoveringMasterOff() {
            // The master reaches past its own kind: with it off there is no cursor read to draw off.
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(false);
                settingsMock.when(KmuMapLayerSettings::getMapHoverEffectsEnabled).thenReturn(true);

                assertThat(MapHoverGates.isHoverEffectsEnabled()).isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheGlobalEffectsSwitchOff() {
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverEffectsEnabled).thenReturn(false);

                assertThat(MapHoverGates.isHoverEffectsEnabled()).isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsUntouchedByTheGlobalTooltipSwitch() {
            // The pair is a pair, not a chain: switching the box off leaves the halo and wash alone.
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverEffectsEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverTooltipEnabled).thenReturn(false);

                assertThat(MapHoverGates.isHoverEffectsEnabled()).isTrue();
            }
        }
    }

    @Nested
    class IsHoverTooltipEnabled {

        @Test
        void isHoverTooltipEnabledIsTrueWithBothTiersOn() {
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverTooltipEnabled).thenReturn(true);

                assertThat(MapHoverGates.isHoverTooltipEnabled()).isTrue();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheHoveringMasterOff() {
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(false);
                settingsMock.when(KmuMapLayerSettings::getMapHoverTooltipEnabled).thenReturn(true);

                assertThat(MapHoverGates.isHoverTooltipEnabled()).isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheGlobalTooltipSwitchOff() {
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverTooltipEnabled).thenReturn(false);

                assertThat(MapHoverGates.isHoverTooltipEnabled()).isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsUntouchedByTheGlobalEffectsSwitch() {
            // The other half of the pair: a player who wants the standings box without the map
            // lighting up under the cursor keeps the box.
            try (MockedStatic<KmuMapLayerSettings> settingsMock = mockStatic(KmuMapLayerSettings.class)) {
                settingsMock.when(KmuMapLayerSettings::getMapHoveringEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverTooltipEnabled).thenReturn(true);
                settingsMock.when(KmuMapLayerSettings::getMapHoverEffectsEnabled).thenReturn(false);

                assertThat(MapHoverGates.isHoverTooltipEnabled()).isTrue();
            }
        }
    }
}
