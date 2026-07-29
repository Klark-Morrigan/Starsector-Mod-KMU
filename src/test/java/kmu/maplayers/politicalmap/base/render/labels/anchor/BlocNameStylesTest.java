package kmu.maplayers.politicalmap.base.render.labels.anchor;

import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins that each group's name styling threads its own two settings into its own slot. Both
 * groups hold the same shape - an outer-border colour choice and a name opacity - so a
 * crossed pair would still type-check and would only show as independent space's names
 * taking the core factions' shade in-game.
 */
final class BlocNameStylesTest {

    // Distinct sentinels so a slot fed from the wrong setting is caught by value.
    private static final double FACTION_NAME_OPACITY = 0.25;
    private static final double INDEPENDENT_NAME_OPACITY = 0.75;

    @Nested
    class ReadFromLunaSettings {

        @Test
        void readFromLunaSettingsThreadsEachGroupsColourChoiceAndOpacityIntoItsOwnSlot() {
            try (MockedStatic<KmuLunaSettings> settingsMock = mockStatic(KmuLunaSettings.class)) {
                settingsMock.when(KmuLunaSettings::getFactionOuterBorderColor)
                        .thenReturn(FactionPaletteChoice.PRIMARY);
                settingsMock.when(KmuLunaSettings::getFactionNameOpacity)
                        .thenReturn(FACTION_NAME_OPACITY);
                settingsMock.when(KmuLunaSettings::getIndependentOuterBorderColor)
                        .thenReturn(FactionPaletteChoice.SECONDARY);
                settingsMock.when(KmuLunaSettings::getIndependentNameOpacity)
                        .thenReturn(INDEPENDENT_NAME_OPACITY);

                var nameStyles = BlocNameStyles.readFromLunaSettings();

                assertThat(nameStyles.factionNameStyle().color())
                        .isEqualTo(FactionPaletteChoice.PRIMARY);
                assertThat(nameStyles.factionNameStyle().opacity()).isEqualTo(FACTION_NAME_OPACITY);
                assertThat(nameStyles.independentNameStyle().color())
                        .isEqualTo(FactionPaletteChoice.SECONDARY);
                assertThat(nameStyles.independentNameStyle().opacity())
                        .isEqualTo(INDEPENDENT_NAME_OPACITY);
            }
        }
    }
}
