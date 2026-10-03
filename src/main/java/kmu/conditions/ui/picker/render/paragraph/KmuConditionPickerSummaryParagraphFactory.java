package kmu.conditions.ui.picker.render.paragraph;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.highlight.Highlight;
import kmlib.starsector.ui.highlight.HighlightedParagraph;

import kmu.conditions.ui.picker.model.KmuConditionPickerModel;
import kmu.util.KmuStringKeys;

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
 * - The group separator separates category groups; the item separator separates items within a group.
 *   Both are strings, so each language punctuates in its own way.
 * - available and total are always last, one grey highlight with the separators between them.
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

    /** Pairs a separator with the token text and its highlight colour. */
    private static final class TokenSpec {

        final String separator;
        final String token;
        final Color colour;

        TokenSpec(String separator, String token, Color colour) {

            this.separator = separator;
            this.token = token;
            this.colour = colour;
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

        var visibleToken = KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_VISIBLE, visible);
        var suppressedToken = KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_SUPPRESSED, suppressed);
        var presentToken = KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_PRESENT, present);
        var hiddenToken = KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_HIDDEN, hidden);
        var availableToken = KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_AVAILABLE, available);
        var totalToken = KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_TOTAL, total);

        // Each separator carries the spacing its language needs around a highlighted count: the game
        // highlights a run only where the characters beside it are whitespace or ASCII punctuation.
        var groupSeparator = KmuStringKeys.get(KmuStringKeys.CONDITION_MANAGER_SUMMARY_GROUP_SEPARATOR);
        var itemSeparator = KmuStringKeys.get(KmuStringKeys.CONDITION_MANAGER_SUMMARY_ITEM_SEPARATOR);

        var ctx = new AppendContext();

        if (visible > 0)
            appendToken(ctx, new TokenSpec(itemSeparator, visibleToken, green));

        if (suppressed > 0)
            appendToken(ctx, new TokenSpec(groupSeparator, suppressedToken, red));

        if (present > 0)
            // present is white - same as the label base colour, so no highlight slot needed.
            appendSegment(ctx, new TokenSpec(itemSeparator, presentToken, null));

        if (hidden > 0)
            appendToken(ctx, new TokenSpec(itemSeparator, hiddenToken, lightBlue));

        appendTrailingRun(ctx, groupSeparator, availableToken + itemSeparator + totalToken, grey);

        var headerParagraph = new HighlightedParagraph(
            KmuStringKeys.get(KmuStringKeys.CONDITION_MANAGER_SUMMARY),
            StarsectorUiColour.VANILLA_GRAY.resolve());

        var countsParagraph = new HighlightedParagraph(
            ctx.sb.toString(),
            ctx.highlights.toArray(new Highlight[0]));

        var result = new ArrayList<HighlightedParagraph>();

        result.add(headerParagraph);
        result.add(countsParagraph);

        return result;
    }

    /** Appends a token and highlights it. */
    private static void appendToken(AppendContext ctx, TokenSpec spec) {

        if (ctx.hasSegment) {
            ctx.sb.append(spec.separator);
        }

        ctx.sb.append(spec.token);
        ctx.highlights.add(new Highlight(spec.token, spec.colour));
        ctx.hasSegment = true;
    }

    // Closes the line with one run in one colour, the separator before it included. The game highlights a
    // run only when the characters beside it are whitespace or punctuation, so a separator highlighted on
    // its own would start with a space touching the word before it and never tint. The run starts after
    // that space instead, at the separator's mark, or at the start of the line when nothing precedes it.
    private static void appendTrailingRun(AppendContext ctx, String separator, String runText, Color colour) {

        var highlightedText = runText;

        if (ctx.hasSegment) {
            ctx.sb.append(separator);
            highlightedText = separator.stripLeading() + runText;
        }

        ctx.sb.append(runText);
        ctx.highlights.add(new Highlight(highlightedText, colour));
        ctx.hasSegment = true;
    }

    /** Appends a token with no highlight - used when the token colour matches the base. */
    private static void appendSegment(AppendContext ctx, TokenSpec spec) {

        if (ctx.hasSegment)
            ctx.sb.append(spec.separator);

        ctx.sb.append(spec.token);
        ctx.hasSegment = true;
    }
}
