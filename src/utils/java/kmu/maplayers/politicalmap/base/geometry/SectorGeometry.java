package kmu.maplayers.politicalmap.base.geometry;

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
 * <p>Everything is keyed by CELL rather than by system, and the grouping keys travel with
 * the cells rather than being read back off the fixture. Today the two are the same thing:
 * one cell per system, keyed exactly as the fixture says. They stop being the same thing
 * under the frontier's redistribution, where a cell may be an absorbed wedge keyed to an
 * owner that is not its own star, or a shard of a dead star's leftover space keyed to
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
 * @param cellEdgesByCellId  each cell, as its adjacency-tagged edges
 * @param groupKeyByCellId   the grouping key per cell; a cell absent from the map is
 *                           ungrouped, which is what makes it neutral ground
 * @param shapedCellByCellId each cell after the border channel is cut inward
 * @param ringsByBlocId      each bloc's traced cluster rings, keyed by grouping key
 */
record SectorGeometry(
        Map<String, List<CellEdge>> cellEdgesByCellId,
        Map<String, String> groupKeyByCellId,
        Map<String, ShapedCell> shapedCellByCellId,
        Map<String, List<List<double[]>>> ringsByBlocId) {

    /**
     * Runs the full pipeline: partition the sites, shape each cell, trace each bloc.
     *
     * @param fixture    the sector to build
     * @param parameters the knobs to build it under
     * @return the assembled geometry
     */
    static SectorGeometry buildSectorGeometry(
            SectorFixture fixture, SectorGeometryParameters parameters) {
        var cellEdges = fixture.buildCellEdgesBySystemId(
                parameters.cellRadius(), parameters.boundSegments());
        // The one place the effective keys are decided. The frontier's redistribution pass
        // belongs here, between the partition and the shaping, replacing both the cell set
        // and the keys with what it emits; every consumer downstream then follows without
        // knowing it happened. Today one cell per system, each drawing as its own star, so the
        // draws-as map is identity over the cell ids.
        var grouping = new CellGrouping(
                identityOver(cellEdges.keySet()),
                fixture.getGroupKeyBySystemId());
        var groupKeys = fixture.getGroupKeyBySystemId();
        var shaped = CellShaper.shapeCells(cellEdges, grouping, parameters.borderInset());
        var rings = new LinkedHashMap<String, List<List<double[]>>>();
        for (var bloc : groupCellIdsByBloc(groupKeys).entrySet()) {
            rings.put(bloc.getKey(), SystemClusterBorders.traceBorderRings(
                    bloc.getValue(),
                    cellEdges,
                    grouping,
                    Set.of(),
                    parameters.borderInset(),
                    parameters.weldTolerance(),
                    parameters.miterSpikeLimit()));
        }
        return new SectorGeometry(cellEdges, groupKeys, shaped, rings);
    }

    /**
     * The cells each bloc holds, sorted so a failure names the same bloc run to run and a
     * drawing's layer order does not shift under a map iteration change.
     *
     * @param groupKeyByCellId the grouping key per cell
     * @return member cell ids per grouping key
     */
    static Map<String, List<String>> groupCellIdsByBloc(Map<String, String> groupKeyByCellId) {
        var members = new TreeMap<String, List<String>>();
        for (var entry : groupKeyByCellId.entrySet()) {
            members.computeIfAbsent(entry.getValue(), key -> new ArrayList<>()).add(entry.getKey());
        }
        return members;
    }

    // A map of each id to itself, so a cell set with one cell per system draws each cell as its
    // own star - the draws-as identity the redistribution pass later replaces with real cells.
    private static Map<String, String> identityOver(Set<String> ids) {
        var identity = new LinkedHashMap<String, String>();
        for (var id : ids) {
            identity.put(id, id);
        }
        return identity;
    }
}
