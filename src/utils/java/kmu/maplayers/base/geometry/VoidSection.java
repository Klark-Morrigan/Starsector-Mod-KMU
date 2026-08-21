package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * One piece of void, small enough to be decided as a single thing.
 *
 * <p>The unit the whole consolidation works in: a section is owned, shaped and drawn on its own,
 * exactly as a cell is.
 *
 * <p><b>The walls are what make a section, not a later cut.</b> A section is a hole in the union
 * of the cells' reach discs with the bridges and the coast's reaches laid across it. A bridge
 * spans a short gap between two cells that face each other, so laying one shuts the void on its
 * two sides off from each other - and a long corridor of void with bridges across it is
 * therefore already several holes rather than one long one. The coast does the same at the
 * sector's outer edge: smoothing it into a less bubbly shape traps more void against the cell
 * borders and the bridges, and each piece it traps is another hole.
 *
 * <p>So there is nothing to divide afterwards. Cutting a chord across a pocket to break it into
 * cell-sized pieces is a second answer to a question the walls have already answered, and it
 * answers it worse: a chord is a straight line drawn across open space wherever the arithmetic
 * put it, while a bridge lands where two cells actually face each other.
 *
 * <p>It carries the cells it runs on beside its outline because everything asked of a section
 * afterwards is asked of those: which cell each of its edges faces, who has a claim on it, and
 * what it is called. Read back off the outline by whoever needed them, the answer would be
 * recomputed at each site and two of them could disagree about the same piece of void.
 *
 * @param outline its own closed boundary, at the reach that defines the void
 * @param cells   the sites whose circles it runs on, ascending, which are the cells around it
 *                and so the whole of what it can be named or owned by
 * @param kind    which of the two ways it came to be shut in, which is what tells a reader
 *                looking at its name whether to expect it among the cells or out at the edge
 */
record VoidSection(
    List<double[]> outline,
    List<Integer> cells,
    SectionKind kind) {

    // A piece of void with no cell around it is not a piece of void at all, so this is the one
    // thing that cannot be true of a section.
    private static final int MIN_CELLS = 1;

    VoidSection {
        if (cells.size() < MIN_CELLS) {
            throw new IllegalArgumentException(
                "a section runs on at least " + MIN_CELLS + " cell, not " + cells.size());
        }
    }

    /**
     * One hole as the section it is.
     *
     * <p>The cells come off the hole's own ring rather than being read back out of its samples
     * as the nearest site to each. The ring is what the boundary walk actually found - the
     * circles it ran on, in the order it met them - so it is exact, while a reading taken off
     * the samples would let an arc shorter than one sample drop a cell out of the answer or a
     * grazing contact put one in.
     *
     * @param hole the hole
     * @return it as a section
     */
    static VoidSection buildFromHole(VoidHole hole) {

        var cells = new ArrayList<>(hole.ringing());
        cells.sort(Integer::compareTo);

        return new VoidSection(hole.boundary(), List.copyOf(cells), readKind(hole));
    }

    /**
     * The two ways a piece of void comes to be shut in.
     *
     * <p>Told apart by the walls the hole closes on rather than by where it sits, because that
     * is what the difference IS. Both kinds are holes in the same union and both are named and
     * owned the same way; they are separated because a reader switches them on and off
     * separately - the coast is a proposal about the sector's outer shape and the bridges are a
     * proposal about the gaps between cells, and judging either means seeing it without the
     * other.
     */
    enum SectionKind {

        /**
         * Void the cells closed around, on their own or with a bridge across the gap. Inside
         * the sector, among the cells.
         */
        INLAND,

        /**
         * Void a reach of the smoothed coast shut in behind it. At the sector's outer edge,
         * and there only because the coast is drawn where it is.
         */
        COASTAL
    }

    // Coastal when a reach of coast is among the walls it closes on. A bridge is not enough:
    // bridges are laid between cells all over the map, and void one shuts in is inland void
    // that happened to need a wall.
    private static SectionKind readKind(VoidHole hole) {

        for (var wall : hole.walledBy()) {

            if (wall.kind() == DiscUnionBoundary.WallKind.COAST_REACH) {
                return SectionKind.COASTAL;
            }
        }
        return SectionKind.INLAND;
    }
}
