package kmu.conditions.ui.picker.render.paragraph;

import com.fs.starfarer.api.util.Misc;
import kmlib.starsector.ui.highlight.HighlightedParagraph;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.starsector.StarsectorSettingsFake;
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

class KmuConditionPickerSummaryParagraphFactoryTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color BLUE = new Color(170, 222, 255, 255);
    private static final Color TEXT = new Color(220, 220, 220, 255);
    private MockedStatic<Misc> misc;

    @BeforeEach
    void mockStarsectorThemeColors() {
        StarsectorSettingsFake.installSettings();
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
        StarsectorSettingsFake.clearSettings();
    }

    @Test
    void returnsTwoLines() {
        assertThat(KmuConditionPickerSummaryParagraphFactory.get(model(entries()))).hasSize(2);
    }

    @Test
    void returnsConditionsHeaderAsFirstLine() {
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(model(entries()));

        assertThat(paragraphs.get(0).getText()).isEqualTo("Conditions:");
        assertThat(paragraphs.get(0).getHighlightTexts()).isEmpty();
    }

    @Test
    void conditionsHeaderHasGrayBaseColor() {
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(model(entries()));

        assertThat(paragraphs.get(0).getBaseColor()).isEqualTo(GRAY);
    }

    @Test
    void summarizesTotalAndPresentConditionsInCountsLine() {
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(model(entries()));

        assertThat(paragraphs.get(1).getText())
                .isEqualTo("2 visible - 1 suppressed, 3 present, 1 hidden - 1 available, 4 total.");
    }

    @Test
    void showsOnlyTailWhenAllCountsAreZero() {
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(
                model(Collections.emptyList()));

        assertThat(paragraphs.get(1).getText()).isEqualTo("0 available, 0 total.");
    }

    @Test
    void startsDirectlyWithFirstCountTokenWhenVisibleIsZero() {
        // Entry that is both suppressed and hidden: visible=0 (hidden), suppressed=1
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(
                model(Collections.singletonList(
                        entry("hot", KmuConditionPickerEntryState.PRESENT, true, true))));

        assertThat(paragraphs.get(1).getText())
                .isEqualTo("1 suppressed, 1 present, 1 hidden - 0 available, 1 total.");
        assertThat(paragraphs.get(1).getText()).doesNotStartWith(" ");
    }

    @Test
    void exposesCountHighlightsOnCountsLine() {
        // "3 present" has no highlight slot because its color matches the label base
        // (white). The " - " before "1 available" and the ", " before "4 total." are
        // each highlighted in grey so the trailing tail renders fully grey.
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(model(entries()));

        assertThat(paragraphs.get(1).getHighlightTexts())
                .containsExactly(
                        "2 visible", "1 suppressed", "1 hidden",
                        " - ", "1 available", ", ", "4 total.");
    }

    @Test
    void exposesHighlightColorsOnCountsLine() {
        // Colors align one-to-one with highlights above. The four trailing segments
        // (" - ", "1 available", ", ", "4 total.") are all grey.
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(model(entries()));

        assertThat(paragraphs.get(1).getHighlightColors())
                .containsExactly(GREEN, RED, BLUE, GRAY, GRAY, GRAY, GRAY);
    }

    @Test
    void omitsZeroSuppressedSegment() {
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(
                model(Collections.singletonList(
                        entry("hot", KmuConditionPickerEntryState.PRESENT, false, false))));

        assertThat(paragraphs.get(1).getHighlightTexts()).doesNotContain("0 suppressed");
    }

    @Test
    void exposesRedHighlightForSuppressedSegment() {
        var paragraphs = KmuConditionPickerSummaryParagraphFactory.get(
                model(Collections.singletonList(
                        entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false))));

        var highlights = paragraphs.get(1).getHighlightTexts();
        var colors = paragraphs.get(1).getHighlightColors();

        var suppIdx = Arrays.asList(highlights).indexOf("1 suppressed");
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
