package kmu.conditions.ui.picker.render.paragraph;

import com.fs.starfarer.api.util.Misc;

import kmlib.starsector.ui.highlight.Highlight;
import kmlib.testfixtures.starsector.ui.label.LabelHighlightRule;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.util.KmuStringsSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerSummaryParagraphFactoryTests {

    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color BLUE = new Color(170, 222, 255, 255);
    private static final Color TEXT = new Color(220, 220, 220, 255);

    private MockedStatic<Misc> miscMock;

    @BeforeEach
    void mockStarsectorThemeColours() {

        KmuStringsSettingsFake.installSettings();

        miscMock = Mockito.mockStatic(Misc.class);
        miscMock
            .when(Misc::getGrayColor)
            .thenReturn(GRAY);
        miscMock
            .when(Misc::getHighlightColor)
            .thenReturn(GOLD);
        miscMock
            .when(Misc::getNegativeHighlightColor)
            .thenReturn(RED);
        miscMock
            .when(Misc::getPositiveHighlightColor)
            .thenReturn(GREEN);
        miscMock
            .when(Misc::getTextColor)
            .thenReturn(TEXT);
        miscMock
            .when(Misc::getBasePlayerColor)
            .thenReturn(BLUE);
    }

    @AfterEach
    void closeStarsectorThemeColours() {

        miscMock.close();
        KmuStringsSettingsFake.clearSettings();
    }

    @Nested
    class CreateParagraphs {

        @Test
        void returnsTwoLines() {

            assertThat(KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModel(listEntries())))
                .hasSize(2);
        }

        @Test
        void returnsConditionsHeaderAsFirstLine() {

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModel(listEntries()));

            assertThat(paragraphs.get(0).getText())
                .isEqualTo("Conditions:");
            assertThat(paragraphs.get(0).getHighlights())
                .extracting(Highlight::getText)
                .isEmpty();
        }

        @Test
        void conditionsHeaderHasGrayBaseColour() {

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModel(listEntries()));

            assertThat(paragraphs.get(0).getBaseColour())
                .isEqualTo(GRAY);
        }

        @Test
        void summarizesTotalAndPresentConditionsInCountsLine() {

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModel(listEntries()));

            assertThat(paragraphs.get(1).getText())
                .isEqualTo("2 visible - 1 suppressed, 3 present, 1 hidden - 1 available, 4 total.");
        }

        @Test
        void showsOnlyTailWhenAllCountsAreZero() {

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(
                buildModel(Collections.emptyList()));

            assertThat(paragraphs.get(1).getText())
                .isEqualTo("0 available, 0 total.");
        }

        @Test
        void startsDirectlyWithFirstCountTokenWhenVisibleIsZero() {
            // Entry that is both suppressed and hidden: visible=0 (hidden), suppressed=1
            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(
                buildModel(Collections.singletonList(
                    buildEntry("hot", KmuConditionPickerEntryState.PRESENT, true, true))));

            assertThat(paragraphs.get(1).getText())
                .isEqualTo("1 suppressed, 1 present, 1 hidden - 0 available, 1 total.");
            assertThat(paragraphs.get(1).getText())
                .doesNotStartWith(" ");
        }

        @Test
        void exposesCountHighlightsOnCountsLine() {
            // "3 present" has no highlight slot because its colour matches the label base
            // (white). The tail from the dash on is one grey run, so it renders fully grey.
            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModel(listEntries()));

            assertThat(paragraphs.get(1).getHighlights())
                .extracting(Highlight::getText)
                .containsExactly(
                    "2 visible", "1 suppressed", "1 hidden",
                    "- 1 available, 4 total.");
        }

        @Test
        void exposesHighlightColoursOnCountsLine() {
            // Colours align one-to-one with highlights above; the tail is grey.
            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModel(listEntries()));

            assertThat(paragraphs.get(1).getHighlights())
                .extracting(Highlight::getColour)
                .containsExactly(GREEN, RED, BLUE, GRAY);
        }

        @Test
        void everyRunOfTheCountsLineIsOneTheGameHighlights() {
            // A run opening with a space touches the word before it, which the game refuses
            // to highlight against - the reason the tail starts at its dash.
            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(buildModel(listEntries()));

            assertThat(LabelHighlightRule.findUnhighlightedRuns(paragraphs.get(1)))
                .isEmpty();
        }

        @Test
        void highlightsTheWholeTailWhenItIsTheOnlyText() {

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(
                buildModel(Collections.emptyList()));

            assertThat(paragraphs.get(1).getHighlights())
                .extracting(Highlight::getText)
                .containsExactly("0 available, 0 total.");
        }

        @Test
        void omitsZeroSuppressedSegment() {

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(
                buildModel(Collections.singletonList(
                    buildEntry("hot", KmuConditionPickerEntryState.PRESENT, false, false))));

            assertThat(paragraphs.get(1).getHighlights())
                .extracting(Highlight::getText)
                .doesNotContain("0 suppressed");
        }

        @Test
        void exposesRedHighlightForSuppressedSegment() {

            var paragraphs = KmuConditionPickerSummaryParagraphFactory.createParagraphs(
                buildModel(Collections.singletonList(
                    buildEntry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false))));

            assertThat(paragraphs.get(1).getHighlights())
                .filteredOn(highlight -> highlight.getText().equals("1 suppressed"))
                .extracting(Highlight::getColour)
                .containsExactly(RED);
        }
    }

    private static KmuConditionPickerModel buildModel(List<KmuConditionPickerEntry> entryList) {

        return new KmuConditionPickerModel(
            entryList,
            KmuConditionPickerLocation.createUnknownLocation());
    }

    private static List<KmuConditionPickerEntry> listEntries() {

        return Arrays.asList(
            buildEntry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
            buildEntry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
            buildEntry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
            buildEntry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false));
    }

    private static KmuConditionPickerEntry buildEntry(
            String id,
            KmuConditionPickerEntryState state,
            boolean isSuppressed,
            boolean isHidden) {

        return new KmuConditionPickerEntry
            .Builder(id, id, state)
            .withIcon("graphics/icons/markets/" + id + ".png")
            .withTooltipText("Test tooltip")
            .withSuppressed(isSuppressed)
            .withHidden(isHidden)
            .build();
    }
}
