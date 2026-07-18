package kmu.maplayers.politicalmap.base.geometry;

import java.util.List;
import java.util.Map;

/**
 * The cell set and grouping the frontier redistribution pass hands to the shaping layer:
 * the same shape a raw partition takes, so every downstream consumer reads it without
 * knowing a pass ran.
 *
 * <p>The pass leaves owned and deep-empty cells exactly as it found them and rewrites each
 * frontier-empty cell into its parts - the wedges an owner absorbs, the keep-out pocket, and
 * the space capped beyond an owner's reach. Those parts are cells with no star of their own:
 * an absorbed wedge draws as the owner whose ground it joins, a pocket or capped shard as
 * nobody. So the draws-as map is an OUTPUT here rather than the identity it was going in, and
 * the grouping keys travel with the emitted cells rather than being read back off the sites.
 *
 * <p>The per-system grouping keys themselves are untouched - the pass moves ground between
 * cells, it does not change who owns a star - so {@code grouping.groupKeyBySystemId()} is the
 * one the partition came in with, paired here with the rewritten draws-as map.
 *
 * @param cellEdgesByCellId each emitted cell, as its adjacency-tagged edges in winding order
 * @param grouping          which system each emitted cell draws as, over the unchanged
 *                          per-system grouping keys
 */
public record RedistributedCells(
        Map<String, List<CellEdge>> cellEdgesByCellId,
        CellGrouping grouping) {
}
