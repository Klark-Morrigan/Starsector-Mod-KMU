package kmu.maplayers.politicalmap.base.hover;

import java.util.List;

/**
 * What the cursor is over on the political map: the one system whose cell covers it, and the whole
 * contiguous territory that cell belongs to.
 *
 * <p>Two nested scopes rather than one because the map answers a hover at two levels - the cell is
 * the scope a per-system breakdown is about, while the cluster is the scope a territorial highlight
 * reads at. Carried together in one value so every reader sees one consistent pair: a cell and the
 * cluster around it can never be from different frames.
 *
 * <p>"Nothing hovered" is {@link #NONE} rather than a null hover, so a reader tests a flag instead
 * of guarding a dereference.
 *
 * <p>The star-icon flag rides along in the same value so a reader gates on it and the hovered cell
 * as one consistent pair: the tooltip both names the hovered system and steps aside when the cursor
 * is on that system's star icon, and a torn read could show the box for one frame's cell while
 * suppressing on another's. The highlight ignores the flag by design - it covers the whole cell,
 * icon included - so an icon-agnostic hover leaves it false.
 *
 * @param hoveredSystemId          the system under the cursor, or null when it is over no cell -
 *                                 the channel between cells, or empty space beyond the map
 * @param clusterMemberSystemIds   every system in that cell's contiguous territory, the hovered
 *                                 cell included; empty when nothing is hovered
 * @param isOverStarIcon           whether the cursor is directly on the hovered system's star icon,
 *                                 the gate that hands the icon back to the vanilla star tooltip
 */
public record PoliticalMapHover(
        String hoveredSystemId, List<String> clusterMemberSystemIds, boolean isOverStarIcon) {

    /** The cursor is over no cell, so nothing highlights and nothing is broken down. */
    public static final PoliticalMapHover NONE = new PoliticalMapHover(null, List.of());

    public PoliticalMapHover {
        clusterMemberSystemIds = List.copyOf(clusterMemberSystemIds);
    }

    /**
     * A hover carrying no icon-gate verdict, for a caller with no notion of it - the highlight,
     * which lights the whole cell, and the {@link #NONE} sentinel.
     *
     * @param hoveredSystemId        the system under the cursor, or null when over no cell
     * @param clusterMemberSystemIds the systems in that cell's contiguous territory
     */
    public PoliticalMapHover(String hoveredSystemId, List<String> clusterMemberSystemIds) {
        this(hoveredSystemId, clusterMemberSystemIds, false);
    }

    /**
     * @return whether the cursor is over a cell at all, the one test a reader gates its draw on
     */
    public boolean isHovering() {
        return hoveredSystemId != null;
    }
}
