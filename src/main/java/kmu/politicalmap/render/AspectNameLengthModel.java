package kmu.politicalmap.render;

import java.util.List;

/**
 * A {@link NameLengthModel} with no real text behind it: it treats a name as a
 * rectangle a fixed multiple longer than it is tall, so the box fit can still size a
 * band where no font or display name is available - a face that failed to load, or an
 * owner whose faction no longer resolves. The debug anchor overlay then still shows a
 * plausible label footprint for such a cluster; no name draws there regardless.
 *
 * <p>A one-line name at a given line height is {@code aspect} times as long as it is
 * tall; splitting it across more lines gives each line about that fraction of the
 * characters, so the longest line - the length the box must clear - shrinks by the
 * line count.
 *
 * @param aspect a one-line name's length as a multiple of its line height
 */
record AspectNameLengthModel(double aspect) implements NameLengthModel {

    @Override
    public double requiredLengthFor(double lineHeight, int lineCount) {
        return aspect * lineHeight / lineCount;
    }

    // A stand-in has no text to wrap, so no label is minted from it - only the debug
    // band shows.
    @Override
    public List<String> wrapIntoLines(int lineCount) {
        return List.of();
    }
}
