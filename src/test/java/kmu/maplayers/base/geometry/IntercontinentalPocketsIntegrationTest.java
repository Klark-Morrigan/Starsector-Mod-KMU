package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the sea the links shut in between the continents, over real sectors.
 *
 * <p>The construction is a walk plus one keep-rule, and it is the keep-rule that can be wrong: the
 * walk hands back every hole it finds, including the bays the inlet spans closed and the water the
 * cells closed unaided, and each of those has a layer of its own that would then paint it a second
 * time. So the checks ask what a kept pocket must be rather than how it was built - open sea, run
 * against two shapes at once, and clear of every cell.
 *
 * <p>Asked at both shapings, because the channel is where a fill can go wrong without changing
 * shape: a pocket that sits right at its true extent and overlaps a cell once the channel is taken
 * would be a fill drawn over the border that defines it.
 *
 * <p>The set being non-empty is checked first and on its own, since every other claim here is
 * satisfied by drawing nothing at all.
 */
class IntercontinentalPocketsIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.IntercontinentalPocketsIntegrationTest"
            + "#provideSectorNames";

    // The laying the map ships, which is the one worth reporting on. The same three knobs the
    // link search is judged under: two lines closer than a span's own width read as doubled,
    // feet closer than that read as one place, and the inlet spans are thinned as the map thins
    // them.
    private static final double COAST_SLACK = 120;
    private static final double ANCHOR_SEPARATION = 120;
    private static final boolean SHOULD_THIN_FORMATIONS = true;

    // One set of geometry knobs for the whole suite, for the reason the shipped pipeline keeps
    // one: a coast traced under one set and a fill walked under another describe two maps.
    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    private static final ContinentBridges.BridgeRules SPAN_RULES =
        new ContinentBridges.BridgeRules(
            Coastlines.DEFAULT_RULES.bridgeReachMultiple(),
            COAST_SLACK,
            SHOULD_THIN_FORMATIONS,
            ANCHOR_SEPARATION);

    // How far inside a cell's reach an outline point may sit before it is over the border rather
    // than on it, in map units. Only the arithmetic of flattening an arc onto the cell's own
    // vertex angles is being absorbed - every point of the walk is on a circle by construction.
    private static final double ON_THE_BORDER = 1;

    // Built once per sector and shared: tracing a coast is O(n^2) in a sector's systems, and both
    // span searches walk every pair of frontages, so each check asking for its own would pay for
    // the pipeline several times over.
    private static final Map<String, SectorFixture> FIXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Coastlines.TracedCoasts> TRACES = new ConcurrentHashMap<>();
    private static final Map<String, List<CellGap>> INLET_SPANS = new ConcurrentHashMap<>();
    private static final Map<String, List<CellGap>> LINKS = new ConcurrentHashMap<>();
    private static final Map<String, List<List<double[]>>> POCKETS = new ConcurrentHashMap<>();

    static List<String> provideSectorNames() {

        var names = SectorFixture.listSectorNames();

        assertThat(names)
            .as("no sector fixtures on the classpath")
            .isNotEmpty();

        return names;
    }

    @Nested
    class FindLinkWalledPockets {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_sector_the_links_ringed_has_a_sea_in_it(String sector) {
            // Asked first and alone, because every other claim below is true of an empty set.
            // Two links between the same pair of continents ring the void between them, and a
            // sector linked in several places has such pairs by construction - so drawing
            // nothing at all is the keep-rule having refused everything.
            assertThat(linkContinentsOf(sector))
                .as("%s: nothing was linked, so there is no sea to shut in", sector)
                .isNotEmpty();

            assertThat(fillSeasOf(sector, VoidPockets.PocketShaping.AT_TRUE_EXTENT))
                .as("%s: the links closed no water at all", sector)
                .isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_pocket_is_a_shape_with_water_in_it(String sector) {
            // What a fill has to be to be drawn. An outline of two points encloses nothing, and
            // one of zero area is a line the painter would draw as a hair - both are a pocket
            // reported where there is no water.
            for (var shaping : VoidPockets.PocketShaping.values()) {

                var degenerate = new ArrayList<String>();

                for (var outline : fillSeasOf(sector, shaping)) {

                    if (outline.size() < Limits.MIN_VERTICES_TO_ENCLOSE_AREA
                            || Math.abs(PolygonRegions.computeSignedArea(outline)) == 0) {

                        degenerate.add(String.format("%d points", outline.size()));
                    }
                }

                assertThat(degenerate)
                    .as("%s, %s: a pocket with no water in it", sector, shaping)
                    .isEmpty();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void no_pocket_reaches_inside_a_cell(String sector) {
            // The sea is what the cells left over, so a fill drawn over one is the layer below
            // covering the layer above. At the drawn shaping this is also the channel: the walk
            // is run one channel out from the cells, so a point closer than that is a fill
            // touching the border it is supposed to stand off.
            var sites = traceCoastOf(sector).union().sites();

            for (var shaping : VoidPockets.PocketShaping.values()) {

                var reach = measureReachAt(shaping);
                var covered = new ArrayList<String>();

                for (var outline : fillSeasOf(sector, shaping)) {
                    for (var point : outline) {
                        for (var site = 0; site < sites.size(); site++) {

                            var separation = Points.computeDistance(sites.get(site), point);

                            if (separation < reach - ON_THE_BORDER) {
                                covered.add(String.format(
                                    "cell %d at %.0f, inside %.0f", site, separation, reach));
                            }
                        }
                    }
                }

                assertThat(covered)
                    .as("%s, %s: a pocket drawn over a cell", sector, shaping)
                    .isEmpty();
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_pocket_runs_against_two_continents(String sector) {
            // The keep-rule, asked of the geometry rather than of the walk's own bookkeeping. A
            // link joins two shapes, so the water it helps close runs against a cell of each;
            // water ringed by cells of ONE shape is a bay or a lake, and those have layers of
            // their own that would then paint it a second time.
            var shapeOf = Coastlines.mapCellsToShapes(traceCoastOf(sector));
            var sites = traceCoastOf(sector).union().sites();

            for (var shaping : VoidPockets.PocketShaping.values()) {

                var enclosed = new ArrayList<String>();

                for (var outline : fillSeasOf(sector, shaping)) {

                    var shapes = new LinkedHashSet<Integer>();

                    for (var point : outline) {

                        // Only cells that are ON a shape count. One on none faces nothing but a
                        // lake, so it cannot bound open sea - and counted as a shape of its own
                        // it would satisfy this check without any second continent.
                        var shape = shapeOf.get(findNearestSite(sites, point));

                        if (shape != null) {
                            shapes.add(shape);
                        }
                    }

                    if (shapes.size() < 2) {
                        enclosed.add(String.format("water against shapes %s", shapes));
                    }
                }

                assertThat(enclosed)
                    .as("%s, %s: a pocket that runs against one shape only", sector, shaping)
                    .isEmpty();
            }
        }
    }

    // The reach the cells were taken to have when the fill was walked, which is what every point
    // of it stands outside of: the cells' own border at the true extent, and one channel out from
    // it at the drawn one.
    private static double measureReachAt(VoidPockets.PocketShaping shaping) {

        return shaping.isAtTrueExtent()
            ? PARAMETERS.cellRadius()
            : PARAMETERS.measureDrawnReach();
    }

    // The cell an outline point runs along. Every point of the walk lies on some cell's circle,
    // so the nearest site is the one whose border it was sampled from.
    private static int findNearestSite(List<double[]> sites, double[] point) {

        var nearest = 0;
        var closest = Double.MAX_VALUE;

        for (var site = 0; site < sites.size(); site++) {

            var separation = Points.computeDistance(sites.get(site), point);

            if (separation < closest) {

                closest = separation;
                nearest = site;
            }
        }
        return nearest;
    }

    private static List<List<double[]>> fillSeasOf(
            String sector,
            VoidPockets.PocketShaping shaping) {

        return POCKETS.computeIfAbsent(sector + "@" + shaping, key ->
            IntercontinentalPockets.findLinkWalledPockets(
                traceCoastOf(sector),
                linkContinentsOf(sector),
                layInletSpansOn(sector),
                new VoidPockets.PocketRules(PARAMETERS, shaping)));
    }

    private static List<CellGap> linkContinentsOf(String sector) {

        return LINKS.computeIfAbsent(sector, named ->
            IntercontinentalBridges.findIntercontinentalBridges(
                traceCoastOf(named), layInletSpansOn(named), PARAMETERS, SPAN_RULES));
    }

    // The spans the map lays first, which are both what a link is judged against and the walls
    // this fill comes to rest against.
    private static List<CellGap> layInletSpansOn(String sector) {

        return INLET_SPANS.computeIfAbsent(sector, named ->
            ContinentBridges.findAnchoredBridges(
                traceCoastOf(named), CoastFrontages.Shore.EXTERIOR, PARAMETERS, SPAN_RULES));
    }

    private static Coastlines.TracedCoasts traceCoastOf(String sector) {

        return TRACES.computeIfAbsent(sector, named -> Coastlines.traceContinentCoasts(
            buildFixtureFor(named).getSites(), PARAMETERS, Coastlines.DEFAULT_RULES));
    }

    private static SectorFixture buildFixtureFor(String sector) {
        return FIXTURES.computeIfAbsent(sector, SectorFixture::loadSector);
    }
}
