package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segment;

/**
 * A line with two loose ends, laid across whatever it falls on, saying which line it is.
 *
 * <p>The open sibling of {@link LabelledRing}: one edge rather than a ring of them, and one
 * label rather than one per edge, because a wall is one line however many pieces the walk
 * cuts it into. Every piece of it carries this label, so a face bounded by half a wall still
 * says which wall.
 *
 * @param segment where it runs
 * @param label   which line it is
 */
public record LabelledWall(
    Segment segment,
    int label) {
}
