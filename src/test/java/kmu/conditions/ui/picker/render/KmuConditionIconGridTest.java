package kmu.conditions.ui.picker.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionIconGridTest {

    @Nested
    class ComputeLayout {

        @Test
        void layoutReturnsEmptyPlacementsForEmptyMetrics() {
            assertThat(KmuConditionIconGrid.computeLayout(Collections.emptyList(), 400f)).isEmpty();
        }

        @Test
        void placesAllButtonsOnOneRowWhenTheyFitWithinWidth() {
            var m = KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(40f, 40f);
            var rowWidth = 3 * m.getButtonWidth() + 2 * KmuConditionIconGrid.CELL_GAP;

            var placements =
                    KmuConditionIconGrid.computeLayout(Arrays.asList(m, m, m), rowWidth);

            assertThat(placements.get(0).y()).isZero();
            assertThat(placements.get(1).y()).isZero();
            assertThat(placements.get(2).y()).isZero();
            assertThat(placements.get(1).x()).isEqualTo(m.getButtonWidth() + KmuConditionIconGrid.CELL_GAP);
            assertThat(placements.get(2).x()).isEqualTo(2 * (m.getButtonWidth() + KmuConditionIconGrid.CELL_GAP));
        }

        @Test
        void packsVariableWidthButtonsIntoRows() {
            var metrics = Arrays.asList(
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(64f, 64f),
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(128f, 64f),
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(64f, 64f));

            var placements = KmuConditionIconGrid.computeLayout(metrics, 160f);

            assertThat(placements).hasSize(3);
            assertThat(placements.get(0).x()).isZero();
            assertThat(placements.get(1).x()).isEqualTo(metrics.get(0).getButtonWidth() + 8f);
            assertThat(placements.get(2).x()).isZero();
            assertThat(placements.get(2).y()).isEqualTo(metrics.get(1).getButtonHeight() + 8f);
        }
    }

    @Nested
    class ComputeHeightForPlacements {

        @Test
        void heightForPlacementsReturnsZeroForEmptyPlacements() {
            assertThat(KmuConditionIconGrid.computeHeightForPlacements(Collections.emptyList())).isZero();
        }

        @Test
        void computesHeightFromLastPackedRow() {
            var metrics = Arrays.asList(
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(64f, 64f),
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(128f, 64f),
                    KmuConditionIconButtonFactory.computeKmuConditionIconButtonLayout(64f, 64f));
            var placements = KmuConditionIconGrid.computeLayout(metrics, 160f);

            assertThat(KmuConditionIconGrid.computeHeightForPlacements(placements))
                    .isEqualTo(placements.get(2).y() + metrics.get(2).getButtonHeight());
        }
    }

    @Nested
    class ComputeGridWidth {

        @Test
        void reservesRightPaddingForScrollbar() {
            assertThat(KmuConditionIconGrid.computeGridWidth(400f)).isEqualTo(352f);
        }

        @Test
        void snapsGridWidthToFullRowsOfSquareVanillaCells() {
            assertThat(KmuConditionIconGrid.DEFAULT_COLUMNS).isEqualTo(12);
            assertThat(KmuConditionIconGrid.computeDefaultTotalWidth()).isEqualTo(730f);
            assertThat(KmuConditionIconGrid.computeGridWidth(KmuConditionIconGrid.computeDefaultTotalWidth()))
                    .isEqualTo(712f);
        }

        @Test
        void keepsGridWidthPositiveForNarrowContainers() {
            assertThat(KmuConditionIconGrid.computeGridWidth(12f)).isEqualTo(1f);
        }
    }

    @Nested
    class ComputeTotalWidthForSquareColumns {

        @Test
        void derivesContainerWidthFromSquareVanillaCellCount() {
            assertThat(KmuConditionIconGrid.computeTotalWidthForSquareColumns(1)).isEqualTo(70f);
        }
    }

    @Nested
    class ComputeWidthForSquareColumns {

        @Test
        void widthForColumnsClampsToOneColumnWhenZeroIsGiven() {
            assertThat(KmuConditionIconGrid.computeWidthForSquareColumns(0))
                    .isEqualTo(KmuConditionIconGrid.computeWidthForSquareColumns(1));
        }
    }
}
