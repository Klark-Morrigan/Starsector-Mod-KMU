package kmu.maplayers.base.geometry;

/**
 * Which edges of a shape take the cosmetic inset.
 *
 * <p>True geometry carries no inset: cells partition the map and meet along shared edges, and
 * the inset is a later pass that pulls an edge back so two bodies leave a visible channel
 * between them. Which edges that applies to is the question answered here, kept apart from the
 * shaping that applies it so that one rule serves everything shaped this way rather than each
 * shaper deciding for itself.
 *
 * <p>{@link #AT_EVERY_BORDER} is the map's own rule and the only one the game ships: an edge
 * facing something the body is not part of pulls in, an edge fusing with the rest of the body
 * stays on the true line. The other two ignore what an edge faces and answer for every edge at
 * once, which is what makes the true geometry and the inset separable on screen - under
 * {@link #NOWHERE} the shape drawn IS the partition, and under {@link #EVERYWHERE} every edge
 * stands off by the one depth whatever it faces.
 */
public enum EdgeInsetRule {
    AT_EVERY_BORDER,
    NOWHERE,
    EVERYWHERE;

    /**
     * How far one edge pulls in under this rule.
     *
     * <p>Takes the verdict rather than the edge, so the caller keeps the whole question of what
     * an edge faces - which is a different question for a cell than for a piece of void, while
     * the depth and the three answers are the same for both.
     *
     * @param isBorderEdge whether the edge faces something the body is not part of, which is
     *                     the only thing the shipped rule asks
     * @param borderInset  the channel depth a pulled-in edge takes
     * @return the inward distance for this edge, zero to leave it on the true line
     */
    public double resolveInsetOf(boolean isBorderEdge, double borderInset) {
        return switch (this) {
            case AT_EVERY_BORDER -> isBorderEdge ? borderInset : 0.0;
            case NOWHERE -> 0.0;
            case EVERYWHERE -> borderInset;
        };
    }
}
