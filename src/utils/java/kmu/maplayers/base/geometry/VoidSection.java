package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One piece of void, small enough to be decided as a single thing.
 *
 * <p>The unit the whole consolidation works in: a section is owned, shaped and drawn on its own,
 * exactly as a cell is.
 *
 * <p><b>The walls are what make a section, not a later cut.</b> A section is a hole in the union
 * of the cells' reach discs with the spans and the coast's reaches laid across it. A span
 * crosses a short gap between two cells that face each other, so laying one shuts the void on
 * its two sides off from each other - and a long corridor of void with spans across it is
 * therefore already several holes rather than one long one. The coast does the same at each
 * continent's edge: smoothing it into a less bubbly shape traps more void against the cell
 * borders and the spans, and each piece it traps is another hole.
 *
 * <p>So there is nothing to divide afterwards. Cutting a chord across a pocket to break it into
 * cell-sized pieces is a second answer to a question the walls have already answered, and it
 * answers it worse: a chord is a straight line drawn across open space wherever the arithmetic
 * put it, while a span lands where two cells actually face each other.
 *
 * <p>It carries the cells it runs on beside its outline because everything asked of a section
 * afterwards is asked of those: which cell each of its edges faces, who has a claim on it, and
 * what it is called. Read back off the outline by whoever needed them, the answer would be
 * recomputed at each site and two of them could disagree about the same piece of void.
 *
 * @param outline its own closed boundary, at the reach that defines the void
 * @param cells   the sites whose circles it runs on, ascending, which are the cells around it
 *                and so the whole of what it can be named or owned by
 * @param kind    which layer of the map shut it in, which is what tells a reader looking at its
 *                name what water to expect it to be
 */
public record VoidSection(
    List<double[]> outline,
    List<Integer> cells,
    SectionKind kind) {

    // A piece of void with no cell around it is not a piece of void at all, so this is the one
    // thing that cannot be true of a section.
    private static final int MIN_CELLS = 1;

    public VoidSection {
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
     * @param hole        the hole
     * @param puddleRings the ring of cells around each puddle the trace found, which is what
     *                    tells a hole the cells closed unaided apart from a lake
     * @return it as a section
     */
    static VoidSection buildFromHole(VoidHole hole, Set<Set<Integer>> puddleRings) {

        var cells = new ArrayList<>(hole.ringing());
        cells.sort(Integer::compareTo);

        return new VoidSection(
            hole.boundary(), List.copyOf(cells), readKind(hole, puddleRings));
    }

    /**
     * The layers of the map a piece of void can have been shut in by, one per fill the map
     * draws.
     *
     * <p>Told apart by the walls the hole closes on rather than by where it sits, because that
     * is what the difference IS. Every kind is a hole in the same union and is named and owned
     * the same way; they are separated because each is a layer a reader switches on and off on
     * its own, and judging one means seeing it without the others.
     */
    public enum SectionKind {

        /** A hole the cells closed unaided and the lake floor judged too small for a shore. */
        PUDDLE,

        /**
         * A hole the cells closed unaided that was drawn a shore. The whole lake, spans and
         * all, where nothing crosses it.
         */
        LAKE,

        /** One piece of a lake a span crossed: what that span and the cells shut in. */
        LAKE_POCKET,

        /** Void a reach of an outer shore shut in behind it, and no span reached first. */
        COASTAL,

        /** A bay an inlet span closed: water inside an outer shore that a span holds. */
        INLET,

        /** The sea a run of links shut in between two continents. */
        INTERCONTINENTAL
    }

    // Read off the walls in the order the map's own fills take precedence, because a hole can
    // close on more than one kind of wall and the fills already decide which of them owns the
    // water: the sea between two continents is walked with the links AND the inlet spans laid
    // and only what a link closed is the sea's; an inlet span's water covers the outer shore's
    // fill where a bay's reach runs into it; and a lake nothing crosses stays the lake shore's.
    //
    // A cell-pair bridge is read as a lake span. It is the same search laid over the cells with
    // no shore consulted, so the water it divides is water the cells closed around, and that
    // is a lake's.
    //
    // A hole with no wall at all was closed by the cells alone, and the trace has already said
    // whether such a hole is a puddle or a lake - it is matched by its ring rather than measured
    // again, so a second floor cannot come to disagree with the first.
    private static SectionKind readKind(VoidHole hole, Set<Set<Integer>> puddleRings) {

        var kinds = EnumSet.noneOf(DiscUnionBoundary.WallKind.class);

        for (var wall : hole.walledBy()) {
            kinds.add(wall.kind());
        }

        if (kinds.contains(DiscUnionBoundary.WallKind.LINK)) {
            return SectionKind.INTERCONTINENTAL;
        }
        if (kinds.contains(DiscUnionBoundary.WallKind.INLET_SPAN)) {
            return SectionKind.INLET;
        }
        if (kinds.contains(DiscUnionBoundary.WallKind.COAST_REACH)) {
            return SectionKind.COASTAL;
        }
        if (kinds.contains(DiscUnionBoundary.WallKind.LAKE_SPAN)
                || kinds.contains(DiscUnionBoundary.WallKind.BRIDGE)) {
            return SectionKind.LAKE_POCKET;
        }
        if (kinds.contains(DiscUnionBoundary.WallKind.PUDDLE_SPAN)) {
            return SectionKind.PUDDLE;
        }
        return puddleRings.contains(Set.copyOf(hole.ringing()))
            ? SectionKind.PUDDLE
            : SectionKind.LAKE;
    }
}
