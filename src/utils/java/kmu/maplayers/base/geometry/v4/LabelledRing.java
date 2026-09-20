package kmu.maplayers.base.geometry.v4;

import java.util.List;

/**
 * A closed ring whose every edge says which line it lies on.
 *
 * <p>What a ring has to carry into the walk for the faces to mean anything afterwards. A face
 * is read by what bounds it - this stretch is a cell's border, that one is a wall - and a walk
 * handed bare points has no way to say so: the corners survive the walk, but which line each
 * edge came from does not, unless it travels with the edge.
 *
 * <p>Entry {@code i} of the labels names the edge leaving vertex {@code i} towards vertex
 * {@code i + 1}, the last wrapping to the first - the same convention KMLib's labelled polygon
 * uses, so a reader of either reads the other.
 *
 * @param vertices   the ring's corners in winding order, without a repeated closing point
 * @param edgeLabels one label per edge, parallel to the vertices
 */
public record LabelledRing(List<double[]> vertices, int[] edgeLabels) {

    /**
     * A ring whose labels were gathered one edge at a time.
     *
     * <p>A walk does not know how many edges a ring has until it closes, so it collects the
     * labels as it goes and only then has a ring. Said here rather than at each walk, because
     * unboxing a list into an array is the sort of line that gets written a little differently
     * every time it is written again.
     *
     * @param vertices   the ring's corners in winding order
     * @param edgeLabels one label per edge, parallel to the vertices
     * @return the ring
     */
    public static LabelledRing ofGatheredLabels(
            List<double[]> vertices, List<Integer> edgeLabels) {

        var labels = new int[edgeLabels.size()];

        for (var index = 0; index < edgeLabels.size(); index++) {
            labels[index] = edgeLabels.get(index);
        }
        return new LabelledRing(List.copyOf(vertices), labels);
    }

    /**
     * @throws IllegalArgumentException when the labels are not parallel to the vertices,
     *                                  since a ring with an edge nobody can name is a ring
     *                                  the walk would label wrongly rather than refuse
     */
    public LabelledRing {

        if (vertices.size() != edgeLabels.length) {
            throw new IllegalArgumentException(
                "one label per edge: " + vertices.size() + " vertices, "
                    + edgeLabels.length + " labels");
        }
    }
}
