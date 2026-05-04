package kmu.conditions.ui.picker.render.spec;

import com.fs.starfarer.api.util.Misc;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.conditions.ui.picker.model.KmuConditionPickerEntryState;
import kmu.conditions.ui.picker.model.KmuConditionPickerLocation;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.conditions.ui.picker.model.KmuPickerFaction;
import kmu.starsector.StarsectorTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class KmuConditionPickerSummaryLabelSpecFactoryTest {
    private static final Color GRAY = new Color(155, 155, 155);
    private static final Color GOLD = new Color(255, 220, 80);
    private static final Color RED = new Color(255, 80, 80);
    private static final Color GREEN = new Color(80, 220, 80);
    private static final Color LIGHT_BLUE = new Color(100, 180, 255);
    private static final Color TEXT = new Color(220, 220, 220, 255);
    private static final Color FACTION = new Color(90, 150, 240);
    private static final Color RELATIONSHIP = new Color(240, 80, 80);

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
    }

    @AfterEach
    void closeStarsectorThemeColors() {
        misc.close();
        StarsectorTestSupport.clearSettings();
    }

    @Test
    void summarizesTotalAndPresentConditions() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(Arrays.asList(
                entry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
                entry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
                location());

        assertThat(KmuConditionPickerSummaryLabelSpecFactory.getText(model))
                .isEqualTo("Conditions: 2 visible - 1 suppressed, 3 present, 1 hidden - 1 available, 4 total.");
    }

    @Test
    void showsOnlyTailWhenAllCountsAreZero() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.emptyList(),
                new KmuConditionPickerLocation(null, null, null, null, null, null, null));

        assertThat(KmuConditionPickerSummaryLabelSpecFactory.getText(model))
                .isEqualTo("Conditions: 0 available, 0 total.");
    }

    @Test
    void usesSingleSpaceBeforeSuppressedWhenItIsFirstSegment() {
        // An entry that is both suppressed and hidden has visible=0 (hidden) but suppressed=1.
        // appendToken ignores the " - " separator for the first token, using " " instead.
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.singletonList(
                        entry("hot", KmuConditionPickerEntryState.PRESENT, true, true)),
                new KmuConditionPickerLocation(null, null, null, null, null, null, null));

        assertThat(KmuConditionPickerSummaryLabelSpecFactory.getText(model))
                .isEqualTo("Conditions: 1 suppressed, 1 present, 1 hidden - 0 available, 1 total.");
    }

    @Test
    void exposesCountHighlights() {
        // hot=visible, cold=available, hidden=present+hidden, suppressed=present+suppressed
        KmuConditionPickerModel model = new KmuConditionPickerModel(Arrays.asList(
                entry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
                entry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
                location());

        assertThat(KmuConditionPickerSummaryLabelSpecFactory.get(model).getHighlights())
                .containsExactly(
                        "Conditions:", "2 visible", "1 suppressed", "3 present", "1 hidden",
                        " - 1 available", ", 4 total.");
    }

    @Test
    void exposesHighlightColors() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(Arrays.asList(
                entry("hot", KmuConditionPickerEntryState.PRESENT, false, false),
                entry("cold", KmuConditionPickerEntryState.ABSENT, false, false),
                entry("hidden", KmuConditionPickerEntryState.PRESENT, false, true),
                entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
                location());

        Color[] colors = KmuConditionPickerSummaryLabelSpecFactory.get(model).getHighlightColors();

        assertThat(colors).containsExactly(TEXT, GREEN, RED, TEXT, LIGHT_BLUE, GRAY, GRAY);
    }

    @Test
    void omitsZeroSuppressedSegment() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.singletonList(
                        entry("hot", KmuConditionPickerEntryState.PRESENT, false, false)),
                location());

        assertThat(KmuConditionPickerSummaryLabelSpecFactory.get(model).getHighlights())
                .doesNotContain("0 suppressed");
    }

    @Test
    void exposesRedHighlightForSuppressedSegment() {
        KmuConditionPickerModel model = new KmuConditionPickerModel(
                Collections.singletonList(
                        entry("suppressed", KmuConditionPickerEntryState.PRESENT, true, false)),
                location());

        String[] highlights = KmuConditionPickerSummaryLabelSpecFactory.get(model).getHighlights();
        Color[] colors = KmuConditionPickerSummaryLabelSpecFactory.get(model).getHighlightColors();

        int suppIdx = Arrays.asList(highlights).indexOf("1 suppressed");
        assertThat(suppIdx).isNotEqualTo(-1);
        assertThat(colors[suppIdx]).isEqualTo(RED);
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

    private static KmuConditionPickerLocation location() {
        return new KmuConditionPickerLocation(
                "Valis",
                "terran world",
                new KmuPickerFaction("Hegemony", FACTION, null, "Vengeful (-100 / 100)", RELATIONSHIP),
                "Corvus Star System",
                "yellow star",
                "Corvus",
                "Corvus");
    }
}
