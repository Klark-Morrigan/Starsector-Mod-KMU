package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerContainerTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color BLUE = new Color(170, 222, 255, 255);
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
        miscMock.when(Misc::getNegativeHighlightColor).thenReturn(RED);
        miscMock.when(Misc::getPositiveHighlightColor).thenReturn(GREEN);
        miscMock.when(Misc::getTextColor).thenReturn(TEXT);
        miscMock.when(Misc::getBasePlayerColor).thenReturn(BLUE);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class Render {

        @Test
        void renderDoesNotPrintTitleInsidePicker() {
            var titles = new ArrayList<String>();
            var body = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
                if ("addTitle".equals(method.getName()) && args != null && args.length >= 1) {
                    titles.add(String.valueOf(args[0]));
                    return createLabel();
                }
                if ("addPara".equals(method.getName())) {
                    return createLabel();
                }
                return defaultValue(method.getReturnType());
            });
            var panel = proxy(CustomPanelAPI.class, (proxy, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return createRowPanel();
                return defaultValue(method.getReturnType());
            });
            var model = new KmuConditionPickerModel(
                    Collections.emptyList(),
                    createLocation());

            new KmuConditionPickerContainer(panel).render(body, body, model, action -> { }, 400f);

            assertThat(titles).isEmpty();
        }

        @Test
        void renderRoutesLabelsToHeaderBodyAndGridToGridBody() {
            var headerParas = new ArrayList<String>();
            var headerCustoms = new ArrayList<Object>();
            var gridParas = new ArrayList<String>();

            var headerBody = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
                if ("addPara".equals(method.getName()) && args != null && args.length >= 1) {
                    headerParas.add(String.valueOf(args[0]));
                    return createLabel();
                }
                if ("addCustom".equals(method.getName())) {
                    headerCustoms.add(args[0]);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
            var gridBody = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
                if ("addPara".equals(method.getName()) && args != null && args.length >= 1) {
                    gridParas.add(String.valueOf(args[0]));
                    return createLabel();
                }
                return defaultValue(method.getReturnType());
            });
            var panel = proxy(CustomPanelAPI.class, (proxy, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return createRowPanel();
                return defaultValue(method.getReturnType());
            });

            // Empty model: summary row (containing location + conditions) goes via addCustom,
            // empty-state message to gridBody. No separate addPara for location.
            var model = new KmuConditionPickerModel(
                    Collections.emptyList(),
                    createLocation());

            new KmuConditionPickerContainer(panel).render(headerBody, gridBody, model, action -> { }, 400f);

            assertThat(headerParas).isEmpty();    // location is inside the summary panel
            assertThat(headerCustoms).hasSize(1); // summary row panel
            assertThat(gridParas).hasSize(1);     // empty-state message
        }

        @Test
        void renderUsesCustomPanelForSummaryRowWhenFactionHasCrestSprite() {
            var headerParas = new ArrayList<String>();
            var headerCustoms = new ArrayList<Object>();

            // Proxy chain for the icon+text row panel created inside the container.
            var position = proxy(PositionAPI.class,
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
            var rowElement = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
                if ("addPara".equals(method.getName())) return createLabel();
                return defaultValue(method.getReturnType());
            });
            var summaryRow = proxy(CustomPanelAPI.class, (proxy, method, args) -> {
                if ("createUIElement".equals(method.getName())) return rowElement;
                if ("addUIElement".equals(method.getName())) return position;
                return defaultValue(method.getReturnType());
            });
            var panel = proxy(CustomPanelAPI.class, (proxy, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return summaryRow;
                return defaultValue(method.getReturnType());
            });
            var headerBody = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
                if ("addPara".equals(method.getName()) && args != null && args.length >= 1) {
                    headerParas.add(String.valueOf(args[0]));
                    return createLabel();
                }
                if ("addCustom".equals(method.getName())) {
                    headerCustoms.add(args[0]);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
            var gridBody = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
                if ("addPara".equals(method.getName())) return createLabel();
                return defaultValue(method.getReturnType());
            });

            var faction = new KmuPickerFaction(
                    "Hegemony", FACTION, "graphics/factions/hegemony_crest.png", null, null);
            var model = new KmuConditionPickerModel(
                    Collections.emptyList(),
                    new KmuConditionPickerLocation("Valis", "terran world", faction, null, null, null, null));

            new KmuConditionPickerContainer(panel).render(headerBody, gridBody, model, action -> { }, 400f);

            // Location and conditions are both inside the summary panel; only addCustom is called.
            assertThat(headerParas).isEmpty();
            assertThat(headerCustoms).hasSize(1);
        }

        @Test
        void renderSkipsLocationLabelWhenLocationHasNoDisplayableFields() {
            var headerParas = new ArrayList<String>();
            var headerCustoms = new ArrayList<Object>();

            var headerBody = proxy(TooltipMakerAPI.class, (p, method, args) -> {
                if ("addPara".equals(method.getName()) && args != null && args.length >= 1) {
                    headerParas.add(String.valueOf(args[0]));
                    return createLabel();
                }
                if ("addCustom".equals(method.getName())) {
                    headerCustoms.add(args[0]);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
            var gridBody = proxy(TooltipMakerAPI.class, (p, method, args) -> {
                if ("addPara".equals(method.getName())) return createLabel();
                return defaultValue(method.getReturnType());
            });
            var panel = proxy(CustomPanelAPI.class, (p, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return createRowPanel();
                return defaultValue(method.getReturnType());
            });
            var model = new KmuConditionPickerModel(
                    Collections.emptyList(),
                    new KmuConditionPickerLocation(null, null, null, null, null, null, null));

            new KmuConditionPickerContainer(panel).render(headerBody, gridBody, model, action -> { }, 400f);

            assertThat(headerParas).isEmpty();    // no location label
            assertThat(headerCustoms).hasSize(1); // summary row only
        }

        @Test
        void renderSkipsEmptyStateAndInvokesGridForNonEmptyModel() {
            var gridParas = new ArrayList<String>();
            var gridCustoms = new ArrayList<Object>();

            var gridBody = proxy(TooltipMakerAPI.class, (p, method, args) -> {
                if ("addPara".equals(method.getName()) && args != null && args.length >= 1) {
                    gridParas.add(String.valueOf(args[0]));
                    return createLabel();
                }
                if ("addCustom".equals(method.getName())) {
                    gridCustoms.add(args[0]);
                    return null;
                }
                return defaultValue(method.getReturnType());
            });
            var headerBody = proxy(TooltipMakerAPI.class, (p, method, args) -> {
                if ("addPara".equals(method.getName())) return createLabel();
                return defaultValue(method.getReturnType());
            });

            var gridPosition = proxy(PositionAPI.class,
                    (p, method, args) -> defaultValue(method.getReturnType()));
            var buttonPanelStub = proxy(CustomPanelAPI.class,
                    (p, method, args) -> defaultValue(method.getReturnType()));
            var gridPanel = proxy(CustomPanelAPI.class, (p, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return buttonPanelStub;
                if ("addComponent".equals(method.getName())) return gridPosition;
                return defaultValue(method.getReturnType());
            });
            int[] createCustomPanelCount = {0};
            var panel = proxy(CustomPanelAPI.class, (p, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) {
                    return (createCustomPanelCount[0]++ == 0) ? createRowPanel() : gridPanel;
                }
                return defaultValue(method.getReturnType());
            });

            var model = new KmuConditionPickerModel(
                    Collections.singletonList(entry("hot", KmuConditionPickerEntryState.PRESENT)),
                    new KmuConditionPickerLocation(null, null, null, null, null, null, null));

            new KmuConditionPickerContainer(panel).render(headerBody, gridBody, model, action -> { }, 400f);

            assertThat(gridParas).isEmpty();      // no empty-state message
            assertThat(gridCustoms).hasSize(1);   // grid panel added to gridBody
        }

        @Test
        void renderResultReturnsFalseFromUpdateEntryWhenModelIsEmpty() {
            var body = proxy(TooltipMakerAPI.class, (p, method, args) -> {
                if ("addPara".equals(method.getName())) return createLabel();
                return defaultValue(method.getReturnType());
            });
            var panel = proxy(CustomPanelAPI.class, (p, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return createRowPanel();
                return defaultValue(method.getReturnType());
            });
            var model = new KmuConditionPickerModel(
                    Collections.emptyList(),
                    new KmuConditionPickerLocation(null, null, null, null, null, null, null));

            var result =
                    new KmuConditionPickerContainer(panel).render(body, body, model, action -> { }, 400f);

            assertThat(result.updateEntry(entry("hot", KmuConditionPickerEntryState.PRESENT))).isFalse();
        }

        @Test
        void renderResultDelegatesToGridHandleForUpdateEntry() {
            var headerBody = proxy(TooltipMakerAPI.class, (p, method, args) -> {
                if ("addPara".equals(method.getName())) return createLabel();
                return defaultValue(method.getReturnType());
            });
            var gridBody = proxy(TooltipMakerAPI.class,
                    (p, method, args) -> defaultValue(method.getReturnType()));

            var gridPosition = proxy(PositionAPI.class,
                    (p, method, args) -> defaultValue(method.getReturnType()));
            var buttonPanelStub = proxy(CustomPanelAPI.class,
                    (p, method, args) -> defaultValue(method.getReturnType()));
            var gridPanel = proxy(CustomPanelAPI.class, (p, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return buttonPanelStub;
                if ("addComponent".equals(method.getName())) return gridPosition;
                return defaultValue(method.getReturnType());
            });
            int[] count = {0};
            var panel = proxy(CustomPanelAPI.class, (p, method, args) -> {
                if ("createCustomPanel".equals(method.getName())) return (count[0]++ == 0) ? createRowPanel() : gridPanel;
                return defaultValue(method.getReturnType());
            });

            var model = new KmuConditionPickerModel(
                    Collections.singletonList(entry("hot", KmuConditionPickerEntryState.PRESENT)),
                    new KmuConditionPickerLocation(null, null, null, null, null, null, null));

            var result =
                    new KmuConditionPickerContainer(panel).render(headerBody, gridBody, model, action -> { }, 400f);

            assertThat(result.updateEntry(entry("hot", KmuConditionPickerEntryState.ABSENT))).isTrue();
            assertThat(result.updateEntry(entry("cold", KmuConditionPickerEntryState.ABSENT))).isFalse();
        }
    }

    @Nested
    class ComputeHeaderHeight {

        @Test
        void headerHeightScalesWithLocationAndSummaryLineCount() {
            // createLocation() has 4 specs (header + planet + system + constellation)
            // summary has 2 specs (header + counts) = 6 total lines * 20f = 120f, plus
            // SECTION_PAD (8f) between location and summary = 128f > ICON_SIZE 80f.
            var model = new KmuConditionPickerModel(
                    Collections.emptyList(),
                    createLocation());

            assertThat(KmuConditionPickerContainer.computeHeaderHeight(model))
                    .isEqualTo(8f + 128f);
        }

        @Test
        void headerHeightShowsUnknownAndSummaryWhenLocationIsAbsent() {
            // Empty location -> 2 specs (header + Unknown); summary -> 2 specs = 4 lines * 20f = 80f,
            // plus SECTION_PAD (8f) between the two sections = 88f.
            var model = new KmuConditionPickerModel(
                    Collections.emptyList(),
                    new KmuConditionPickerLocation(null, null, null, null, null, null, null));

            assertThat(KmuConditionPickerContainer.computeHeaderHeight(model))
                    .isEqualTo(8f + 88f);
        }
    }

    private static KmuConditionPickerEntry entry(String id, KmuConditionPickerEntryState state) {
        return new KmuConditionPickerEntry(
                id,
                id,
                "graphics/icons/markets/" + id + ".png",
                state,
                "Test tooltip",
                null,
                false,
                false);
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

    private static CustomPanelAPI createRowPanel() {
        var position = proxy(PositionAPI.class,
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        var rowElement = proxy(TooltipMakerAPI.class, (proxy, method, args) -> {
            if ("addPara".equals(method.getName())) return createLabel();
            return defaultValue(method.getReturnType());
        });
        return proxy(CustomPanelAPI.class, (proxy, method, args) -> {
            if ("createUIElement".equals(method.getName())) return rowElement;
            if ("addUIElement".equals(method.getName())) return position;
            return defaultValue(method.getReturnType());
        });
    }

    private static LabelAPI createLabel() {
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
