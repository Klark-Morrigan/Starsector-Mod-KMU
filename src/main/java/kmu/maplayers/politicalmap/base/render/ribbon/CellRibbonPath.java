package kmu.maplayers.politicalmap.base.render.ribbon;

import java.util.List;

/**
 * One cell's band path as the diagnostic overlay draws it: the ring the band would run along, cut
 * into the stretches it may use and the stretches its own shape denied it, plus what the cell's
 * room lets a band do with the ring as a whole.
 *
 * <p>Held for the overlay alone, and only while the player has it on. A cell that draws no band -
 * because nothing but its own bloc is present in it, or because its ring had no room - shows
 * nothing at all on the map, and the two look identical to a reader and to whoever is tuning the
 * sizes. This is the path underneath that silence: it says where a band would have gone and, in
 * its verdict's colour, which of the two kinds of nothing the cell is.
 *
 * <p>The two sets of stretches are separate fields rather than one ring plus a rule for reading it,
 * because they are drawn in different shades and a reader has to tell them apart at a glance. A
 * cell narrow in one place carries ring a band may not lie on, and on a cell narrow over most of
 * its length that ring can fold outside the cell's own border - so a path drawn as one loop shows
 * a line escaping the very shape it reports on, with nothing to say which part of it was real.
 *
 * <p>Flattened at bake rather than at draw, exactly as {@link RibbonBand} is: the overlay is
 * emitted every frame the map is open, and the path it emits changes only when the cells are
 * re-shaped.
 *
 * @param heldStretches   the ring a band may lie on, each stretch {@code [x, y, x, y, ...]} world
 *                        coordinates in draw order; open polylines rather than a closed loop,
 *                        since a carved ring is no longer one
 * @param carvedStretches the ring a band may not lie on - where the traced path stood nearer the
 *                        cell's border than the inset it was built from - in the same form
 * @param startPoint      the {@code [x, y]} the ring is walked from, carried in its own right
 *                        because the carve can take the stretch that opens there
 * @param verdict         what a band does with this ring - the reading the overlay colours the
 *                        held stretches by, not a promise that a band was laid: a path with room
 *                        on it still draws nothing where the cell has nothing to report
 */
public record CellRibbonPath(
    List<float[]> heldStretches,
    List<float[]> carvedStretches,
    float[] startPoint,
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
        new CellRibbonPath(List.of(), List.of(), new float[0], RibbonPathVerdict.REFUSED);

    /**
     * @return true when the cell has no path to draw, so the overlay can skip it without reading
     *         into it
     */
    public boolean isEmpty() {
        return heldStretches.isEmpty() && carvedStretches.isEmpty();
    }
}
