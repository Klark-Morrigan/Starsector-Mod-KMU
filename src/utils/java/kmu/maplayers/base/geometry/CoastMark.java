package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Angles;

/**
 * One stretch of border a traced cycle runs along: the arc of one cell, as the angles it
 * spans. What a coast is made of, and equally what the edge of a hole is made of - both
 * come off the one walk, and a stretch of border is the same thing whichever side of it
 * the cells lie on.
 *
 * <p>The whole stretch rather than a single point on it, because a smoothed coast wants
 * two different things from it: the middle, which is where the line would pass if nothing
 * were in the way, and the two ends, which bound how far along the cell's border the line
 * may be slid when something is.
 *
 * @param circle    whose cell the stretch of coast belongs to
 * @param fromAngle the angle it begins at
 * @param toAngle   the angle it ends at, always greater than {@code fromAngle}
 */
public record CoastMark(
    int circle,
    double fromAngle,
    double toAngle) {

    /**
     * The middle of the stretch - where a coast passes when nothing blocks it.
     *
     * @return the angle halfway along
     */
    public double midAngle() {
        return (fromAngle + toAngle) / 2;
    }

    /**
     * How much of the cell's own border this stretch is, as a share of the whole turn.
     *
     * <p>How far a cell sticks out into the void, in the only terms that compare across
     * a map: a share rather than an arc length, so the answer does not move when the
     * reach slider does, and so one number means the same thing on every cell.
     *
     * <p>Of the STRETCH rather than of the cell. A cell facing the void on two separate
     * frontages - a strait, or the inside of a C - contributes one stretch per frontage,
     * and each is a separate place the coast passes; summing them would report a cell
     * that peeks out twice as though it presented one broad face.
     *
     * @return the share of the full turn, from 0 to 1
     */
    public double measureShareOfCircle() {
        return (toAngle - fromAngle) / Angles.FULL_TURN;
    }
}
