package kmu.maplayers.ownermap.render.hover;

import kmu.settings.KmuMapHoverSettings;
import kmu.settings.KmuOwnerMapHighlightSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the bottom tier of hover switching against the two above it: that the owner-map switch can
 * withhold either kind of feedback, that neither of the tiers above can be overruled by it, and that
 * the two kinds stay independent at this tier as they are at the global one.
 *
 * <p>Two static mocks per test because the tiers live in two settings classes: the upper two are the
 * framework's and the bottom pair is the owner-map tier's, which is the split the gate reads across.
 */
final class SharedOwnerMapHoverGatesTest {

    @Nested
    class IsHoverEffectsEnabled {

        @Test
        void isHoverEffectsEnabledIsTrueWithEveryTierOn() {

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubOwnerMapHoverEffects(layerSettingsMock, true);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverEffectsEnabled())
                    .isTrue();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheHoveringMasterOff() {

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, false);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubOwnerMapHoverEffects(layerSettingsMock, true);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheGlobalEffectsSwitchOff() {

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, false);
                stubOwnerMapHoverEffects(layerSettingsMock, true);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheOwnerMapEffectsSwitchOff() {
            // The case the tiers above cannot express: every other layer keeps its halo and wash.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubOwnerMapHoverEffects(layerSettingsMock, false);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsUntouchedByTheOwnerMapTooltipSwitch() {
            // The bottom tier is a pair, like the global one above it: silencing this layer's box
            // leaves this layer's halo and wash burning.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubOwnerMapHoverEffects(layerSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubOwnerMapHoverTooltip(layerSettingsMock, false);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverEffectsEnabled())
                    .isTrue();
            }
        }
    }

    @Nested
    class IsHoverTooltipEnabled {

        @Test
        void isHoverTooltipEnabledIsTrueWithEveryTierOn() {

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubOwnerMapHoverTooltip(layerSettingsMock, true);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverTooltipEnabled())
                    .isTrue();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheHoveringMasterOff() {

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, false);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubOwnerMapHoverTooltip(layerSettingsMock, true);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheGlobalTooltipSwitchOff() {

            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, false);
                stubOwnerMapHoverTooltip(layerSettingsMock, true);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheOwnerMapTooltipSwitchOff() {
            // This layer's box goes; another layer's box, reading its own switch, is untouched.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubOwnerMapHoverTooltip(layerSettingsMock, false);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsUntouchedByTheOwnerMapEffectsSwitch() {
            // The other half: a player who wants the standings box without this map lighting up under
            // the cursor turns off this layer's effects alone and keeps the box.
            try (var frameworkSettingsMock = mockStatic(KmuMapHoverSettings.class);
                    var layerSettingsMock = mockStatic(KmuOwnerMapHighlightSettings.class)) {

                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubOwnerMapHoverTooltip(layerSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubOwnerMapHoverEffects(layerSettingsMock, false);

                assertThat(SharedOwnerMapHoverGates.INSTANCE.isHoverTooltipEnabled())
                    .isTrue();
            }
        }
    }

    // One helper per switch rather than one per kind of feedback. A test then names each switch it
    // sets at the call site instead of passing a row of bare booleans, and the master - which both
    // kinds read - is set once, so no test can set it twice and take whichever write landed last.
    private static void stubHoveringMaster(
            MockedStatic<KmuMapHoverSettings> frameworkSettingsMock,
            boolean isEnabled) {

        frameworkSettingsMock
            .when(KmuMapHoverSettings::isMapHoveringEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubGlobalHoverEffects(
            MockedStatic<KmuMapHoverSettings> frameworkSettingsMock,
            boolean isEnabled) {

        frameworkSettingsMock
            .when(KmuMapHoverSettings::areMapHoverEffectsEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubGlobalHoverTooltip(
            MockedStatic<KmuMapHoverSettings> frameworkSettingsMock,
            boolean isEnabled) {

        frameworkSettingsMock
            .when(KmuMapHoverSettings::isMapHoverTooltipEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubOwnerMapHoverEffects(
            MockedStatic<KmuOwnerMapHighlightSettings> layerSettingsMock,
            boolean isEnabled) {

        layerSettingsMock
            .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverEffectsEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubOwnerMapHoverTooltip(
            MockedStatic<KmuOwnerMapHighlightSettings> layerSettingsMock,
            boolean isEnabled) {

        layerSettingsMock
            .when(KmuOwnerMapHighlightSettings::getOwnerMapHoverTooltipEnabled)
            .thenReturn(isEnabled);
    }
}
