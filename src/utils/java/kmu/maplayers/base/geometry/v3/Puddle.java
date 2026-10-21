package kmu.maplayers.base.geometry.v3;

import java.util.List;
import java.util.Set;

/**
 * One puddle: a hole the floor judged too small to be a lake.
 *
 * <p>No shore, for either of the two reasons water can end up without one: too little of it
 * to deserve a drawn line, or too little room to place one through. Both leave water that
 * still has to be filled, so both arrive here rather than being dropped.
 *
 * <p>It carries only what filling it up needs: the water itself, and the cells that ring it -
 * which is what a bridge across it is laid between.
 *
 * @param waterEdge the water, as the cells' own arcs around the hole, sampled
 * @param ringCells the cells whose borders make that edge. A set, because the only thing
 *                  ever asked of it is whether a given cell is in it - a bridge belongs to
 *                  a puddle when both its cells ring that puddle - and the walk order it
 *                  would otherwise carry is order nothing reads
 */
public record Puddle(
    List<double[]> waterEdge,
    Set<Integer> ringCells) {
}
