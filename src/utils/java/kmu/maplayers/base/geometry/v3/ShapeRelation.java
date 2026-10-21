package kmu.maplayers.base.geometry.v3;

import java.util.Map;

/**
 * Where two cells stand relative to the shapes of the sector.
 *
 * <p>Three answers rather than a predicate, because the third is not the negation of
 * either: a cell can be on no shape at all, and BOTH "these are one shape" and "these are
 * two shapes" have to refuse it. Written as a boolean by each search in turn, that refusal
 * is a null check stated twice, in two classes, with nothing holding the two statements
 * together - and a search that came to drop it would offer spans from cells that have
 * nothing to anchor on.
 */
enum ShapeRelation {

    /** Both cells sit on one shape, so a span between them stays inside that outline. */
    SAME_SHAPE,

    /** Each sits on a shape, and not the same one, so a span between them joins two. */
    DIFFERENT_SHAPES,

    /**
     * At least one of them is on no shape at all, and so joins nothing.
     *
     * <p>The silhouettes name only the cells the outer walk touched, so a cell facing
     * nothing but a lake is absent from them - and it has no frontage on the void to reach
     * out over in the first place. Refused by every search, whichever answer it wanted.
     */
    UNPLACED;

    /**
     * How two cells stand to each other in one of the trace's maps of cells to shapes.
     *
     * <p>Over either map, since which one is passed is what decides what a shape IS: the
     * continents alone, or the continents and the sector's islands. That choice belongs to the
     * search asking, and nothing about comparing two lookups depends on it.
     *
     * @param shapeOf which shape each cell sits on, from {@link TracedCoasts#mapCellsToContinents}
     *                or {@link TracedCoasts#mapCellsToShapes}
     * @param from    one cell
     * @param to      the other
     * @return how the two stand
     */
    static ShapeRelation compareShapesOf(Map<Integer, Integer> shapeOf, int from, int to) {

        var one = shapeOf.get(from);
        var other = shapeOf.get(to);

        if (one == null || other == null) {
            return UNPLACED;
        }

        return one.equals(other)
            ? SAME_SHAPE
            : DIFFERENT_SHAPES;
    }

    /** @return true where both cells sit on one shape */
    boolean isSameShape() {
        return this == SAME_SHAPE;
    }

    /** @return true where each sits on a shape and the two are different */
    boolean isDifferentShapes() {
        return this == DIFFERENT_SHAPES;
    }
}
