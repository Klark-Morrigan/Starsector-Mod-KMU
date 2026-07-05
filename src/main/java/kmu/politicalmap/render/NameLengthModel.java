package kmu.politicalmap.render;

import java.util.List;

/**
 * The name a label box is sized for: how much length it needs at a given line height and
 * line count, and what its wrapped lines actually are - the two facts the geometry-only
 * box fit cannot know on its own.
 *
 * <p>The seam between sizing a label box against a region and knowing the name that must
 * fill it. {@link LabelBoxFitter} grows a band's girth against the border and, at each
 * trial, asks this how much length the name would demand there; whatever answers it
 * decides the fit. Both answers come from the same wrap - the required length is the
 * width of the widest wrapped line - so keeping them behind one model stops the measured
 * fit and the drawn lines from ever disagreeing. {@link FontNameLengthModel} answers
 * from real glyph metrics; {@link AspectNameLengthModel} stands in with a fixed shape
 * where no font or name is available (the fit still sizes a debug band there, but no
 * text draws).
 */
interface NameLengthModel {

    /**
     * The length a name needs to read at {@code lineHeight} per line across
     * {@code lineCount} lines, in the same world units the fit works in.
     *
     * @param lineHeight the height of a single line
     * @param lineCount  how many lines the name is stacked into - more lines carry
     *                   fewer characters each, so each line is shorter
     * @return the length the longest line of the name would occupy, or positive
     *         infinity when the name cannot be split into that many lines at all
     */
    double requiredLengthFor(double lineHeight, int lineCount);

    /**
     * The name's lines at the given line count - the same wrap
     * {@link #requiredLengthFor} measured, so a label draws exactly the block the fit
     * sized.
     *
     * @param lineCount how many lines to wrap the name into
     * @return the wrapped lines, top line first, or an empty list when the name cannot
     *         be split into that many lines or the model has no real text (a stand-in)
     */
    List<String> wrapIntoLines(int lineCount);
}
