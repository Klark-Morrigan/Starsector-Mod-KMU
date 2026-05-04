package kmu.conditions.ui.picker.render.spec;

import kmu.KmuStrings;
import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces the conditions summary label spec.
 *
 * The base label color is white; grey items must be highlighted explicitly:
 * - "Conditions:" in white
 * - "N visible"    in green      (omitted when zero)
 * - "N suppressed" in red        (omitted when zero)
 * - "N present"    in white      (omitted when zero)
 * - "N hidden"     in light blue (omitted when zero)
 * - "N available, N total." in grey (always appended last)
 *
 * Separator rules:
 * - " - " separates categories; ", " separates items within the same section.
 * - The grey tail (" - N available, N total.") is always appended last.
 */
public final class KmuConditionPickerSummaryLabelSpecFactory {
    private KmuConditionPickerSummaryLabelSpecFactory() {
    }

    public static KmuLabelSpec get(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        int visible = model.getVisibleCount();
        int suppressed = model.getSuppressedCount();
        int present = model.getPresentCount();
        int hidden = model.getHiddenCount();
        int available = model.getAvailableCount();
        int total = model.getEntryCount();

        Color white = StarsectorUiColorProvider.get(StarsectorUiColor.WHITE);
        Color green = StarsectorUiColorProvider.get(StarsectorUiColor.GREEN);
        Color red = StarsectorUiColorProvider.get(StarsectorUiColor.RED);
        Color lightBlue = StarsectorUiColorProvider.get(StarsectorUiColor.LIGHT_BLUE);

        String prefix = KmuStrings.get(KmuStrings.CONDITION_PICKER_SUMMARY);
        StringBuilder sb = new StringBuilder(prefix);
        List<String> highlightList = new ArrayList<>();
        List<Color> colorList = new ArrayList<>();

        addHighlight(highlightList, colorList, prefix, white);

        String visibleToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_VISIBLE, visible);
        String suppressedToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_SUPPRESSED, suppressed);
        String presentToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_PRESENT, present);
        String hiddenToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_HIDDEN, hidden);
        String availableToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_AVAILABLE, available);
        String totalToken = KmuStrings.format(KmuStrings.CONDITION_PICKER_SUMMARY_TOTAL, total);

        boolean hasSegment = false;

        if (visible > 0)
            hasSegment = appendToken(sb, highlightList, colorList, hasSegment, " ", visibleToken, green);
        if (suppressed > 0)
            hasSegment = appendToken(sb, highlightList, colorList, hasSegment, " - ", suppressedToken, red);
        if (present > 0)
            hasSegment = appendToken(sb, highlightList, colorList, hasSegment, ", ", presentToken, white);
        if (hidden > 0)
            hasSegment = appendToken(sb, highlightList, colorList, hasSegment, ", ", hiddenToken, lightBlue);

        // The grey tail includes the category divider so the hyphen is grey too.
        Color grey = StarsectorUiColorProvider.get(StarsectorUiColor.GRAY);
        String greyAvailableToken = (hasSegment ? " - " : "") + availableToken;

        sb.append(hasSegment ? " - " : " ").append(availableToken).append(", ").append(totalToken);

        addHighlight(highlightList, colorList, greyAvailableToken, grey);
        addHighlight(highlightList, colorList, ", " + totalToken, grey);

        return new KmuLabelSpec(
                sb.toString(),
                highlightList.toArray(new String[0]),
                colorList.toArray(new Color[0]));
    }

    public static String getText(KmuConditionPickerModel model) {
        return get(model).getText();
    }

    private static boolean appendToken(
            StringBuilder sb,
            List<String> highlights,
            List<Color> colors,
            boolean hasSegment,
            String separator,
            String token,
            Color color) {
        sb.append(hasSegment ? separator : " ").append(token);
        addHighlight(highlights, colors, token, color);
        return true;
    }

    private static void addHighlight(List<String> highlights, List<Color> colors, String token, Color color) {
        highlights.add(token);
        colors.add(color);
    }
}
