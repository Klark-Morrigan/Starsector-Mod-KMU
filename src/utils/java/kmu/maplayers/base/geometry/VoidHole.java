package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;

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
 * neither owns. It also keeps the fields travelling together: they are one hole described
 * several ways, and a method taking some of them separately can be handed a set that do not
 * belong to each other.
 *
 * @param boundary its outline, sampled
 * @param corners  where its arcs meet. Its only extreme points, and so what its extent is
 *                 measured across: every disc bounding a hole has its centre outside it, so
 *                 each arc bulges inward and no point along one can be further out than the
 *                 corners either side of it. Measuring across these rather than across
 *                 {@code boundary} therefore gives the true extent instead of one that
 *                 wanders with how finely the arcs were sampled
 * @param marks    the stretches of border it runs along, one per arc, in the order the walk
 *                 met them. What {@code boundary} was sampled FROM, and the one form that
 *                 says WHICH part of a cell's border this hole took: a caller asking that of
 *                 the samples has to work out again which point belongs to which cell, and
 *                 working it out again is what strays. In walk order, which a hole's boundary
 *                 is deliberately not - that is reversed to wind like any other filled shape
 * @param ringing  the sites whose circles it runs on, in the order it meets them - the
 *                 distinct circles of {@code marks}, deduped once here because a cell facing
 *                 the hole twice is one ringing cell, and because callers ask this inside
 *                 loops over the boundary
 * @param reach    how far the cells were taken to reach when it was traced, which is what
 *                 makes the space inside it void and what any width measured across it is
 *                 measured against
 * @param walledBy the walls laid across the void that this hole closes on, empty when the
 *                 cells closed around it unaided. What tells apart two holes that are
 *                 otherwise the same kind of thing: void a bridge shut in and void a coast
 *                 shut in come out of one walk, and the wall is the only thing that says
 *                 which is which
 */
public record VoidHole(
    List<double[]> boundary,
    List<double[]> corners,
    List<CoastMark> marks,
    List<Integer> ringing,
    double reach,
    List<Chord> walledBy) {

    /**
     * How far it reaches across, as the distance between its two most distant corners.
     *
     * <p>Across the CORNERS rather than across the sampled boundary, for the reason
     * {@link #corners} gives: every disc bounding a hole has its centre outside it, so each arc
     * bulges inward and no point along one can be further out than the corners either side of
     * it. Measured on the samples instead, the answer would wander with how finely the arcs
     * were flattened.
     *
     * <p>Asked of the hole rather than computed by each reader, because more than one wants it
     * and a span measured two ways is two answers about one piece of void.
     *
     * @return the widest distance across it, or zero where it has fewer than two corners
     */
    public double measureSpan() {

        var widest = 0.0;

        for (var first = 0; first < corners.size(); first++) {
            for (var second = first + 1; second < corners.size(); second++) {

                widest = Math.max(
                    widest,
                    Points.computeDistance(corners.get(first), corners.get(second)));
            }
        }
        return widest;
    }
}
