package kmu.maplayers.base.geometry;

/**
 * The cosmetic channel as one value: which edges take it, and how deep it is.
 *
 * <p>The two never mean anything apart. A depth says nothing without the rule that decides
 * where it applies, and a rule handed a depth from somewhere else shapes a body whose fill
 * stops in a different place from the border traced beside it. {@link BorderTraceStyle} carries
 * the same pair for the same reason, flat, alongside the two tolerances a trace also needs.
 *
 * <p>{@link #asTheMapDraws()} is the one the game ships, so a pass that shapes the map itself
 * names it rather than restating the rule and the distance, two values that have to move
 * together.
 *
 * @param rule  which edges the channel is cut into
 * @param depth how far each of those edges pulls in
 */
public record EdgeInset(
    EdgeInsetRule rule,
    double depth) {

    /**
     * The shaping the map itself draws with: every border edge pulled in by the channel.
     *
     * @return the map's own inset
     */
    public static EdgeInset asTheMapDraws() {
        return new EdgeInset(EdgeInsetRule.AT_EVERY_BORDER, CellShaper.BORDER_INSET_DISTANCE);
    }

    /**
     * How far one edge pulls in under this inset.
     *
     * @param isBorderEdge whether the edge faces something the body is not part of
     * @return the inward distance for this edge, zero to leave it on the true line
     */
    public double resolveInsetOf(boolean isBorderEdge) {
        return rule.resolveInsetOf(isBorderEdge, depth);
    }
}
