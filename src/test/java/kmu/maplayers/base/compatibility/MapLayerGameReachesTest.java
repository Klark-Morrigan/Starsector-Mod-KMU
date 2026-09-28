package kmu.maplayers.base.compatibility;

import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how a failed reach names KMU as the mod that lost something: under KMU's own ID, the feature
 * key it was given, and the two sentences its keys name rather than either in the other's slot.
 */
final class MapLayerGameReachesTest {

    @AfterEach
    void clearSettings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class DescribeConsumer {

        @Test
        void namesKmuTheFeatureAndTheSentencesEachKeyHolds() {

            StarsectorSettingsFake.installSettings((category, key) -> "the sentence for " + key);

            var consumer = MapLayerGameReaches.describeConsumer("map-view", "lost_key", "unaffected_key");

            assertThat(consumer.consumerKey())
                .isEqualTo("kmu:map-view");
            assertThat(consumer.lostFeature())
                .isEqualTo("the sentence for lost_key");
            assertThat(consumer.unaffectedFeature())
                .isEqualTo("the sentence for unaffected_key");
        }
    }
}
