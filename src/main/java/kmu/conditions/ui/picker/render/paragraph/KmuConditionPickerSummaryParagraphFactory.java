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
 *   <li>"Conditions:" header - no highlights, grey</li>
 *   <li>Count line - highlights for visible (green), suppressed (red) and hidden (blue); present stays in
 *       the body colour; available and total close the line as one grey run</li>
 * </ol>
 *
 * Separator rules for the count line:
 * - First count starts immediately (no leading character).
 * - The group separator separates category groups; the item separator separates items within a group.
 *   Both are strings, so each language punctuates in its own way.
 * - Zero-value counts are omitted except available and total.
 */
public final class KmuConditionPickerSummaryParagraphFactory {

    private KmuConditionPickerSummaryParagraphFactory() {
    }

    /**
     * Composes the header and the counts line for a model.
     *
     * @param model the picker's model
     * @return the header, then the counts line
     */
    public static List<HighlightedParagraph> createParagraphs(KmuConditionPickerModel model) {

        Objects.requireNonNull(model, "model");

        var visible = model.getVisibleCount();
        var suppressed = model.getSuppressedCount();
        var present = model.getPresentCount();
        var hidden = model.getHiddenCount();

        // Each separator carries the spacing its language needs around a highlighted count: the game
        // highlights a run only where the characters beside it are whitespace or ASCII punctuation.
        var groupSeparator = KmuStringKeys.get(KmuStringKeys.CONDITION_MANAGER_GROUP_SEPARATOR);
        var itemSeparator = KmuStringKeys.get(KmuStringKeys.CONDITION_MANAGER_ITEM_SEPARATOR);

        var countsLine = new CountsLine();

        if (visible > 0) {
            countsLine.appendHighlightedPart(
                itemSeparator,
                KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_VISIBLE, visible),
                StarsectorUiColour.VANILLA_HIGHLIGHT_GREEN.resolve());
        }
        if (suppressed > 0) {
            countsLine.appendHighlightedPart(
                groupSeparator,
                KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_SUPPRESSED, suppressed),
                StarsectorUiColour.VANILLA_HIGHLIGHT_RED.resolve());
        }
        // Present draws in the body colour, so it takes no highlight.
        if (present > 0) {
            countsLine.appendPart(
                itemSeparator,
                KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_PRESENT, present));
        }
        if (hidden > 0) {
            countsLine.appendHighlightedPart(
                itemSeparator,
                KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_HIDDEN, hidden),
                StarsectorUiColour.LIGHT_BLUE.resolve());
        }
        countsLine.appendHighlightedTail(
            groupSeparator,
            KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_AVAILABLE, model.getAvailableCount())
                + itemSeparator
                + KmuStringKeys.format(KmuStringKeys.CONDITION_MANAGER_SUMMARY_TOTAL, model.getEntryCount()),
            StarsectorUiColour.VANILLA_GRAY.resolve());

        var headerParagraph = new HighlightedParagraph(
            KmuStringKeys.get(KmuStringKeys.CONDITION_MANAGER_SUMMARY),
            StarsectorUiColour.VANILLA_GRAY.resolve());

        return List.of(headerParagraph, countsLine.buildParagraph());
    }

    // The counts line being composed: its text, and the runs tinted in it. A part after the first is joined
    // to the one before by the separator given with it.
    private static final class CountsLine {

        private final List<Highlight> highlights = new ArrayList<>();
        private final StringBuilder lineText = new StringBuilder();

        void appendHighlightedPart(String separator, String partText, Color colour) {

            appendPart(separator, partText);
            highlights.add(new Highlight(partText, colour));
        }

        // Closes the line with one run in one colour, the separator before it included. The game highlights
        // a run only when the characters beside it are whitespace or punctuation, so a separator highlighted
        // on its own would start with a space touching the word before it and never tint. The run starts
        // after that space instead, at the separator's mark, or at the start of the line when nothing
        // precedes it.
        void appendHighlightedTail(String separator, String tailText, Color colour) {

            var highlightedText = lineText.isEmpty()
                ? tailText
                : separator.stripLeading() + tailText;

            appendPart(separator, tailText);
            highlights.add(new Highlight(highlightedText, colour));
        }

        void appendPart(String separator, String partText) {

            if (!lineText.isEmpty()) {
                lineText.append(separator);
            }
            lineText.append(partText);
        }

        HighlightedParagraph buildParagraph() {
            return new HighlightedParagraph(lineText.toString(), highlights.toArray(new Highlight[0]));
        }
    }
}
