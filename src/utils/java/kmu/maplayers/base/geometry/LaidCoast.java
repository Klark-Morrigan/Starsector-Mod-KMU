package kmu.maplayers.base.geometry;

import java.util.List;

/**
 * One coast, and the walls laid across it, as a single value.
 *
 * <p>Four things that only mean anything together: the coast, the knobs it was traced under,
 * the reaches it offered as walls, and the wall set the walk was actually handed. Anything
 * asking what the walk did with a wall is asking about the wall THAT walk was given - so a
 * reader holding three of these and rebuilding the fourth gets an answer about a map nobody
 * drew, and a caller threading four loose values can pair a coast with walls built from
 * another one and still compile.
 *
 * <p>Its own type rather than a shape nested in whichever class reports on it, because more
 * than one does: what the walls closed, why a reach was refused, and what claims a point are
 * three questions about the same laying.
 *
 * @param traced     the coast, as it was traced
 * @param parameters the knobs it was traced under
 * @param offered    its own straight reaches, as the walls they are offered as
 * @param walls      those reaches together with the bridges, which is what the walk lays
 */
record LaidCoast(
    Coastlines.TracedCoasts traced,
    SectorGeometryParameters parameters,
    List<DiscUnionBoundary.Chord> offered,
    DiscUnionBoundary.Walls walls) {

    /**
     * Lays every wall across a traced coast.
     *
     * @param traced     the coast
     * @param parameters the knobs it was traced under
     * @return the coast with its walls down
     */
    static LaidCoast layCoast(
            Coastlines.TracedCoasts traced,
            SectorGeometryParameters parameters) {

        var offered = CoastPockets.buildCoastWalls(traced);

        return new LaidCoast(
            traced, parameters, offered, CoastPockets.layCoastWalls(traced, offered));
    }

    // The sites the coast was walked against, taken off the coast rather than carried
    // beside it - a second list is a list that can belong to another construction.
    List<double[]> sites() {
        return traced.union().sites();
    }

    // The discs at the cells' own reach, which is where the map's void is defined.
    DiscUnion atCells() {
        return new DiscUnion(sites(), parameters.cellRadius());
    }

    // The discs at the reach a shape is drawn at once the channel is taken out.
    DiscUnion atDrawnReach() {
        return VoidPockets.buildDrawnUnion(sites(), parameters);
    }
}
