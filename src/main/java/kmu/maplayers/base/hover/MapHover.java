package kmu.maplayers.base.hover;

import com.fs.starfarer.api.campaign.StarSystemAPI;

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
 * <p>The live system rides beside its key because the box describing it draws every frame the cursor
 * rests on the cell, and finding a system by key walks every system in the sector. Resolved once
 * where the cursor reaches the cell, it costs one walk per cell visited rather than one per frame.
 *
 * <p>"Nothing hovered" is {@link #NONE} rather than a null hover, so a reader tests a flag instead
 * of guarding a dereference.
 *
 * @param hoveredSystemKey        the system under the cursor, or null when it is over no cell -
 *                                the channel between cells, or empty space beyond the map
 * @param hoveredSystem           the live system that key named when the cursor reached the cell,
 *                                or null when nothing is hovered or the key named no system then
 * @param clusterMemberSystemKeys every system in that cell's contiguous cluster, the hovered
 *                                cell included; empty when nothing is hovered
 */
public record MapHover(
    SystemKey hoveredSystemKey,
    StarSystemAPI hoveredSystem,
    List<SystemKey> clusterMemberSystemKeys) {

    /** The cursor is over no cell, so nothing highlights and nothing is broken down. */
    public static final MapHover NONE = new MapHover(null, null, List.of());

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
