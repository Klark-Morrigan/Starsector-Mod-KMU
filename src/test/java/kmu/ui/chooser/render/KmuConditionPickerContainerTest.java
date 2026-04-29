package kmu.ui.chooser.render;

import kmu.ui.chooser.model.KmuConditionChooserEntry;
import kmu.ui.chooser.model.KmuConditionChooserEntryState;
import kmu.ui.chooser.model.KmuConditionChooserModel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerContainerTest {
    @Test
    void summarizesTotalAndPresentConditions() {
        KmuConditionChooserModel model = new KmuConditionChooserModel(Arrays.asList(
                entry("hot", KmuConditionChooserEntryState.PRESENT),
                entry("cold", KmuConditionChooserEntryState.ABSENT)));

        assertThat(KmuConditionPickerContainer.summaryText(model))
                .isEqualTo("2 planetary conditions found; 1 already present on this market.");
    }

    @Test
    void reservesRightPaddingForScrollbar() {
        assertThat(KmuConditionPickerContainer.gridWidth(400f))
                .isEqualTo(328f);
    }

    @Test
    void snapsGridWidthToFullRowsOfSquareVanillaCells() {
        assertThat(KmuConditionPickerContainer.DEFAULT_SQUARE_ICON_COLUMNS)
                .isEqualTo(12);
        assertThat(KmuConditionPickerContainer.defaultContainerWidth())
                .isEqualTo(696f);
        assertThat(KmuConditionPickerContainer.gridWidth(KmuConditionPickerContainer.defaultContainerWidth()))
                .isEqualTo(664f);
    }

    @Test
    void derivesContainerWidthFromSquareVanillaCellCount() {
        assertThat(KmuConditionPickerContainer.containerWidthForSquareIconColumns(1))
                .isEqualTo(80f);
    }

    @Test
    void keepsGridWidthPositiveForNarrowContainers() {
        assertThat(KmuConditionPickerContainer.gridWidth(12f))
                .isEqualTo(1f);
    }

    private static KmuConditionChooserEntry entry(String id, KmuConditionChooserEntryState state) {
        return new KmuConditionChooserEntry(
                id,
                id,
                "graphics/icons/markets/" + id + ".png",
                state,
                "Test tooltip");
    }
}
