package kmu.ui.chooser.render;

import kmu.ui.chooser.model.KmuConditionChooserEntry;
import kmu.ui.chooser.model.KmuConditionChooserEntryState;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionIconButtonTest {
    @Test
    void greysOutAbsentEntriesOnly() {
        assertThat(KmuConditionIconButton.shouldGreyOut(entry(KmuConditionChooserEntryState.ABSENT)))
                .isTrue();
        assertThat(KmuConditionIconButton.shouldGreyOut(entry(KmuConditionChooserEntryState.PRESENT)))
                .isFalse();
    }

    @Test
    void preservesOriginalIconSizeWhenItFits() {
        KmuConditionIconButton.ButtonMetrics wide = KmuConditionIconButton.metricsForSource(120f, 40f);

        assertThat(wide.getIconWidth()).isEqualTo(120f);
        assertThat(wide.getIconHeight()).isEqualTo(40f);
        assertThat(wide.getButtonWidth()).isEqualTo(120f + KmuConditionIconButton.ICON_MARGIN * 2f);
        assertThat(wide.getButtonHeight()).isEqualTo(40f + KmuConditionIconButton.ICON_MARGIN * 2f);
    }

    @Test
    void downscalesOversizedIconsWithoutChangingAspectRatio() {
        KmuConditionIconButton.ButtonMetrics tall = KmuConditionIconButton.metricsForSource(64f, 192f);

        assertThat(tall.getIconWidth()).isEqualTo(32f);
        assertThat(tall.getIconHeight()).isEqualTo(KmuConditionIconButton.MAX_ICON_HEIGHT);
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
}
