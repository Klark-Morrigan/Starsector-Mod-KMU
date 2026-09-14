package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Bounds;
import kmlib.starsector.systems.SystemKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The room a re-fit moved a map's cluster names into or out of: every box that appeared, vanished
 * or shifted between the placements that were standing and the ones the fit produced.
 *
 * <p>It exists so that whatever keeps clear of the names can be re-laid over the cells the names
 * actually touched rather than over the whole map. A fit reports a list of placements and nothing
 * about which of them are new, so a consumer holding work laid around the previous list has no way
 * to tell a name that moved from one carried over untouched - and answers that by redoing all of
 * it. The comparison is cheap, the names that move in one fit are few, and everything downstream
 * of it is per cell.
 *
 * <p>A placement counts as moved unless it is identical to the one that stood under the same
 * cluster. The whole placement is compared rather than the box alone because a name's extent has
 * more than one reading - the box its fit reserved, and the words it draws inside that box - and
 * the two are read off different components. Comparing the components either reading uses is the
 * same as comparing all of them but for the shade, and a restyled name costing a re-lay of the
 * cells it touches is the safe direction to be wrong in.
 *
 * <p>What is recorded per moved name is the box its fit reserved, at both its old and its new
 * place: the room it gave up matters exactly as much as the room it took, since what is laid
 * around a name is free to reclaim the ring it left. The fitted box is the looser of the two
 * readings and encloses the drawn words, so it is conservative whichever reading the consumer
 * keeps clear of - see {@link ClusterNameBoxes} and {@link kmu.maplayers.base.labels.LabelLineBoxes}.
 */
public final class ClusterNameDisturbance {

    /** A fit that moved no name at all, and so obliges nobody to re-lay anything. */
    public static final ClusterNameDisturbance NONE = new ClusterNameDisturbance(List.of());

    private final List<Bounds> disturbedBounds;

    private ClusterNameDisturbance(List<Bounds> disturbedBounds) {
        this.disturbedBounds = disturbedBounds;
    }

    /**
     * Reads what changed between two fits' placements.
     *
     * @param standingAnchors the placements that were standing before this fit, in any order
     * @param fittedAnchors   the placements this fit produced, in any order
     * @return the room the fit disturbed, {@link #NONE} where it disturbed none
     */
    public static ClusterNameDisturbance compareFittedNames(
            List<ClusterAnchor> standingAnchors,
            List<ClusterAnchor> fittedAnchors) {

        // Copied into a map this walk may consume, since what is left in it once every fitted
        // placement has been matched off is exactly the clusters this fit no longer names - the
        // vanished half, with no second walk to find it.
        var unmatchedStanding = new HashMap<>(ClusterAnchor.mapAnchorsByIdentity(standingAnchors));
        var disturbedBounds = new ArrayList<Bounds>();

        for (var fitted : fittedAnchors) {

            var standing = unmatchedStanding.remove(fitted.identity());

            if (fitted.equals(standing)) {
                continue;
            }
            addNameBounds(disturbedBounds, standing);
            addNameBounds(disturbedBounds, fitted);
        }
        for (var vanished : unmatchedStanding.values()) {
            addNameBounds(disturbedBounds, vanished);
        }
        return disturbedBounds.isEmpty()
            ? NONE
            : new ClusterNameDisturbance(disturbedBounds);
    }

    /** @return whether this fit left every name where it was, so nothing has to be re-laid */
    public boolean isDisturbingNothing() {
        return disturbedBounds.isEmpty();
    }

    /**
     * The cells the moved names reach into.
     *
     * <p>Tested against each cell's own bounding box rather than against the shape inside it,
     * deliberately. A box that meets no cell can be dropped without asking what its name would
     * have covered, while a box that meets one costs only a re-lay that changes nothing - and the
     * two errors are not worth trading evenly, since a cell wrongly skipped keeps whatever it was
     * carrying until something unrelated rebuilds the map.
     *
     * @param ringByCellKey each cell's outline, keyed by the cell it belongs to
     * @return the cells any disturbed name reaches, in the order they were offered
     */
    public List<SystemKey> selectDisturbedCellKeys(Map<SystemKey, List<double[]>> ringByCellKey) {

        if (disturbedBounds.isEmpty()) {
            return List.of();
        }
        var disturbedCellKeys = new ArrayList<SystemKey>();

        for (var cell : ringByCellKey.entrySet()) {

            // A cell with no outline encloses nothing, so no name can reach into it.
            if (!cell.getValue().isEmpty()
                    && isDisturbing(Bounds.computeEnclosingBounds(cell.getValue()))) {
                disturbedCellKeys.add(cell.getKey());
            }
        }
        return disturbedCellKeys;
    }

    // Whether any of the moved names reaches the given extent.
    private boolean isDisturbing(Bounds cellBounds) {

        for (var nameBounds : disturbedBounds) {
            if (cellBounds.overlaps(nameBounds)) {
                return true;
            }
        }
        return false;
    }

    // Records the room one placement holds, where it holds any. Absent placements are accepted
    // rather than guarded against at each call site, since half of every comparison above is a
    // name that did not exist on one side of the fit.
    private static void addNameBounds(List<Bounds> disturbedBounds, ClusterAnchor anchor) {

        if (anchor == null) {
            return;
        }
        var box = ClusterNameBoxes.computeNameBox(anchor);

        if (!box.isEmpty()) {
            disturbedBounds.add(Bounds.computeEnclosingBounds(box));
        }
    }
}
