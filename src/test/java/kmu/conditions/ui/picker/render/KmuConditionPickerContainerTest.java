package kmu.conditions.ui.picker.render;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import kmu.starsector.StarsectorTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerContainerTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color FACTION = new Color(90, 150, 240);
    private static final Color RELATIONSHIP = new Color(240, 80, 80);

    private MockedStatic<Misc> misc;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorTestSupport.installSettings();
        misc = Mockito.mockStatic(Misc.class);
        misc.when(Misc::getGrayColor).thenReturn(GRAY);
        misc.when(Misc::getHighlightColor).thenReturn(GOLD);
        misc.when(Misc::getNegativeHighlightColor).thenReturn(RED);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        misc.close();
        StarsectorTestSupport.clearSettings();
    }

    @Test
    void renderDoesNotPrintTitleInsidePicker() {
        List<String> titles = new ArrayList<>();
        TooltipMakerAPI body = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
            if ("addTitle".equals(method.getName()) && args != null && args.length >= 1) {
                titles.add(String.valueOf(args[0]));
                return label();
            }
            if ("addPara".equals(method.getName())) {
                return label();
            }
            return defaultValue(method.getReturnType());
        });
        CustomPanelAPI panel = proxy(CustomPanelAPI.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                location());

        new KmuConditionPickerContainer(panel).render(body, model, action -> { }, 400f);

        assertThat(titles).isEmpty();
    }

    @Test
    void summarizesTotalAndPresentConditions() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(Arrays.asList(
                entry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
                entry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
                location());

        assertThat(KmuConditionPickerContainer.summaryText(model))
                .isEqualTo("Conditions: 4 total; 3 present; 1 hidden; 1 suppressed.");
    }

    @Test
    void summarizesLocationSeparatelyFromConditionCounts() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                location());

        assertThat(KmuConditionPickerContainer.locationText(model))
                .isEqualTo("Location: Valis (terran world) - owned by Hegemony "
                        + "(Vengeful (-100 / 100)) - Corvus Star System (yellow star) - Corvus.");
    }

    @Test
    void showsGravityWellEntityNameWhenNotImpliedBySystemName() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null, null, null, null, null, null,
                        "Kumari System",
                        "Yellow Dwarf",
                        "Kumari A",
                        null));

        KmuLabelSpec spec = KmuConditionPickerContainer.locationLabelSpec(model);
        assertThat(spec.getText())
                .isEqualTo("Location: Kumari System (Kumari A, Yellow Dwarf).");
        assertThat(spec.getHighlights()).containsExactly("Kumari System", "Kumari A");
    }

    @Test
    void suppressesGravityWellEntityNameWhenImpliedBySystemName() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null, null, null, null, null, null,
                        "Agreus System",
                        "Black Hole",
                        "Agreus",
                        null));

        KmuLabelSpec spec = KmuConditionPickerContainer.locationLabelSpec(model);
        assertThat(spec.getText()).isEqualTo("Location: Agreus System (Black Hole).");
        assertThat(spec.getHighlights()).containsExactly("Agreus System");
    }

    @Test
    void deduplicatesGravityWellNameAndEntityNameForNonPlanetGravityWells() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null, null, null, null, null, null,
                        "Kumari Star System",
                        "Kumari Barycenter",
                        "Kumari Barycenter",
                        null));

        KmuLabelSpec spec = KmuConditionPickerContainer.locationLabelSpec(model);
        assertThat(spec.getText())
                .isEqualTo("Location: Kumari Star System (Kumari Barycenter).");
        assertThat(spec.getHighlights()).containsExactly("Kumari Star System");
    }

    @Test
    void omitsMissingLocationFields() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                new KmuConditionPickerLocation(
                        "Valis",
                        " ",
                        null,
                        null,
                        null,
                        null,
                        "Corvus Star System",
                        null,
                        null,
                        null));

        KmuLabelSpec spec = KmuConditionPickerContainer.locationLabelSpec(model);
        assertThat(spec.getText()).isEqualTo("Location: Valis - Corvus Star System.");
        assertThat(spec.getHighlights()).containsExactly("Valis", "Corvus Star System");
    }

    @Test
    void returnsEmptyLocationTextWhenLocationHasNoDisplayableFields() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null));

        KmuLabelSpec spec = KmuConditionPickerContainer.locationLabelSpec(model);
        assertThat(spec.getText()).isEmpty();
        assertThat(spec.getHighlights()).isEmpty();
        assertThat(spec.getHighlightColors()).isEmpty();
    }

    @Test
    void exposesLocationHighlights() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                location());

        assertThat(KmuConditionPickerContainer.locationLabelSpec(model).getHighlights())
                .containsExactly(
                        "Valis",
                        "Hegemony",
                        "Vengeful (-100 / 100)",
                        "Corvus Star System",
                        "Corvus");
    }

    @Test
    void exposesLocationHighlightColors() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                location());

        assertThat(KmuConditionPickerContainer.locationLabelSpec(model).getHighlightColors())
                .containsExactly(GOLD, FACTION, RELATIONSHIP, GOLD, GOLD);
    }

    @Test
    void defaultsMissingFactionAndRelationshipColorsToWhiteInRenderer() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                java.util.Collections.emptyList(),
                new KmuConditionPickerLocation(
                        null,
                        null,
                        "Hegemony",
                        null,
                        "Vengeful (-100 / 100)",
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(KmuConditionPickerContainer.locationLabelSpec(model).getHighlightColors())
                .containsExactly(Color.WHITE, Color.WHITE);
    }

    @Test
    void exposesSummaryCountHighlights() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(Arrays.asList(
                entry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
                entry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
                location());

        assertThat(KmuConditionPickerContainer.summaryLabelSpec(model).getHighlights())
                .containsExactly("4", "3", "1", "1");
    }

    @Test
    void exposesRedSummaryHighlightForSuppressedCount() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(Arrays.asList(
                entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
                location());

        Color[] colors = KmuConditionPickerContainer.summaryLabelSpec(model).getHighlightColors();

        assertThat(colors).hasSize(4);
        assertThat(colors[0]).isEqualTo(GOLD);
        assertThat(colors[2]).isEqualTo(GOLD);
        assertThat(colors[3]).isEqualTo(RED);
    }

    @Test
    void reservesRightPaddingForScrollbar() {
        assertThat(KmuConditionPickerContainer.gridWidth(400f))
                .isEqualTo(352f);
    }

    @Test
    void snapsGridWidthToFullRowsOfSquareVanillaCells() {
        assertThat(KmuConditionPickerContainer.DEFAULT_SQUARE_ICON_COLUMNS)
                .isEqualTo(12);
        assertThat(KmuConditionPickerContainer.defaultContainerWidth())
                .isEqualTo(744f);
        assertThat(KmuConditionPickerContainer.gridWidth(KmuConditionPickerContainer.defaultContainerWidth()))
                .isEqualTo(712f);
    }

    @Test
    void derivesContainerWidthFromSquareVanillaCellCount() {
        assertThat(KmuConditionPickerContainer.containerWidthForSquareIconColumns(1))
                .isEqualTo(84f);
    }

    @Test
    void keepsGridWidthPositiveForNarrowContainers() {
        assertThat(KmuConditionPickerContainer.gridWidth(12f))
                .isEqualTo(1f);
    }

    private static KmuConditionPickerEntry entry(String id, KmuConditionPickerEntryState state) {
        return entry(id, state, false, false);
    }

    private static KmuConditionPickerEntry entry(
            String id,
            KmuConditionPickerEntryState state,
            boolean suppressed,
            boolean hidden) {
        return new KmuConditionPickerEntry(
                id,
                id,
                "graphics/icons/markets/" + id + ".png",
                state,
                "Test tooltip",
                null,
                suppressed,
                hidden);
    }

    private static KmuConditionPickerLocation location() {
        return new KmuConditionPickerLocation(
                "Valis",
                "terran world",
                "Hegemony",
                FACTION,
                "Vengeful (-100 / 100)",
                RELATIONSHIP,
                "Corvus Star System",
                "yellow star",
                "Corvus",
                "Corvus");
    }

    private static LabelAPI label() {
        return proxy(LabelAPI.class, (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (boolean.class.equals(returnType)) {
            return false;
        }
        if (char.class.equals(returnType)) {
            return '\0';
        }
        if (byte.class.equals(returnType)) {
            return (byte) 0;
        }
        if (short.class.equals(returnType)) {
            return (short) 0;
        }
        if (int.class.equals(returnType)) {
            return 0;
        }
        if (long.class.equals(returnType)) {
            return 0L;
        }
        if (float.class.equals(returnType)) {
            return 0f;
        }
        if (double.class.equals(returnType)) {
            return 0d;
        }
        return null;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
