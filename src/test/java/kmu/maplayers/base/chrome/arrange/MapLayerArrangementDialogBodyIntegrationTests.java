package kmu.maplayers.base.chrome.arrange;

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
 * Draws the arrange box's hint in every locale KMU ships and holds its highlights to the game's rule: a
 * run highlights only where the characters beside it are whitespace or ASCII punctuation.
 */
final class MapLayerArrangementDialogBodyIntegrationTests {

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
    class FillHeader {

        @ParameterizedTest
        @MethodSource("kmu.maplayers.base.chrome.arrange.MapLayerArrangementDialogBodyIntegrationTests#listLocaleTags")
        void everyRunOfTheHintHighlightsInEveryLocale(String localeTag) {

            var directory = new LocalisationDirectory(LocalisationDirectory.LOCALISATION_DIRECTORY);
            var locale = directory.readManifest().declaredLocalesByTag().get(localeTag);

            StarsectorSettingsFake.installSettings(directory.openBundle(locale).readStringSource());

            var headerMock = HighlightedTooltipMock.createTooltipMock();

            MapLayerArrangementDialogBody.fillHeader(headerMock.getTooltip());

            assertThat(headerMock.findUnhighlightedRuns())
                .isEmpty();
        }
    }
}
