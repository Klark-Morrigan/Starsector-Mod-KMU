package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * One hole in the union of the cells' reach discs at a single reach, before anything is
 * decided about it.
 *
 * <p>Not a pocket yet: a pocket is a hole at the TRUE reach, together with what becomes of it
 * at the reach it is drawn at, who rings it, and how it divides.
 *
 * <p>Its own type rather than a shape nested inside whichever class builds it, because two
 * do different things with the same hole - one shapes it into a pocket, the other cuts it
 * into sections - and a type nested in either would make the other depend on it for a value
 * neither owns. It also keeps the four fields travelling together: they are one hole
 * described four ways, and a method taking three of them separately can be handed three that
 * do not belong to each other.
 *
 * @param boundary its outline, sampled
 * @param corners  where its arcs meet. Its only extreme points, and so what its extent is
 *                 measured across: every disc bounding a hole has its centre outside it, so
 *                 each arc bulges inward and no point along one can be further out than the
 *                 corners either side of it. Measuring across these rather than across
 *                 {@code boundary} therefore gives the true extent instead of one that
 *                 wanders with how finely the arcs were sampled
 * @param ringing  the sites whose circles it runs on, in the order it meets them
 * @param reach    how far the cells were taken to reach when it was traced, which is what
 *                 makes the space inside it void and what any width measured across it is
 *                 measured against
 */
record VoidHole(
    List<double[]> boundary,
    List<double[]> corners,
    List<Integer> ringing,
    double reach) {
}
