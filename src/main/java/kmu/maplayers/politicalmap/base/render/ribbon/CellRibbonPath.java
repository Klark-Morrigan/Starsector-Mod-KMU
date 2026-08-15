package kmu.maplayers.politicalmap.base.render.ribbon;

/**
 * One cell's band path as the diagnostic overlay draws it: the ring the band would run along,
 * flattened for emission, plus what the cell's own room lets a band do with it.
 *
 * <p>Held for the overlay alone, and only while the player has it on. A cell that draws no band -
 * because nothing but its own bloc is present in it, or because its ring had no room - shows
 * nothing at all on the map, and the two look identical to a reader and to whoever is tuning the
 * sizes. This is the path underneath that silence: it says where a band would have gone and, in
 * its verdict's colour, which of the two kinds of nothing the cell is.
 *
 * <p>Flattened at bake rather than at draw, exactly as {@link RibbonBand} is: the overlay is
 * emitted every frame the map is open, and the path it emits changes only when the cells are
 * re-shaped.
 *
 * @param centreline the traced path as {@code [x, y, x, y, ...]} world coordinates in draw order,
 *                   closed by the emission rather than by a repeated last vertex; empty on a cell
 *                   nothing could be traced on
 * @param verdict    what a band does with this ring - the reading the overlay colours by, not a
 *                   promise that a band was laid: a path with room on it still draws nothing where
 *                   the cell has nothing to report
 */
public record CellRibbonPath(
    float[] centreline,
    RibbonPathVerdict verdict) {

    /**
     * The cell no path could be traced on: narrower than the band is wide, or one the overlay was
     * not asked about at all.
     *
     * <p>Refused rather than carrying an outcome of its own, since a ring with nowhere to put a
     * centreline is the plainest refusal there is - and an empty path emits nothing, so the
     * verdict is never drawn.
     */
    public static final CellRibbonPath NONE =
        new CellRibbonPath(new float[0], RibbonPathVerdict.REFUSED);

    /**
     * @return true when the cell has no path to draw, so the overlay can skip it without reading
     *         into it
     */
    public boolean isEmpty() {
        return centreline.length == 0;
    }
}
