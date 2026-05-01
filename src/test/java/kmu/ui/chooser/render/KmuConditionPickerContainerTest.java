package kmu.ui.chooser.render;

import kmu.ui.chooser.model.KmuConditionChooserEntry;
import kmu.ui.chooser.model.KmuConditionChooserEntryState;
import kmu.ui.chooser.model.KmuConditionChooserLocation;
import kmu.ui.chooser.model.KmuConditionChooserModel;

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
        KmuConditionChooserModel model = new KmuConditionChooserModel(
                java.util.Collections.emptyList(),
                new KmuConditionChooserLocation("Valis Outpost", "Corvus Star System", "Corvus Constellation"));

        new KmuConditionPickerContainer(panel).render(body, model, action -> { }, 400f);

        assertThat(titles).isEmpty();
    }

    @Test
    void summarizesTotalAndPresentConditions() {
        KmuConditionChooserModel model = new KmuConditionChooserModel(Arrays.asList(
                entry("hot", KmuConditionChooserEntryState.PRESENT, false, false),
                entry("cold", KmuConditionChooserEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionChooserEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionChooserEntryState.PRESENT, true, false)),
                new KmuConditionChooserLocation("Valis Outpost", "Corvus Star System", "Corvus Constellation"));

        assertThat(KmuConditionPickerContainer.summaryText(model))
                .isEqualTo("Valis Outpost, Corvus Star System, Corvus Constellation. "
                        + "Conditions: 4 total; 3 present; 1 hidden; 1 suppressed.");
    }

    @Test
    void exposesSummaryNumberHighlights() {
        KmuConditionChooserModel model = new KmuConditionChooserModel(Arrays.asList(
                entry("hot", KmuConditionChooserEntryState.PRESENT, false, false),
                entry("cold", KmuConditionChooserEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionChooserEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionChooserEntryState.PRESENT, true, false)),
                new KmuConditionChooserLocation("Valis Outpost", "Corvus Star System", "Corvus Constellation"));

        assertThat(KmuConditionPickerContainer.summaryHighlights(model))
                .containsExactly(
                        "Valis Outpost",
                        "Corvus Star System",
                        "Corvus Constellation",
                        "4 total",
                        "3 present",
                        "1 hidden",
                        "1 suppressed");
    }

    @Test
    void exposesRedSummaryHighlightForSuppressedCount() {
        Color highlight = new Color(255, 220, 80);
        Color suppressed = new Color(255, 80, 80);
        Color[] colors = KmuConditionPickerContainer.summaryHighlightColors(highlight, suppressed);

        assertThat(colors).hasSize(7);
        assertThat(colors[0]).isEqualTo(highlight);
        assertThat(colors[5]).isEqualTo(highlight);
        assertThat(colors[6]).isEqualTo(suppressed);
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

    private static KmuConditionChooserEntry entry(String id, KmuConditionChooserEntryState state) {
        return entry(id, state, false, false);
    }

    private static KmuConditionChooserEntry entry(
            String id,
            KmuConditionChooserEntryState state,
            boolean suppressed,
            boolean hidden) {
        return new KmuConditionChooserEntry(
                id,
                id,
                "graphics/icons/markets/" + id + ".png",
                state,
                "Test tooltip",
                null,
                suppressed,
                hidden);
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
