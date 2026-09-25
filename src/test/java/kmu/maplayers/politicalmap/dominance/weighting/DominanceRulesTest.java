package kmu.maplayers.politicalmap.dominance.weighting;

import kmu.settings.HiddenMarketScalingChoice;
import kmu.settings.KmuPoliticalMapDominanceSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins that {@link DominanceRules#readFromLunaSettings()} lands every settings getter in its own
 * field. The rules are positional records holding ten numbers of one type and three switches of
 * another, so two getters swapped in the constructor calls still compile and would quietly weigh the
 * map by the wrong knob. Each case therefore gives every getter a value no other getter shares.
 */
class DominanceRulesTest {

    @Nested
    class ReadFromLunaSettings {

        @Test
        void readFromLunaSettingsLandsEachNumberAndTheScalingChoiceInItsOwnField() {

            try (var settingsMock = mockStatic(KmuPoliticalMapDominanceSettings.class)) {

                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::shouldWeighDominanceByStability)
                    .thenReturn(true);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getColonySizeWeight)
                    .thenReturn(1.1);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getHiddenMarketScaling)
                    .thenReturn(HiddenMarketScalingChoice.NORMAL);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getHiddenMarketFixedWeight)
                    .thenReturn(1.2);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getNormalLowStabilityPenalty)
                    .thenReturn(0.13);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::shouldWeighDominanceByStation)
                    .thenReturn(true);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getStationWeight)
                    .thenReturn(1.4);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getStationHiddenMarketRate)
                    .thenReturn(0.15);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getStationLowStabilityPenalty)
                    .thenReturn(0.16);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::shouldWeighDominanceByPatrols)
                    .thenReturn(true);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getPatrolSmallWeight)
                    .thenReturn(0.17);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getPatrolMediumWeight)
                    .thenReturn(0.18);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getPatrolLargeWeight)
                    .thenReturn(1.9);
                settingsMock
                    .when(KmuPoliticalMapDominanceSettings::getPatrolLowStabilityPenalty)
                    .thenReturn(0.21);

                assertThat(DominanceRules.readFromLunaSettings())
                    .isEqualTo(new DominanceRules(
                        true,
                        new BaseSizeWeighting(1.1, HiddenMarketScalingChoice.NORMAL, 1.2, 0.13),
                        new StationWeighting(true, 1.4, 0.15, 0.16),
                        new PatrolWeighting(true, 0.17, 0.18, 1.9, 0.21)));
            }
        }

        @Test
        void readFromLunaSettingsLandsTheStabilitySwitchInTheMasterToggleAlone() {
            // The three switches share a type, so each is turned on by itself: a pair swapped in
            // the constructor calls would then show its one true value in the wrong place.
            var rules = readRulesWithOnlySwitchOn(
                KmuPoliticalMapDominanceSettings::shouldWeighDominanceByStability);

            assertThat(rules)
                .extracting(
                    DominanceRules::isStabilityWeighted,
                    readRules -> readRules.station().isWeighted(),
                    readRules -> readRules.patrols().isWeighted())
                .containsExactly(true, false, false);
        }

        @Test
        void readFromLunaSettingsLandsTheStationSwitchInTheStationFactorAlone() {

            var rules = readRulesWithOnlySwitchOn(
                KmuPoliticalMapDominanceSettings::shouldWeighDominanceByStation);

            assertThat(rules)
                .extracting(
                    DominanceRules::isStabilityWeighted,
                    readRules -> readRules.station().isWeighted(),
                    readRules -> readRules.patrols().isWeighted())
                .containsExactly(false, true, false);
        }

        @Test
        void readFromLunaSettingsLandsThePatrolSwitchInThePatrolFactorAlone() {

            var rules = readRulesWithOnlySwitchOn(
                KmuPoliticalMapDominanceSettings::shouldWeighDominanceByPatrols);

            assertThat(rules)
                .extracting(
                    DominanceRules::isStabilityWeighted,
                    readRules -> readRules.station().isWeighted(),
                    readRules -> readRules.patrols().isWeighted())
                .containsExactly(false, false, true);
        }
    }

    // The rules read with one switch on and every other getter left at the stand-in's default -
    // false for the other two switches - so the one true value marks where that switch landed.
    private static DominanceRules readRulesWithOnlySwitchOn(MockedStatic.Verification switchGetter) {

        try (var settingsMock = mockStatic(KmuPoliticalMapDominanceSettings.class)) {

            settingsMock
                .when(switchGetter)
                .thenReturn(true);

            return DominanceRules.readFromLunaSettings();
        }
    }
}
