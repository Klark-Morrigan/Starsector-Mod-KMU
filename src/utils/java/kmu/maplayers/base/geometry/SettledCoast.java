package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * The settled coast: the sector's bridges found first and laid as walls, then the coast traced
 * across them.
 *
 * <p>An assembly rather than a trace of its own. Which walls a coast is traced against is the
 * one thing the settled construction decides, and it decides it in an order - bridges from the
 * cell geometry alone, then the line - that the per-continent construction reverses. Held apart
 * from the walk so the walk stays order-free: it takes walls and knows nothing of where they
 * came from, and the two constructions assemble it either way round without either leaking its
 * ordering into the other.
 */
public final class SettledCoast {

    private SettledCoast() {
    }

    /**
     * Traces a whole sector's coast with its bridges laid.
     *
     * <p>All this decides is the wall set - find the bridges and lay them as chords at the
     * border channel. Everything else about building a coast lives once, in
     * {@link Coastlines#traceCoastsAcrossWalls}, where no entry can come to differ from another.
     *
     * @param sites      the sites
     * @param parameters the knobs the cells are built under
     * @param rules      the knobs the coast is traced under
     * @return the coast, and what it was traced against
     */
    public static Coastlines.TracedCoasts traceAcrossBridges(
            List<double[]> sites,
            SectorGeometryParameters parameters,
            Coastlines.CoastRules rules) {

        return Coastlines.traceCoastsAcrossWalls(
            sites,
            parameters,
            rules,
            new DiscUnionBoundary.Walls(
                DiscUnionBoundary.buildChordsFrom(VoidBridges.findVoidBridges(
                    sites,
                    parameters.cellRadius(),
                    parameters.cellRadius() * rules.bridgeReachMultiple())),
                parameters.borderInset()));
    }
}
