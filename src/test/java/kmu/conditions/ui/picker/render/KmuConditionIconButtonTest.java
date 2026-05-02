package kmu.conditions.ui.picker.render;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorTestSupport;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionIconButtonTest {
    private static final Color DEFAULT_BACKDROP = new Color(31, 94, 112, 175);
    private static final Color DEFAULT_BORDER = new Color(170, 222, 255, 255);
    private static final Color VISIBLE_PRESENT_BACKDROP = new Color(35, 80, 45);
    private static final Color VISIBLE_PRESENT_BORDER = new Color(90, 220, 95);
    private static final Color SUPPRESSED_BACKDROP = new Color(150, 50, 45);
    private static final Color SUPPRESSED_BORDER = new Color(255, 90, 80);
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color TEXT = new Color(220, 220, 220, 255);

    private MockedStatic<Misc> misc;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorTestSupport.installSettings();
        misc = Mockito.mockStatic(Misc.class);
        misc.when(Misc::getDarkPlayerColor).thenReturn(DEFAULT_BACKDROP);
        misc.when(Misc::getBasePlayerColor).thenReturn(DEFAULT_BORDER);
        misc.when(Misc::getGrayColor).thenReturn(GRAY);
        misc.when(Misc::getTextColor).thenReturn(TEXT);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        misc.close();
        StarsectorTestSupport.clearSettings();
    }

    @Test
    void greysOutAbsentEntriesOnly() {
        assertThat(KmuConditionIconButton.shouldGreyOut(entry(KmuConditionPickerEntryState.ABSENT)))
                .isTrue();
        assertThat(KmuConditionIconButton.shouldGreyOut(entry(KmuConditionPickerEntryState.PRESENT)))
                .isFalse();
        assertThat(KmuConditionIconButton.shouldGreyOut(suppressedEntry()))
                .isFalse();
        assertThat(KmuConditionIconButton.shouldGreyOut(hiddenEntry()))
                .isFalse();
    }

    @Test
    void stylesAbsentEntriesWithDefaultButtonColorsAndGreyedOutIcon() {
        KmuConditionPickerEntry entry = entry(KmuConditionPickerEntryState.ABSENT);

        assertButtonStyle(entry, DEFAULT_BACKDROP, DEFAULT_BORDER, 0.28f, 0.18f);
        assertButtonStyleRoles(entry, StarsectorUiColor.DARK_BLUE, StarsectorUiColor.BLUE);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isTrue();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isFalse();
    }

    @Test
    void stylesVisibleUnsuppressedPresentEntriesWithPositiveGreenButtonColors() {
        KmuConditionPickerEntry entry = entry(KmuConditionPickerEntryState.PRESENT);

        assertButtonStyle(entry, VISIBLE_PRESENT_BACKDROP, VISIBLE_PRESENT_BORDER, 0.28f, 0.42f);
        assertButtonStyleRoles(entry, StarsectorUiColor.DARK_GREEN, StarsectorUiColor.BRIGHT_GREEN);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isFalse();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isTrue();
    }

    @Test
    void stylesHiddenPresentEntriesWithDefaultButtonColorsAndFullIcon() {
        KmuConditionPickerEntry entry = hiddenEntry();

        assertButtonStyle(entry, DEFAULT_BACKDROP, DEFAULT_BORDER, 0.28f, 0.18f);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isFalse();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isFalse();
    }

    @Test
    void stylesSuppressedPresentEntriesWithWarningButtonColorsAndFullIcon() {
        KmuConditionPickerEntry entry = suppressedEntry();

        assertButtonStyle(entry, SUPPRESSED_BACKDROP, SUPPRESSED_BORDER, 0.34f, 0.50f);
        assertButtonStyleRoles(entry, StarsectorUiColor.MUTED_RED, StarsectorUiColor.BRIGHT_RED);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isFalse();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isFalse();
    }

    @Test
    void prioritizesSuppressedStyleWhenEntryIsBothSuppressedAndHidden() {
        KmuConditionPickerEntry entry = new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionPickerEntryState.PRESENT,
                "Test tooltip",
                "Starsector",
                true,
                true);

        assertButtonStyle(entry, SUPPRESSED_BACKDROP, SUPPRESSED_BORDER, 0.34f, 0.50f);
    }

    @Test
    void rendersIconsAtVanillaHeightWhenSourceAlreadyMatches() {
        KmuConditionIconButton.ButtonMetrics wide = KmuConditionIconButton.metricsForSource(120f, 40f);

        assertThat(wide.getIconWidth()).isEqualTo(120f);
        assertThat(wide.getIconHeight()).isEqualTo(40f);
        assertThat(wide.getButtonWidth()).isEqualTo(120f + KmuConditionIconButton.Sizing.ICON_MARGIN * 2f);
        assertThat(wide.getButtonHeight()).isEqualTo(40f + KmuConditionIconButton.Sizing.ICON_MARGIN * 2f);
    }

    @Test
    void upscalesSmallerIconsToVanillaHeightWithoutChangingAspectRatio() {
        KmuConditionIconButton.ButtonMetrics small = KmuConditionIconButton.metricsForSource(20f, 20f);

        assertThat(small.getIconWidth()).isEqualTo(40f);
        assertThat(small.getIconHeight())
                .isEqualTo(KmuConditionIconButton.Sizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
        assertThat(small.getButtonWidth()).isEqualTo(40f + KmuConditionIconButton.Sizing.ICON_MARGIN * 2f);
        assertThat(small.getButtonHeight()).isEqualTo(40f + KmuConditionIconButton.Sizing.ICON_MARGIN * 2f);
    }

    @Test
    void preservesWideIconWidthWhenHeightMatchesVanillaTarget() {
        KmuConditionIconButton.ButtonMetrics wide = KmuConditionIconButton.metricsForSource(180f, 40f);

        assertThat(wide.getIconWidth()).isEqualTo(180f);
        assertThat(wide.getIconHeight())
                .isEqualTo(KmuConditionIconButton.Sizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
    }

    @Test
    void downscalesOversizedIconsToVanillaHeightWithoutChangingAspectRatio() {
        KmuConditionIconButton.ButtonMetrics tall = KmuConditionIconButton.metricsForSource(64f, 192f);

        assertThat(tall.getIconWidth()).isBetween(13.33f, 13.34f);
        assertThat(tall.getIconHeight())
                .isEqualTo(KmuConditionIconButton.Sizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
    }

    @Test
    void updatesEntryStateInPlace() {
        KmuConditionIconButton button = new KmuConditionIconButton(
                entry(KmuConditionPickerEntryState.ABSENT),
                action -> {
                });

        button.updateEntry(entry(KmuConditionPickerEntryState.PRESENT));

        assertThat(button.getEntry().getState()).isEqualTo(KmuConditionPickerEntryState.PRESENT);
        assertThat(KmuConditionIconButton.shouldGreyOut(button.getEntry())).isFalse();
    }

    @Test
    void appendsMetadataFooterInExpectedOrder() {
        RecordingTooltip tooltip = RecordingTooltip.create();
        KmuConditionPickerEntry entry = new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionPickerEntryState.PRESENT,
                "Test tooltip",
                "Starsector",
                true,
                false);

        new KmuConditionIconButton.EntryTooltipCreator(() -> entry)
                .createTooltip(tooltip.api(), false, null);

        assertThat(tooltip.headings()).contains("Metadata");
        assertThat(tooltip.paragraphs()).containsSequence(
                "source: Starsector",
                "id: hot",
                "icon: graphics/icons/markets/hot.png",
                "hidden: false",
                "suppressed: true");
    }

    @Test
    void omitsHiddenAndSuppressedMetadataForAbsentEntries() {
        RecordingTooltip tooltip = RecordingTooltip.create();
        KmuConditionPickerEntry entry = new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionPickerEntryState.ABSENT,
                "Test tooltip",
                "Starsector",
                true,
                true);

        new KmuConditionIconButton.EntryTooltipCreator(() -> entry)
                .createTooltip(tooltip.api(), false, null);

        assertThat(tooltip.paragraphs()).containsSequence(
                "source: Starsector",
                "id: hot",
                "icon: graphics/icons/markets/hot.png");
        assertThat(tooltip.paragraphs()).doesNotContain("hidden: true", "suppressed: true");
    }

    private static KmuConditionPickerEntry entry(KmuConditionPickerEntryState state) {
        return new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                state,
                "Test tooltip");
    }

    private static KmuConditionPickerEntry suppressedEntry() {
        return new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionPickerEntryState.PRESENT,
                "Test tooltip",
                "Starsector",
                true,
                false);
    }

    private static KmuConditionPickerEntry hiddenEntry() {
        return new KmuConditionPickerEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionPickerEntryState.PRESENT,
                "Test tooltip",
                "Starsector",
                false,
                true);
    }

    private static void assertButtonStyle(
            KmuConditionPickerEntry entry,
            Color backdropColor,
            Color borderColor,
            float backdropAlpha,
            float borderAlpha) {
        assertThat(KmuConditionIconButton.backdropColorFor(entry)).isEqualTo(backdropColor);
        assertThat(KmuConditionIconButton.borderColorFor(entry)).isEqualTo(borderColor);
        assertThat(KmuConditionIconButton.backdropAlphaFor(entry)).isEqualTo(backdropAlpha);
        assertThat(KmuConditionIconButton.borderAlphaFor(entry)).isEqualTo(borderAlpha);
    }

    private static void assertButtonStyleRoles(
            KmuConditionPickerEntry entry,
            StarsectorUiColor backdropColor,
            StarsectorUiColor borderColor) {
        KmuConditionIconButtonStyle style = KmuConditionIconButton.styleFor(entry);

        assertThat(style.backdropRawColor()).isEqualTo(backdropColor);
        assertThat(style.borderRawColor()).isEqualTo(borderColor);
    }

    private static final class RecordingTooltip implements InvocationHandler {
        private final List<String> headings = new ArrayList<>();
        private final List<String> paragraphs = new ArrayList<>();
        private final TooltipMakerAPI api;

        private RecordingTooltip() {
            this.api = (TooltipMakerAPI) Proxy.newProxyInstance(
                    TooltipMakerAPI.class.getClassLoader(),
                    new Class<?>[]{TooltipMakerAPI.class},
                    this);
        }

        static RecordingTooltip create() {
            return new RecordingTooltip();
        }

        TooltipMakerAPI api() {
            return api;
        }

        List<String> headings() {
            return headings;
        }

        List<String> paragraphs() {
            return paragraphs;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            if ("addTitle".equals(method.getName()) && args != null && args.length >= 1) {
                headings.add((String) args[0]);
                return null;
            }
            if ("addSectionHeading".equals(method.getName()) && args != null && args.length >= 1) {
                headings.add((String) args[0]);
                return null;
            }
            if ("addPara".equals(method.getName()) && args != null && args.length >= 1) {
                paragraphs.add((String) args[0]);
                return null;
            }
            if ("toString".equals(method.getName())) {
                return "RecordingTooltip";
            }
            if ("hashCode".equals(method.getName())) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(method.getName())) {
                return proxy == args[0];
            }
            return defaultValue(method.getReturnType());
        }

        private Object defaultValue(Class<?> returnType) {
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
    }
}
