package kmu.conditions.ui.picker.render.paragraph;

import kmlib.testfixtures.localisation.ShippedLocales;
import kmlib.testfixtures.starsector.ui.label.LabelHighlightRule;

import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Composes the location lines in every locale KMU ships and holds their highlights to the game's rule: a run
 * highlights only where the characters beside it are whitespace or ASCII punctuation. The owner's wording
 * and the separators are each language's own strings; the names are the game's.
 */
final class KmuConditionPickerLocationParagraphFactoryIntegrationTests {

    private StarsectorUiColoursMock coloursMock;

    // Every field set, so every separator and every run the lines can carry is drawn.
    private static KmuConditionPickerModel buildModelHoldingEveryField() {

        var faction = new KmuPickerFaction("Hegemony", null, null, "Vengeful (-100 / 100)", null);
        var location = new KmuConditionPickerLocation.Builder()
            .withPlanetName("Valis")
            .withPlanetType("terran world")
            .withFaction(faction)
            .withStarSystemName("Corvus Star System")
            .withGravityWellTypeName("yellow star")
            .withGravityWellName("Corvus")
            .withConstellationName("Orion Constellation")
            .build();

        return new KmuConditionPickerModel(List.of(), location);
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
        void everyRunOfEveryLineHighlightsInEveryLocale(String localeTag) {

            ShippedLocales.installLocaleStrings(localeTag);

            var paragraphs = KmuConditionPickerLocationParagraphFactory.createParagraphs(buildModelHoldingEveryField());

            assertThat(paragraphs)
                .flatMap(LabelHighlightRule::findUnhighlightedRuns)
                .isEmpty();
        }
    }
}
