package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

/**
 * How one pass lays its presence bands out: every size they are settled at, all of them world
 * sizes, plus the two answers about laying one out that are not sizes at all.
 *
 * <p>World units throughout, and that is the design decision the rest of the band follows from. A
 * width stated in pixels would have to be re-measured every frame against the camera, would
 * mismatch the world pad it is offset alongside, and would make how far a run reaches depend on
 * how far the player has zoomed out. Stated in the world, the whole band resolves once at rebuild
 * and is drawn thereafter as the fills are - so a band occupies the same share of its cell's
 * outline at every zoom, and a frame measures nothing about it at all.
 *
 * <p>The two lengths ride here with the two sizes because they are the same authored decision:
 * how long a market's run is relative to the parting between two of them is what makes a bloc's
 * run read as several colonies, and reading it apart from the width those lengths are multiples
 * of would let one be tuned against a stale reading of the other.
 *
 * @param widthWorld      how thick the band is, half of it either side of the path it is drawn
 *                        along
 * @param insetPadWorld   how far clear of the cell's own ring the band's near edge runs, so the
 *                        band reads as sitting inside the border rather than as part of it
 * @param miterSpikeLimit how far past a corner a mitre may reach, as a multiple of the half
 *                        width, before the corner is bevelled instead. Shared by the inset that
 *                        finds the path and the stroke that gives it girth, so a corner sharp
 *                        enough to spike is given up on at one angle rather than at two
 * @param lengths         how far a market's segment and an interjection run, in widths
 * @param isBandAlwaysDrawn whether a cell that planned a band gets one even where its ring has no
 *                        room to hold it - the names covering the whole of it, or the cell being
 *                        too narrow for the band and its pad together. The player's, and here
 *                        rather than beside the plan because both cases are the ring refusing a
 *                        band rather than the system having nothing to report
 */
public record RibbonStyle(
    double widthWorld,
    double insetPadWorld,
    double miterSpikeLimit,
    RibbonSegmentLengths lengths,
    boolean isBandAlwaysDrawn) {

    /**
     * How far inside the cell's ring the band's path runs: clear of the border by the pad, and
     * then far enough again for the band's own near edge to sit at that pad rather than over it.
     *
     * <p>Asked of the style rather than worked out where the path is traced, because the pad is a
     * statement about the band's edge while the trace needs one about its centre, and the two
     * differing by half a width is a fact about how a band is stroked - not about the cell.
     *
     * @return the inset the band's centreline is traced at
     */
    public double computeCentrelineInset() {
        return insetPadWorld + widthWorld / 2;
    }

    /**
     * The shallowest inset a band can be traced at and still sit inside the ring: the pad given
     * up, the half width kept.
     *
     * <p>What a cell too narrow to hold the authored inset falls back to, where a band is drawn
     * whatever room the cell leaves. The half width is not negotiable in the way the pad is - it
     * is what puts the band's own near edge on the border rather than over it, so giving up any of
     * it would hang the band outside the cell it reports on.
     *
     * @return the inset a band traces at when the cell has no room for the padded one
     */
    public double computeUnpaddedCentrelineInset() {
        return widthWorld / 2;
    }
}
