package kmu.conditions.ui.picker.render.spec;

import com.fs.starfarer.api.util.Misc;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.starsector.StarsectorTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerSummaryLabelSpecFactoryTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color BLUE = new Color(170, 222, 255, 255);
    private static final Color TEXT = new Color(220, 220, 220, 255);
    private MockedStatic<Misc> misc;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorTestSupport.installSettings();
        misc = Mockito.mockStatic(Misc.class);
        misc.when(Misc::getGrayColor).thenReturn(GRAY);
        misc.when(Misc::getHighlightColor).thenReturn(GOLD);
        misc.when(Misc::getNegativeHighlightColor).thenReturn(RED);
        misc.when(Misc::getPositiveHighlightColor).thenReturn(GREEN);
        misc.when(Misc::getTextColor).thenReturn(TEXT);
        misc.when(Misc::getBasePlayerColor).thenReturn(BLUE);
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        misc.close();
        StarsectorTestSupport.clearSettings();
    }

    @Test
    void returnsTwoLines() {
        assertThat(KmuConditionPickerSummaryLabelSpecFactory.get(model(entries()))).hasSize(2);
    }

    @Test
    void returnsConditionsHeaderAsFirstLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(model(entries()));

        assertThat(specs.get(0).getText()).isEqualTo("Conditions:");
        assertThat(specs.get(0).getHighlights()).isEmpty();
    }

    @Test
    void conditionsHeaderHasGrayBaseColor() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(model(entries()));

        assertThat(specs.get(0).getBaseColor()).isEqualTo(GRAY);
    }

    @Test
    void summarizesTotalAndPresentConditionsInCountsLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(model(entries()));

        assertThat(specs.get(1).getText())
                .isEqualTo("2 visible - 1 suppressed, 3 present, 1 hidden - 1 available, 4 total.");
    }

    @Test
    void showsOnlyTailWhenAllCountsAreZero() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(
                model(Collections.emptyList()));

        assertThat(specs.get(1).getText()).isEqualTo("0 available, 0 total.");
    }

    @Test
    void startsDirectlyWithFirstCountTokenWhenVisibleIsZero() {
        // Entry that is both suppressed and hidden: visible=0 (hidden), suppressed=1
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(
                model(Collections.singletonList(
                        entry("hot", KmuConditionPickerEntryState.PRESENT, true, true))));

        assertThat(specs.get(1).getText())
                .isEqualTo("1 suppressed, 1 present, 1 hidden - 0 available, 1 total.");
        assertThat(specs.get(1).getText()).doesNotStartWith(" ");
    }

    @Test
    void exposesCountHighlightsOnCountsLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(model(entries()));

        assertThat(specs.get(1).getHighlights())
                .containsExactly(
                        "2 visible", "1 suppressed", "3 present", "1 hidden",
                        " - 1 available, 4 total.");
    }

    @Test
    void exposesHighlightColorsOnCountsLine() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(model(entries()));

        assertThat(specs.get(1).getHighlightColors())
                .containsExactly(GREEN, RED, TEXT, BLUE, GRAY);
    }

    @Test
    void omitsZeroSuppressedSegment() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(
                model(Collections.singletonList(
                        entry("hot", KmuConditionPickerEntryState.PRESENT, false, false))));

        assertThat(specs.get(1).getHighlights()).doesNotContain("0 suppressed");
    }

    @Test
    void exposesRedHighlightForSuppressedSegment() {
        List<KmuLabelSpec> specs = KmuConditionPickerSummaryLabelSpecFactory.get(
                model(Collections.singletonList(
                        entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false))));

        String[] highlights = specs.get(1).getHighlights();
        Color[] colors = specs.get(1).getHighlightColors();

        int suppIdx = Arrays.asList(highlights).indexOf("1 suppressed");
        assertThat(suppIdx).isNotEqualTo(-1);
        assertThat(colors[suppIdx]).isEqualTo(RED);
    }

    private static KmuConditionPickerModel model(List<KmuConditionPickerEntry> entryList) {
        return new KmuConditionPickerModel(entryList,
                new KmuConditionPickerLocation(null, null, null, null, null, null, null));
    }

    private static List<KmuConditionPickerEntry> entries() {
        return Arrays.asList(
                entry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
                entry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false));
    }

    private static KmuConditionPickerEntry entry(
            String id,
            KmuConditionPickerEntryState state,
            boolean suppressed,
            boolean hidden) {
        return new KmuConditionPickerEntry(
                id,
                id,
                "graphics/icons/markets/" + id + ".png",
                state,
                "Test tooltip",
                null,
                suppressed,
                hidden);
    }
}
