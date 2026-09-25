package kmu.maplayers.ownermap.holding;

import kmu.settings.KmuOwnerMapStyleSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the one mapping from the player's setting to the habitation rule: the toggle on counts a
 * decivilised world as populated, off as unpopulated. Both directions are read back, since a
 * mapping inverted would pass either case alone and flip every decivilised cell on the map. The
 * setting is stubbed, so this pins the mapping rather than how the setting is stored.
 */
final class DecivilisedColonyHabitationTest {

    @Nested
    class ReadFromLunaSettings {

        @Test
        void readFromLunaSettingsCountsADecivilisedWorldAsPopulatedWhenTheSettingIsOn() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::shouldCountDecivilisedSystemsAsPopulated)
                    .thenReturn(true);

                assertThat(DecivilisedColonyHabitation.readFromLunaSettings())
                    .isEqualTo(DecivilisedColonyHabitation.COUNTS_AS_POPULATED);
            }
        }

        @Test
        void readFromLunaSettingsCountsADecivilisedWorldAsUnpopulatedWhenTheSettingIsOff() {

            try (var styleSettingsMock = mockStatic(KmuOwnerMapStyleSettings.class)) {

                styleSettingsMock
                    .when(KmuOwnerMapStyleSettings::shouldCountDecivilisedSystemsAsPopulated)
                    .thenReturn(false);

                assertThat(DecivilisedColonyHabitation.readFromLunaSettings())
                    .isEqualTo(DecivilisedColonyHabitation.COUNTS_AS_UNPOPULATED);
            }
        }
    }
}
