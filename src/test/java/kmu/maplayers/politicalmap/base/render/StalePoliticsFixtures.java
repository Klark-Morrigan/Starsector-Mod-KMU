package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared fixtures for the two suites over a drained batch of stale systems: the re-derive that
 * brings those systems back into step with the sector, and the redraw that follows it.
 *
 * <p>One home for them because both pose their cases over the same arrangement - two adjacent
 * cells, one of which flips - and a square cell or a faction id that drifted between the suites
 * would let the redraw be pinned against geometry the re-derive never sees.
 */
final class StalePoliticsFixtures {

    static final String HEGEMONY = "hegemony";
    static final String TRITACHYON = "tritachyon";

    // The flipping system and the neighbour whose shared edge flips with it. Both seed a cell, so
    // both are redrawable; the third seeds none.
    static final String FLIPPED_SYSTEM = "flipped";
    static final String NEIGHBOUR_SYSTEM = "neighbour";
    static final String CELL_LESS_SYSTEM = "cellless";

    // A system nothing about the flip reaches: sharing no edge with what flipped, so the only
    // things that can oblige its cell are its own facts and a re-fitted name landing on it.
    static final String DISTANT_SYSTEM = "distant";

    // Fixtures only; never instantiated.
    private StalePoliticsFixtures() {
    }

    // A holder of the given faction. Only the faction id is read by what these suites assert, so
    // the shades are one shared placeholder pair - which is also what makes two holders of one
    // faction compare equal, as the re-derive's own no-change test needs them to.
    static DominantHolder buildHolderOf(String factionId) {
        return new DominantHolder(factionId, Color.GRAY, Color.GRAY);
    }

    // The holders of the given systems, keyed by system, for a case stating who holds what before
    // the batch runs.
    static Map<String, DominantHolder> buildHoldersOf(Map<String, String> factionIdBySystemId) {

        var holderBySystemId = new LinkedHashMap<String, DominantHolder>();

        for (var entry : factionIdBySystemId.entrySet()) {
            holderBySystemId.put(entry.getKey(), buildHolderOf(entry.getValue()));
        }
        return holderBySystemId;
    }

    static StarSystemAPI buildSystem(String systemId) {
        return StarSystemFixture.buildSystem(systemId);
    }

    // A sector listing the named systems and nothing else - what a batch resolves its marked
    // systems through. The systems are built before the sector is stubbed, since building a mock
    // inside another stubbing reads to Mockito as an unfinished stub.
    static SectorAPI buildSectorWithSystems(String... systemIds) {

        var systems = new ArrayList<StarSystemAPI>(systemIds.length);

        for (var systemId : systemIds) {
            systems.add(buildSystem(systemId));
        }
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        return sectorMock;
    }

    // Matches the star system carrying the given id, so a stub names the system it answers for
    // rather than the mock instance the fixture happened to build.
    static StarSystemAPI matchSystemArg(String systemId) {
        return argThat(system -> system != null && systemId.equals(system.getId()));
    }

    // A geometry cache holding two adjacent square cells, each drawing as its own star: the shape a
    // batch reads to find a flipped system's neighbours. Mocked rather than built from a sector
    // because only its two lookups are read, and a real partition would make each case depend on
    // the Voronoi build as well as on the batch.
    static CellGeometryCache buildTwoAdjacentCells() {

        var geometryCacheMock = mock(CellGeometryCache.class);

        when(geometryCacheMock.getCellEdgesByCellId())
            .thenReturn(Map.of(
                FLIPPED_SYSTEM,
                buildSquareCellFacing(NEIGHBOUR_SYSTEM, 0),
                NEIGHBOUR_SYSTEM,
                buildSquareCellFacing(FLIPPED_SYSTEM, 100)));

        when(geometryCacheMock.getSystemIdByCellId())
            .thenReturn(Map.of(
                FLIPPED_SYSTEM,
                FLIPPED_SYSTEM,
                NEIGHBOUR_SYSTEM,
                NEIGHBOUR_SYSTEM));

        return geometryCacheMock;
    }

    // A closed four-edge cell offset along x, one edge of which faces the given neighbour while the
    // rest face the cell's own outer reach. Real coordinates because a redraw insets these edges
    // for real; only the adjacency tag is what the re-derive itself reads.
    static List<CellEdge> buildSquareCellFacing(String neighbourSystemId, double offsetX) {
        return List.of(
            new CellEdge(
                offsetX,
                0,
                offsetX + 50,
                0,
                new EdgeTarget.AcrossSystem(neighbourSystemId)),
            new CellEdge(offsetX + 50, 0, offsetX + 50, 50, EdgeTarget.REACH_BOUND),
            new CellEdge(offsetX + 50, 50, offsetX, 50, EdgeTarget.REACH_BOUND),
            new CellEdge(offsetX, 50, offsetX, 0, EdgeTarget.REACH_BOUND));
    }
}
