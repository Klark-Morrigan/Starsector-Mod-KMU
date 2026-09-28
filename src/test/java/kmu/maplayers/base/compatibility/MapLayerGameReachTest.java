package kmu.maplayers.base.compatibility;

import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins which feature key and which two sentences each loss files under. A loss handed another's
 * sentence composes as plausibly as the right one, and reaches a player as a report of something that
 * did not stop working.
 */
final class MapLayerGameReachTest {

    @BeforeEach
    void installSettings() {

        StarsectorSettingsFake.installSettings((category, key) -> "the sentence for " + key);
    }

    @AfterEach
    void clearSettings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class DescribeConsumer {

        @ParameterizedTest
        @CsvSource({
            "MAP_VIEW, kmu:map-view, map_view",
            "FILTER_ROW_TOGGLE, kmu:filter-row-toggle, filter_row_toggle",
            "ARRANGE_DIALOG, kmu:arrange-dialog, arrange_dialog",
            "SCREEN_COVERS, kmu:screen-covers, screen_covers",
            "MAP_TOOLTIPS, kmu:map-tooltips, map_tooltips",
            "STARSCAPE_RESEAT, kmu:starscape-reseat, starscape_reseat",
            "STARSCAPE_TERRAIN, kmu:starscape-terrain, starscape_terrain"})
        void filesUnderItsOwnFeatureWithItsOwnSentences(
                MapLayerGameReach reach,
                String consumerKey,
                String stringKeySuffix) {

            var consumer = reach.describeConsumer();

            assertThat(consumer.consumerKey())
                .isEqualTo(consumerKey);
            assertThat(consumer.lostFeature())
                .isEqualTo("the sentence for compatibility_lost_" + stringKeySuffix);
            assertThat(consumer.unaffectedFeature())
                .isEqualTo("the sentence for compatibility_unaffected_" + stringKeySuffix);
        }
    }

    @Nested
    class GetReporter {

        @Test
        void holdsOneReporterPerLoss() {
            // One per loss is what makes a loss reported once however many reaches share it.
            assertThat(MapLayerGameReach.MAP_VIEW.getReporter())
                .isSameAs(MapLayerGameReach.MAP_VIEW.getReporter())
                .isNotSameAs(MapLayerGameReach.MAP_TOOLTIPS.getReporter());
        }
    }
}
