package kmu.conditions.ui.picker.render;

import kmu.ui.KmuUiPlacement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionIconGridTest {
    @Test
    void layoutReturnsEmptyPlacementsForEmptyMetrics() {
        assertThat(KmuConditionIconGrid.computeLayout(Collections.emptyList(), 400f)).isEmpty();
    }

    @Test
    void heightForPlacementsReturnsZeroForEmptyPlacements() {
        assertThat(KmuConditionIconGrid.computeHeightForPlacements(Collections.emptyList())).isZero();
    }

    @Test
    void placesAllButtonsOnOneRowWhenTheyFitWithinWidth() {
        KmuConditionIconButton.ButtonMetrics m = KmuConditionIconButton.metricsForSource(40f, 40f);
        float rowWidth = 3 * m.getButtonWidth() + 2 * KmuConditionIconGrid.CELL_GAP;

        List<KmuUiPlacement> placements =
                KmuConditionIconGrid.computeLayout(Arrays.asList(m, m, m), rowWidth);

        assertThat(placements.get(0).getY()).isZero();
        assertThat(placements.get(1).getY()).isZero();
        assertThat(placements.get(2).getY()).isZero();
        assertThat(placements.get(1).getX()).isEqualTo(m.getButtonWidth() + KmuConditionIconGrid.CELL_GAP);
        assertThat(placements.get(2).getX()).isEqualTo(2 * (m.getButtonWidth() + KmuConditionIconGrid.CELL_GAP));
    }

    @Test
    void packsVariableWidthButtonsIntoRows() {
        List<KmuConditionIconButton.ButtonMetrics> metrics = Arrays.asList(
                KmuConditionIconButton.metricsForSource(64f, 64f),
                KmuConditionIconButton.metricsForSource(128f, 64f),
                KmuConditionIconButton.metricsForSource(64f, 64f));

        List<KmuUiPlacement> placements = KmuConditionIconGrid.computeLayout(metrics, 160f);

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
        List<KmuUiPlacement> placements = KmuConditionIconGrid.computeLayout(metrics, 160f);

        assertThat(KmuConditionIconGrid.computeHeightForPlacements(placements))
                .isEqualTo(placements.get(2).getY() + metrics.get(2).getButtonHeight());
    }

    @Test
    void reservesRightPaddingForScrollbar() {
        assertThat(KmuConditionIconGrid.computeGridWidth(400f)).isEqualTo(352f);
    }

    @Test
    void snapsGridWidthToFullRowsOfSquareVanillaCells() {
        assertThat(KmuConditionIconGrid.DEFAULT_COLUMNS).isEqualTo(12);
        assertThat(KmuConditionIconGrid.computeDefaultTotalWidth()).isEqualTo(744f);
        assertThat(KmuConditionIconGrid.computeGridWidth(KmuConditionIconGrid.computeDefaultTotalWidth()))
                .isEqualTo(712f);
    }

    @Test
    void derivesContainerWidthFromSquareVanillaCellCount() {
        assertThat(KmuConditionIconGrid.computeTotalWidthForSquareColumns(1)).isEqualTo(84f);
    }

    @Test
    void keepsGridWidthPositiveForNarrowContainers() {
        assertThat(KmuConditionIconGrid.computeGridWidth(12f)).isEqualTo(1f);
    }

    @Test
    void widthForColumnsClampsToOneColumnWhenZeroIsGiven() {
        assertThat(KmuConditionIconGrid.computeWidthForSquareColumns(0))
                .isEqualTo(KmuConditionIconGrid.computeWidthForSquareColumns(1));
    }
}
