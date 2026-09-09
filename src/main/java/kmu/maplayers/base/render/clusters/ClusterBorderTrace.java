package kmu.maplayers.base.render.clusters;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.SystemClusterBorders;
import kmu.settings.KmuMapLabelSettings;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The parameters of one cluster-border ring trace, and the trace itself - the single path every
 * ring a map layer draws, or fits a name inside, comes from. That is what makes "a name is
 * clipped against the rings the player sees" hold by construction: a new trace parameter lands
 * here once and every consumer picks it up together. Agnostic to what a cluster is grouped by;
 * the caller supplies the owners.
 *
 * @param weldTolerance   largest gap between two reports of a shared corner still
 *                        welded into one when chaining the boundary
 * @param miterSpikeLimit the multiple of the border inset past which a sharp
 *                        corner's inset miter is bevelled instead of pointed
 */
public record ClusterBorderTrace(
    double weldTolerance,
    double miterSpikeLimit) {

    // Reads the live trace parameters from the "Map - Dev" tab's "Border tracing" section.
    public static ClusterBorderTrace readFromLunaSettings() {
        return new ClusterBorderTrace(
            KmuMapLabelSettings.getMapBorderWeldTolerance(),
            KmuMapLabelSettings.getMapBorderMiterLimit());
    }

    // Traces one cluster's inset border rings with these parameters and the fixed
    // border channel every trace shares, insetting every boundary edge by that channel.
    public List<List<double[]>> traceRings(
            Collection<String> memberCellIds,
            Map<String, List<CellEdge>> edgesByCellId,
            CellGrouping grouping) {

        return traceRings(memberCellIds, edgesByCellId, grouping, Set.of());
    }

    // Traces one cluster's rings while opting a set of neighbours out of the border channel:
    // the boundary edge shared with any of them insets by nothing, so a cluster traced from
    // the far side of that edge lands on the same line and the two abut exactly. This is how
    // one same-owner body is carved into clusters that meet without a channel opening between
    // them; the channel still applies to every other boundary edge.
    public List<List<double[]>> traceRings(
            Collection<String> memberCellIds,
            Map<String, List<CellEdge>> edgesByCellId,
            CellGrouping grouping,
            Set<String> coincidentNeighbourSystemIds) {

        return SystemClusterBorders.traceBorderRings(
            memberCellIds,
            edgesByCellId,
            grouping,
            coincidentNeighbourSystemIds,
            CellShaper.BORDER_INSET_DISTANCE,
            weldTolerance,
            miterSpikeLimit);
    }
}
