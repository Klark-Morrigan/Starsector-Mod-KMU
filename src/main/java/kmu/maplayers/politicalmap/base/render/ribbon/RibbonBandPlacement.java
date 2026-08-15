package kmu.maplayers.politicalmap.base.render.ribbon;

/**
 * Where a cell's band sits along the one stretch of ring it was given.
 *
 * <p>A band shorter than its stretch has room to slide, and where it sits along it is a decision
 * rather than an accident of where the surviving ring happened to begin. Every cell is read from
 * the same place - its top centre, the origin the path is measured from and where the dominant
 * bloc's run begins - so a band that could sit at that landmark and instead sits wherever a name
 * left off costs the reader the one thing every cell's band has in common.
 *
 * <p>Hence the rule in one line: <b>a band sits as near the top centre as its stretch allows</b>.
 * Near is measured on the band's <em>start</em>, not on its centre or its nearest edge, because
 * the start is where the reading begins - the dominant bloc first, running clockwise - so a band
 * pulled toward the anchor by its middle would straddle the anchor and put the middle of the
 * readout where its opening belongs.
 *
 * <p>Pure arithmetic over a stretch, a length and a perimeter, with the anchor the path's own
 * origin and so always zero, so every placement is posed on literals rather than on a cell.
 */
public final class RibbonBandPlacement {

    private RibbonBandPlacement() {
    }

    /**
     * Where the band's first run begins, clockwise, on the stretch it is laid along.
     *
     * <p>One clamp answers it: the band starts at the top centre, pulled into the room the
     * stretch leaves it by the shortest way round. A band of length {@code bandLength} may start
     * anywhere in {@code [stretchStart, stretchEnd - bandLength]}, and that range is never empty
     * - the band was already sized to fit the stretch - so this is only ever a placement question
     * and never a refusal.
     *
     * <p>Four behaviours fall out of the one clamp rather than being written as cases:
     *
     * <ul>
     *   <li>the anchor on the stretch with room to its end: the band starts on the anchor and
     *       runs clockwise, which is the common cell and the unchanged one;</li>
     *   <li>the anchor on the stretch but a name beginning too soon after it: the start backs up
     *       to the latest the stretch allows, so the anchor still falls on the band and only
     *       which part of it lands there moves;</li>
     *   <li>a name over the anchor and a little of the ring clockwise of it: the band begins
     *       where the stretch opens, as near the anchor as the name allows;</li>
     *   <li>a stretch nowhere near the anchor: whichever of its two ends puts the band's start
     *       nearer, measured the short way round.</li>
     * </ul>
     *
     * @param stretchStart where the stretch opens, in arc length from the path's own start
     * @param stretchEnd   where it closes, measured from that same start; past the perimeter for
     *                     the stretch straddling it, which arrives fused into one interval rather
     *                     than as the two ends it is carved as
     * @param bandLength   how far round the whole band reaches, already sized to fit the stretch
     * @param perimeter    the path's full length, the lap nearness is measured within
     * @return the arc length the band begins at
     */
    public static double placeBandStart(
            double stretchStart,
            double stretchEnd,
            double bandLength,
            double perimeter) {

        var latestStart = Math.max(stretchStart, stretchEnd - bandLength);
        var anchor = alignAnchorWithStretch(stretchStart, perimeter);

        if (anchor <= latestStart) {
            return anchor;
        }
        if (anchor <= stretchEnd) {
            return latestStart;
        }
        return selectNearerEndOfStretch(stretchStart, latestStart, anchor, perimeter);
    }

    // The anchor stated on the stretch's own lap: the first top centre at or after the stretch
    // opens.
    //
    // The path's origin is zero, but the stretch straddling it runs past the perimeter, so the
    // anchor such a stretch holds is the one a lap on. Measuring every stretch back to zero would
    // put the anchor behind every fused stretch instead of within it, and the cells whose names
    // sit anywhere but their top centre are exactly the ones that arrive fused.
    private static double alignAnchorWithStretch(double stretchStart, double perimeter) {
        return Math.ceil(stretchStart / perimeter) * perimeter;
    }

    // Which end of the stretch a band goes to when the anchor lies off the stretch entirely. The
    // two candidate starts are the stretch's own start and the latest start it allows, and the
    // band takes whichever of them the anchor is nearer to going round the ring.
    //
    // Measured to those two starts rather than to the stretch's two edges, because nearness is
    // nearness of the band's start: a stretch closing just behind the anchor can still hold a
    // long band reaching far back round the ring, and aligning to that end would throw the
    // reading's opening to the far side of the cell for the sake of a sliver of name.
    //
    // A tie takes the stretch's start, so a stretch lying exactly opposite the anchor places
    // deterministically rather than on whichever way the last of the rounding fell.
    private static double selectNearerEndOfStretch(
            double stretchStart,
            double latestStart,
            double anchor,
            double perimeter) {

        var forwardToStretchStart = stretchStart + perimeter - anchor;
        var backwardToLatestStart = anchor - latestStart;

        return forwardToStretchStart <= backwardToLatestStart ? stretchStart : latestStart;
    }
}
