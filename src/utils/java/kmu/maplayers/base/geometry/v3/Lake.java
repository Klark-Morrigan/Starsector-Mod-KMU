package kmu.maplayers.base.geometry.v3;

import java.util.List;
import java.util.Set;

/**
 * One inland lake: a hole the cells closed around unaided, drawn the way a coast is.
 *
 * <p>Built ON a coast rather than beside one, because that is what a lake shore is - the
 * same smoothing over the same kind of marks, read from the water's side. What a lake
 * adds is the edge that water actually runs to.
 *
 * @param shore     the lake's coast, selected and placed like any other
 * @param waterEdge the water's true edge: the cells' own arcs around the hole, sampled.
 *                  What the fill runs against - the lake's margin is the water between
 *                  the drawn shore and this edge, and the open water inside the shore is
 *                  left to the backdrop the way the open void outside the sector is
 * @param ringCells the cells whose borders close the water in, as a puddle carries its
 *                  own. What anything laid ACROSS the lake is laid between - and the one
 *                  honest source for it, since a cell can ring a lake without ever
 *                  touching the outer coast, and such a cell appears nowhere else at all
 */
public record Lake(
    Coast shore,
    List<double[]> waterEdge,
    Set<Integer> ringCells) {
}
