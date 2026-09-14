package kmu.maplayers.base.sidebar;

/**
 * One screen's sidebar fold: whether that screen's panel rests folded to its docked rail or open. Each
 * screen holds its own, so folding one screen's panel leaves the other's alone rather than driving both
 * through a single shared value. A fold may be persistent (read from and written to the save) or kept only
 * for the session; the implementation decides which, and a consumer reads and writes through this seam
 * without knowing.
 *
 * <p>Only the resting end passes through here. The collapse animation's in-flight progress belongs to the
 * panel's controller and is never offered to a selection, since a half-slid rail is not a choice worth
 * recording - a consumer records a fold once it has settled at an end.
 */
public interface SidebarFoldSelection {

    /**
     * @return whether the panel rests folded to its docked rail; false means it rests open. Answers for a
     *         fold that was never chosen too, each implementation resolving that to its own default
     */
    boolean isRailDocked();

    /**
     * Records the end the panel has settled at. A consumer may call this every frame its panel draws, so an
     * implementation that stores the fold decides for itself whether a given call is worth a write.
     *
     * @param isRailDocked the settled fold: true folded to the rail, false resting open
     */
    void recordFold(boolean isRailDocked);
}
