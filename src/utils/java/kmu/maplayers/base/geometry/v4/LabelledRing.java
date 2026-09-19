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
