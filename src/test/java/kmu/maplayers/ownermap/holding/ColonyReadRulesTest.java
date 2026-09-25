package kmu.maplayers.ownermap.holding;

import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.settings.KmuOwnerMapStyleSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the live read of both colony rules: the visibility rule comes from the player's map-visibility
 * settings and the habitation rule from theirs, each landing in its own slot. It is the one place the
 * pair is assembled from live settings, so a slot left at a default here would reach every rebuild
 * and every picker read at once. Both settings readers are stubbed, and each is posed away from what
 * ships so a default standing in for a read cannot pass.
 */
final class ColonyReadRulesTest {

    @Nested
    class ReadFromLunaSettings {

        @Test
        void readFromLunaSettingsPairsTheLiveVisibilityRuleWithTheLiveHabitationRule() {

            try (var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                    var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                visibilityRulesMock
                    .when(MapVisibilityRules::readFromLunaSettings)
                    .thenReturn(new MapVisibilityRules(UNDER_THE_REVEAL, false));
                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::shouldCountDecivilisedSystemsAsPopulated)
                    .thenReturn(false);

                assertThat(ColonyReadRules.readFromLunaSettings())
                    .isEqualTo(new ColonyReadRules(
                        UNDER_THE_REVEAL,
                        DecivilisedColonyHabitation.COUNTS_AS_UNPOPULATED));
            }
        }
    }
}
