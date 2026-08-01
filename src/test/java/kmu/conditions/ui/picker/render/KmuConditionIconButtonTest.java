package kmu.conditions.ui.picker.render;

import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.colour.StarsectorUiColour;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.tooltip.KmuConditionEntryTooltipCreator;
import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

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

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void mockStarsectorThemeColours() {
        StarsectorSettingsFake.installSettings();
        miscMock = Mockito.mockStatic(Misc.class);
        miscMock.when(Misc::getDarkPlayerColor).thenReturn(DEFAULT_BACKDROP);
        miscMock.when(Misc::getBasePlayerColor).thenReturn(DEFAULT_BORDER);
        miscMock.when(Misc::getGrayColor).thenReturn(GRAY);
        miscMock.when(Misc::getTextColor).thenReturn(TEXT);
    }

    @AfterEach
    void closeStarsectorThemeColours() {
        miscMock.close();
        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class IsPresent {

        @Test
        void greysOutAbsentEntriesOnly() {
            assertThat(entry(KmuConditionPickerEntryState.ABSENT).isPresent()).isFalse();
            assertThat(entry(KmuConditionPickerEntryState.PRESENT).isPresent()).isTrue();
            assertThat(suppressedEntry().isPresent()).isTrue();
            assertThat(hiddenEntry().isPresent()).isTrue();
        }
    }

    @Nested
    class ForEntry {

        @Test
        void stylesAbsentEntriesWithDefaultButtonColoursAndGreyedOutIcon() {
            var entry = entry(KmuConditionPickerEntryState.ABSENT);

            assertButtonStyle(entry, DEFAULT_BACKDROP, DEFAULT_BORDER, 0.28f, 0.18f);
            assertButtonStyleRoles(entry, StarsectorUiColour.DARK_BLUE, StarsectorUiColour.LIGHT_BLUE);
            assertThat(entry.isPresent()).isFalse();
            assertThat(entry.isPresent() && !entry.isSuppressed() && !entry.isHidden()).isFalse();
        }

        @Test
        void stylesVisibleUnsuppressedPresentEntriesWithPositiveGreenButtonColours() {
            var entry = entry(KmuConditionPickerEntryState.PRESENT);

            assertButtonStyle(entry, VISIBLE_PRESENT_BACKDROP, VISIBLE_PRESENT_BORDER, 0.28f, 0.42f);
            assertButtonStyleRoles(entry, StarsectorUiColour.DARK_GREEN, StarsectorUiColour.BRIGHT_GREEN);
            assertThat(entry.isPresent()).isTrue();
            assertThat(entry.isPresent() && !entry.isSuppressed() && !entry.isHidden()).isTrue();
        }

        @Test
        void stylesHiddenPresentEntriesWithDefaultButtonColoursAndFullIcon() {
            var entry = hiddenEntry();

            assertButtonStyle(entry, DEFAULT_BACKDROP, DEFAULT_BORDER, 0.28f, 0.18f);
            assertThat(entry.isPresent()).isTrue();
            assertThat(entry.isPresent() && !entry.isSuppressed() && !entry.isHidden()).isFalse();
        }

        @Test
        void stylesSuppressedPresentEntriesWithWarningButtonColoursAndFullIcon() {
            var entry = suppressedEntry();

            assertButtonStyle(entry, SUPPRESSED_BACKDROP, SUPPRESSED_BORDER, 0.34f, 0.50f);
            assertButtonStyleRoles(entry, StarsectorUiColour.MUTED_RED, StarsectorUiColour.BRIGHT_RED);
            assertThat(entry.isPresent()).isTrue();
            assertThat(entry.isPresent() && !entry.isSuppressed() && !entry.isHidden()).isFalse();
        }

        @Test
        void prioritizesSuppressedStyleWhenEntryIsBothSuppressedAndHidden() {
            var entry = new KmuConditionPickerEntry(
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
    }

    @Nested
    class ComputeIconSize {

        @Test
        void iconSizeReturnsFallbackSizeForZeroDimensions() {
            var bounds =
                    KmuConditionIconButtonFactory.computeIconSize(0f, 0f);

            assertThat(bounds.getWidth()).isEqualTo(KmuConditionIconButtonSizing.FALLBACK_ICON_SIZE);
            assertThat(bounds.getHeight()).isEqualTo(KmuConditionIconButtonSizing.FALLBACK_ICON_SIZE);
        }

        @Test
        void iconSizeReturnsFallbackSizeForNegativeDimensions() {
            var bounds =
                    KmuConditionIconButtonFactory.computeIconSize(-1f, -1f);

            assertThat(bounds.getWidth()).isEqualTo(KmuConditionIconButtonSizing.FALLBACK_ICON_SIZE);
            assertThat(bounds.getHeight()).isEqualTo(KmuConditionIconButtonSizing.FALLBACK_ICON_SIZE);
        }
    }

    @Nested
    class ComputeKmuConditionIconButtonLayout {

        @Test
        void rendersIconsAtVanillaHeightWhenSourceAlreadyMatches() {
            var wide =
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(120f, 40f);

            assertThat(wide.getIconWidth()).isEqualTo(120f);
            assertThat(wide.getIconHeight()).isEqualTo(40f);
            assertThat(wide.getButtonWidth())
                    .isEqualTo(120f + KmuConditionIconButtonSizing.ICON_MARGIN * 2f);
            assertThat(wide.getButtonHeight())
                    .isEqualTo(40f + KmuConditionIconButtonSizing.ICON_MARGIN * 2f);
        }

        @Test
        void upscalesSmallerIconsToVanillaHeightWithoutChangingAspectRatio() {
            var small =
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(20f, 20f);

            assertThat(small.getIconWidth()).isEqualTo(40f);
            assertThat(small.getIconHeight())
                    .isEqualTo(KmuConditionIconButtonSizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
            assertThat(small.getButtonWidth())
                    .isEqualTo(40f + KmuConditionIconButtonSizing.ICON_MARGIN * 2f);
            assertThat(small.getButtonHeight())
                    .isEqualTo(40f + KmuConditionIconButtonSizing.ICON_MARGIN * 2f);
        }

        @Test
        void preservesWideIconWidthWhenHeightMatchesVanillaTarget() {
            var wide =
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(180f, 40f);

            assertThat(wide.getIconWidth()).isEqualTo(180f);
            assertThat(wide.getIconHeight())
                    .isEqualTo(KmuConditionIconButtonSizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
        }

        @Test
        void downscalesOversizedIconsToVanillaHeightWithoutChangingAspectRatio() {
            var tall =
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(64f, 192f);

            assertThat(tall.getIconWidth()).isBetween(13.33f, 13.34f);
            assertThat(tall.getIconHeight())
                    .isEqualTo(KmuConditionIconButtonSizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
        }
    }

    @Nested
    class UpdateEntry {

        @Test
        void updatesEntryStateInPlace() {
            var button = new KmuConditionIconButton(
                    entry(KmuConditionPickerEntryState.ABSENT),
                    action -> {
                    });

            button.updateEntry(entry(KmuConditionPickerEntryState.PRESENT));

            assertThat(button.getEntry().getState()).isEqualTo(KmuConditionPickerEntryState.PRESENT);
            assertThat(button.getEntry().isPresent()).isTrue();
        }
    }

    @Nested
    class CreateTooltip {

        @Test
        void appendsMetadataFooterInExpectedOrder() {
            var tooltip = RecordingTooltip.create();
            var entry = new KmuConditionPickerEntry(
                    "hot",
                    "Hot",
                    "graphics/icons/markets/hot.png",
                    KmuConditionPickerEntryState.PRESENT,
                    "Test tooltip",
                    "Starsector",
                    true,
                    false);

            new KmuConditionEntryTooltipCreator(() -> entry)
                    .createTooltip(tooltip.getApi(), false, null);

            assertThat(tooltip.getHeadings()).contains("Metadata");
            assertThat(tooltip.getParagraphs()).containsSequence(
                    "source: Starsector",
                    "id: hot",
                    "icon: graphics/icons/markets/hot.png",
                    "hidden: false",
                    "suppressed: true");
        }

        @Test
        void omitsHiddenAndSuppressedMetadataForAbsentEntries() {
            var tooltip = RecordingTooltip.create();
            var entry = new KmuConditionPickerEntry(
                    "hot",
                    "Hot",
                    "graphics/icons/markets/hot.png",
                    KmuConditionPickerEntryState.ABSENT,
                    "Test tooltip",
                    "Starsector",
                    true,
                    true);

            new KmuConditionEntryTooltipCreator(() -> entry)
                    .createTooltip(tooltip.getApi(), false, null);

            assertThat(tooltip.getParagraphs()).containsSequence(
                    "source: Starsector",
                    "id: hot",
                    "icon: graphics/icons/markets/hot.png");
            assertThat(tooltip.getParagraphs()).doesNotContain("hidden: true", "suppressed: true");
        }
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
            Color backdropColour,
            Color borderColour,
            float backdropAlpha,
            float borderAlpha) {
        var style = KmuConditionIconButtonStyle.forEntry(entry);
        assertThat(style.getBackdropColour()).isEqualTo(backdropColour);
        assertThat(style.getBorderColour()).isEqualTo(borderColour);
        assertThat(style.getBackdropAlpha()).isEqualTo(backdropAlpha);
        assertThat(style.getBorderAlpha()).isEqualTo(borderAlpha);
    }

    private static void assertButtonStyleRoles(
            KmuConditionPickerEntry entry,
            StarsectorUiColour backdropColour,
            StarsectorUiColour borderColour) {
        var style = KmuConditionIconButtonStyle.forEntry(entry);

        assertThat(style.getBackdropRawColour()).isEqualTo(backdropColour);
        assertThat(style.getBorderRawColour()).isEqualTo(borderColour);
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

        TooltipMakerAPI getApi() {
            return api;
        }

        List<String> getHeadings() {
            return headings;
        }

        List<String> getParagraphs() {
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
