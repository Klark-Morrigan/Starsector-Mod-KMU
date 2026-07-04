package kmu.politicalmap.render;

import kmu.politicalmap.domain.geometry.CellEdge;
import kmu.politicalmap.domain.geometry.SystemClusterBorders;
import kmu.politicalmap.domain.politics.DominantOwner;
import kmu.settings.KmuLunaSettings;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * The parameters of one national-border ring trace, and the trace itself - the single
 * path the territory build ({@link DrawablesBuilder}) and the anchor fit
 * ({@link ClusterAnchorsBuilder}) go through, so "the anchor clips against the rings
 * the player sees" holds by construction: a new trace parameter lands here once and
 * both consumers pick it up together.
 *
 * @param weldTolerance   largest gap between two reports of a shared corner still
 *                        welded into one when chaining the boundary
 * @param miterSpikeLimit the multiple of the border inset past which a sharp
 *                        corner's inset miter is bevelled instead of pointed
 */
record BorderTrace(double weldTolerance, double miterSpikeLimit) {

    // Reads the live trace parameters from the Dev "Border tracing" section.
    static BorderTrace readFromSettings() {
        return new BorderTrace(
                KmuLunaSettings.getPoliticalMapBorderWeldTolerance(),
                KmuLunaSettings.getPoliticalMapBorderMiterLimit());
    }

    // Traces one cluster's inset border rings with these parameters and the fixed
    // border channel every trace shares.
    List<List<double[]>> traceRings(Collection<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId,
            Map<String, DominantOwner> ownerBySystemId) {
        return SystemClusterBorders.traceBorderRings(memberSystemIds, edgesBySystemId,
                ownerBySystemId, PoliticalMapStyle.BORDER_INSET_DISTANCE,
                weldTolerance, miterSpikeLimit);
    }
}
