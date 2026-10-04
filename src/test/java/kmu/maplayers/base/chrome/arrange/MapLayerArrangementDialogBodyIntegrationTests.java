package kmu.maplayers.base.chrome.arrange;

import kmlib.testfixtures.localisation.ShippedLocales;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;
import kmlib.testfixtures.starsector.ui.label.HighlightedTooltipMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Draws the arrange box's hint in every locale KMU ships and holds its highlights to the game's rule: a
 * run highlights only where the characters beside it are whitespace or ASCII punctuation.
 */
final class MapLayerArrangementDialogBodyIntegrationTests {

    @AfterEach
    void clearSettings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class FillHeader {

        @ParameterizedTest
        @MethodSource("kmlib.testfixtures.localisation.ShippedLocales#listLocaleTags")
        void everyRunOfTheHintHighlightsInEveryLocale(String localeTag) {

            ShippedLocales.installLocaleStrings(localeTag);

            var headerMock = HighlightedTooltipMock.createTooltipMock();

            MapLayerArrangementDialogBody.fillHeader(headerMock.getTooltip());

            assertThat(headerMock.findUnhighlightedRuns())
                .isEmpty();
        }
    }
}
