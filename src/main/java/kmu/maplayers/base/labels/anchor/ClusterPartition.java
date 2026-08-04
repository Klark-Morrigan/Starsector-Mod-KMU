package kmu.maplayers.base.labels.anchor;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;

import java.util.List;
import java.util.Map;

/**
 * The partition one label sweep runs over: which systems form each contiguous cluster, and the
 * cell geometry those clusters were cut from.
 *
 * <p>The four are one fact rather than four inputs. A cluster list says nothing without the
 * cells its members were grouped from, and the sweep reads all four against each other on every
 * cluster - the members index the sites, the sites and edges bound the box, the grouping names
 * the owner. Left apart at the entry point, a caller is free to hand over four halves of
 * different passes, and nothing downstream could tell: a name would be fitted inside a border
 * traced from cells that no longer group that way.
 *
 * <p>It is also the term this seam was missing. A layer supplies one of these and the two
 * resolvers, and gets placements back - which is what makes the contract statable rather than an
 * argument list each caller reproduces.
 *
 * <p>Opaque owners throughout, like the geometry it is cut from, so any layer can partition on
 * whatever it groups by.
 *
 * @param clusterMemberSystemIds each contiguous cluster's member system ids
 * @param edgesByCellId          each cell's raw edges - the geometry lines are clipped against
 * @param siteBySystemId         each system's world position, for the axis fit and the icon
 *                               keep-outs
 * @param grouping               which system each cell draws as, and each system's owner
 */
public record ClusterPartition(
    List<List<String>> clusterMemberSystemIds,
    Map<String, List<CellEdge>> edgesByCellId,
    Map<String, double[]> siteBySystemId,
    CellGrouping grouping) {
}
