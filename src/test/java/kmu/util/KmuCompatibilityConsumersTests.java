package kmu.util;

import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins how KMU names itself to the compatibility channel: under its own mod ID, the feature key it
 * was given, and the sentence each key names rather than either in the other's slot.
 */
final class KmuCompatibilityConsumersTests {

    @AfterEach
    void clearSettings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class DescribeConsumer {

        @Test
        void namesKmuTheFeatureAndTheSentenceEachKeyHolds() {

            StarsectorSettingsFake.installSettings((category, key) -> "the sentence for " + key);

            var consumer = KmuCompatibilityConsumers.describeConsumer("map-view", "lost_key", "unaffected_key");

            assertThat(consumer.consumerKey())
                .isEqualTo("kmu:map-view");
            assertThat(consumer.lostFeature())
                .isEqualTo("the sentence for lost_key");
            assertThat(consumer.unaffectedFeature())
                .isEqualTo("the sentence for unaffected_key");
        }

        @Test
        void fillsTheLostSentencesSlotsWithTheArgumentsGiven() {

            StarsectorSettingsFake.installSettings((category, key) -> "the %s sentence for " + key);

            var consumer = KmuCompatibilityConsumers.describeConsumer(
                "map-layer-political_map",
                "lost_key",
                "unaffected_key",
                "Political Map");

            assertThat(consumer.lostFeature())
                .isEqualTo("the Political Map sentence for lost_key");
        }
    }
}
