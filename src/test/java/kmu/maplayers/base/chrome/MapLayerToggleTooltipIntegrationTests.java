package kmu.maplayers.base.chrome;

import kmlib.testfixtures.localisation.ShippedLocales;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;
import kmlib.testfixtures.starsector.ui.label.HighlightedTooltipMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Draws the filter-row box's paragraphs in every locale KMU ships and holds their highlights to the game's
 * rule: a run highlights only where the characters beside it are whitespace or ASCII punctuation.
 */
final class MapLayerToggleTooltipIntegrationTests {

    @AfterEach
    void clearSettings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class DescribeToggle {

        @ParameterizedTest
        @MethodSource("kmlib.testfixtures.localisation.ShippedLocales#listLocaleTags")
        void everyRunHighlightsInEveryLocale(String localeTag) {

            ShippedLocales.installLocaleStrings(localeTag);

            var tooltipMock = HighlightedTooltipMock.createTooltipMock();

            // With the alliances view offered, the sentence names every view it can.
            new MapLayerToggleTooltip(() -> true).describeToggle(tooltipMock.getTooltip());

            assertThat(tooltipMock.findUnhighlightedRuns())
                .isEmpty();
        }
    }
}
