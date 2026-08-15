package kmu.maplayers.base.labels.anchor;

import java.util.ArrayList;
import java.util.List;

/**
 * The room the cluster names were placed into, as world-space boxes: one per name, the box its fit
 * reserved.
 *
 * <p>A placement states where a name sits as a line plus the girth of the block filling it, which
 * is what the fit needed to search with. Anything that has to stay out of a name's way needs the
 * same two read as one shape instead, so that turn is made here rather than by each thing keeping
 * clear.
 *
 * <p>The looser of the two readings of a name's extent, and deliberately so: a box is as long as
 * the chord the search accepted, which is only bounded below by the widest wrapped line, so it
 * reaches past the drawn words by however much the chord beat them. It is the room the placement
 * holds - and the box the diagnostic overlay draws, so what is shown and what is kept clear of are
 * one reading. Where the words as drawn are what matters, that is
 * {@link kmu.maplayers.base.labels.LabelLineBoxes}, measured line by line.
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
