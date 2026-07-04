package kmu.politicalmap.render;

/**
 * How much length a faction name needs when set at a given line height across a given
 * number of lines - the one fact the label box fit cannot know from geometry alone.
 *
 * <p>The seam between sizing a label box against a region and knowing how long the
 * actual name is. {@link LabelBoxFitter} grows a band's girth against the border and,
 * at each trial, asks this how much length the name would demand there; whatever
 * answers it decides the fit. Today {@link AspectNameLengthModel} answers from a
 * stand-in aspect ratio, before any font is loaded; once names draw, a font-backed
 * model measures the wrapped glyphs instead - and only this implementation changes,
 * not the fitter that consumes it.
 */
@FunctionalInterface
interface NameLengthModel {

    /**
     * The length a name needs to read at {@code lineHeight} per line across
     * {@code lineCount} lines, in the same world units the fit works in.
     *
     * @param lineHeight the height of a single line
     * @param lineCount  how many lines the name is stacked into - more lines carry
     *                   fewer characters each, so each line is shorter
     * @return the length the longest line of the name would occupy
     */
    double requiredLengthFor(double lineHeight, int lineCount);
}
