package kmu.conditions.ui.picker.render.spec;

import com.fs.starfarer.api.util.Misc;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.starsector.StarsectorTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerLocationLabelSpecFactoryTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color TEXT = new Color(220, 220, 220, 255);
    private static final Color FACTION = new Color(90, 150, 240);
    private static final Color RELATIONSHIP = new Color(240, 80, 80);

    private MockedStatic<Misc> misc;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorTestSupport.installSettings();
        misc = Mockito.mockStatic(Misc.class);
        misc.when(Misc::getGrayColor).thenReturn(GRAY);
        misc.when(Misc::getHighlightColor).thenReturn(GOLD);
        misc.when(Misc::getTextColor).thenReturn(TEXT);
        misc.when(Misc::getNegativeHighlightColor).thenReturn(RED);
        misc.when(Misc::getPositiveHighlightColor).thenReturn(GREEN);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        misc.close();
        StarsectorTestSupport.clearSettings();
    }

    @Test
    void returnsHeaderAndUnknownWhenLocationHasNoDisplayableFields() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(null, null, null, null, null, null, null)));

        assertThat(specs).hasSize(2);
        assertThat(specs.get(0).getText()).isEqualTo("Location:");
        assertThat(specs.get(1).getText()).isEqualTo("Unknown");
        assertThat(specs.get(1).getHighlights()).containsExactly("Unknown");
        assertThat(specs.get(1).getHighlightColors()).containsExactly(GOLD);
    }

    @Test
    void returnsLocationHeaderAsFirstLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(0).getText()).isEqualTo("Location:");
        assertThat(specs.get(0).getHighlights()).isEmpty();
    }

    @Test
    void returnsPlanetLineWithTypeAndOwnershipAsSecondLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(1).getText())
                .isEqualTo("Valis (terran world) - owned by Hegemony (Vengeful (-100 / 100))");
    }

    @Test
    void returnsSystemLineAsThirdLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(2).getText()).isEqualTo("Corvus Star System (yellow star)");
    }

    @Test
    void returnsConstellationLineAsFourthLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(3).getText()).isEqualTo("Corvus");
    }

    @Test
    void returnsFourLinesForFullLocation() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        // header + planet + system + constellation
        assertThat(specs).hasSize(4);
    }

    @Test
    void omitsSystemLineWhenSystemIsAbsent() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(
                        "Valis", "terran world", null, null, null, null, null)));

        // header + planet only
        assertThat(specs).hasSize(2);
        assertThat(specs.get(1).getText()).isEqualTo("Valis (terran world)");
    }

    @Test
    void omitsConstellationLineWhenConstellationIsAbsent() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(
                        "Valis", null, null, "Corvus System", null, null, null)));

        // header + planet + system; no constellation
        assertThat(specs).hasSize(3);
    }

    @Test
    void omitsMissingLocationFields() {
        // Planet type whitespace is normalized to absent, no faction, no constellation
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(
                        "Valis", " ", null, "Corvus Star System", null, null, null)));

        assertThat(specs).hasSize(3); // header + planet + system
        assertThat(specs.get(1).getText()).isEqualTo("Valis");
        assertThat(specs.get(2).getText()).isEqualTo("Corvus Star System");
    }

    @Test
    void showsGravityWellEntityNameInSystemLineWhenNotImpliedBySystemName() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null, null, "Kumari System", "Yellow Dwarf", "Kumari A", null)));

        // header + system only
        KmuLabelSpec systemSpec = specs.get(1);
        assertThat(systemSpec.getText()).isEqualTo("Kumari System (Kumari A, Yellow Dwarf)");
        assertThat(systemSpec.getHighlights()).containsExactly("Kumari System", "Kumari A");
    }

    @Test
    void suppressesGravityWellEntityNameInSystemLineWhenImpliedBySystemName() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null, null, "Agreus System", "Black Hole", "Agreus", null)));

        KmuLabelSpec systemSpec = specs.get(1);
        assertThat(systemSpec.getText()).isEqualTo("Agreus System (Black Hole)");
        assertThat(systemSpec.getHighlights()).containsExactly("Agreus System");
    }

    @Test
    void deduplicatesGravityWellNameAndEntityNameForNonPlanetGravityWells() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null, null, "Kumari Star System", "Kumari Barycenter",
                        "Kumari Barycenter", null)));

        KmuLabelSpec systemSpec = specs.get(1);
        assertThat(systemSpec.getText()).isEqualTo("Kumari Star System (Kumari Barycenter)");
        assertThat(systemSpec.getHighlights()).containsExactly("Kumari Star System");
    }

    @Test
    void exposesPlanetLineHighlights() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(1).getHighlights())
                .containsExactly("Valis", "Hegemony", "Vengeful (-100 / 100)");
    }

    @Test
    void exposesSystemLineHighlights() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(2).getHighlights()).containsExactly("Corvus Star System");
    }

    @Test
    void exposesConstellationLineHighlights() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(3).getHighlights()).containsExactly("Corvus");
    }

    @Test
    void exposesPlanetLineHighlightColors() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(1).getHighlightColors())
                .containsExactly(GOLD, FACTION, RELATIONSHIP);
    }

    @Test
    void exposesSystemLineHighlightColors() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(2).getHighlightColors()).containsExactly(GOLD);
    }

    @Test
    void exposesConstellationLineHighlightColors() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(createLocation()));

        assertThat(specs.get(3).getHighlightColors()).containsExactly(GOLD);
    }

    @Test
    void defaultsMissingFactionAndRelationshipColorsToTextWhite() {
        List<KmuLabelSpec> specs = KmuConditionPickerLocationLabelSpecFactory.get(
                model(new KmuConditionPickerLocation(
                        null, null,
                        new KmuPickerFaction("Hegemony", null, null, "Vengeful (-100 / 100)", null),
                        null, null, null, null)));

        assertThat(specs.get(1).getHighlightColors()).containsExactly(TEXT, TEXT);
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
