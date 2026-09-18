package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.Chord;
import kmu.maplayers.base.geometry.walls.DiscUnionBoundary;
import kmu.maplayers.base.geometry.walls.Walls;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static kmu.maplayers.base.geometry.v3.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.v3.SectorPipeline.layInletSpans;
import static kmu.maplayers.base.geometry.v3.SectorPipeline.layLinks;
import static kmu.maplayers.base.geometry.v3.SectorPipeline.traceContinentCoast;

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

    private static final String SECTORS = SectorPipeline.SECTORS;

    // Whether the spans sharing an anchor are thinned, which is how the map lays the inlet
    // spans: the thinned set is what the map lays, and an unthinned one is a different laying for the
    // links to be judged against.
    private static final boolean SHOULD_THIN_FORMATIONS = true;

    // How far off a wall already down a span may run and still count as doubling it. The width a
    // span is drawn at, which is the shipped setting: two lines closer than that overlap on
    // screen, which is the state a reader calls doubled. Stated here rather than read off the
    // drawing, which this package may not reach into.
    private static final double COAST_SLACK = 120;

    // How close two span feet may stand before one of them moves. The shipped setting, which
    // separates feet that are coincident and leaves the rest where the search put them.
    private static final double ANCHOR_SEPARATION = 120;

    // How near a sea has to come to a pinched foot to be judged against it at all: half a cell
    // radius, which is far closer than any other sea and far further than any channel.
    private static final double WITHIN_REACH_OF_A_FOOT = PARAMETERS.cellRadius() / 2;

    // How far inside a cell's reach an outline point may sit before it is over the border rather
    // than on it, in map units. Only the arithmetic of flattening an arc onto the cell's own
    // vertex angles is being absorbed - every point of the walk is on a circle by construction.
    private static final double ON_THE_BORDER = 1;

    private static final Map<String, List<List<double[]>>> POCKETS = new ConcurrentHashMap<>();

    @Nested
    class FindLinkWalledPockets {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void aSectorTheLinksRingedHasASeaInIt(String sector) {
            // Asked first and alone, because every other claim below is true of an empty set.
            // Two links between the same pair of continents ring the void between them, and a
            // sector linked in several places has such pairs by construction - so drawing
            // nothing at all is the keep-rule having refused everything.
            assertThat(layLinks(sector))
                .as("%s: nothing was linked, so there is no sea to shut in", sector)
                .isNotEmpty();

            assertThat(fillSeasOf(sector, VoidPockets.PocketShaping.AT_TRUE_EXTENT))
                .as("%s: the links closed no water at all", sector)
                .isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void theSeaReachesEveryFootALinkPutsOnASinglePoint(String sector) {
            // A cell whose whole frontage is one point offers a wall nowhere else to attach, so
            // the wall is laid with no width there and the water it bounds runs up to the very
            // point - rather than stopping a channel short, which is what a wall of any width
            // does to the place it lands on. Judged at the true extent, where a fill IS its
            // hole and nothing has been pulled back from anything.
            //
            // Of the LINKS, and only where a sea comes near the foot at all. An inlet span's
            // foot borders a bay this layer leaves to the inlet fill, and a single link between
            // two continents closes nothing, so a foot no sea comes within half a cell of is a
            // foot with no sea to reach it - whereas one a sea comes that close to and stops
            // short of has been held off by a channel that should not be there.
            var walls = buildLaidWallsOf(sector);
            var links = Set.copyOf(Chord.buildChordsFrom(layLinks(sector)));
            var outlines = fillSeasOf(sector, VoidPockets.PocketShaping.AT_TRUE_EXTENT);
            var stoppedShort = new ArrayList<String>();

            for (var chord : DiscUnionBoundary.findAttachableChords(
                    traceContinentCoast(sector).union(), walls)) {
                if (!links.contains(chord)) {
                    continue;
                }

                for (var end : List.of(chord.fromCircle(), chord.toCircle())) {
                    if (!walls.pinchedCells().contains(end)) {
                        continue;
                    }
                    var foot = chord.findEndOn(end);
                    var nearest = measureToNearestOutline(outlines, foot);

                    if (nearest <= WITHIN_REACH_OF_A_FOOT && nearest > ON_THE_BORDER) {
                        stoppedShort.add(String.format("(%.0f, %.0f)", foot[0], foot[1]));
                    }
                }
            }

            assertThat(stoppedShort)
                .as("%s: a sea that stops short of the single point its wall lands on", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyPocketIsAShapeWithWaterInIt(String sector) {
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
        void noPocketReachesInsideACell(String sector) {
            // The sea is what the cells left over, so a fill drawn over one is the layer below
            // covering the layer above. At the drawn shaping this is also the channel: the walk
            // is run one channel out from the cells, so a point closer than that is a fill
            // touching the border it is supposed to stand off.
            var sites = traceContinentCoast(sector).union().sites();

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
        void everyPocketRunsAgainstTwoContinents(String sector) {
            // The keep-rule, asked of the geometry rather than of the walk's own bookkeeping. A
            // link joins two shapes, so the water it helps close runs against a cell of each;
            // water ringed by cells of ONE shape is a bay or a lake, and those have layers of
            // their own that would then paint it a second time.
            var shapeOf = Coastlines.mapCellsToShapes(traceContinentCoast(sector));
            var sites = traceContinentCoast(sector).union().sites();

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

    // The walls the fill is walked against, built as the construction builds them: the links
    // and the standing inlet spans, pinched on every cell whose whole frontage is one point.
    private static Walls buildLaidWallsOf(String sector) {
        var laid = new ArrayList<>(Chord.buildChordsFrom(layLinks(sector)));

        laid.addAll(Chord.buildChordsFrom(layInletSpans(sector)));

        return new Walls(
            laid,
            PARAMETERS.borderInset(),
            CoastFrontages.collectPinchedCells(traceContinentCoast(sector)));
    }

    private static double measureToNearestOutline(List<List<double[]>> outlines, double[] point) {
        var nearest = Double.MAX_VALUE;

        for (var outline : outlines) {
            for (var index = 0; index < outline.size(); index++) {
                nearest = Math.min(nearest, Segments.computeDistanceToPoint(
                    outline.get(index), outline.get((index + 1) % outline.size()), point));
            }
        }
        return nearest;
    }

    private static List<List<double[]>> fillSeasOf(
            String sector,
            VoidPockets.PocketShaping shaping) {
        return POCKETS.computeIfAbsent(sector + "@" + shaping, key ->
            IntercontinentalPockets.findLinkWalledPockets(
                traceContinentCoast(sector),
                layLinks(sector),
                layInletSpans(sector),
                new VoidPockets.PocketRules(PARAMETERS, shaping)));
    }
}
