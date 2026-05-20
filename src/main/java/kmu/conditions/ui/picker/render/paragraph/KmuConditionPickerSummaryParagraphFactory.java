package kmu.conditions.ui.picker.render.paragraph;

import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.color.StarsectorUiColorProvider;
import kmlib.starsector.ui.highlight.Highlight;
import kmlib.starsector.ui.highlight.HighlightedParagraph;

import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.util.KmuStrings;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces the conditions summary paragraphs as two stacked lines:
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
public final class KmuConditionPickerSummaryParagraphFactory {
    private KmuConditionPickerSummaryParagraphFactory() {
    }

    /** Accumulates the text and highlight pairs for the count line. */
    private static final class AppendContext {
        final StringBuilder sb = new StringBuilder();
        final List<Highlight> highlights = new ArrayList<>();
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

    public static List<HighlightedParagraph> get(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        int visible = model.getVisibleCount();
        int suppressed = model.getSuppressedCount();
        int present = model.getPresentCount();
        int hidden = model.getHiddenCount();
        int available = model.getAvailableCount();
        int total = model.getEntryCount();

        Color green = StarsectorUiColorProvider.get(StarsectorUiColor.VANILLA_HIGHLIGHT_GREEN);
        Color red = StarsectorUiColorProvider.get(StarsectorUiColor.VANILLA_HIGHLIGHT_RED);
        Color lightBlue = StarsectorUiColorProvider.get(StarsectorUiColor.LIGHT_BLUE);
        Color grey = StarsectorUiColorProvider.get(StarsectorUiColor.VANILLA_GRAY);

        String visibleToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_VISIBLE, visible);
        String suppressedToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_SUPPRESSED, suppressed);
        String presentToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_PRESENT, present);
        String hiddenToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_HIDDEN, hidden);
        String availableToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_AVAILABLE, available);
        String totalToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_TOTAL, total);

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

        HighlightedParagraph headerParagraph = new HighlightedParagraph(
                KmuStrings.get(KmuStrings.CONDITION_MANAGER_SUMMARY),
                StarsectorUiColorProvider.get(StarsectorUiColor.VANILLA_GRAY));
        HighlightedParagraph countsParagraph = new HighlightedParagraph(
                ctx.sb.toString(),
                ctx.highlights.toArray(new Highlight[0]));

        List<HighlightedParagraph> result = new ArrayList<>();
        result.add(headerParagraph);
        result.add(countsParagraph);
        return result;
    }

    /** Appends a token and highlights it. Highlights the separator too if {@link TokenSpec#highlightSeparator}. */
    private static void appendToken(AppendContext ctx, TokenSpec spec) {
        if (ctx.hasSegment) {
            ctx.sb.append(spec.separator);
            if (spec.highlightSeparator)
                ctx.highlights.add(new Highlight(spec.separator, spec.color));
        }
        ctx.sb.append(spec.token);
        ctx.highlights.add(new Highlight(spec.token, spec.color));
        ctx.hasSegment = true;
    }

    /** Appends a token with no highlight - used when the token color matches the base. */
    private static void appendSegment(AppendContext ctx, TokenSpec spec) {
        if (ctx.hasSegment) ctx.sb.append(spec.separator);
        ctx.sb.append(spec.token);
        ctx.hasSegment = true;
    }
}
