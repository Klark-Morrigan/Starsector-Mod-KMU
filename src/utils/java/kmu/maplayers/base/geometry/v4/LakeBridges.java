package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.List;

/**
 * The lines the lake bridges lay: straight runs across a lake from one cell on its coast to
 * another.
 *
 * <p>Found elsewhere and untouched here, for the reason the coast is: which pairs are offered,
 * how each is placed on its two frontages, and which survive the ones already down is the bridge
 * search's whole construction, tuned by eye against the map. This tier decides only where the
 * lines go - into the one partition, as walls, each end carried through the shore by
 * {@link CarriedLines} - so the water a set of them closes off comes back as pieces.
 *
 * <p><b>A bridge lands only on the lake's frontage.</b> Both ends stand where the lake's coast
 * runs along a cell: a stretch of fillet, or the one point where two reaches meet on a cell the
 * coast only touches. Border the coast stands off from, behind a reach, faces water the coast has
 * already captured, and nothing arrives there. The search anchors on the coast's own points, so
 * that holds of every line handed in rather than being checked here.
 */
public final class LakeBridges {

    /**
     * What a lake bridge is labelled with on the pieces it closes.
     *
     * <p>Its own negative, apart from the coast's, because a piece is named by what closed it -
     * and water shut in by a bridge is not water shut in by the coast.
     */
    public static final int THE_LAKE_BRIDGES = -4;

    private LakeBridges() {
    }

    /**
     * Lays every bridge as a wall, each end carried through its shore.
     *
     * @param bridges    the bridges across the lakes, as the search left them
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
