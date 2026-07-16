package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.geometry.CellEdge;
import kmu.maplayers.politicalmap.base.geometry.FrontierSettings;
import kmu.maplayers.politicalmap.base.geometry.SystemClusterBorders;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapStyle;
import kmu.settings.KmuLunaSettings;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The parameters of one cluster-border ring trace, and the trace itself - the single path the
 * territory build and the anchor fit both go through, so "the anchor clips against the rings
 * the player sees" holds by construction: a new trace parameter lands here once and both
 * consumers pick it up together. Agnostic to what a cluster is grouped by; the caller supplies
 * the grouping keys.
 *
 * @param weldTolerance   largest gap between two reports of a shared corner still
 *                        welded into one when chaining the boundary
 * @param miterSpikeLimit the multiple of the border inset past which a sharp
 *                        corner's inset miter is bevelled instead of pointed
 * @param frontier        the pass's frontier snapshot, carried into the trace so an open
 *                        frontier reaches around its unheld star exactly as the fills do
 */
public record PoliticalBorderTrace(
        double weldTolerance,
        double miterSpikeLimit,
        FrontierSettings frontier) {

    // Reads the live trace parameters from the Dev "Border tracing" section, pairing them
    // with the pass's frontier snapshot so the trace offsets under the same snapshot the
    // cell shaping already read once.
    public static PoliticalBorderTrace readFromLunaSettings(FrontierSettings frontier) {
        return new PoliticalBorderTrace(
                KmuLunaSettings.getPoliticalMapBorderWeldTolerance(),
                KmuLunaSettings.getPoliticalMapBorderMiterLimit(),
                frontier);
    }

    // Traces one cluster's inset border rings with these parameters and the fixed
    // border channel every trace shares.
    public List<List<double[]>> traceRings(
            Collection<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, String> groupKeyBySystemId) {
        return SystemClusterBorders.traceBorderRings(
                memberSystemIds,
                edgesBySystemId,
                groupKeyBySystemId,
                PoliticalMapStyle.BORDER_INSET_DISTANCE,
                weldTolerance,
                miterSpikeLimit,
                frontier);
    }
}
