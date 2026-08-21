package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mockStatic;

/**
 * Pins that every id the sweep lists actually reaches the store.
 *
 * <p>Nothing else can say so. The settings CSV suite holds the same ids against the shipped table
 * and against the source text, which catches a row left in the file and an id nobody mentions - but
 * a mention is not a removal, so a list that had stopped being walked would satisfy it in full while
 * the values it names travelled in every player's settings file forever.
 *
 * <p>Stated against the id-taking write rather than against LunaLib, that write being the seam the
 * mod id is bound at: what this class decides is which ids are shed, and the store the shedding
 * lands in is not its subject.
 */
final class KmuRetiredSettingsTest {

    // The uncontested-bands switch, withdrawn when an uncontested cell was made to band
    // unconditionally. Spelt out here rather than read off the class under test, an expectation
    // taken from the code it checks being one that agrees with any typo it contains.
    private static final String RETIRED_UNCONTESTED_BANDS_FIELD =
        "kmu_map_politics_visuals_presenceRibbons_uncontestedEnabled";

    // The two visibility overrides as they shipped on the Map - Dev tab, before visibility earned
    // a tab of its own and all four rows were renamed onto it. Spelt out for the reason above, and
    // with a second one: these ids no longer appear anywhere but the sweep, so a spelling read off
    // the class under test would agree with a typo that shed nothing at all.
    private static final String RETIRED_SHOW_UNDISCOVERED_MARKETS_FIELD =
        "kmu_map_dev_visibilityOverrides_showUndiscoveredMarkets";
    private static final String RETIRED_SHOW_HIDDEN_SYSTEMS_FIELD =
        "kmu_map_dev_visibilityOverrides_showHiddenSystems";

    @Nested
    class ClearRetiredSettings {

        @Test
        void clearRetiredSettingsShedsTheWithdrawnUncontestedBandsSwitch() {

            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                KmuRetiredSettings.clearRetiredSettings();

                settingsMock.verify(
                    () -> KmuLunaSettings.clearSetting(RETIRED_UNCONTESTED_BANDS_FIELD));
            }
        }

        @Test
        void clearRetiredSettingsShedsTheVisibilityOverridesOldDevIds() {
            // A rename leaves the same orphan a withdrawal does, and a worse one: the row is still
            // on the screen under a new key, so a player who had set the old one sees a toggle
            // that reads as untouched while their real answer sits unreachable in the file.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                KmuRetiredSettings.clearRetiredSettings();

                settingsMock.verify(
                    () -> KmuLunaSettings.clearSetting(RETIRED_SHOW_UNDISCOVERED_MARKETS_FIELD));
                settingsMock.verify(
                    () -> KmuLunaSettings.clearSetting(RETIRED_SHOW_HIDDEN_SYSTEMS_FIELD));
            }
        }
    }
}
