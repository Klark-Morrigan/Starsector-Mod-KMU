package kmu.politicalmap.domain.geometry;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmlib.math.geometry.VoronoiCellBuilder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapGeometryCache}: the first update builds cells for the
 * reachable systems and skips inaccessible ones; a later update rebuilds only the
 * cells near a changed system, leaving distant ones in place (asserted by object
 * identity); and a removed system's cell is dropped.
 */
final class PoliticalMapGeometryCacheTest {
    // Comfortably beyond 2 * MAX_CELL_RADIUS (8000), so changes near one do not
    // reach the other.
    private static final float FAR = 20_000f;

    // The frontier resolution cells are seeded at unless the tuning knob changes it;
    // the access-diff tests all run at this one count so a rebuild is driven only by
    // the reachable set, never by a resolution change.
    private static final int DEFAULT_BOUND_SEGMENTS = VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS;

    // No movers in the access-diff tests: an empty moving set makes every drawn system
    // participate in the partition. The exclusion tests pass an explicit set to drop
    // one system.
    private static final Set<String> NO_MOVING_SYSTEMS = Set.of();

    @Nested
    class UpdateFromSector {

        @Test
        void updateBuildsCellsForReachableSystemsAndSkipsInaccessibleOnes() {
            var cache = new PoliticalMapGeometryCache();

            updateAtDefaultResolution(cache,
                    accessibleSystem("a", 0, 0),
                    accessibleSystem("b", 4000, 0),
                    inaccessibleSystem("hidden", 8000, 0));

            assertThat(cache.getCellEdgesBySystemId()).containsOnlyKeys("a", "b");
            assertThat(cache.getCellEdgesBySystemId().get("a")).isNotEmpty();
        }

        @Test
        void updateLeavesDistantCellsUntouchedWhenASystemIsAdded() {
            var cache = new PoliticalMapGeometryCache();
            updateAtDefaultResolution(cache, accessibleSystem("a", 0, 0), accessibleSystem("b", FAR, 0));
            var distantBefore = cache.getCellEdgesBySystemId().get("b");

            // Add a system next to "a"; "b" is far away, so its cell must be the
            // very same object - proof it was not recomputed.
            updateAtDefaultResolution(cache,
                    accessibleSystem("a", 0, 0),
                    accessibleSystem("b", FAR, 0),
                    accessibleSystem("c", 100, 0));

            assertThat(cache.getCellEdgesBySystemId()).containsKey("c");
            assertThat(cache.getCellEdgesBySystemId().get("b")).isSameAs(distantBefore);
        }

        @Test
        void updateReseedsEveryCellWhenTheFrontierResolutionChanges() {
            var cache = new PoliticalMapGeometryCache();
            cache.updateFromSector(
                    sectorOf(accessibleSystem("a", 0, 0), accessibleSystem("b", FAR, 0)),
                    NO_MOVING_SYSTEMS, DEFAULT_BOUND_SEGMENTS);
            var distantBefore = cache.getCellEdgesBySystemId().get("b");

            // The segment count seeds every cell's frontier polygon, so lowering it
            // invalidates all cells even where the reachable set is identical: the
            // distant cell must be a fresh object, not the untouched one an access
            // diff would leave. A lone bounded cell keeps one edge per seed segment,
            // so its edge count drops to the new count.
            cache.updateFromSector(
                    sectorOf(accessibleSystem("a", 0, 0), accessibleSystem("b", FAR, 0)),
                    NO_MOVING_SYSTEMS, 24);

            assertThat(cache.getCellEdgesBySystemId().get("b")).isNotSameAs(distantBefore);
            assertThat(cache.getCellEdgesBySystemId().get("b")).hasSize(24);
        }

        @Test
        void updateDropsTheCellOfASystemThatLosesAccess() {
            var cache = new PoliticalMapGeometryCache();
            updateAtDefaultResolution(cache, accessibleSystem("a", 0, 0), accessibleSystem("b", FAR, 0));

            updateAtDefaultResolution(cache, accessibleSystem("a", 0, 0));

            assertThat(cache.getCellEdgesBySystemId()).containsOnlyKeys("a");
        }

        @Test
        void updateBuildsTheAdjacencyGraphTaggingNeighboursAcrossSharedEdges() {
            var cache = new PoliticalMapGeometryCache();

            // Two close systems share a Voronoi edge, so each cell must name the
            // other across exactly that edge.
            updateAtDefaultResolution(cache,
                    accessibleSystem("a", 0, 0),
                    accessibleSystem("b", 1000, 0));

            assertThat(neighboursOf(cache, "a")).containsExactly("b");
            assertThat(neighboursOf(cache, "b")).containsExactly("a");
        }

        @Test
        void updateMarksAFrontierEdgeWithNoNeighbour() {
            var cache = new PoliticalMapGeometryCache();

            // A lone system has no neighbour to share an edge with, so every edge
            // is a frontier into empty space - a null neighbour id.
            updateAtDefaultResolution(cache, accessibleSystem("a", 0, 0));

            assertThat(cache.getCellEdgesBySystemId().get("a"))
                    .isNotEmpty()
                    .allSatisfy(edge -> assertThat(edge.neighbourSystemId()).isNull());
        }

        @Test
        void updateDropsTheAdjacencyOfASystemThatLosesAccess() {
            var cache = new PoliticalMapGeometryCache();
            updateAtDefaultResolution(cache, accessibleSystem("a", 0, 0), accessibleSystem("b", FAR, 0));

            updateAtDefaultResolution(cache, accessibleSystem("a", 0, 0));

            assertThat(cache.getCellEdgesBySystemId()).containsOnlyKeys("a");
        }

        @Test
        void updateSeedsAnUnreachableSystemHoldingARevealedDecivilisedPlanet() {
            // No jump point, so the access rule rejects it, but the revealed ruin
            // makes it inhabited - it must still seed a cell.
            var cache = new PoliticalMapGeometryCache();

            updateAtDefaultResolution(cache,
                    accessibleSystem("a", 0, 0),
                    decivilisedUnreachableSystem("ruin", 4000, 0));

            assertThat(cache.getCellEdgesBySystemId()).containsOnlyKeys("a", "ruin");
        }

        @Test
        void updateExcludesAMovingSystemAndReshapesItsNeighbourButNotDistantCells() {
            var cache = new PoliticalMapGeometryCache();
            updateAtDefaultResolution(cache,
                    accessibleSystem("m", 0, 0),
                    accessibleSystem("n", 2000, 0),
                    accessibleSystem("f", FAR, 0));
            var neighbourBefore = cache.getCellEdgesBySystemId().get("n");
            var distantBefore = cache.getCellEdgesBySystemId().get("f");

            // "m" starts moving, so it drops out of the partition: it seeds no cell, and
            // "n" (within a neighbourhood radius) reshapes to reclaim its space, while
            // "f" is beyond reach and keeps the very same cell object.
            updateExcluding(cache, Set.of("m"),
                    accessibleSystem("m", 0, 0),
                    accessibleSystem("n", 2000, 0),
                    accessibleSystem("f", FAR, 0));

            assertThat(cache.getCellEdgesBySystemId()).doesNotContainKey("m");
            assertThat(cache.getCellEdgesBySystemId().get("n")).isNotSameAs(neighbourBefore);
            assertThat(cache.getCellEdgesBySystemId().get("f")).isSameAs(distantBefore);
        }

        @Test
        void updateReturnsAStoppedSystemToThePartition() {
            var cache = new PoliticalMapGeometryCache();
            updateAtDefaultResolution(cache, accessibleSystem("m", 0, 0), accessibleSystem("n", 2000, 0));
            updateExcluding(cache, Set.of("m"),
                    accessibleSystem("m", 0, 0), accessibleSystem("n", 2000, 0));

            // "m" comes to rest, so it is no longer a mover and rejoins the partition
            // with a fresh cell.
            updateAtDefaultResolution(cache, accessibleSystem("m", 0, 0), accessibleSystem("n", 2000, 0));

            assertThat(cache.getCellEdgesBySystemId()).containsKey("m");
            assertThat(cache.getCellEdgesBySystemId().get("m")).isNotEmpty();
        }

        @Test
        void updateExcludingAnIsolatedSystemLeavesDistantCellsUntouched() {
            var cache = new PoliticalMapGeometryCache();
            updateAtDefaultResolution(cache, accessibleSystem("m", 0, 0), accessibleSystem("f", FAR, 0));
            var distantBefore = cache.getCellEdgesBySystemId().get("f");

            // "m" starts moving but has no neighbour within a neighbourhood radius, so
            // its removal touches only itself; "f" keeps the same cell object.
            updateExcluding(cache, Set.of("m"),
                    accessibleSystem("m", 0, 0), accessibleSystem("f", FAR, 0));

            assertThat(cache.getCellEdgesBySystemId()).doesNotContainKey("m");
            assertThat(cache.getCellEdgesBySystemId().get("f")).isSameAs(distantBefore);
        }
    }

    // The distinct neighbouring system ids one cell names across its edges,
    // dropping the null frontier markers - the adjacency the merge step reads.
    private static Set<String> neighboursOf(PoliticalMapGeometryCache cache, String systemId) {
        var neighbours = new LinkedHashSet<String>();
        for (var edge : cache.getCellEdgesBySystemId().get(systemId)) {
            if (edge.neighbourSystemId() != null) {
                neighbours.add(edge.neighbourSystemId());
            }
        }
        return neighbours;
    }

    // Runs an update at the default frontier resolution with no movers, the count
    // cells stay at unless the tuning knob changes it. The access-diff tests use this
    // so the only thing that drives a rebuild is the reachable set.
    private static void updateAtDefaultResolution(PoliticalMapGeometryCache cache,
            StarSystemAPI... systems) {
        cache.updateFromSector(sectorOf(systems), NO_MOVING_SYSTEMS, DEFAULT_BOUND_SEGMENTS);
    }

    // Runs an update at the default frontier resolution with the named systems
    // excluded from the partition as movers. The exclusion tests use this to drop a
    // system: it seeds no cell and clips no neighbour.
    private static void updateExcluding(PoliticalMapGeometryCache cache,
            Set<String> movingSystemIds, StarSystemAPI... systems) {
        cache.updateFromSector(sectorOf(systems), movingSystemIds, DEFAULT_BOUND_SEGMENTS);
    }

    private static SectorAPI sectorOf(StarSystemAPI... systems) {
        // The hyperspace mock is built before the getHyperspace() stubbing so its
        // own stubbing is not nested inside this one.
        var hyperspaceMock = hyperspaceWithVisibleStarAnchorsFor(systems);
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systems));
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    // A visible (untagged) star anchor leading into each system, so a system with
    // a jump point reads as map-visible. A cut-off or jump-point-less system stays
    // off the map regardless, so its anchor cannot admit it.
    private static LocationAPI hyperspaceWithVisibleStarAnchorsFor(StarSystemAPI... systems) {
        var anchors = new ArrayList<JumpPointAPI>();
        for (var system : systems) {
            var anchorMock = mock(JumpPointAPI.class);
            when(anchorMock.isStarAnchor()).thenReturn(true);
            when(anchorMock.getDestinationStarSystem()).thenReturn(system);
            anchors.add(anchorMock);
        }
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(anchors);
        return hyperspaceMock;
    }

    private static StarSystemAPI accessibleSystem(String id, float x, float y) {
        // Wired into hyperspace by a jump point, so the access rule admits it.
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(x, y));
        when(systemMock.getJumpPoints()).thenReturn(List.of(mock(SectorEntityToken.class)));
        return systemMock;
    }

    private static StarSystemAPI inaccessibleSystem(String id, float x, float y) {
        var system = accessibleSystem(id, x, y);
        when(system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)).thenReturn(true);
        when(system.getEntitiesWithTag(Tags.GATE)).thenReturn(List.of());
        return system;
    }

    private static StarSystemAPI decivilisedUnreachableSystem(String id, float x, float y) {
        // No jump point (Mockito defaults the list empty) and not cut off, so the
        // access rule rejects it; the revealed decivilised planet is its only
        // route onto the map. The planet is built before the getPlanets() stubbing
        // so Mockito does not see one stubbing nested inside another.
        var planet = decivilisedPlanet();
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(x, y));
        when(systemMock.getPlanets()).thenReturn(List.of(planet));
        return systemMock;
    }

    private static PlanetAPI decivilisedPlanet() {
        var conditionMock = mock(MarketConditionAPI.class);
        when(conditionMock.requiresSurveying()).thenReturn(false);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getSurveyLevel()).thenReturn(MarketAPI.SurveyLevel.FULL);
        when(marketMock.getFirstCondition(Conditions.DECIVILIZED)).thenReturn(conditionMock);
        var planetMock = mock(PlanetAPI.class);
        when(planetMock.getMarket()).thenReturn(marketMock);
        return planetMock;
    }
}
