package kmu.maplayers.politicalmap.base.render.ribbon;

import kmu.maplayers.politicalmap.base.ribbon.RibbonSegmentLengths;

/**
 * Every size the presence band is drawn at, in world units.
 *
 * <p>World units throughout, and that is the design decision the rest of the band follows from.
 * A width stated in pixels would have to be re-measured every frame against the camera, would
 * mismatch the world pad it is offset alongside, and would make how far a run reaches depend on
 * how far the player has zoomed out. Stated in the world, the whole band resolves once at rebuild
 * and is drawn thereafter as the fills are - so a band occupies the same share of its cell's
 * outline at every zoom, and the only thing zoom changes is whether it can be seen.
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
 */
public record RibbonStyle(
    double widthWorld,
    double insetPadWorld,
    double miterSpikeLimit,
    RibbonSegmentLengths lengths) {

    // The band's thickness. Read against the 150-unit border inset the cells are shaped by: a band
    // near that thickness sits clearly inside a cell without competing with the border it follows.
    private static final double DEFAULT_WIDTH_WORLD = 120.0;

    // The gap between the border and the band's near edge - half a width, so the band reads as
    // separated from the border rather than doubling it.
    private static final double DEFAULT_INSET_PAD_WORLD = 60.0;

    // Where a corner stops being a corner and becomes a spike. Two half-widths of overshoot is
    // about a 53-degree turn; anything sharper bevels, which on a cell ring is rare enough to be
    // the exception it is meant to be.
    private static final double DEFAULT_MITER_SPIKE_LIMIT = 2.0;

    // The design's own proportions: a market runs three widths, and two markets of one bloc are
    // parted by one.
    private static final RibbonSegmentLengths DEFAULT_LENGTHS = new RibbonSegmentLengths(3, 1);

    /**
     * The authored sizes the band is drawn at.
     *
     * <p>TODO: read these from the player's LunaLib visuals-tab sliders, so the proportions the
     * whole design rests on are tunable in play rather than fixed here.
     *
     * @return the shipped defaults
     */
    public static RibbonStyle createAuthoredDefaults() {
        return new RibbonStyle(
            DEFAULT_WIDTH_WORLD,
            DEFAULT_INSET_PAD_WORLD,
            DEFAULT_MITER_SPIKE_LIMIT,
            DEFAULT_LENGTHS);
    }

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
}
