package kmu.maplayers.base.tooltip;

import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What a layer composed for one paint of its hover box: the blocks its body reads as, and what the
 * deeper detail levels would add beyond the one being drawn.
 *
 * <p>The two travel back together because the second is a fact about the first. Whether anything
 * deeper is there to show turns on what the body found - a system the box lists nobody for has no
 * account for a deeper level to open - so a box asked the two questions separately would read the
 * system a second time to answer a question its own paint had just settled, once per frame for as
 * long as the cursor rests on the cell.
 *
 * <p>Agreement between the box and the hint at its foot is then structural rather than hoped for.
 * Asked apart, the two are answered from two reads of a live sector, and the shape that eventually
 * takes is the cruellest one: a box advertising a key that does nothing, or drawing a body it has
 * just declined to offer.
 *
 * <p>What is carried is the words rather than a flag, because the line at the foot has to say what
 * the player would gain and only the layer knows how to say it.
 *
 * @param sections         the body's blocks, in reading order; empty where the layer has nothing to
 *                         say about the system, which is what stops the box being drawn at all
 * @param deeperDetailName what a deeper level would add for this system, in the player's words, or
 *                         empty where it would add nothing and the box offers no key
 */
public record ComposedCellBody(
    List<TooltipSection> sections,
    Optional<String> deeperDetailName) {

    /**
     * A layer with nothing to say about the system, and so nothing to open up either. Offered as one
     * value rather than as an empty pair each caller spells out, since "nothing found" is one state
     * and two spellings of it agree only until one is edited.
     */
    public static final ComposedCellBody NOTHING =
        new ComposedCellBody(List.of(), Optional.empty());

    /**
     * Copies the blocks, so a layer that went on appending to the list it handed over cannot reshape
     * a body the box has already drawn.
     */
    public ComposedCellBody {
        Objects.requireNonNull(deeperDetailName, "deeperDetailName");
        sections = List.copyOf(sections);
    }
}
