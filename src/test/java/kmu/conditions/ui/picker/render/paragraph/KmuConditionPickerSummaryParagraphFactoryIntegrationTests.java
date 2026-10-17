package kmu.conditions.ui.picker.render.paragraph;

import kmlib.testfixtures.localisation.ShippedLocales;
import kmlib.testfixtures.starsector.ui.label.LabelHighlightRule;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Composes the counts line in every locale KMU ships and holds its highlights to the game's rule: a run
 * highlights only where the characters beside it are whitespace or ASCII punctuation. The separators are
 * each language's own strings, so this is what holds a translation's punctuation to the rule.
 */
final class KmuConditionPickerSummaryParagraphFactoryIntegrationTests {

    private StarsectorUiColoursMock coloursMock;

    // Every count above zero, so every separator the line can carry is drawn.
    private static KmuConditionPickerModel buildModelHoldingEveryCount() {

        return new KmuConditionPickerModel(
            List.of(
                buildEntry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
                buildEntry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
                buildEntry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
                buildEntry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
            new KmuConditionPickerLocation(null, null, null, null, null, null, null));
    }

    private static KmuConditionPickerEntry buildEntry(
            String id,
            KmuConditionPickerEntryState state,
            boolean isSuppressed,
            boolean isHidden) {

        return new KmuConditionPickerEntry(
            id,
            id,
            "graphics/icons/markets/" + id + ".png",
            state,
            "Tooltip",
            null,
            isSuppressed,
            isHidden);
    }

    @BeforeEach
    void mockThemeColours() {
        coloursMock = StarsectorUiColoursMock.install();
    }

    @AfterEach
    void closeThemeColours() {

        coloursMock.close();
    }

    @Nested
    class CreateParagraphs {

        @ParameterizedTest
        @MethodSource("kmlib.testfixtures.localisation.ShippedLocales#listLocaleTags")
        void everyRunOfTheCountsLineHighlightsInEveryLocale(String localeTag) {

            ShippedLocales.installLocaleStrings(localeTag);

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModelHoldingEveryCount());

            assertThat(LabelHighlightRule.findUnhighlightedRuns(paragraphs.get(1)))
                .isEmpty();
        }
    }
}
