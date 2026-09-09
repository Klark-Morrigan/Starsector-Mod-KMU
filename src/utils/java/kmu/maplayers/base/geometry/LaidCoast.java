package kmu.maplayers.base.geometry;

import java.util.ArrayList;
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
 * @param walls      those reaches together with every span laid across the water, which is what
 *                   the walk lays
 */
public record LaidCoast(
    Coastlines.TracedCoasts traced,
    SectorGeometryParameters parameters,
    List<DiscUnionBoundary.Chord> offered,
    DiscUnionBoundary.Walls walls) {

    /**
     * Lays every wall across a traced coast.
     *
     * <p>The spans come in rather than off the coast because a coast does not know them. A
     * construction that finds its walls first carries them on the trace; one that traces first
     * and spans the water afterwards has them nowhere but in the caller's hand, and a walk that
     * read only the trace would give that map a set of sections its own spans do not divide.
     *
     * @param traced     the coast
     * @param spans      the lines laid across the water afterwards, as the walls they become -
     *                   each still saying which water it crossed, since that is what a hole it
     *                   closes is read as
     * @param parameters the knobs it was traced under
     * @return the coast with its walls down
     */
    public static LaidCoast layCoast(
            Coastlines.TracedCoasts traced,
            List<DiscUnionBoundary.Chord> spans,
            SectorGeometryParameters parameters) {

        var offered = CoastPockets.buildCoastWalls(traced);
        var laid = new ArrayList<>(spans);
        laid.addAll(offered);

        return new LaidCoast(
            traced,
            parameters,
            offered,
            CoastPockets.layCoastWalls(traced, laid, parameters.borderInset()));
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
