package kmu.maplayers.base.geometry;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import kmlib.math.geometry.VoronoiCellBuilder;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.DecivilisedPlanetFixtures;
import kmu.maplayers.base.profiling.MapBuildCounters;
import kmu.maplayers.base.visibility.systems.MapVisibilityPass;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.systems.MapSectorFixture.buildStarAnchoredSectorOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link CellGeometryCache}: the first update builds cells for the
 * reachable systems and skips inaccessible ones; a later update rebuilds only the
 * cells near a changed system, leaving distant ones in place (asserted by object
 * identity); a removed system's cell is dropped; and every update reports what it recomputed and
 * which of its two outcomes it was onto the scope it opens.
 */
final class CellGeometryCacheTest {

    // Comfortably beyond twice the default cell radius (8000), so changes near one do
    // not reach the other.
    private static final float FAR = 20_000f;

    // The cell reach cells are seeded at unless a test changes it - the production default.
    // FAR is set well past twice it, so a change near one cell cannot reach the other.
    private static final double DEFAULT_CELL_RADIUS = 4000.0;

    // What cells are seeded with unless a test changes it: the production frontier resolution
    // and reach. The access-diff tests all run at this one pair, so a rebuild there is driven
    // only by the reachable set and never by a reseed.
    private static final CellSeedInputs DEFAULT_SEED_INPUTS = new CellSeedInputs(
        VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS,
        DEFAULT_CELL_RADIUS);

    // No movers in the access-diff tests: an empty moving set makes every drawn system
    // participate in the partition. The exclusion tests pass an explicit set to drop
    // one system.
    private static final Set<String> NO_MOVING_SYSTEMS = Set.of();

    // No reveal widening in the diff tests: each fixture decides admission through the normal
    // gates, so a cell appearing or vanishing is the access diff and never an override flip.
    private static final MapVisibilityRules NO_REVEAL = MapVisibilityRules.BASE;

    // The row an update lands on.
    private static final String UPDATE_SECTION = "mapLayer.updateGeometry";

    // The force override on, which admits every system whatever the normal gates say - the one
    // widening that needs no economy staged behind it to change the participating set.
    private static final MapVisibilityRules FORCED_ONTO_MAP =
        new MapVisibilityRules(BASE_FOG, true);

    @Nested
    class UpdateFromSector {

        @Test
        void updateBuildsCellsForReachableSystemsAndSkipsInaccessibleOnes() {

            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", 4000, 0),
                buildInaccessibleSystem("hidden", 8000, 0));

            assertThat(cache.getCellEdgesByCellId())
                .containsOnlyKeys("a", "b");
            assertThat(cache.getCellEdgesByCellId().get("a"))
                .isNotEmpty();
        }

        @Test
        void countsTheCellsItRecomputed() {
            // The number the update's duration is read against, which used to be printed in a log
            // line the profiler never saw.
            var cache = new CellGeometryCache();

            var capture = RecordedCapture.recordWhile(() -> updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", 4000, 0)));

            assertThat(capture
                    .findNode(UPDATE_SECTION)
                    .findCount(MapBuildCounters.CELLS)
                    .getTotals()
                    .getTotal())
                .isEqualTo(2);
        }

        @Test
        void namesAnUpdateThatFoundNothingToRebuild() {
            // A refresh over an identical participating set still costs the diff, and the call's
            // name is what tells that from a rebuild - the counts alone cannot.
            var cache = new CellGeometryCache();
            var systems = new StarSystemAPI[] {
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", 4000, 0)};

            updateAtDefaultResolution(cache, systems);

            var capture =
                RecordedCapture.recordWhile(() -> updateAtDefaultResolution(cache, systems));

            assertThat(capture.findNode(UPDATE_SECTION).getWorstCall().getTag())
                .startsWith("unchanged");
        }

        @Test
        void updateSeedsACellForAnInaccessibleSystemWhenTheOverridesForceItOntoTheMap() {
            // The rules reach the partition through the drawn-set walk, so the system the
            // test above proves is skipped has to participate here - and its neighbour has to
            // be clipped by it, since a site that seeds a cell also takes area from the cells
            // around it. Nothing else in this class passes a widening, so without this the
            // parameter could be dropped on the floor and every test would still pass.
            var cache = new CellGeometryCache();

            cache.updateFromSector(
                MapVisibilityPass.over(
                    buildStarAnchoredSectorOf(
                        buildAccessibleSystem("a", 0, 0),
                        buildInaccessibleSystem("hidden", 4000, 0)),
                    FORCED_ONTO_MAP),
                NO_MOVING_SYSTEMS,
                DEFAULT_SEED_INPUTS);

            assertThat(cache.getCellEdgesByCellId())
                .containsOnlyKeys("a", "hidden");
            assertThat(listNeighboursOf(cache, "a"))
                .containsExactly("hidden");
        }

        @Test
        void updateLeavesDistantCellsUntouchedWhenASystemIsAdded() {

            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", FAR, 0));

            var distantBefore = cache
                .getCellEdgesByCellId()
                .get("b");

            // Add a system next to "a"; "b" is far away, so its cell must be the
            // very same object - proof it was not recomputed.
            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", FAR, 0),
                buildAccessibleSystem("c", 100, 0));

            assertThat(cache.getCellEdgesByCellId())
                .containsKey("c");
            assertThat(cache.getCellEdgesByCellId().get("b"))
                .isSameAs(distantBefore);
        }

        @Test
        void updateReseedsEveryCellWhenTheFrontierResolutionChanges() {

            var cache = new CellGeometryCache();

            cache.updateFromSector(
                MapVisibilityPass.over(
                    buildStarAnchoredSectorOf(
                        buildAccessibleSystem("a", 0, 0),
                        buildAccessibleSystem("b", FAR, 0)),
                    NO_REVEAL),
                NO_MOVING_SYSTEMS,
                DEFAULT_SEED_INPUTS);

            var distantBefore = cache
                .getCellEdgesByCellId()
                .get("b");

            // The segment count seeds every cell's frontier polygon, so lowering it
            // invalidates all cells even where the reachable set is identical: the
            // distant cell must be a fresh object, not the untouched one an access
            // diff would leave. A lone bounded cell keeps one edge per seed segment,
            // so its edge count drops to the new count.
            cache.updateFromSector(
                MapVisibilityPass.over(
                    buildStarAnchoredSectorOf(
                        buildAccessibleSystem("a", 0, 0),
                        buildAccessibleSystem("b", FAR, 0)),
                    NO_REVEAL),
                NO_MOVING_SYSTEMS,
                new CellSeedInputs(24, DEFAULT_CELL_RADIUS));

            assertThat(cache.getCellEdgesByCellId().get("b"))
                .isNotSameAs(distantBefore);
            assertThat(cache.getCellEdgesByCellId().get("b"))
                .hasSize(24);
        }

        @Test
        void updateReseedsEveryCellWhenTheCellRadiusChanges() {

            var cache = new CellGeometryCache();

            cache.updateFromSector(
                MapVisibilityPass.over(
                    buildStarAnchoredSectorOf(
                        buildAccessibleSystem("a", 0, 0),
                        buildAccessibleSystem("b", FAR, 0)),
                    NO_REVEAL),
                NO_MOVING_SYSTEMS,
                DEFAULT_SEED_INPUTS);

            var distantBefore = cache
                .getCellEdgesByCellId()
                .get("b");

            // The cell radius seeds each cell's reach into empty space, so changing it
            // invalidates every cell even where the reachable set is identical - the
            // isolated cell must be a fresh object, not the untouched one an access diff
            // would leave in place, exactly as a frontier-resolution change reseeds it.
            cache.updateFromSector(
                MapVisibilityPass.over(
                    buildStarAnchoredSectorOf(
                        buildAccessibleSystem("a", 0, 0),
                        buildAccessibleSystem("b", FAR, 0)),
                    NO_REVEAL),
                NO_MOVING_SYSTEMS,
                new CellSeedInputs(
                    VoronoiCellBuilder.DEFAULT_CELL_BOUND_SEGMENTS,
                    DEFAULT_CELL_RADIUS / 2.0));

            assertThat(cache.getCellEdgesByCellId().get("b"))
                .isNotSameAs(distantBefore);
        }

        @Test
        void updateDropsTheCellOfASystemThatLosesAccess() {

            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", FAR, 0));

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0));

            assertThat(cache.getCellEdgesByCellId())
                .containsOnlyKeys("a");
        }

        @Test
        void updateBuildsTheAdjacencyGraphTaggingNeighboursAcrossSharedEdges() {

            var cache = new CellGeometryCache();

            // Two close systems share a Voronoi edge, so each cell must name the
            // other across exactly that edge.
            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", 1000, 0));

            assertThat(listNeighboursOf(cache, "a"))
                .containsExactly("b");
            assertThat(listNeighboursOf(cache, "b"))
                .containsExactly("a");
        }

        @Test
        void updateMarksAFrontierEdgeWithTheReachBound() {

            var cache = new CellGeometryCache();

            // A lone system has no neighbour to share an edge with, so every edge
            // is a frontier into empty space - the cell's own reach bound.
            updateAtDefaultResolution(cache, buildAccessibleSystem("a", 0, 0));

            assertThat(cache.getCellEdgesByCellId().get("a"))
                .isNotEmpty()
                .allSatisfy(edge -> assertThat(edge.target()).isEqualTo(EdgeTarget.REACH_BOUND));
        }

        @Test
        void updateKeysEachCellByTheSystemItDrawsAs() {

            var cache = new CellGeometryCache();

            // Every cell here is one star's own, so it draws as that star: the
            // draws-as map is the identity a cell-keyed consumer resolves a key through.
            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", 1000, 0));

            assertThat(cache.getSystemIdByCellId())
                .containsOnly(
                    org.assertj.core.api.Assertions.entry("a", "a"),
                    org.assertj.core.api.Assertions.entry("b", "b"));
        }

        @Test
        void updateDropsTheAdjacencyOfASystemThatLosesAccess() {

            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildAccessibleSystem("b", FAR, 0));

            updateAtDefaultResolution(cache, buildAccessibleSystem("a", 0, 0));

            assertThat(cache.getCellEdgesByCellId())
                .containsOnlyKeys("a");
        }

        @Test
        void updateSeedsAnUnreachableSystemHoldingARevealedDecivilisedPlanet() {
            // No jump point, so the access rule rejects it, but the revealed ruin
            // makes it inhabited - it must still seed a cell.
            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("a", 0, 0),
                buildDecivilisedUnreachableSystem("ruin", 4000, 0));

            assertThat(cache.getCellEdgesByCellId())
                .containsOnlyKeys("a", "ruin");
        }

        @Test
        void updateExcludesAMovingSystemAndReshapesItsNeighbourButNotDistantCells() {

            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("m", 0, 0),
                buildAccessibleSystem("n", 2000, 0),
                buildAccessibleSystem("f", FAR, 0));

            var neighbourBefore = cache.getCellEdgesByCellId().get("n");
            var distantBefore = cache.getCellEdgesByCellId().get("f");

            // "m" starts moving, so it drops out of the partition: it seeds no cell, and
            // "n" (within a neighbourhood radius) reshapes to reclaim its space, while
            // "f" is beyond reach and keeps the very same cell object.
            updateExcluding(
                cache,
                Set.of("m"),
                buildAccessibleSystem("m", 0, 0),
                buildAccessibleSystem("n", 2000, 0),
                buildAccessibleSystem("f", FAR, 0));

            assertThat(cache.getCellEdgesByCellId())
                .doesNotContainKey("m");
            assertThat(cache.getCellEdgesByCellId().get("n"))
                .isNotSameAs(neighbourBefore);
            assertThat(cache.getCellEdgesByCellId().get("f"))
                .isSameAs(distantBefore);
        }

        @Test
        void updateReturnsAStoppedSystemToThePartition() {

            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("m", 0, 0),
                buildAccessibleSystem("n", 2000, 0));

            updateExcluding(
                cache,
                Set.of("m"),
                buildAccessibleSystem("m", 0, 0),
                buildAccessibleSystem("n", 2000, 0));

            // "m" comes to rest, so it is no longer a mover and rejoins the partition
            // with a fresh cell.
            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("m", 0, 0),
                buildAccessibleSystem("n", 2000, 0));

            assertThat(cache.getCellEdgesByCellId())
                .containsKey("m");
            assertThat(cache.getCellEdgesByCellId().get("m"))
                .isNotEmpty();
        }

        @Test
        void updateExcludingAnIsolatedSystemLeavesDistantCellsUntouched() {

            var cache = new CellGeometryCache();

            updateAtDefaultResolution(
                cache,
                buildAccessibleSystem("m", 0, 0),
                buildAccessibleSystem("f", FAR, 0));

            var distantBefore = cache.getCellEdgesByCellId().get("f");

            // "m" starts moving but has no neighbour within a neighbourhood radius, so
            // its removal touches only itself; "f" keeps the same cell object.
            updateExcluding(
                cache,
                Set.of("m"),
                buildAccessibleSystem("m", 0, 0),
                buildAccessibleSystem("f", FAR, 0));

            assertThat(cache.getCellEdgesByCellId())
                .doesNotContainKey("m");
            assertThat(cache.getCellEdgesByCellId().get("f"))
                .isSameAs(distantBefore);
        }
    }

    // The distinct neighbouring system ids one cell names across its edges,
    // dropping the reach-bound edges - the adjacency the merge step reads.
    private static Set<String> listNeighboursOf(CellGeometryCache cache, String systemId) {

        var neighbours = new LinkedHashSet<String>();

        for (var edge : cache.getCellEdgesByCellId().get(systemId)) {

            if (edge.target() instanceof EdgeTarget.AcrossSystem acrossSystem) {

                neighbours.add(acrossSystem.systemId());
            }
        }
        return neighbours;
    }

    // Runs an update at the default frontier resolution with no movers, the count
    // cells stay at unless the tuning knob changes it. The access-diff tests use this
    // so the only thing that drives a rebuild is the reachable set.
    private static void updateAtDefaultResolution(
            CellGeometryCache cache,
            StarSystemAPI... systems) {

        cache.updateFromSector(
            MapVisibilityPass.over(buildStarAnchoredSectorOf(systems), NO_REVEAL),
            NO_MOVING_SYSTEMS,
            DEFAULT_SEED_INPUTS);
    }

    // Runs an update at the default frontier resolution with the named systems
    // excluded from the partition as movers. The exclusion tests use this to drop a
    // system: it seeds no cell and clips no neighbour.
    private static void updateExcluding(
            CellGeometryCache cache,
            Set<String> movingSystemIds,
            StarSystemAPI... systems) {

        cache.updateFromSector(
            MapVisibilityPass.over(buildStarAnchoredSectorOf(systems), NO_REVEAL),
            movingSystemIds,
            DEFAULT_SEED_INPUTS);
    }

    private static StarSystemAPI buildAccessibleSystem(String id, float x, float y) {

        // Wired into hyperspace by a jump point, so the access rule admits it.
        var systemMock = StarSystemFixture.buildSystemAt(id, x, y);

        when(systemMock.getJumpPoints()).thenReturn(List.of(mock(SectorEntityToken.class)));

        return systemMock;
    }

    private static StarSystemAPI buildInaccessibleSystem(String id, float x, float y) {

        var system = buildAccessibleSystem(id, x, y);

        when(system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER))
            .thenReturn(true);
        when(system.getEntitiesWithTag(Tags.GATE))
            .thenReturn(List.of());

        return system;
    }

    private static StarSystemAPI buildDecivilisedUnreachableSystem(String id, float x, float y) {

        // No jump point (Mockito defaults the list empty) and not cut off, so the
        // access rule rejects it; the revealed decivilised planet is its only
        // route onto the map. The ruin is placed among the system's entities as
        // well as its planets, that walk being how a colony set reaches it.
        var systemMock = StarSystemFixture.buildSystemAt(id, x, y);

        DecivilisedPlanetFixtures.placeRevealedDecivilisedPlanetIn(systemMock);

        return systemMock;
    }
}
