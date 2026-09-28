package kmu.maplayers.base.geometry.v4;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.SectorGeometryParameters;

import java.util.List;

/**
 * The lines the lake coast lays: each lake's coast where it cuts across a bay.
 *
 * <p>The coast is traced elsewhere and untouched here - the smoothing is the whole point of a
 * coastline, and reinventing it would mean re-tuning it. What this tier adds is only where that
 * line goes: into the one partition every tier divides, as walls, so the water inside it and the
 * bays outside it come back as pieces rather than as fills traced by a second construction.
 *
 * <p><b>Only the reaches are laid.</b> A coast alternates between fillets, which run along one
 * cell's own border, and reaches, which cross open void from one cell to another. Along a fillet
 * the piece's boundary already IS the coast, and laying a line there would lay it within the
 * walk's weld of the shore - where it is welded onto the shore's corners and becomes a partial
 * overlap the walk cannot cut. So a fillet lays nothing, and a reach lays one wall, carried
 * through the shore at each end by {@link CarriedLines}. Which step is which is the tracer's own
 * answer, and the reaches arrive already told apart.
 *
 * <p>What is drawn is what is laid - the reaches - and not the fillets, which are the shore and
 * are already on screen as the piece's own edge.
 */
public final class LakeCoast {

    /**
     * What a reach of a lake's coast is labelled with on the pieces it closes.
     *
     * <p>Its own negative below the frame's, for the reason the frame has one: a piece is named
     * by what closed it, and this is the first line a tier lays.
     */
    public static final int THE_LAKE_COAST = -3;

    private LakeCoast() {
    }

    /**
     * Lays every reach as a wall, each end carried through its shore.
     *
     * @param reaches    the lakes' coast reaches, already told apart from the fillets
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
}
