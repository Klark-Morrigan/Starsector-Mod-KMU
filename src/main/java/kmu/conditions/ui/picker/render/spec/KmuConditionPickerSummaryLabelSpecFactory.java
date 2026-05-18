package kmu.conditions.ui.picker.render.spec;

import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.color.StarsectorUiColorProvider;
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

    /** Accumulates the text, highlight strings, and colors for the count line. */
    private static final class AppendContext {
        final StringBuilder sb = new StringBuilder();
        final List<String> highlights = new ArrayList<>();
        final List<Color> colors = new ArrayList<>();
        boolean hasSegment = false;
    }

    /** Pairs a separator with the token text, its highlight color, and whether the separator is also highlighted. */
    private static final class TokenSpec {
        final String separator;
        final String token;
        final Color color;
        final boolean highlightSeparator;

        TokenSpec(String separator, String token, Color color, boolean highlightSeparator) {
            this.separator = separator;
            this.token = token;
            this.color = color;
            this.highlightSeparator = highlightSeparator;
        }

        TokenSpec(String separator, String token, Color color) {
            this(separator, token, color, false);
        }
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
        Color grey = StarsectorUiColorProvider.get(StarsectorUiColor.GRAY);

        String visibleToken = KmuLocalisation.format(KmuLocalisation.CONDITION_MANAGER_SUMMARY_VISIBLE, visible);
        String suppressedToken = KmuLocalisation.format(KmuLocalisation.CONDITION_MANAGER_SUMMARY_SUPPRESSED, suppressed);
        String presentToken = KmuLocalisation.format(KmuLocalisation.CONDITION_MANAGER_SUMMARY_PRESENT, present);
        String hiddenToken = KmuLocalisation.format(KmuLocalisation.CONDITION_MANAGER_SUMMARY_HIDDEN, hidden);
        String availableToken = KmuLocalisation.format(KmuLocalisation.CONDITION_MANAGER_SUMMARY_AVAILABLE, available);
        String totalToken = KmuLocalisation.format(KmuLocalisation.CONDITION_MANAGER_SUMMARY_TOTAL, total);

        AppendContext ctx = new AppendContext();

        if (visible > 0)
            appendToken(ctx, new TokenSpec(" ", visibleToken, green));
        if (suppressed > 0)
            appendToken(ctx, new TokenSpec(" - ", suppressedToken, red));
        if (present > 0)
            // present is white - same as the label base color, so no highlight slot needed.
            appendSegment(ctx, new TokenSpec(", ", presentToken, null));
        if (hidden > 0)
            appendToken(ctx, new TokenSpec(", ", hiddenToken, lightBlue));

        appendToken(ctx, new TokenSpec(" - ", availableToken, grey, true));
        appendToken(ctx, new TokenSpec(", ", totalToken, grey, true));

        KmuLabelSpec headerSpec = new KmuLabelSpec(
                KmuLocalisation.get(KmuLocalisation.CONDITION_MANAGER_SUMMARY),
                StarsectorUiColorProvider.get(StarsectorUiColor.GRAY),
                new String[0],
                new Color[0]);
        KmuLabelSpec countsSpec = new KmuLabelSpec(
                ctx.sb.toString(),
                ctx.highlights.toArray(new String[0]),
                ctx.colors.toArray(new Color[0]));

        List<KmuLabelSpec> result = new ArrayList<>();
        result.add(headerSpec);
        result.add(countsSpec);
        return result;
    }

    /** Appends a token and highlights it. Highlights the separator too if {@link TokenSpec#highlightSeparator}. */
    private static void appendToken(AppendContext ctx, TokenSpec spec) {
        if (ctx.hasSegment) {
            ctx.sb.append(spec.separator);
            if (spec.highlightSeparator)
                KmuHighlights.add(ctx.highlights, ctx.colors, spec.separator, spec.color);
        }
        ctx.sb.append(spec.token);
        KmuHighlights.add(ctx.highlights, ctx.colors, spec.token, spec.color);
        ctx.hasSegment = true;
    }

    /** Appends a token with no highlight - used when the token color matches the base. */
    private static void appendSegment(AppendContext ctx, TokenSpec spec) {
        if (ctx.hasSegment) ctx.sb.append(spec.separator);
        ctx.sb.append(spec.token);
        ctx.hasSegment = true;
    }
}
