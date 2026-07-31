package kmu.maplayers.politicalmap.base.render.hover;

import kmu.settings.KmuLunaSettings;

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
 */
final class PoliticalMapHoverGatesTest {

    @Nested
    class IsHoverEffectsEnabled {

        @Test
        void isHoverEffectsEnabledIsTrueWithEveryTierOn() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isTrue();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheHoveringMasterOff() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, false);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithTheGlobalEffectsSwitchOff() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, false);
                stubPoliticalHoverEffects(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsFalseWithThisLayersOwnEffectsSwitchOff() {
            // The case the tiers above cannot express: every other layer keeps its halo and wash.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverEffectsEnabledIsUntouchedByThisLayersTooltipSwitch() {
            // The bottom tier is a pair, like the global one above it: silencing this layer's box
            // leaves this layer's halo and wash burning.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, true);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverEffectsEnabled())
                    .isTrue();
            }
        }
    }

    @Nested
    class IsHoverTooltipEnabled {

        @Test
        void isHoverTooltipEnabledIsTrueWithEveryTierOn() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isTrue();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheHoveringMasterOff() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, false);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithTheGlobalTooltipSwitchOff() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverTooltip(settingsMock, false);
                stubPoliticalHoverTooltip(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsFalseWithThisLayersOwnTooltipSwitchOff() {
            // This layer's box goes; another layer's box, reading its own switch, is untouched.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isFalse();
            }
        }

        @Test
        void isHoverTooltipEnabledIsUntouchedByThisLayersEffectsSwitch() {
            // The other half: a player who wants the standings box without this map lighting up under
            // the cursor turns off this layer's effects alone and keeps the box.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, false);

                assertThat(PoliticalMapHoverGates.isHoverTooltipEnabled())
                    .isTrue();
            }
        }
    }

    @Nested
    class IsCursorReadNeeded {

        @Test
        void isCursorReadNeededIsTrueForTheEffectsAlone() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, true);
                stubGlobalHoverTooltip(settingsMock, false);
                stubPoliticalHoverTooltip(settingsMock, false);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isTrue();
            }
        }

        @Test
        void isCursorReadNeededIsTrueForTheHoverBoxAlone() {
            // The reason the read is the union of the two: the box names the system the read
            // resolves, so gating the read on the effects would switch the box off with them.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, false);
                stubPoliticalHoverEffects(settingsMock, false);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isTrue();
            }
        }

        @Test
        void isCursorReadNeededIsFalseWithBothKindsOfFeedbackOff() {
            // Nothing is left to answer, so the map-matrix read and hit test behind the cursor are
            // skipped rather than resolved into a hover nothing draws. Switched off at the bottom
            // tier, which is the case the layer alone can decide.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, true);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, false);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, false);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isFalse();
            }
        }

        @Test
        void isCursorReadNeededIsFalseWithTheHoveringMasterOff() {
            // The master alone, with every switch beneath it left on: one row takes the whole read.
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                stubHoveringMaster(settingsMock, false);
                stubGlobalHoverEffects(settingsMock, true);
                stubPoliticalHoverEffects(settingsMock, true);
                stubGlobalHoverTooltip(settingsMock, true);
                stubPoliticalHoverTooltip(settingsMock, true);

                assertThat(PoliticalMapHoverGates.isCursorReadNeeded())
                    .isFalse();
            }
        }
    }

    // One helper per switch rather than one per kind of feedback. A test then names each switch it
    // sets at the call site instead of passing a row of bare booleans, and the master - which both
    // kinds read - is set once, so no test can set it twice and take whichever write landed last.
    private static void stubHoveringMaster(
            MockedStatic<KmuLunaSettings> settingsMock,
            boolean isEnabled) {
        settingsMock
            .when(KmuLunaSettings::getMapHoveringEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubGlobalHoverEffects(
            MockedStatic<KmuLunaSettings> settingsMock,
            boolean isEnabled) {
        settingsMock
            .when(KmuLunaSettings::getMapHoverEffectsEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubGlobalHoverTooltip(
            MockedStatic<KmuLunaSettings> settingsMock,
            boolean isEnabled) {
        settingsMock
            .when(KmuLunaSettings::getMapHoverTooltipEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubPoliticalHoverEffects(
            MockedStatic<KmuLunaSettings> settingsMock,
            boolean isEnabled) {
        settingsMock
            .when(KmuLunaSettings::getPoliticalMapHoverEffectsEnabled)
            .thenReturn(isEnabled);
    }

    private static void stubPoliticalHoverTooltip(
            MockedStatic<KmuLunaSettings> settingsMock,
            boolean isEnabled) {
        settingsMock
            .when(KmuLunaSettings::getPoliticalMapHoverTooltipEnabled)
            .thenReturn(isEnabled);
    }
}
