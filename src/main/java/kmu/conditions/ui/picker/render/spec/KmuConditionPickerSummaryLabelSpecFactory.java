package kmu.conditions.ui.picker.render.spec;

import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.starsector.StarsectorUiColor;
import kmu.starsector.StarsectorUiColorProvider;
import kmu.ui.utils.KmuHighlights;
import kmu.util.KmuLocalisation;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces the conditions summary label specs as two stacked lines:
 * <ol>
 *   <li>"Conditions:" header - no highlights, TEXT_WHITE base color</li>
 *   <li>Count line - highlights for visible (green), suppressed (red),
 *       present (white), hidden (blue); available and total each in grey</li>
 * </ol>
 *
 * Separator rules for the count line:
 * - First token starts immediately (no leading character).
 * - " - " separates category groups; ", " separates items within a group.
 * - available and total are always last, each a separate grey highlight.
 * - Zero-value tokens are omitted except available and total.
 */
public final class KmuConditionPickerSummaryLabelSpecFactory {
    private KmuConditionPickerSummaryLabelSpecFactory() {
    }

    public static List<KmuLabelSpec> get(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        int visible = model.getVisibleCount();
        int suppressed = model.getSuppressedCount();
        int present = model.getPresentCount();
        int hidden = model.getHiddenCount();
        int available = model.getAvailableCount();
        int total = model.getEntryCount();

        Color green = StarsectorUiColorProvider.get(StarsectorUiColor.GREEN);
        Color red = StarsectorUiColorProvider.get(StarsectorUiColor.RED);
        Color lightBlue = StarsectorUiColorProvider.get(StarsectorUiColor.BLUE);

        String visibleToken = KmuLocalisation.format(KmuLocalisation.CONDITION_PICKER_SUMMARY_VISIBLE, visible);
        String suppressedToken = KmuLocalisation.format(KmuLocalisation.CONDITION_PICKER_SUMMARY_SUPPRESSED, suppressed);
        String presentToken = KmuLocalisation.format(KmuLocalisation.CONDITION_PICKER_SUMMARY_PRESENT, present);
        String hiddenToken = KmuLocalisation.format(KmuLocalisation.CONDITION_PICKER_SUMMARY_HIDDEN, hidden);
        String availableToken = KmuLocalisation.format(KmuLocalisation.CONDITION_PICKER_SUMMARY_AVAILABLE, available);
        String totalToken = KmuLocalisation.format(KmuLocalisation.CONDITION_PICKER_SUMMARY_TOTAL, total);

        StringBuilder sb = new StringBuilder();
        List<String> highlightList = new ArrayList<>();
        List<Color> colorList = new ArrayList<>();

        boolean hasSegment = false;

        if (visible > 0)
            hasSegment = appendToken(sb, highlightList, colorList, hasSegment, " ", visibleToken, green);
        if (suppressed > 0)
            hasSegment = appendToken(sb, highlightList, colorList, hasSegment, " - ", suppressedToken, red);
        if (present > 0)
            // present is white - same as the label base color, so no highlight slot needed.
            hasSegment = appendSegment(sb, hasSegment, ", ", presentToken);
        if (hidden > 0)
            hasSegment = appendToken(sb, highlightList, colorList, hasSegment, ", ", hiddenToken, lightBlue);

        Color grey = StarsectorUiColorProvider.get(StarsectorUiColor.GRAY);
        hasSegment = appendToken(sb, highlightList, colorList, hasSegment, " - ", availableToken, grey);
        appendToken(sb, highlightList, colorList, true, ", ", totalToken, grey);

        KmuLabelSpec headerSpec = new KmuLabelSpec(
                KmuLocalisation.get(KmuLocalisation.CONDITION_PICKER_SUMMARY),
                new String[0],
                new Color[0]);
        KmuLabelSpec countsSpec = new KmuLabelSpec(
                sb.toString(),
                highlightList.toArray(new String[0]),
                colorList.toArray(new Color[0]));

        List<KmuLabelSpec> result = new ArrayList<>();
        result.add(headerSpec);
        result.add(countsSpec);
        return result;
    }

    private static boolean appendToken(
            StringBuilder sb,
            List<String> highlights,
            List<Color> colors,
            boolean hasSegment,
            String separator,
            String token,
            Color color) {
        if (hasSegment) {
            sb.append(separator);
        }
        sb.append(token);
        KmuHighlights.add(highlights, colors, token, color);
        return true;
    }

    /** Appends a token with no highlight - used when the token color matches the base. */
    private static boolean appendSegment(
            StringBuilder sb, boolean hasSegment, String separator, String token) {
        if (hasSegment) sb.append(separator);
        sb.append(token);
        return true;
    }
}
