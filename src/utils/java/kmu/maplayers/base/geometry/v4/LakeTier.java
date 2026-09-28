package kmu.maplayers.base.geometry.v4;

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
}
