package kmu.maplayers.base.geometry;

import java.util.ArrayList;
import java.util.List;

/**
 * The void the links between the continents shut in, as shapes to draw.
 *
 * <p><b>A link holds nothing on its own, and a run of them holds a sea.</b> One line between two
 * shapes joins them and closes nothing; the moment a second link joins the same two, the void
 * between them is ringed - link, one continent's cells, link, the other's - and what is inside
 * that ring is water as much as a bay behind an inlet span is. So the fill is not a second way of
 * drawing the links: it is the one thing they say that the lines themselves cannot, which is how
 * much of the sector they took in.
 *
 * <p><b>Laid against what is already down, because that is what the void is divided by.</b> The
 * cells' borders are the walk's own, the links are the subject, and the inlet spans are the
 * remaining lines a sea between two continents can come to rest against - a bay a span has
 * already closed is that span's water, and left out of the walk the sea would run into it and be
 * painted twice. The lake and puddle spans are deliberately not laid: both stand over water the
 * cells closed around unaided, which nothing coming off the open void can reach without crossing
 * a cell, so laying them would add walls no hole here could ever meet.
 *
 * <p><b>The coast reaches are not laid either, and the reason is the channel rather than the
 * shape.</b> A wall holds both its sides half a channel off its own line, so a reach laid as a
 * wall holds the sea back along every reach as well. That strip is a whole channel wide and
 * nothing draws it, so the fill would stand off at each reach and run flush along the cell arcs
 * between them - a notched edge rather than a filled sea. Left out, the fill instead runs into
 * the notches the smoothing cut across, which is water the coast's own fill holds and is painted
 * in the same colour: an overlap that costs nothing, against a gap that shows.
 *
 * <p><b>Only what a link closed.</b> The walk hands back every hole it finds, because the winding
 * is what says whether anything was shut in and it can only say so about boundary that is
 * actually there - a wall left out strings two silhouettes together instead of closing a ring.
 * But a hole an inlet span closed is the inlet fill's, and one the cells closed unaided is a lake
 * or a puddle with a layer of its own; drawn from here too they would be painted twice, and go on
 * being painted with their own switches off. Read off {@link VoidHole#walledBy}, which is the
 * walk's record of what each hole came to rest against.
 *
 * <p><b>The links are offered to the walk first.</b> Two walls leaving one cell within a channel
 * of each other compete for the mouth, and the first one laid takes it. A link crowded out is a
 * ring left open and so a whole pocket missing; an inlet span crowded out only lets this sea run
 * on into a bay that is drawn as water anyway. So the subject goes first, and what it costs is
 * the lesser of the two.
 */
public final class IntercontinentalPockets {

    private IntercontinentalPockets() {
    }

    /**
     * Finds the water the links shut in between the continents.
     *
     * @param traced     the continent coasts, which carry the sites everything here is measured
     *                   against. Taken off the trace rather than handed in beside it, so the
     *                   cells a link names are the cells the walk is walked over
     * @param links      the links, as {@link IntercontinentalBridges} laid them
     * @param standing   the spans already down that a sea can come to rest against, which are the
     *                   inlet spans the links were themselves judged against
     * @param parameters the knobs the cells are built under, which are also what the arcs are
     *                   flattened onto
     * @param shaping    how much of the channel each pocket gives up against the cells
     * @return one outline per pocket a link helped close
     */
    public static List<List<double[]>> findLinkWalledPockets(
            Coastlines.TracedCoasts traced,
            List<CellGap> links,
            List<CellGap> standing,
            SectorGeometryParameters parameters,
            VoidPockets.PocketShaping shaping) {

        if (links.isEmpty()) {
            return List.of();
        }

        // The same chord instances the walk is handed, so a hole's walls are asked about by the
        // walls themselves rather than by a set built afterwards to look like them.
        var linkWalls = DiscUnionBoundary.buildChordsFrom(links);
        var laid = new ArrayList<>(linkWalls);

        laid.addAll(DiscUnionBoundary.buildChordsFrom(standing));

        var walls = new DiscUnionBoundary.Walls(laid, parameters.borderInset());
        var outlines = new ArrayList<List<double[]>>();

        for (var hole : WalledVoid.traceVoidAcrossWalls(
                traced.union().sites(), walls, parameters, shaping)) {

            if (!WalledVoid.findClosingWalls(hole, linkWalls).isEmpty()) {
                outlines.add(hole.boundary());
            }
        }
        return List.copyOf(outlines);
    }
}
