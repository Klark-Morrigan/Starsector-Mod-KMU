package kmu.politicalmap.render;

/**
 * A {@link NameLengthModel} that stands in for a real font while the label work is
 * still a debug overlay: it treats a name as a rectangle a fixed multiple longer than
 * it is tall, so the box fit can size a plausible label before any glyphs exist.
 *
 * <p>The one piece the reuse story throws away. A one-line name at a given line height
 * is {@code aspect} times as long as it is tall; splitting it across more lines gives
 * each line about that fraction of the characters, so the longest line - the length
 * the box must clear - shrinks by the line count. When names draw, a font-backed model
 * measuring the actual wrapped string replaces this, and nothing else in the fit moves.
 *
 * @param aspect a one-line name's length as a multiple of its line height
 */
record AspectNameLengthModel(double aspect) implements NameLengthModel {

    @Override
    public double requiredLengthFor(double lineHeight, int lineCount) {
        return aspect * lineHeight / lineCount;
    }
}
