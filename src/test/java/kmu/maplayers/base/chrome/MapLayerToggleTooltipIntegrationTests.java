package kmu.maplayers.base.chrome;

import kmlib.testfixtures.localisation.LocalisationDirectory;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;
import kmlib.testfixtures.starsector.ui.label.HighlightedTooltipMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Draws the filter-row box's paragraphs in every locale KMU ships and holds their highlights to the game's
 * rule: a run highlights only where the characters beside it are whitespace or ASCII punctuation.
 */
final class MapLayerToggleTooltipIntegrationTests {

    static List<String> listLocaleTags() {

        return List.copyOf(new LocalisationDirectory(LocalisationDirectory.LOCALISATION_DIRECTORY)
            .readManifest()
            .declaredLocalesByTag()
            .keySet());
    }

    @AfterEach
    void clearSettings() {
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class DescribeToggle {

        @ParameterizedTest
        @MethodSource("kmu.maplayers.base.chrome.MapLayerToggleTooltipIntegrationTests#listLocaleTags")
        void everyRunHighlightsInEveryLocale(String localeTag) {

            var directory = new LocalisationDirectory(LocalisationDirectory.LOCALISATION_DIRECTORY);
            var locale = directory.readManifest().declaredLocalesByTag().get(localeTag);

            StarsectorSettingsFake.installSettings(directory.openBundle(locale).readStringSource());

            var tooltipMock = HighlightedTooltipMock.createTooltipMock();

            // With the alliances view offered, the sentence names every view it can.
            new MapLayerToggleTooltip(() -> true).describeToggle(tooltipMock.getTooltip());

            assertThat(tooltipMock.findUnhighlightedRuns())
                .isEmpty();
        }
    }
}
