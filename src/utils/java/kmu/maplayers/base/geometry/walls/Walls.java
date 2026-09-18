package kmu.maplayers.base.geometry.walls;

import kmu.maplayers.base.geometry.Chord;

import java.util.List;
import java.util.Set;

/**
 * The walls to lay across the void: which chords, the channel every one keeps, and the
 * cells on which that channel closes to nothing.
 *
 * <p>One value because neither half means anything without the other. A chord list with
 * no channel is not a harmless default - the two pockets either side of every wall then
 * close on the same line and read as one mass, and a zero-width mouth corrupts the cover
 * sweep's bookkeeping besides - so the pairing refuses it outright rather than trusting
 * every caller to remember.
 *
 * <p><b>A pinched cell is the one place a wall is allowed no width.</b> A cell whose whole
 * bridgeable frontage is a single point has every wall attaching at that point, and a wall
 * of any width there buries the very place it lands on: its mouth takes the border either
 * side of the anchor, and a coast running up to the wall stops a channel short of it. So
 * on such a cell the mouth closes to the anchor and the wall's two sides meet there - a
 * wedge rather than a strip - and the coast reaches the one point it was offered.
 * Everywhere else the channel stands, because everywhere else there is border to spare.
 *
 * <p>Per cell rather than per wall end, since the fact is about the cell: every wall on a
 * pinched cell lands on the same point, and one of them kept wide while the rest closed
 * would hold the coast a channel off all of them.
 *
 * <p><b>The channel cannot be given up wall by wall.</b> A mouth is how a cycle is routed
 * onto a wall, so a wall with none does not divide the void - it is touched and walked
 * past. Measured: with the lake shores laid at no width, the pockets inside them ran up to
 * 1291 units out past the shore and the water one fixture's lakes had painted fell by half.
 * A pinched CELL is the exception that proves it: there only one end of a wall closes, and
 * the wall still has a mouth at the other to be routed by.
 *
 * @param chords       the walls, as pairs of circles
 * @param channel      how far each side of a wall holds back from it
 * @param pinchedCells the cells on which a wall keeps no channel at all
 */
public record Walls(
    List<Chord> chords,
    double channel,
    Set<Integer> pinchedCells) {

    // Nothing laid across the void, for the callers that want the cells' own boundary -
    // whether the trace over the bare discs or a coast traced with no bridges. Named
    // rather than built at each of them, because the pair is only legal together: the
    // channel is zero, which the constructor refuses for any real wall and does not
    // need here, since with no chords there is no mouth for a channel to size.
    public static final Walls NONE = new Walls(List.of(), 0);

    /**
     * Walls that keep their channel on every cell.
     *
     * @param chords  the walls, as pairs of circles
     * @param channel how far each side of a wall holds back from it
     */
    public Walls(List<Chord> chords, double channel) {
        this(chords, channel, Set.of());
    }

    public Walls {
        if (!chords.isEmpty() && channel <= 0) {
            throw new IllegalArgumentException("walls need a channel to keep");
        }
        pinchedCells = Set.copyOf(pinchedCells);
    }

    /**
     * How far a wall's sides hold back from it on one cell.
     *
     * @param circle the cell
     * @return the channel, or nothing at all on a pinched cell
     */
    public double channelOn(int circle) {
        return isPinchedOn(circle) ? 0 : channel;
    }

    /**
     * Whether every wall on a cell closes to the one point it lands on.
     *
     * @param circle the cell
     * @return true where the cell is pinched, so a wall's mouth there is its anchor
     */
    public boolean isPinchedOn(int circle) {
        return pinchedCells.contains(circle);
    }
}
