package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;
import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a wall meets one of its circles, as the stretch of that circle it takes out of the
 * boundary.
 *
 * <p>The one place a wall's position is turned into angles, and the reason a coast's reaches
 * and a bridge can be the same kind of thing. Kept apart from {@link DiscUnionBoundary}
 * because the sweep there is bookkeeping over intervals and this is the trigonometry that
 * produces them - and because every defect this construction has had was in the interval,
 * not in the sweep that merged it.
 *
 * <p><b>A mouth is measured against the wall, not against its line.</b> What lies within a
 * channel of a segment is a capsule: the band either side of the line, cut off at each end,
 * and rounded off there by the channel measured to the end itself. Three pieces, joined.
 *
 * <p>Taking the unbounded line instead costs a coast dearly, though it costs a bridge
 * nothing. A bridge's line runs through both sites and crosses its circle steeply, so the
 * stretch it grazes is narrow and lies where the bridge actually is. A reach of coast leaves
 * a cell along its TANGENT, so its line hugs the circle for most of a radian, nearly all of
 * it past where the reach stops - and unbounded, that whole stretch is claimed. A cell the
 * coast turns on then has the mouth it arrives by swallow the mouth it leaves by; the sweep
 * makes sense of that by handing the boundary straight from one wall to the other, but only
 * because the swallowing mouth is the size of a wall's end rather than the size of a radian.
 *
 * <p>The rounding at the ends is not a nicety either. Where a wall is laid across discs one
 * channel wider than the border it was built on, its end sits a channel inside the circle and
 * the cap meets it at a single point, rounding off nothing. Where a wall is laid across that
 * border itself - which is how a coast is walked - its end sits ON the circle, and the cap is
 * the whole of the mouth behind that end. One construction, and which of its parts carries
 * the answer depends on the reach it is asked at.
 */
public final class WallMouths {

    private WallMouths() {
    }

    /**
     * The stretch of one circle a wall's mouth takes out of the boundary.
     *
     * @param union   the discs the wall is laid across
     * @param chord   the wall
     * @param circle  which of its circles to measure the mouth on
     * @param channel how far each side of the wall holds back from it
     * @return the mouth as {@code {start, width}}, or null when the wall passes too far from
     *         this circle to open one at all
     */
    public static double[] measureMouth(
            DiscUnion union,
            DiscUnionBoundary.Chord chord,
            int circle,
            double channel) {

        var towards = measureAngleToWallEnd(chord, circle, union.sites().get(circle));

        // A wall of no width meets the circle at exactly one angle - where its end sits - and
        // that angle is the whole of its mouth. Answered directly rather than through the
        // capsule below, which at no width is a band of nothing intersected away and caps that
        // cover nothing: it would report no mouth at all, and a wall with no mouth is refused,
        // when what the wall has is a mouth of no width at a known place.
        if (channel <= 0) {
            return new double[] {Angles.normalise(towards), 0};
        }

        var arcs = Angles.mergeSpans(
            collectMouthPieces(union, chord, circle, channel), towards);

        if (arcs.isEmpty()) {
            return null;
        }
        var mouth = arcs.size() < 2 ? arcs.get(0) : pickArcHolding(towards, arcs);

        // Folded into the first turn only now that it has been chosen. The choice is an
        // interval test at an exact boundary, and folding a start by a whole turn moves that
        // boundary by a rounding - so the pieces are compared in the turn they were built in
        // and put in a standard one on the way out.
        return new double[] {Angles.normalise(mouth[0]), mouth[1]};
    }

    // The capsule, as the pieces it is made of: the band across the wall's line, narrowed to
    // the wall's own length, and a rounding at each of its two ends. Handed back unjoined,
    // since each is built about its own axis and only the merge puts them in one turn.
    private static List<double[]> collectMouthPieces(
            DiscUnion union,
            DiscUnionBoundary.Chord chord,
            int circle,
            double channel) {

        var line = chord.line().toUnitLine();

        if (line == null) {
            return List.of();
        }
        var centre = union.sites().get(circle);

        var awayX = centre[0] - line.originX();
        var awayY = centre[1] - line.originY();

        // Across the wall and along it. A point at angle t on the circle sits
        // offset + reach * cos(t - axis) from the origin along either axis, so the two
        // conditions are the same shape and the band is the stretch where both hold.
        var across = findArcsWithin(
            union.reach(),
            Math.atan2(line.directionX(), -line.directionY()),
            Points.projectPointOnto(awayX, awayY, -line.directionY(), line.directionX()),
            -channel,
            channel);

        var along = findArcsWithin(
            union.reach(),
            Math.atan2(line.directionY(), line.directionX()),
            Points.projectPointOnto(awayX, awayY, line.directionX(), line.directionY()),
            0,
            Math.hypot(chord.line().directionX(), chord.line().directionY()));

        var pieces = new ArrayList<>(Angles.intersectSpans(across, along));

        pieces.addAll(findCapArc(union.reach(), -awayX, -awayY, channel));
        pieces.addAll(findCapArc(
            union.reach(),
            chord.line().directionX() - awayX,
            chord.line().directionY() - awayY,
            channel));

        return pieces;
    }

    /**
     * The stretches of one circle whose offset along an axis falls in a given range.
     *
     * <p>A point at angle {@code t} sits {@code offset + reach * cos(t - axisAngle)} along the
     * axis, so the answer is the run of {@code t} keeping that between the two bounds - which
     * is a run of {@code cos t}, and so either two stretches or one.
     *
     * <p>Two of a mouth's three pieces are this shape, which is why it is one routine. Across
     * the wall the range is the channel either side of its line; along the wall it is nothing
     * to the wall's own length.
     *
     * <p>TWO while the band cuts clean through the circle: it crosses twice, going in and
     * coming out, on opposite stretches with the rest of the circle between them.
     *
     * <p>ONE once either edge of the band clears the circle entirely. The two stretches then
     * join up round the near end or the far one and are a single stretch, and handing back
     * half of it leaves the circle uncovered where the wall actually crosses - so the boundary
     * walks straight past the wall and the void behind it never closes.
     *
     * <p>NONE when the band misses the circle altogether.
     *
     * @param reach     the circle's radius
     * @param axisAngle the direction from the circle's centre along the axis
     * @param offset    how far the centre sits along that axis from where the range is
     *                  measured
     * @param low       the near bound
     * @param high      the far bound
     * @return the stretches as {@code {start, width}} pairs - none, one or two of them - each
     *         starting in the turn it was built in rather than folded into the first
     */
    private static List<double[]> findArcsWithin(
            double reach,
            double axisAngle,
            double offset,
            double low,
            double high) {

        var nearest = (high - offset) / reach;
        var furthest = (low - offset) / reach;

        if (furthest >= 1 || nearest <= -1) {
            return List.of();
        }

        // Joined round the far end of the circle, the band's far edge having cleared it. A
        // coast reach sits exactly on that threshold across its line: it is drawn tangent to
        // a cell's own border and laid across discs one channel wider, so whether it clears
        // turns on a rounding. A bridge runs through both sites and is nowhere near it.
        if (furthest <= -1) {

            var half = Math.PI - Math.acos(Math.min(1, nearest));
            return List.of(new double[] {axisAngle + Math.PI - half, 2 * half});
        }

        // Joined round the near end, the band's near edge having cleared it instead.
        if (nearest >= 1) {

            var half = Math.acos(furthest);
            return List.of(new double[] {axisAngle - half, 2 * half});
        }

        var inner = Math.acos(nearest);
        var outer = Math.acos(furthest);

        return List.of(
            new double[] {axisAngle + inner, outer - inner},
            new double[] {axisAngle - outer, outer - inner});
    }

    // The stretch of a circle lying within a channel of one of the wall's ends, which is what
    // rounds a mouth off there. Two circles meeting is the same closed form the discs' own
    // crossings use, taken here between this circle and the channel's reach about the end.
    //
    // Given the end as an offset from the centre rather than as a place on the map, so that
    // it reads the same way as the bands it is joined with: every part of a mouth is measured
    // from the centre of the circle it is a mouth on.
    private static List<double[]> findCapArc(
            double reach,
            double awayX,
            double awayY,
            double channel) {

        var away = Points.computeVectorLength(awayX, awayY);

        if (away < Limits.MIN_EDGE_LENGTH) {
            return List.of();
        }
        var cosine = (reach * reach + away * away - channel * channel) / (2 * reach * away);

        if (cosine >= 1) {
            return List.of();
        }
        var half = Math.acos(Math.max(-1, cosine));

        return List.of(new double[] {Math.atan2(awayY, awayX) - half, 2 * half});
    }

    /**
     * Which of the stretches a capsule cuts is the one the wall actually meets.
     *
     * <p>The stretch that CONTAINS the wall's end, not the one whose middle is nearest it.
     * Nearest is a tiebreak, and a tiebreak needs its candidates to be far apart. On a cell
     * facing void most of the way round - the end of a chain, or either half of a two-cell
     * island - they close up on each other and the tiebreak stops meaning anything. Picked
     * wrong there, the wall wraps the far side of the circle and the void it closes runs off
     * along the line instead of stopping at the cells: long wedges out to sea that no
     * side-of-the-line check will complain about, because they lie between two near-parallel
     * reaches and a pair of those bounds a slab rather than a shape.
     *
     * <p>Containment cannot degenerate that way. The wall meets the circle at one known
     * angle, and exactly one of the stretches holds it.
     *
     * @param towards where the wall meets this circle, as an angle from its centre
     * @param arcs    the stretches to choose between
     * @return the one holding it
     */
    private static double[] pickArcHolding(double towards, List<double[]> arcs) {

        for (var arc : arcs) {

            if (Angles.placeAfter(towards, arc[0]) <= arc[0] + arc[1]) {
                return arc;
            }
        }

        // None holds it, which the geometry says cannot happen: the wall meets the circle, so
        // its end is on one of the stretches its own channel opens. Falling back to the nearer
        // of them keeps a rounding at an edge from dropping the wall.
        var nearest = arcs.get(0);

        for (var arc : arcs) {

            if (Angles.measureGap(towards, arc[0] + arc[1] / 2)
                    < Angles.measureGap(towards, nearest[0] + nearest[1] / 2)) {

                nearest = arc;
            }
        }
        return nearest;
    }

    /**
     * Where a wall meets one of its circles, as an angle from that circle's centre.
     *
     * <p>The wall's own end rather than the direction to the cell at its far end. Those agree
     * for a bridge, whose line runs through both sites, and they are most of a right angle
     * apart for a reach of coast, which leaves a cell along its tangent. Taking the far cell's
     * direction opened half a coast's mouths on the wrong side of the cell, which walled off
     * pockets inland of it and left the void it had actually shut in open to the sea.
     *
     * @param chord  the wall
     * @param circle which of its circles the angle is measured at
     * @param centre that circle's centre
     * @return the angle
     */
    private static double measureAngleToWallEnd(
            DiscUnionBoundary.Chord chord,
            int circle,
            double[] centre) {

        var end = chord.findEndOn(circle);

        return Math.atan2(end[1] - centre[1], end[0] - centre[0]);
    }
}
