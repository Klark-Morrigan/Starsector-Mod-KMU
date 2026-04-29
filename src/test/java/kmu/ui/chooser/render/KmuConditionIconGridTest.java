package kmu.ui.chooser.render;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionIconGridTest {
    @Test
    void packsVariableWidthButtonsIntoRows() {
        List<KmuConditionIconButton.ButtonMetrics> metrics = Arrays.asList(
                KmuConditionIconButton.metricsForSource(64f, 64f),
                KmuConditionIconButton.metricsForSource(128f, 64f),
                KmuConditionIconButton.metricsForSource(64f, 64f));

        List<KmuConditionIconGrid.Placement> placements = KmuConditionIconGrid.layout(metrics, 160f);

        assertThat(placements).hasSize(3);
        assertThat(placements.get(0).getX()).isZero();
        assertThat(placements.get(1).getX()).isEqualTo(metrics.get(0).getButtonWidth() + 8f);
        assertThat(placements.get(2).getX()).isZero();
        assertThat(placements.get(2).getY()).isEqualTo(metrics.get(1).getButtonHeight() + 8f);
    }

    @Test
    void computesHeightFromLastPackedRow() {
        List<KmuConditionIconButton.ButtonMetrics> metrics = Arrays.asList(
                KmuConditionIconButton.metricsForSource(64f, 64f),
                KmuConditionIconButton.metricsForSource(128f, 64f),
                KmuConditionIconButton.metricsForSource(64f, 64f));
        List<KmuConditionIconGrid.Placement> placements = KmuConditionIconGrid.layout(metrics, 160f);

        assertThat(KmuConditionIconGrid.heightForPlacements(placements))
                .isEqualTo(placements.get(2).getY() + metrics.get(2).getButtonHeight());
    }
}
