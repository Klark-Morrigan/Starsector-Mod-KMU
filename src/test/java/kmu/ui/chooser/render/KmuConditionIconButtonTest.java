package kmu.ui.chooser.render;

import kmu.ui.chooser.model.KmuConditionChooserEntry;
import kmu.ui.chooser.model.KmuConditionChooserEntryState;

import java.awt.Color;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionIconButtonTest {
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
    void stylesVisibleUnsuppressedPresentEntriesWithPositiveGreenTint() {
        KmuConditionChooserEntry entry = entry(KmuConditionChooserEntryState.PRESENT);

        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isTrue();
        assertThat(KmuConditionIconButton.backdropColorFor(entry)).isEqualTo(new Color(35, 80, 45));
        assertThat(KmuConditionIconButton.borderColorFor(entry)).isEqualTo(new Color(90, 220, 95));
        assertThat(KmuConditionIconButton.backdropAlphaFor(entry)).isEqualTo(0.28f);
        assertThat(KmuConditionIconButton.borderAlphaFor(entry)).isEqualTo(0.42f);
    }

    @Test
    void doesNotApplySpecialTintToHiddenEntries() {
        KmuConditionChooserEntry entry = hiddenEntry();

        assertThat(KmuConditionIconButton.isVisibleUnsuppressedPresent(entry)).isFalse();
        assertThat(KmuConditionIconButton.backdropAlphaFor(entry)).isEqualTo(0.28f);
        assertThat(KmuConditionIconButton.borderAlphaFor(entry)).isEqualTo(0.18f);
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

        assertThat(KmuConditionIconButton.backdropColorFor(entry)).isEqualTo(new Color(150, 50, 45));
        assertThat(KmuConditionIconButton.borderColorFor(entry)).isEqualTo(new Color(255, 90, 80));
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
}
