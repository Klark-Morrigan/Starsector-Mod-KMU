package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import kmu.conditions.ui.picker.render.spec.KmuLabelSpec;
import kmu.starsector.StarsectorSettingsFake;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerInfoRowTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color TEXT = new Color(220, 220, 220, 255);

    private MockedStatic<Misc> misc;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorSettingsFake.installSettings();
        misc = Mockito.mockStatic(Misc.class);
        misc.when(Misc::getGrayColor).thenReturn(GRAY);
        misc.when(Misc::getHighlightColor).thenReturn(GOLD);
        misc.when(Misc::getNegativeHighlightColor).thenReturn(RED);
        misc.when(Misc::getPositiveHighlightColor).thenReturn(GREEN);
        misc.when(Misc::getTextColor).thenReturn(TEXT);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        misc.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Test
    void computeHeightIncludesSectionGapWhenBothSectionsPresent() {
        float height = KmuConditionPickerInfoRow.computeHeight(0f, 2, 2);

        float expected = 4 * KmuConditionPickerInfoRow.LINE_HEIGHT + 6f; // 6f = SECTION_PAD
        assertThat(height).isEqualTo(expected);
    }

    @Test
    void computeHeightExcludesSectionGapWhenOnlyLocationPresent() {
        float height = KmuConditionPickerInfoRow.computeHeight(0f, 2, 0);

        float expected = Math.max(KmuConditionPickerInfoRow.ICON_SIZE, 2 * KmuConditionPickerInfoRow.LINE_HEIGHT);
        assertThat(height).isEqualTo(expected);
    }

    @Test
    void computeHeightExcludesSectionGapWhenOnlySummaryPresent() {
        float height = KmuConditionPickerInfoRow.computeHeight(0f, 0, 2);

        float expected = Math.max(KmuConditionPickerInfoRow.ICON_SIZE, 2 * KmuConditionPickerInfoRow.LINE_HEIGHT);
        assertThat(height).isEqualTo(expected);
    }

    @Test
    void rendersAllTextsWithWhiteBaseColor() {
        List<Color> paraColors = new ArrayList<>();
        CustomPanelAPI row = rowPanel(paraColors);

        List<KmuLabelSpec> locationSpecs = Arrays.asList(
                new KmuLabelSpec("Location:", new String[0], new Color[0]),
                new KmuLabelSpec("Valis (terran world)", new String[0], new Color[0]));
        List<KmuLabelSpec> summarySpecs = Arrays.asList(
                new KmuLabelSpec("Conditions:", new String[0], new Color[0]),
                new KmuLabelSpec("0 available, 0 total.", new String[0], new Color[0]));

        KmuConditionPickerInfoRow.render(row, locationSpecs, summarySpecs, Optional.empty(), 400f);

        // 2 location + 2 summary lines, all TEXT base color
        assertThat(paraColors).hasSize(4).containsOnly(TEXT);
    }

    @Test
    void rendersOnlySummaryWhenLocationListIsEmpty() {
        List<Color> paraColors = new ArrayList<>();
        CustomPanelAPI row = rowPanel(paraColors);

        List<KmuLabelSpec> summarySpecs = Arrays.asList(
                new KmuLabelSpec("Conditions:", new String[0], new Color[0]),
                new KmuLabelSpec("0 available, 0 total.", new String[0], new Color[0]));

        KmuConditionPickerInfoRow.render(
                row, Collections.emptyList(), summarySpecs, Optional.empty(), 400f);

        assertThat(paraColors).hasSize(2).containsOnly(TEXT);
    }

    /**
     * Builds a row panel whose every UI element captures addPara base colors
     * into {@code paraColors}.
     */
    private static CustomPanelAPI rowPanel(List<Color> paraColors) {
        PositionAPI position = proxy(PositionAPI.class,
                (p, method, args) -> defaultValue(method.getReturnType()));
        TooltipMakerAPI element = proxy(TooltipMakerAPI.class, (p, method, args) -> {
            if ("addPara".equals(method.getName()) && args != null && args.length >= 2
                    && args[1] instanceof Color) {
                paraColors.add((Color) args[1]);
                return createLabel();
            }
            return defaultValue(method.getReturnType());
        });
        // createCustomPanel returns self so the inner row proxy is this same panel proxy,
        // allowing createUIElement and addUIElement calls on it to be intercepted.
        return proxy(CustomPanelAPI.class, (p, method, args) -> {
            if ("createCustomPanel".equals(method.getName())) return p;
            if ("createUIElement".equals(method.getName())) return element;
            if ("addUIElement".equals(method.getName())) return position;
            return defaultValue(method.getReturnType());
        });
    }

    private static LabelAPI createLabel() {
        return proxy(LabelAPI.class, (p, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive()) return null;
        if (boolean.class.equals(returnType)) return false;
        if (char.class.equals(returnType)) return '\0';
        if (byte.class.equals(returnType)) return (byte) 0;
        if (short.class.equals(returnType)) return (short) 0;
        if (int.class.equals(returnType)) return 0;
        if (long.class.equals(returnType)) return 0L;
        if (float.class.equals(returnType)) return 0f;
        if (double.class.equals(returnType)) return 0d;
        return null;
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
