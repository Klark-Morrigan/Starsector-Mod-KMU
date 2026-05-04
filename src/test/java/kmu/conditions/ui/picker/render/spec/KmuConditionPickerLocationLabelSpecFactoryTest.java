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
    void summarizesLocationFields() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                location());

        assertThat(KmuConditionPickerLocationLabelSpecFactory.getText(model))
                .isEqualTo("Location: Valis (terran world) - owned by Hegemony "
                        + "(Vengeful (-100 / 100)) - Corvus Star System (yellow star) - Corvus.");
    }

    @Test
    void showsGravityWellEntityNameWhenNotImpliedBySystemName() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null, null, null,
                        "Kumari System",
                        "Yellow Dwarf",
                        "Kumari A",
                        null));

        KmuLabelSpec spec = KmuConditionPickerLocationLabelSpecFactory.get(model);
        assertThat(spec.getText())
                .isEqualTo("Location: Kumari System (Kumari A, Yellow Dwarf).");
        assertThat(spec.getHighlights()).containsExactly("Kumari System", "Kumari A");
    }

    @Test
    void suppressesGravityWellEntityNameWhenImpliedBySystemName() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null, null, null,
                        "Agreus System",
                        "Black Hole",
                        "Agreus",
                        null));

        KmuLabelSpec spec = KmuConditionPickerLocationLabelSpecFactory.get(model);
        assertThat(spec.getText()).isEqualTo("Location: Agreus System (Black Hole).");
        assertThat(spec.getHighlights()).containsExactly("Agreus System");
    }

    @Test
    void deduplicatesGravityWellNameAndEntityNameForNonPlanetGravityWells() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null, null, null,
                        "Kumari Star System",
                        "Kumari Barycenter",
                        "Kumari Barycenter",
                        null));

        KmuLabelSpec spec = KmuConditionPickerLocationLabelSpecFactory.get(model);
        assertThat(spec.getText())
                .isEqualTo("Location: Kumari Star System (Kumari Barycenter).");
        assertThat(spec.getHighlights()).containsExactly("Kumari Star System");
    }

    @Test
    void omitsMissingLocationFields() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                new KmuConditionPickerLocation(
                        "Valis",
                        " ",
                        null,
                        "Corvus Star System",
                        null,
                        null,
                        null));

        KmuLabelSpec spec = KmuConditionPickerLocationLabelSpecFactory.get(model);
        assertThat(spec.getText()).isEqualTo("Location: Valis - Corvus Star System.");
        assertThat(spec.getHighlights()).containsExactly("Valis", "Corvus Star System");
    }

    @Test
    void returnsEmptySpecWhenLocationHasNoDisplayableFields() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                new KmuConditionPickerLocation(null, null, null, null, null, null, null));

        KmuLabelSpec spec = KmuConditionPickerLocationLabelSpecFactory.get(model);
        assertThat(spec.getText()).isEmpty();
        assertThat(spec.getHighlights()).isEmpty();
        assertThat(spec.getHighlightColors()).isEmpty();
    }

    @Test
    void exposesHighlights() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                location());

        assertThat(KmuConditionPickerLocationLabelSpecFactory.get(model).getHighlights())
                .containsExactly(
                        "Valis",
                        "Hegemony",
                        "Vengeful (-100 / 100)",
                        "Corvus Star System",
                        "Corvus");
    }

    @Test
    void exposesHighlightColors() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                location());

        assertThat(KmuConditionPickerLocationLabelSpecFactory.get(model).getHighlightColors())
                .containsExactly(GOLD, FACTION, RELATIONSHIP, GOLD, GOLD);
    }

    @Test
    void defaultsMissingFactionAndRelationshipColorsToWhite() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null,
                        null,
                        new KmuPickerFaction("Hegemony", null, null, "Vengeful (-100 / 100)", null),
                        null,
                        null,
                        null,
                        null));

        assertThat(KmuConditionPickerLocationLabelSpecFactory.get(model).getHighlightColors())
                .containsExactly(TEXT, TEXT);
    }

    private static KmuConditionPickerLocation location() {
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
