package kmu.maplayers.ownermap.render.hover;

/**
 * Whether the layer being drawn answers the cursor, and with which of the two kinds of feedback:
 * the halo and cell wash painted over the map, and the box naming what is under the pointer.
 *
 * <p>The tier runs the frame and never decides this. Each answer is one layer's own switch ANDed
 * with the switches above it that answer for every layer, and the switches a layer offers are its
 * own business - one painting hazards has no reason to spell them the way one painting holdings
 * does.
 *
 * <p>Three questions rather than one because the two kinds of feedback are separately switchable
 * and the third is neither: {@link #isCursorReadNeeded} asks whether the cursor has to be resolved
 * at all this frame, which is true while <em>either</em> kind still wants an answer. Folding it
 * into the other two would make a frame with only the box enabled skip the read the box needs.
 */
public interface OwnerMapHoverGates {

    /**
     * Whether anything may be painted over the map for the cursor's sake.
     *
     * @return true when the halo and cell wash are to be drawn
     */
    boolean isHoverEffectsEnabled();

    /**
     * Whether the box naming what is under the pointer may be drawn.
     *
     * @return true when the hover box is to be resolved
     */
    boolean isHoverTooltipEnabled();

    /**
     * Whether this frame's passes are to resolve the cursor at all.
     *
     * <p>The union of the other two rather than either one, the read backing both: with only the box
     * on, the read must still run to name what is under the pointer, and with both off it can be
     * skipped entirely along with the map-matrix read and hit test behind it. Stated once here, so
     * no layer's gates can answer it apart from their own switches.
     *
     * @return true while either kind of feedback still wants an answer
     */
    default boolean isCursorReadNeeded() {
        return isHoverEffectsEnabled() || isHoverTooltipEnabled();
    }
}
