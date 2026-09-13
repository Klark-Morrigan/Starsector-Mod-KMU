package kmu.maplayers.base.hover;

import kmlib.starsector.systems.SystemKey;

import java.util.List;

/**
 * What the cursor is over on a map layer: the one system whose cell covers it, and the whole
 * contiguous cluster that cell belongs to.
 *
 * <p>Two nested scopes rather than one because a map answers a hover at two levels - the cell is
 * the scope a per-system breakdown is about, while the cluster is the scope a cluster-wide highlight
 * reads at. Carried together in one value so every reader sees one consistent pair: a cell and the
 * cluster around it can never be from different frames.
 *
 * <p>"Nothing hovered" is {@link #NONE} rather than a null hover, so a reader tests a flag instead
 * of guarding a dereference.
 *
 * @param hoveredSystemKey        the system under the cursor, or null when it is over no cell -
 *                                the channel between cells, or empty space beyond the map
 * @param clusterMemberSystemKeys every system in that cell's contiguous cluster, the hovered
 *                                cell included; empty when nothing is hovered
 */
public record MapHover(
    SystemKey hoveredSystemKey,
    List<SystemKey> clusterMemberSystemKeys) {

    /** The cursor is over no cell, so nothing highlights and nothing is broken down. */
    public static final MapHover NONE = new MapHover(null, List.of());

    public MapHover {
        clusterMemberSystemKeys = List.copyOf(clusterMemberSystemKeys);
    }

    /**
     * @return whether the cursor is over a cell at all, the one test a reader gates its draw on
     */
    public boolean isHovering() {
        return hoveredSystemKey != null;
    }
}
