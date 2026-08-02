package kmu.maplayers.base.render.clusters;

import java.util.ArrayList;
import java.util.List;

/**
 * One connected cluster ready to draw: the ground it fills, and the loops bounding it.
 *
 * <p>The cluster-level sibling of {@link StyledCell}. A cell that fused into a cluster keeps
 * only the seam it contributes to its neighbours; everything drawn once for the whole fused
 * body is here. A cell that fused with nothing needs none of it and carries its own fill and
 * outline instead.
 *
 * <p>One cluster, not one owner's whole holding: an owner in two disjoint bodies has two of
 * these, each with its own geometry, gathered under the one {@link StyledClusterGroup} that
 * carries the paint they share. That is why nothing here is a colour - which cluster a run
 * belongs to is geometry, and what it paints in is not decided per cluster.
 *
 * <p>Geometry arrives pre-flattened: {@code fillTriangles} is a GL_TRIANGLES soup
 * ([x, y, x, y, ...]), so a concave cluster or one with an enclave fills correctly without the
 * emission assuming anything about the boundary; {@code outerLoop} and each of
 * {@code enclaveLoops} is a closed GL_LINE_LOOP run, with any narrow-neck self-crossing already
 * resolved away. The fill is cut from the same loops the boundary strokes, so the two cannot
 * drift apart.
 *
 * @param fillTriangles the solid fill as a GL_TRIANGLES soup; empty when nothing fills
 * @param hatchSegments the hatched fill as a GL_LINES run of diagonals, pre-clipped to the
 *                      hatched ground at build time; empty when this cluster hatches nothing
 * @param outerLoop     the ring enclosing the cluster, as a GL_LINE_LOOP run; empty when
 *                      nothing strokes
 * @param enclaveLoops  the rings cut out of its interior, each its own GL_LINE_LOOP run;
 *                      empty when the cluster encloses no enclave, or when nothing strokes
 */
public record StyledCluster(
    float[] fillTriangles,
    float[] hatchSegments,
    float[] outerLoop,
    List<float[]> enclaveLoops) {

    /**
     * Every loop bounding this cluster, outer first.
     *
     * <p>For a reader that treats the boundary as one set - hit-testing a point against it, or
     * measuring it - rather than one that draws the outer ring and its enclaves differently.
     * The emission does not use it: it strokes both kinds identically and walks the two fields
     * directly, since a per-frame allocation per cluster is what this would cost it.
     *
     * @return the outer loop followed by each enclave loop; empty when nothing strokes
     */
    public List<float[]> listLoops() {
        if (outerLoop.length == 0) {
            return List.of();
        }
        var loops = new ArrayList<float[]>(1 + enclaveLoops.size());
        loops.add(outerLoop);
        loops.addAll(enclaveLoops);
        return loops;
    }
}
