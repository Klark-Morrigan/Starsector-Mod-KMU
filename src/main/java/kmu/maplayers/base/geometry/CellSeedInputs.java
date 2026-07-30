package kmu.maplayers.base.geometry;

/**
 * The two inputs every cell in a partition is seeded from: how many sides its frontier
 * polygon is cut with, and how far it may reach into empty space.
 *
 * <p>They travel as one value because they share a consequence that neither shares with
 * anything else the partition is built from. A change to either reseeds <em>every</em> cell,
 * so a cache diffing which systems joined or left cannot act on them the same way - it has
 * to discard what it holds and rebuild whole. Passing them as one value means a holder
 * compares one thing to detect that, and a caller cannot update one while leaving the other
 * behind.
 *
 * <p>The reach also sets how far a change at one site can propagate, since two sites farther
 * apart than twice it can never share a cell edge. So the pair is what a partition is
 * measured in, not merely a pair of tuning knobs that happen to be read together.
 *
 * @param boundSegments the number of sides to cut each cell's frontier polygon with; a
 *                      higher count rounds the outer edge of a cell that reaches into
 *                      empty space
 * @param cellRadius    how far a cell may reach into empty space, in world units
 */
public record CellSeedInputs(
        int boundSegments,
        double cellRadius) {
}
