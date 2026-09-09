package kmu.maplayers.politicalmap.base.render.hover;

import kmu.settings.KmuMapHoverSettings;
import kmu.settings.KmuPoliticalMapHighlightSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the bottom tier of hover switching against the two above it: that this layer's own switch can
 * withhold either kind of feedback, that neither of the tiers above can be overruled by it, that the
 * two kinds stay independent at this tier as they are at the global one, and that the cursor read
 * runs for whichever kind is still on - the read backs both, so gating it on the effects alone would
 * quietly take the box down with them.
 *
 * <p>Two static mocks per test because the tiers live in two settings classes: the upper two are the
 * framework's and the bottom pair is this layer's, which is the split the gate reads across.
 */
final class PoliticalMapHoverGatesTest {

    @Nested
    class IsHoverEffectsEnabled {

        @Test
        void isHoverEffectsEnabledIsTrueWithEveryTierOn() {
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isTrue();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheHoveringMasterOff() {
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, false);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheGlobalEffectsSwitchOff() {
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, false);
                stubPoliticalHoverEffects(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithThisLayersOwnEffectsSwitchOff() {
            // The case the tiers above cannot express: every other layer keeps its halo and wash.
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsUntouchedByThisLayersTooltipSwitch() {
            // The bottom tier is a pair, like the global one above it: silencing this layer's box
            // leaves this layer's halo and wash burning.
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isTrue();
            }
        }
    }

    @Nested
    class IsHoverTooltipEnabled {

        @Test
        void isHoverTooltipEnabledIsTrueWithEveryTierOn() {
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isTrue();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheHoveringMasterOff() {
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, false);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheGlobalTooltipSwitchOff() {
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, false);
                stubPoliticalHoverTooltip(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithThisLayersOwnTooltipSwitchOff() {
            // This layer's box goes; another layer's box, reading its own switch, is untouched.
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsUntouchedByThisLayersEffectsSwitch() {
            // The other half: a player who wants the standings box without this map lighting up under
            // the cursor turns off this layer's effects alone and keeps the box.
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isTrue();
            }
        }
    }

    @Nested
    class IsCursorReadNeeded {

        @Test
        void isCursorReadNeededIsTrueForTheEffectsAlone() {
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, false);
                stubPoliticalHoverTooltip(layerSettingsMock, false);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isTrue();
            }
        }

        @Test
        void isCursorReadNeededIsTrueForTheHoverBoxAlone() {
            // The reason the read is the union of the two: the box names the system the read
            // resolves, so gating the read on the effects would switch the box off with them.
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, false);
                stubPoliticalHoverEffects(layerSettingsMock, false);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isTrue();
            }
        }

        @Test
        void isCursorReadNeededIsFalseWithBothKindsOfFeedbackOff() {
            // Nothing is left to answer, so the map-matrix read and hit test behind the cursor are
            // skipped rather than resolved into a hover nothing draws. Switched off at the bottom
            // tier, which is the case the layer alone can decide.
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, true);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, false);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, false);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isFalse();
            }
        }

        @Test
        void isCursorReadNeededIsFalseWithTheHoveringMasterOff() {
            // The master alone, with every switch beneath it left on: one row takes the whole read.
            try (MockedStatic<KmuMapHoverSettings> frameworkSettingsMock =
                        mockStatic(KmuMapHoverSettings.class);
                    MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock =
                        mockStatic(KmuPoliticalMapHighlightSettings.class)) {
                stubHoveringMaster(frameworkSettingsMock, false);
                stubGlobalHoverEffects(frameworkSettingsMock, true);
                stubPoliticalHoverEffects(layerSettingsMock, true);
                stubGlobalHoverTooltip(frameworkSettingsMock, true);
                stubPoliticalHoverTooltip(layerSettingsMock, true);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isFalse();
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

    private static void stubPoliticalHoverEffects(
            MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock,
            boolean isEnabled) {
        layerSettingsMock
            .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverEffectsEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubPoliticalHoverTooltip(
            MockedStatic<KmuPoliticalMapHighlightSettings> layerSettingsMock,
            boolean isEnabled) {
        layerSettingsMock
            .when(KmuPoliticalMapHighlightSettings::getPoliticalMapHoverTooltipEnabled)
            .thenReturn(isEnabled);
    }
}
