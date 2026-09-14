package kmu.conditions.ui.picker.render.paragraph;

import kmlib.starsector.ui.colour.StarsectorUiColour;
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
 *   <li>"Conditions:" header - no highlights, TEXT_WHITE base colour</li>
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

    /** Pairs a separator with the token text, its highlight colour, and whether the separator is also highlighted. */
    private static final class TokenSpec {
        final String separator;
        final String token;
        final Color colour;
        final boolean highlightSeparator;

        TokenSpec(String separator, String token, Color colour, boolean highlightSeparator) {
            this.separator = separator;
            this.token = token;
            this.colour = colour;
            this.highlightSeparator = highlightSeparator;
        }

        TokenSpec(String separator, String token, Color colour) {
            this(separator, token, colour, false);
        }
    }

    public static List<HighlightedParagraph> get(KmuConditionPickerModel model) {
        Objects.requireNonNull(model, "model");

        var visible = model.getVisibleCount();
        var suppressed = model.getSuppressedCount();
        var present = model.getPresentCount();
        var hidden = model.getHiddenCount();
        var available = model.getAvailableCount();
        var total = model.getEntryCount();

        var green = StarsectorUiColour.VANILLA_HIGHLIGHT_GREEN.resolve();
        var red = StarsectorUiColour.VANILLA_HIGHLIGHT_RED.resolve();
        var lightBlue = StarsectorUiColour.LIGHT_BLUE.resolve();
        var grey = StarsectorUiColour.VANILLA_GRAY.resolve();

        var visibleToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_VISIBLE, visible);
        var suppressedToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_SUPPRESSED, suppressed);
        var presentToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_PRESENT, present);
        var hiddenToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_HIDDEN, hidden);
        var availableToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_AVAILABLE, available);
        var totalToken = KmuStrings.format(KmuStrings.CONDITION_MANAGER_SUMMARY_TOTAL, total);

        var ctx = new AppendContext();

        if (visible > 0)
            appendToken(ctx, new TokenSpec(" ", visibleToken, green));
        if (suppressed > 0)
            appendToken(ctx, new TokenSpec(" - ", suppressedToken, red));
        if (present > 0)
            // present is white - same as the label base colour, so no highlight slot needed.
            appendSegment(ctx, new TokenSpec(", ", presentToken, null));
        if (hidden > 0)
            appendToken(ctx, new TokenSpec(", ", hiddenToken, lightBlue));

        appendToken(ctx, new TokenSpec(" - ", availableToken, grey, true));
        appendToken(ctx, new TokenSpec(", ", totalToken, grey, true));

        var headerParagraph = new HighlightedParagraph(
            KmuStrings.get(KmuStrings.CONDITION_MANAGER_SUMMARY),
            StarsectorUiColour.VANILLA_GRAY.resolve());

        var countsParagraph = new HighlightedParagraph(
            ctx.sb.toString(),
            ctx.highlights.toArray(new Highlight[0]));

        var result = new ArrayList<HighlightedParagraph>();
        result.add(headerParagraph);
        result.add(countsParagraph);
        return result;
    }

    /** Appends a token and highlights it. Highlights the separator too if {@link TokenSpec#highlightSeparator}. */
    private static void appendToken(AppendContext ctx, TokenSpec spec) {
        if (ctx.hasSegment) {
            ctx.sb.append(spec.separator);
            if (spec.highlightSeparator)
                ctx.highlights.add(new Highlight(spec.separator, spec.colour));
        }
        ctx.sb.append(spec.token);
        ctx.highlights.add(new Highlight(spec.token, spec.colour));
        ctx.hasSegment = true;
    }

    /** Appends a token with no highlight - used when the token colour matches the base. */
    private static void appendSegment(AppendContext ctx, TokenSpec spec) {
        if (ctx.hasSegment) ctx.sb.append(spec.separator);
        ctx.sb.append(spec.token);
        ctx.hasSegment = true;
    }
}
