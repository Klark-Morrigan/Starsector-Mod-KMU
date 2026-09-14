package kmu.maplayers.base.geometry;

import kmlib.starsector.systems.SystemKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A whole sector's map geometry, built from a fixture at a given set of knobs.
 *
 * <p>One path from sites to drawable shapes, shared by everything that needs it: the
 * invariant tests assert on it, {@link SectorSvgWriter} draws it, and
 * {@link SectorGeometryViewer} rebuilds it per slider move. Sharing matters more than it
 * looks - three copies of "how the map is assembled" would drift, and then a picture would
 * stop being evidence about what the tests check.
 *
 * <p>Everything is keyed by CELL rather than by system, and the owners travel with
 * the cells rather than being read back off the fixture. Today the two are the same thing:
 * one cell per system, keyed exactly as the fixture says. They stop being the same thing
 * under the frontier's redistribution, where a cell may be an absorbed wedge keyed to a
 * system that is not its own star, or a shard of a dead star's leftover space keyed to
 * neither - cells with no system at all, and keys that are an OUTPUT of the build rather
 * than an input to it. A consumer that reached back to the fixture for a key would then be
 * colouring the map by what went in instead of by what came out, and would draw a
 * confident, wrong picture. Keying by cell keeps that impossible rather than merely
 * unlikely.
 *
 * <p>This is the geometry only. Colour, opacity, hatching, and label placement are decided
 * further downstream against live settings and the sector, so nothing here says how the map
 * LOOKS - only what shape it is.
 *
 * @param cellEdgesByCellKey  each cell, as its adjacency-tagged edges
 * @param ownerByCellKey      the owner per cell; a cell absent from the map is
 *                            unowned, which is what makes it a neutral cell
 * @param shapedCellByCellKey each cell after the border channel is cut inward
 * @param ringsByOwner        each owner's traced cluster rings
 */
public record SectorGeometry(
        Map<SystemKey, List<CellEdge>> cellEdgesByCellKey,
        Map<SystemKey, String> ownerByCellKey,
        Map<SystemKey, ShapedCell> shapedCellByCellKey,
        Map<String, List<List<double[]>>> ringsByOwner) {

    /**
     * Runs the full pipeline: partition the sites, shape each cell, trace each owner.
     *
     * <p>The inset rule is asked for rather than assumed, because the cells' true partition and
     * the channel cut into it are two separate things and the caller is the one that knows
     * which of them it wants to look at. The map itself always ships
     * {@link EdgeInsetRule#AT_EVERY_BORDER}.
     *
     * @param fixture    the sector to build
     * @param insetRule  which cell edges take the border channel
     * @param parameters the knobs to build it under
     * @return the assembled geometry
     */
    public static SectorGeometry buildSectorGeometry(
            SectorFixture fixture,
            EdgeInsetRule insetRule,
            SectorGeometryParameters parameters) {

        var cellEdges = fixture.buildCellEdgesBySystemKey(
            parameters.cellRadius(),
            parameters.boundSegments());

        // The one place the effective keys are decided. The frontier's redistribution pass
        // belongs here, between the partition and the shaping, replacing both the cell set
        // and the keys with what it emits; every consumer downstream then follows without
        // knowing it happened. Today one cell per system, each drawing as its own star, so the
        // draws-as map is identity over the cell ids.
        var grouping = new CellGrouping(
            identityOver(cellEdges.keySet()),
            fixture.getOwnerBySystemKey());

        // The owners re-addressed by cell, so a consumer asking which owner a cell it holds falls
        // under asks under the key it holds that cell by - the grouping's own map is keyed by the
        // system a cell draws as, which is the same key only while every cell is its own star's.
        var owners = mapOwnerByCellKey(cellEdges.keySet(), grouping);
        var shaped = CellShaper.shapeCells(
            cellEdges,
            grouping,
            insetRule,
            parameters.borderInset());

        var rings = new LinkedHashMap<String, List<List<double[]>>>();

        var tolerances = new BorderTraceTolerances(
            parameters.borderInset(),
            parameters.weldTolerance(),
            parameters.miterSpikeLimit());

        for (var group : groupCellKeysByOwner(owners).entrySet()) {
            rings.put(
                group.getKey(),
                traceOwnerRings(group.getValue(), cellEdges, grouping, insetRule, tolerances));
        }
        return new SectorGeometry(cellEdges, owners, shaped, rings);
    }

    // One owner's rings.
    //
    // Its cells fuse into clusters along the edges the rule leaves on their line, so the whole
    // set is traced at once and the shared edges fall away. Under a rule that insets every edge
    // there is a channel along each of them and nothing fuses - so each cell is traced as the
    // single-cell cluster it now is, and comes back as a ring of its own that everything
    // downstream, the smoothing included, treats like any other.
    private static List<List<double[]>> traceOwnerRings(
            List<SystemKey> memberCellKeys,
            Map<SystemKey, List<CellEdge>> cellEdges,
            CellGrouping grouping,
            EdgeInsetRule insetRule,
            BorderTraceTolerances tolerances) {

        if (insetRule.isFusingSharedEdges()) {

            return SystemClusterBorders.traceBorderRings(
                memberCellKeys,
                cellEdges,
                grouping,
                Set.of(),
                insetRule,
                tolerances);
        }

        var rings = new ArrayList<List<double[]>>();

        for (var cellKey : memberCellKeys) {

            rings.addAll(SystemClusterBorders.traceBorderRings(
                List.of(cellKey),
                cellEdges,
                grouping,
                Set.of(),
                insetRule,
                tolerances));
        }
        return rings;
    }

    /**
     * The cells each owner holds, sorted so a failure names the same key run to run and a
     * drawing's layer order does not shift under a map iteration change.
     *
     * @param ownerByCellKey the owner per cell
     * @return member cells per owner
     */
    static Map<String, List<SystemKey>> groupCellKeysByOwner(
            Map<SystemKey, String> ownerByCellKey) {

        var members = new TreeMap<String, List<SystemKey>>();

        for (var entry : ownerByCellKey.entrySet()) {

            members
                .computeIfAbsent(entry.getValue(), key -> new ArrayList<>())
                .add(entry.getKey());
        }
        return members;
    }

    // A map of each cell to itself, so a cell set with one cell per system draws each cell as its
    // own star - the draws-as identity the redistribution pass later replaces with real cells.
    private static Map<SystemKey, SystemKey> identityOver(Set<SystemKey> cellKeys) {

        var identity = new LinkedHashMap<SystemKey, SystemKey>();

        for (var cellKey : cellKeys) {
            identity.put(cellKey, cellKey);
        }
        return identity;
    }

    // Each owned cell's owner, under the key the cells are held by. An unowned cell is left out
    // rather than held under a null, so a consumer reads absence as unowned exactly as it does of
    // the fixture's own map.
    private static Map<SystemKey, String> mapOwnerByCellKey(
            Set<SystemKey> cellKeys,
            CellGrouping grouping) {

        var ownerByCellKey = new LinkedHashMap<SystemKey, String>();

        for (var cellKey : cellKeys) {

            var owner = grouping.resolveOwnerOf(cellKey);
            if (owner != null) {
                ownerByCellKey.put(cellKey, owner);
            }
        }
        return ownerByCellKey;
    }
}
