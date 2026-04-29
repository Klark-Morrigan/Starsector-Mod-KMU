package kmu.ui.chooser.render;

import kmu.ui.chooser.model.KmuConditionChooserEntry;
import kmu.ui.chooser.model.KmuConditionChooserEntryState;

import java.awt.Color;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionIconButtonTest {
    private static final Color DEFAULT_BACKDROP = new Color(31, 94, 112, 175);
    private static final Color DEFAULT_BORDER = new Color(170, 222, 255, 255);
    private static final Color VISIBLE_PRESENT_BACKDROP = new Color(35, 80, 45);
    private static final Color VISIBLE_PRESENT_BORDER = new Color(90, 220, 95);
    private static final Color SUPPRESSED_BACKDROP = new Color(150, 50, 45);
    private static final Color SUPPRESSED_BORDER = new Color(255, 90, 80);

    @Test
    void greysOutAbsentEntriesOnly() {
        assertThat(KmuConditionIconButton.shouldGreyOut(entry(KmuConditionChooserEntryState.ABSENT)))
                .isTrue();
        assertThat(KmuConditionIconButton.shouldGreyOut(entry(KmuConditionChooserEntryState.PRESENT)))
                .isFalse();
        assertThat(KmuConditionIconButton.shouldGreyOut(suppressedEntry()))
                .isFalse();
        assertThat(KmuConditionIconButton.shouldGreyOut(hiddenEntry()))
                .isFalse();
    }

    @Test
    void stylesAbsentEntriesWithDefaultButtonColorsAndGreyedOutIcon() {
        KmuConditionChooserEntry entry = entry(KmuConditionChooserEntryState.ABSENT);

        assertButtonStyle(entry, DEFAULT_BACKDROP, DEFAULT_BORDER, 0.28f, 0.18f);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isTrue();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isFalse();
    }

    @Test
    void stylesVisibleUnsuppressedPresentEntriesWithPositiveGreenButtonColors() {
        KmuConditionChooserEntry entry = entry(KmuConditionChooserEntryState.PRESENT);

        assertButtonStyle(entry, VISIBLE_PRESENT_BACKDROP, VISIBLE_PRESENT_BORDER, 0.28f, 0.42f);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isFalse();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isTrue();
    }

    @Test
    void stylesHiddenPresentEntriesWithDefaultButtonColorsAndFullIcon() {
        KmuConditionChooserEntry entry = hiddenEntry();

        assertButtonStyle(entry, DEFAULT_BACKDROP, DEFAULT_BORDER, 0.28f, 0.18f);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isFalse();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isFalse();
    }

    @Test
    void stylesSuppressedPresentEntriesWithWarningButtonColorsAndFullIcon() {
        KmuConditionChooserEntry entry = suppressedEntry();

        assertButtonStyle(entry, SUPPRESSED_BACKDROP, SUPPRESSED_BORDER, 0.34f, 0.50f);
        assertThat(KmuConditionIconButton.shouldGreyOut(entry)).isFalse();
        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isFalse();
    }

    @Test
    void prioritizesSuppressedStyleWhenEntryIsBothSuppressedAndHidden() {
        KmuConditionChooserEntry entry = new KmuConditionChooserEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionChooserEntryState.PRESENT,
                "Test tooltip",
                "Starsector",
                true,
                true);

        assertButtonStyle(entry, SUPPRESSED_BACKDROP, SUPPRESSED_BORDER, 0.34f, 0.50f);
    }

    @Test
    void preservesOriginalIconSizeWhenItFits() {
        KmuConditionIconButton.ButtonMetrics wide = KmuConditionIconButton.metricsForSource(120f, 40f);

        assertThat(wide.getIconWidth()).isEqualTo(120f);
        assertThat(wide.getIconHeight()).isEqualTo(40f);
        assertThat(wide.getButtonWidth()).isEqualTo(120f + KmuConditionIconButton.Sizing.ICON_MARGIN * 2f);
        assertThat(wide.getButtonHeight()).isEqualTo(40f + KmuConditionIconButton.Sizing.ICON_MARGIN * 2f);
    }

    @Test
    void preservesWideIconWidthWhenHeightMatchesVanillaCap() {
        KmuConditionIconButton.ButtonMetrics wide = KmuConditionIconButton.metricsForSource(180f, 40f);

        assertThat(wide.getIconWidth()).isEqualTo(180f);
        assertThat(wide.getIconHeight())
                .isEqualTo(KmuConditionIconButton.Sizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
    }

    @Test
    void downscalesOversizedIconsByHeightWithoutChangingAspectRatio() {
        KmuConditionIconButton.ButtonMetrics tall = KmuConditionIconButton.metricsForSource(64f, 192f);

        assertThat(tall.getIconWidth()).isBetween(13.33f, 13.34f);
        assertThat(tall.getIconHeight())
                .isEqualTo(KmuConditionIconButton.Sizing.VANILLA_COLONY_CONDITION_ICON_HEIGHT);
    }

    @Test
    void updatesEntryStateInPlace() {
        KmuConditionIconButton button = new KmuConditionIconButton(
                entry(KmuConditionChooserEntryState.ABSENT),
                action -> {
                });

        button.updateEntry(entry(KmuConditionChooserEntryState.PRESENT));

        assertThat(button.getEntry().getState()).isEqualTo(KmuConditionChooserEntryState.PRESENT);
        assertThat(KmuConditionIconButton.shouldGreyOut(button.getEntry())).isFalse();
    }

    private static KmuConditionChooserEntry entry(KmuConditionChooserEntryState state) {
        return new KmuConditionChooserEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                state,
                "Test tooltip");
    }

    private static KmuConditionChooserEntry suppressedEntry() {
        return new KmuConditionChooserEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionChooserEntryState.PRESENT,
                "Test tooltip",
                "Starsector",
                true,
                false);
    }

    private static KmuConditionChooserEntry hiddenEntry() {
        return new KmuConditionChooserEntry(
                "hot",
                "Hot",
                "graphics/icons/markets/hot.png",
                KmuConditionChooserEntryState.PRESENT,
                "Test tooltip",
                "Starsector",
                false,
                true);
    }

    private static void assertButtonStyle(
            KmuConditionChooserEntry entry,
            Color backdropColor,
            Color borderColor,
            float backdropAlpha,
            float borderAlpha) {
        assertThat(KmuConditionIconButton.backdropColorFor(entry)).isEqualTo(backdropColor);
        assertThat(KmuConditionIconButton.borderColorFor(entry)).isEqualTo(borderColor);
        assertThat(KmuConditionIconButton.backdropAlphaFor(entry)).isEqualTo(backdropAlpha);
        assertThat(KmuConditionIconButton.borderAlphaFor(entry)).isEqualTo(borderAlpha);
    }
}
