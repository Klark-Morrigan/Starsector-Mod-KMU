package kmu.maplayers.base.geometry.v4;

import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.List;

/**
 * The lakes' lines: each lake's coast where it cuts across a bay, and the bridges across the
 * water inside it.
 *
 * <p>One tier, laid in substeps - the coast, then the bridges - each under a label of its own so
 * a piece can tell which of them closed it. Both kinds of line are found elsewhere and untouched
 * here: the smoothing is the whole point of a coastline, and which pairs a bridge is offered, how
 * it lands on its two frontages and which bridges survive the ones already down is the search's
 * whole construction, both tuned by eye against the map. What this tier decides is only where
 * the lines go - into the one partition every tier divides, as walls, each end carried through
 * the shore by {@link CarriedLines} - so the water they close off comes back as pieces rather
 * than as fills traced by a second construction.
 *
 * <p><b>Only a coast's reaches are laid.</b> A coast alternates between fillets, which run along
 * one cell's own border, and reaches, which cross open void from one cell to another. Along a
 * fillet the piece's boundary already IS the coast, and laying a line there would lay it within
 * the walk's weld of the shore - where it is welded onto the shore's corners and becomes a
 * partial overlap the walk cannot cut. So a fillet lays nothing and a reach lays one wall. Which
 * step is which is the tracer's own answer, and the reaches arrive already told apart.
 *
 * <p><b>A bridge lands only on the lake's frontage.</b> Both ends stand where the lake's coast
 * runs along a cell: a stretch of fillet, or the one point where two reaches meet on a cell the
 * coast only touches. Border the coast stands off from, behind a reach, faces water the coast
 * has already captured, and nothing arrives there. The search anchors on the coast's own points,
 * so that holds of every bridge handed in rather than being checked here.
 *
 * <p>What is drawn is what is laid - the reaches and the bridges - and not the fillets, which are
 * the shore and are already on screen as the piece's own edge.
 *
 * <p><b>What the tier captures, it says.</b> A coast gives the bay behind each reach to the
 * cells, and a bridge closes the water either side of it into pockets - so a piece on the land
 * side of a reach, or on either side of a bridge, is water nothing more can arrive in, and its
 * shore stops being frontage. Only the tier knows which side of its own lines is which, so the
 * question is asked here rather than read off the labels by whoever needs the answer.
 */
public final class LakeTier {

    /**
     * What a reach of a lake's coast is labelled with on the pieces it closes.
     *
     * <p>Its own negative below the frame's, for the reason the frame has one: a piece is named
     * by what closed it.
     */
    public static final int THE_LAKE_COAST = -3;

    /**
     * What a lake bridge is labelled with on the pieces it closes.
     *
     * <p>Apart from the coast's, because water shut in by a bridge is not water shut in by the
     * coast.
     */
    public static final int THE_LAKE_BRIDGES = -4;

    // How far a corner may sit off a reach and still lie on it, in map units. Far above what the
    // cutting and the rounding weld move a corner by, and far below anything drawn - so a stub,
    // which leaves the reach's end at an angle, is never taken for a stretch of the reach.
    private static final double ON_THE_REACH = 1e-3;

    private LakeTier() {
    }

    /**
     * Lays every reach of the lakes' coasts as a wall, each end carried through its shore.
     *
     * @param reaches    the reaches, already told apart from the fillets
     * @param sites      the cells' own positions, which the stubs run towards
     * @param parameters the knobs the map is drawn under
     * @return the walls to lay, and the reaches to draw
     */
    public static CarriedLines.LaidLines layCoastWalls(
            List<CellGap> reaches,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        return CarriedLines.layCarriedLines(reaches, sites, parameters, THE_LAKE_COAST);
    }

    /**
     * Lays every bridge across the lakes as a wall, each end carried through its shore.
     *
     * @param bridges    the bridges, as the search left them
     * @param sites      the cells' own positions, which the stubs run towards
     * @param parameters the knobs the map is drawn under
     * @return the walls to lay, and the bridges to draw
     */
    public static CarriedLines.LaidLines layBridgeWalls(
            List<CellGap> bridges,
            List<double[]> sites,
            SectorGeometryParameters parameters) {

        return CarriedLines.layCarriedLines(bridges, sites, parameters, THE_LAKE_BRIDGES);
    }

    /**
     * Whether this tier's lines have closed a piece off.
     *
     * <p>Read off the piece's own edges. A piece is walked with itself on the left of every
     * edge, and a reach is handed over with its lake's water on its left - so an edge along a
     * reach that runs against the reach puts the piece on the reach's land side, in the bay the
     * coast gave up. A bridge captures whichever side a piece is on.
     *
     * @param piece   the piece, its edges labelled with what they lie on
     * @param reaches the reaches laid, each running with its lake's water on its left
     * @return true where the piece lies behind a reach or against a bridge
     */
    public static boolean isCaptured(Face piece, List<CellGap> reaches) {

        if (isCapturedAlong(piece.outline(), reaches)) {
            return true;
        }
        for (var hole : piece.holes()) {

            if (isCapturedAlong(hole, reaches)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCapturedAlong(LabelledRing ring, List<CellGap> reaches) {

        var corners = ring.vertices();
        var labels = ring.edgeLabels();

        for (var edge = 0; edge < corners.size(); edge++) {

            var from = corners.get(edge);
            var to = corners.get((edge + 1) % corners.size());

            if (labels[edge] == THE_LAKE_BRIDGES
                    || labels[edge] == THE_LAKE_COAST && isBehindAReach(from, to, reaches)) {

                return true;
            }
        }
        return false;
    }

    // Whether an edge runs along a reach against that reach's direction, which is what puts
    // the piece walked along it on the land side. An edge off every reach - a stub, carried
    // through the shore at an angle - says nothing either way.
    private static boolean isBehindAReach(double[] from, double[] to, List<CellGap> reaches) {

        for (var reach : reaches) {

            if (Segments.computeDistanceToPoint(reach.start(), reach.end(), from) > ON_THE_REACH
                    || Segments.computeDistanceToPoint(reach.start(), reach.end(), to)
                        > ON_THE_REACH) {

                continue;
            }

            var along = (to[0] - from[0]) * (reach.end()[0] - reach.start()[0])
                + (to[1] - from[1]) * (reach.end()[1] - reach.start()[1]);

            return along < 0;
        }
        return false;
    }
}
