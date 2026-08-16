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

            var box = computeNameBox(anchor);

            if (!box.isEmpty()) {
                boxes.add(box);
            }
        }
        return boxes;
    }

    /**
     * The box one placement's name occupies.
     *
     * <p>Apart from the list walk above because a reader asking about one name is a real question -
     * whether the room a single placement holds moved between two fits, say - and answering it by
     * walking a one-element list would leave the "what is a name's extent" rule stated in two
     * places, free to drift apart.
     *
     * @param anchor the placement to read
     * @return a closed ring of {x, y} corners, or an empty list where the placement occupies no
     *         room at all - a fit that accepted no line, or one whose block has no girth
     */
    static List<double[]> computeNameBox(ClusterAnchor anchor) {

        return anchor.acceptedAxis() == null
            ? List.of()
            : anchor.acceptedAxis().computeBandCorners(anchor.thickness());
    }
}
