package kmu.maplayers.base.labels.anchor;

import java.util.ArrayList;
import java.util.List;

/**
 * The room the drawn cluster names take up on the map, as world-space boxes.
 *
 * <p>A placement states where a name sits as a line plus the girth of the block filling it, which
 * is what the fit needed to search with. Anything that has to stay out of a name's way needs the
 * same two read as one shape instead - the oriented rectangle the words actually cover - so that
 * turn is made here, once, rather than by each thing keeping clear.
 *
 * <p>The boxes are the whole map's, not any one cluster's, because a name sits wherever its
 * cluster is roomiest and that can be over a neighbour: a box is a fact about the plane, and
 * whoever it belongs to says nothing about who it is in the way of.
 */
public final class ClusterNameBoxes {

    // Derives only; never instantiated.
    private ClusterNameBoxes() {
    }

    /**
     * The boxes the given placements' names occupy.
     *
     * @param anchors the placements to read, in any order
     * @return one closed ring of {x, y} corners per name that occupies any room. A placement
     *         whose fit collapsed - no line accepted, or no girth to the block - contributes
     *         none, since nothing is drawn for it to be in the way of
     */
    public static List<List<double[]>> listNameBoxes(List<ClusterAnchor> anchors) {

        var boxes = new ArrayList<List<double[]>>(anchors.size());

        for (var anchor : anchors) {

            if (anchor.acceptedAxis() == null) {
                continue;
            }
            var corners = anchor.acceptedAxis().computeBandCorners(anchor.thickness());

            if (!corners.isEmpty()) {
                boxes.add(corners);
            }
        }
        return boxes;
    }
}
