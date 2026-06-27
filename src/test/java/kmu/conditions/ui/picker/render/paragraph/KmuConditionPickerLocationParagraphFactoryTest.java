package kmu.conditions.ui.picker.render.paragraph;

import com.fs.starfarer.api.util.Misc;

import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerLocationParagraphFactoryTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color TEXT = new Color(220, 220, 220, 255);
    private static final Color FACTION = new Color(90, 150, 240);
    private static final Color RELATIONSHIP = new Color(240, 80, 80);

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getGrayColor).thenReturn(GRAY);
        miscMock.when(Misc::getHighlightColor).thenReturn(GOLD);
        miscMock.when(Misc::getTextColor).thenReturn(TEXT);
        miscMock.when(Misc::getNegativeHighlightColor).thenReturn(RED);
        miscMock.when(Misc::getPositiveHighlightColor).thenReturn(GREEN);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Test
    void returnsHeaderAndUnknownWhenLocationHasNoDisplayableFields() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(null, null, null, null, null, null, null)));

        assertThat(paragraphs).hasSize(2);
        assertThat(paragraphs.get(0).getText()).isEqualTo("Location:");
        assertThat(paragraphs.get(1).getText()).isEqualTo("Unknown");
        assertThat(paragraphs.get(1).getHighlightTexts()).containsExactly("Unknown");
        assertThat(paragraphs.get(1).getHighlightColors()).containsExactly(GOLD);
    }

    @Test
    void returnsLocationHeaderAsFirstLine() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(0).getText()).isEqualTo("Location:");
        assertThat(paragraphs.get(0).getHighlightTexts()).isEmpty();
    }

    @Test
    void locationHeaderHasGrayBaseColor() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(0).getBaseColor()).isEqualTo(GRAY);
    }

    @Test
    void returnsPlanetLineWithTypeAndOwnershipAsSecondLine() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(1).getText())
                .isEqualTo("Valis (terran world) - owned by Hegemony (Vengeful (-100 / 100))");
    }

    @Test
    void returnsSystemLineAsThirdLine() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(2).getText()).isEqualTo("Corvus Star System (yellow star)");
    }

    @Test
    void returnsConstellationLineAsFourthLine() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(3).getText()).isEqualTo("Corvus");
    }

    @Test
    void returnsFourLinesForFullLocation() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        // header + planet + system + constellation
        assertThat(paragraphs).hasSize(4);
    }

    @Test
    void omitsSystemLineWhenSystemIsAbsent() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(
                        "Valis", "terran world", null, null, null, null, null)));

        // header + planet only
        assertThat(paragraphs).hasSize(2);
        assertThat(paragraphs.get(1).getText()).isEqualTo("Valis (terran world)");
    }

    @Test
    void omitsConstellationLineWhenConstellationIsAbsent() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(
                        "Valis", null, null, "Corvus System", null, null, null)));

        // header + planet + system; no constellation
        assertThat(paragraphs).hasSize(3);
    }

    @Test
    void omitsMissingLocationFields() {
        // Planet type whitespace is normalized to absent, no faction, no constellation
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(
                        "Valis", " ", null, "Corvus Star System", null, null, null)));

        assertThat(paragraphs).hasSize(3); // header + planet + system
        assertThat(paragraphs.get(1).getText()).isEqualTo("Valis");
        assertThat(paragraphs.get(2).getText()).isEqualTo("Corvus Star System");
    }

    @Test
    void showsGravityWellEntityNameInSystemLineWhenNotImpliedBySystemName() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null, null, "Kumari System", "Yellow Dwarf", "Kumari A", null)));

        // header + system only
        var systemParagraph = paragraphs.get(1);
        assertThat(systemParagraph.getText()).isEqualTo("Kumari System (Kumari A, Yellow Dwarf)");
        assertThat(systemParagraph.getHighlightTexts()).containsExactly("Kumari System", "Kumari A");
    }

    @Test
    void suppressesGravityWellEntityNameInSystemLineWhenImpliedBySystemName() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null, null, "Agreus System", "Black Hole", "Agreus", null)));

        var systemParagraph = paragraphs.get(1);
        assertThat(systemParagraph.getText()).isEqualTo("Agreus System (Black Hole)");
        assertThat(systemParagraph.getHighlightTexts()).containsExactly("Agreus System");
    }

    @Test
    void deduplicatesGravityWellNameAndEntityNameForNonPlanetGravityWells() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null, null, "Kumari Star System", "Kumari Barycenter",
                        "Kumari Barycenter", null)));

        var systemParagraph = paragraphs.get(1);
        assertThat(systemParagraph.getText()).isEqualTo("Kumari Star System (Kumari Barycenter)");
        assertThat(systemParagraph.getHighlightTexts()).containsExactly("Kumari Star System");
    }

    @Test
    void exposesPlanetLineHighlights() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(1).getHighlightTexts())
                .containsExactly("Valis", "Hegemony", "Vengeful (-100 / 100)");
    }

    @Test
    void exposesSystemLineHighlights() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(2).getHighlightTexts()).containsExactly("Corvus Star System");
    }

    @Test
    void exposesConstellationLineHighlights() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(3).getHighlightTexts()).containsExactly("Corvus");
    }

    @Test
    void exposesPlanetLineHighlightColors() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(1).getHighlightColors())
                .containsExactly(GOLD, FACTION, RELATIONSHIP);
    }

    @Test
    void exposesSystemLineHighlightColors() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(2).getHighlightColors()).containsExactly(GOLD);
    }

    @Test
    void exposesConstellationLineHighlightColors() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(createLocation()));

        assertThat(paragraphs.get(3).getHighlightColors()).containsExactly(GOLD);
    }

    @Test
    void defaultsMissingFactionAndRelationshipColorsToTextWhite() {
        var paragraphs = KmuConditionPickerLocationParagraphFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null,
                        new KmuPickerFaction("Hegemony", null, null, "Vengeful (-100 / 100)", null),
                        null, null, null, null)));

        assertThat(paragraphs.get(1).getHighlightColors()).containsExactly(TEXT, TEXT);
    }

    private static KmuConditionPickerModel model(KmuConditionPickerLocation location) {
        return new KmuConditionPickerModel(Collections.emptyList(), location);
    }

    private static KmuConditionPickerLocation createLocation() {
        return new KmuConditionPickerLocation(
                "Valis",
                "terran world",
                new KmuPickerFaction("Hegemony", FACTION, null, "Vengeful (-100 / 100)", RELATIONSHIP),
                "Corvus Star System",
                "yellow star",
                "Corvus",
                "Corvus");
    }
}
