package kmu.maplayers.base.geometry;

import kmlib.math.geometry.PolygonRegions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the map's geometry over real sectors: the kmlib partition, the
 * adjacency graph, {@link CellShaper}, and {@link SystemClusterBorders} run end to end on
 * every fixture {@link SectorFixture#listSectorNames} finds.
 *
 * <p>These pin the invariants a shape must hold whatever the layout throws at it, which is
 * what hand-built cells cannot probe: a real sector supplies near-collinear triples, pairs
 * a few hundred units apart, and voids wide enough to leave a cell untouched by any
 * neighbour. Each fixture is a separate case rather than one blessed sector, because a
 * layout only ever proves things about its own shape - so adding an extract from another
 * save widens coverage with no code change here.
 *
 * <p>An assertion only catches what someone thought to assert, and the four artifacts the
 * frontier's first attempt shipped were all plainly visible in the geometry rather than
 * caught by a rule. Looking at the shape is the other half of the check, and it belongs to
 * {@link SectorGeometryViewer} - which draws these same sectors through this same pipeline,
 * and saves an SVG on request.
 */
class SectorGeometryIntegrationTest {
    private static final String SECTORS =
            "kmu.maplayers.base.geometry.SectorGeometryIntegrationTest"
                    + "#provideSectorNames";
    // A vertex may sit a hair on the far side of its own bisector after clipping; the
    // nearest-site check tolerates that rather than chasing floating-point dust.
    private static final double NEAREST_SITE_TOLERANCE = 1e-6;

    // Built once per sector and shared across that sector's cases: the partition is O(n^2)
    // in a sector's systems, so rebuilding it per assertion would multiply the suite's cost
    // by the number of invariants for no added coverage.
    private static final Map<String, SectorFixture> FIXTURES = new ConcurrentHashMap<>();
    private static final Map<String, SectorGeometry> GEOMETRIES = new ConcurrentHashMap<>();

    static List<String> provideSectorNames() {
        var names = SectorFixture.listSectorNames();
        assertThat(names).as("no sector fixtures on the classpath").isNotEmpty();
        return names;
    }

    @Nested
    class BuildCellEdgesByCellId {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_cell_holds_only_the_space_nearest_its_own_site(String sector) {
            // The defining property of the partition, and the one that makes cells tile with
            // no overlap. Asserting it directly is cheaper and clearer than testing every
            // pair of cells for intersection, and it is what redistribution will have to
            // consciously break when a grouping absorbs a dead star's space.
            var fixture = fixtureOf(sector);
            var cellEdges = geometryOf(sector).cellEdgesByCellId();
            var sites = fixture.getSites();
            var systemIds = fixture.getSystemIds();
            var offenders = new ArrayList<String>();
            for (var index = 0; index < systemIds.size(); index++) {
                var own = sites.get(index);
                for (var edge : cellEdges.get(systemIds.get(index))) {
                    var vertex = new double[] {edge.x1(), edge.y1()};
                    var ownDistance = distanceBetween(vertex, own);
                    for (var other = 0; other < sites.size(); other++) {
                        if (other != index
                                && distanceBetween(vertex, sites.get(other))
                                    < ownDistance - NEAREST_SITE_TOLERANCE) {
                            offenders.add(systemIds.get(index) + " vs " + systemIds.get(other));
                        }
                    }
                }
            }
            assertThat(offenders).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_system_gets_a_cell_that_encloses_area(String sector) {
            var cellEdges = geometryOf(sector).cellEdgesByCellId();
            assertThat(cellEdges).hasSize(fixtureOf(sector).getSystemIds().size());
            for (var edges : cellEdges.values()) {
                assertThat(computeRingArea(toRing(edges))).isGreaterThan(0.0);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void adjacency_is_mutual_across_every_shared_edge(String sector) {
            // Two systems are neighbours only if each names the other, which is what lets a
            // consumer read one cell's edge and trust the far side agrees. A one-sided tag
            // would leave a cluster ring unable to close.
            var cellEdges = geometryOf(sector).cellEdgesByCellId();
            for (var cell : cellEdges.entrySet()) {
                for (var edge : cell.getValue()) {
                    if (!(edge.target() instanceof EdgeTarget.AcrossSystem acrossSystem)) {
                        continue;
                    }
                    assertThat(namesNeighbour(cellEdges.get(acrossSystem.systemId()),
                            cell.getKey()))
                            .as("%s names %s, so the reverse must hold",
                                    cell.getKey(), acrossSystem.systemId())
                            .isTrue();
                }
            }
        }
    }

    @Nested
    class ShapeCells {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_shaped_cell_stays_within_the_raw_cell_it_came_from(String sector) {
            // The channel is cut inward, so a shaped cell can only ever lose area. One that
            // grew would mean an offset escaped outward - the mechanism behind the poke the
            // frontier's first attempt shipped.
            var geometry = geometryOf(sector);
            for (var entry : geometry.shapedCellByCellId().entrySet()) {
                var shaped = entry.getValue();
                if (shaped.fillPolygon().isEmpty()) {
                    continue;
                }
                var rawArea = computeRingArea(toRing(
                        geometry.cellEdgesByCellId().get(entry.getKey())));
                assertThat(computeRingArea(shaped.fillPolygon()))
                        .as("shaped cell %s must not outgrow its raw cell", entry.getKey())
                        .isLessThanOrEqualTo(rawArea);
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void an_unowned_cell_borders_on_every_edge_and_fuses_on_none(String sector) {
            // An unowned system shares its key with nobody - EdgeClassifier rules even a
            // neighbouring empty cell a plain boundary - so a dead star's cell is bordered
            // the whole way round. This is the base the frontier's redistribution alters, so
            // it is worth pinning before it moves.
            var geometry = geometryOf(sector);
            var pinned = 0;
            for (var entry : geometry.shapedCellByCellId().entrySet()) {
                if (geometry.ownerByCellId().containsKey(entry.getKey())
                        || entry.getValue().fillPolygon().isEmpty()) {
                    continue;
                }
                for (var isBoundary : entry.getValue().edgeIsBoundary()) {
                    assertThat(isBoundary).isTrue();
                }
                pinned++;
            }
            assertThat(pinned).isPositive();
        }
    }

    @Nested
    class TraceBorderRings {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_bloc_holding_a_cell_traces_at_least_one_ring(String sector) {
            var geometry = geometryOf(sector);
            var blocsWithoutRings = new ArrayList<String>();
            for (var bloc : SectorGeometry.groupCellIdsByOwner(geometry.ownerByCellId())
                    .entrySet()) {
                if (geometry.ringsByOwner().get(bloc.getKey()).isEmpty()) {
                    blocsWithoutRings.add(bloc.getKey());
                }
            }
            assertThat(blocsWithoutRings).isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_traced_ring_encloses_area(String sector) {
            // A ring the inset folded over is dropped by the trace's own collapse guard, so
            // anything handed back must be drawable; a degenerate survivor is the shape an
            // orphaned loop takes.
            for (var rings : geometryOf(sector).ringsByOwner().values()) {
                for (var ring : rings) {
                    assertThat(ring.size()).isGreaterThanOrEqualTo(3);
                    assertThat(Math.abs(PolygonRegions.computeSignedArea(ring))).isGreaterThan(0.0);
                }
            }
        }

    }

    private static SectorFixture fixtureOf(String sector) {
        return FIXTURES.computeIfAbsent(sector, SectorFixture::loadSector);
    }

    private static SectorGeometry geometryOf(String sector) {
        return GEOMETRIES.computeIfAbsent(sector, name -> SectorGeometry.buildSectorGeometry(
                fixtureOf(name), SectorGeometryParameters.createDefaults()));
    }

    private static boolean namesNeighbour(List<CellEdge> edges, String systemId) {
        for (var edge : edges) {
            if (edge.target() instanceof EdgeTarget.AcrossSystem acrossSystem
                    && systemId.equals(acrossSystem.systemId())) {
                return true;
            }
        }
        return false;
    }

    private static List<double[]> toRing(List<CellEdge> edges) {
        var ring = new ArrayList<double[]>(edges.size());
        for (var edge : edges) {
            ring.add(new double[] {edge.x1(), edge.y1()});
        }
        return ring;
    }

    private static double computeRingArea(List<double[]> ring) {
        return ring.size() < 3 ? 0.0 : Math.abs(PolygonRegions.computeSignedArea(ring));
    }

    private static double distanceBetween(double[] a, double[] b) {
        return Math.hypot(a[0] - b[0], a[1] - b[1]);
    }
}
