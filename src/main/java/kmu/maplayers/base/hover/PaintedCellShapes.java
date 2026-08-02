package kmu.maplayers.base.hover;

import java.util.List;
import java.util.Map;

/**
 * The shapes one frame put on screen, keyed by the cell each belongs to.
 *
 * <p>Both halves of hovering run off these same shapes - the read that resolves a cursor pixel to
 * a cell, and the highlight that lights that cell up - and they have to be the *same* shapes or
 * the halo traces an outline the cursor was never tested against. Declaring them once, as a
 * supertype of both seams, is what makes that agreement structural instead of a promise two
 * implementations happen to keep.
 *
 * <p>These are the shapes actually painted rather than a re-derivation of them, so the border
 * channel between two cells is genuinely nobody's - a cursor there resolves to no cell, exactly as
 * it draws.
 *
 * <p>Shapes are compared by identity downstream, so an implementation must hand back the same
 * instance for as long as the geometry behind it is unchanged, and a fresh one once a rebuild or
 * an incremental re-shape has replaced it. A defensive copy per call would defeat the highlight's
 * memoisation and re-trace every frame.
 */
public interface PaintedCellShapes {

    /**
     * @return each drawn cell's painted extent as {x, y} vertex pairs in world coordinates,
     *         keyed by cell id; a cell that draws nothing is absent and can never be hit
     */
    Map<String, List<double[]>> getFillPolygonByCellId();

    /**
     * The extent painted for one cell - the shape a cursor lands on and a wash lifts.
     *
     * <p>Absent and empty mean the same thing to a caller (a cell that puts no ink on the map),
     * so this folds the two together and every reader is spared deciding which it got.
     *
     * @param cellId the cell asked about
     * @return its painted extent, or an empty list when the cell draws nothing at all
     */
    default List<double[]> resolvePaintedExtentOf(String cellId) {
        var paintedExtent = getFillPolygonByCellId().get(cellId);
        return paintedExtent == null ? List.of() : paintedExtent;
    }
}
