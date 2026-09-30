package kmu.settings;

import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;
import kmlib.testfixtures.starsector.settings.StubbedModIds;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which report a failed KMU LunaLib binding files under.
 *
 * <p>The bindings themselves go through KMLib's {@code LunaSettingsReader} and {@code LunaLogLevelBinding} and
 * are pinned there.
 */
final class KmuLunaSettingsTests {

    @Nested
    class DescribeLunaLibIntegration {

        @AfterEach
        void clearSettings() {

            StarsectorSettingsFake.clearSettings();
        }

        @Test
        void namesLunaLibAndTheSettingsFeatureTheBindingsCost() {
            // A transposition composes as plausibly as the right pairing, and reaches a player as a
            // report naming a mod that was working or a loss that did not happen.
            StarsectorSettingsFake.installSettings((category, key) -> "the sentence for " + key);

            var integration = KmuLunaSettings.describeLunaLibIntegration();

            assertThat(integration.subjectModId())
                .isEqualTo(StubbedModIds.LUNALIB);
            assertThat(integration.subjectModName())
                .isEqualTo("LunaLib");
            assertThat(integration.consumer().consumerKey())
                .isEqualTo("kmu:lunalib-settings");
            assertThat(integration.consumer().lostFeature())
                .isEqualTo("the sentence for compatibility_lost_lunalib_settings");
            assertThat(integration.consumer().unaffectedFeature())
                .isEqualTo("the sentence for compatibility_unaffected_lunalib_settings");
        }
    }
}
