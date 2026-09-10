package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;
import kmlib.math.geometry.Segments;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static kmu.maplayers.base.geometry.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.SectorPipeline.SPAN_RULES;
import static kmu.maplayers.base.geometry.SectorPipeline.loadFixture;
import static kmu.maplayers.base.geometry.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for what a continent has left facing the open void, over real sectors.
 *
 * <p>The reading is bookkeeping - which arcs a piece of captured water named, and how many
 * pieces named each span - so what is worth pinning is that the bookkeeping and the map agree.
 * Every check here therefore asks the geometry rather than the record: a stretch said to face
 * the void is probed in the water outside it, and a span said to stand on the edge is measured
 * against the outlines that carry its two sides.
 *
 * <p>Both directions of the subtraction are asked, because they fail apart. A reading that
 * captured nothing leaves stretches over water a span shut in, which the probe catches; a
 * reading that captured everything leaves nothing at all, which no probe catches and which the
 * untouched continents pin instead.
 */
class ContinentExposureIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    // Whether the spans sharing an anchor are thinned, which is how the map lays them: the
    // thinned set is what the map lays, and an unthinned one is a different laying to report on.
    private static final boolean SHOULD_THIN_FORMATIONS = true;

    // How far off a wall already down a span may run and still count as doubling it. The width
    // a span is drawn at, which is the shipped setting: two lines closer than that overlap on
    // screen, which is the state a reader calls doubled. Stated here rather than read off the
    // drawing, which this package may not reach into - and nothing below turns on the number
    // anyway, only on the spans being a real laying over a real sector.
    private static final double COAST_SLACK = 120;

    // How close two span feet may stand before one of them moves. Stated here for the reason
    // the slack is - and nothing below turns on the number, only on the spans being a real
    // laying over a real sector.
    private static final double ANCHOR_SEPARATION = 120;

    // How far outside its own cell a stretch is probed for the water it faces, as a share of
    // the cell radius. Out far enough to clear the flattening of a pocket's outline, which cuts
    // inside the true arc by a sagitta; short enough to stay in the water against the stretch
    // rather than reaching whatever lies beyond it.
    private static final double PROBE_SHARE_OF_RADIUS = 0.05;

    // Fewest points a run of frontage needs before the reading owes a stretch for it. One point
    // is a place rather than a stretch, and nothing can be anchored along it - so a frontage cut
    // down to a single point is legitimately dropped.
    private static final int MIN_POINTS_IN_A_STRETCH = 2;

    // How many sides of a span an edge span has captured water on. The whole of what the word
    // means, and so what the check is against.
    private static final int SIDES_CARRIED_ON_AN_EDGE_SPAN = 1;

    // How far off a span's side a captured outline may run and still be said to run along it,
    // in map units. Comfortably above the flattening a sampled outline shows against the true
    // arc, and comfortably below the channel that holds the span's two sides apart - so a side
    // is never credited with the water on the other side of its own span. Measured over both
    // fixtures the two are 40 and 134 apart at their worst.
    private static final double ALONG_A_SIDE = 60;

    private static final Map<String, List<CellGap>> SPANS = new ConcurrentHashMap<>();

    @Nested
    class FindContinentExposure {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_exposed_stretch_faces_water_no_span_shut_in(String sector) {
            // The claim the whole reading exists to make. Asked of the water rather than of the
            // arcs: a point stepped out of the middle of a stretch lands in whatever the coast
            // faces there, and a stretch still on the books over captured water lands inside
            // the very outline that captured it.
            var traced = traceContinentCoast(sector);
            var captured = collectCapturedOutlines(sector);
            var facingCapturedWater = new ArrayList<String>();

            for (var continent : findExposureOf(sector)) {
                for (var stretch : continent.exposedStretches()) {
                    var probe = probeOutsideMiddleOf(stretch, traced);

                    if (isInsideAny(probe, captured)) {
                        facingCapturedWater.add(String.format(
                            "continent %d, cell %d at %.0f, %.0f",
                            continent.continent(), stretch.cell(), probe[0], probe[1]));
                    }
                }
            }

            assertThat(facingCapturedWater)
                .as("%s: frontage reported open over water a span shut in", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_continent_no_span_shut_water_in_on_keeps_its_whole_frontage(String sector) {
            // The other direction, which no probe can catch: a reading that dropped frontage it
            // had no reason to drop is invisible on the map, because what is missing is a
            // stretch nothing was going to be drawn over anyway. A continent whose cells ring
            // no captured water at all has nothing to subtract, so its frontage has to come
            // back whole - and any of it missing is subtraction landing where it was not owed.
            var traced = traceContinentCoast(sector);
            var frontages = CoastFrontages.Shore.EXTERIOR.collectFrontages(traced);
            var untouched = findContinentsHoldingNoCapturedWater(sector, traced);

            assertThat(untouched)
                .as("%s: no continent free of captured water, so this check asks nothing", sector)
                .isNotEmpty();

            for (var continent : findExposureOf(sector)) {
                if (!untouched.contains(continent.continent())) {
                    continue;
                }

                assertThat(countPointsIn(continent.exposedStretches()))
                    .as("%s: continent %d lost frontage no span shut in", sector,
                        continent.continent())
                    .isEqualTo(countFrontagePointsOf(
                        continent.continent(), traced, frontages));
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void an_exposed_stretch_runs_along_the_frontage_it_came_from(String sector) {
            // A stretch is somewhere a further pass may anchor, and an anchor is only worth
            // anything if it sits on the traced line. So a stretch has to be a run of one of
            // its cell's own frontages, unbroken and in order - not a gathering of the points
            // that survived, which would offer an anchor either side of water a span took.
            var traced = traceContinentCoast(sector);
            var frontages = CoastFrontages.Shore.EXTERIOR.collectFrontages(traced);
            var strayed = new ArrayList<String>();

            for (var continent : findExposureOf(sector)) {
                for (var stretch : continent.exposedStretches()) {
                    if (!isRunOfAny(stretch.points(),
                            frontages.getOrDefault(stretch.cell(), List.of()))) {
                        strayed.add(String.format(
                            "continent %d, cell %d", continent.continent(), stretch.cell()));
                    }
                }
            }

            assertThat(strayed)
                .as("%s: a stretch that is not a run of its cell's frontage", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void an_edge_span_holds_captured_water_on_one_side_only(String sector) {
            // What "edge" means, checked against the outlines rather than against the tally the
            // reading was made from. A span keeps a channel, so each of its sides closes on its
            // own line, and a side facing captured water is a line that water's outline runs
            // along - which is a question about where the outlines are, answered without asking
            // what closed them.
            var sites = loadFixture(sector).getSites();
            var captured = collectCapturedOutlines(sector);
            var union = new DiscUnion(sites, PARAMETERS.cellRadius());
            var miscounted = new ArrayList<String>();

            for (var continent : findExposureOf(sector)) {
                for (var span : continent.edgeSpans()) {
                    var carried = countSidesCarriedBy(span, union, captured);

                    if (carried != SIDES_CARRIED_ON_AN_EDGE_SPAN) {
                        miscounted.add(String.format(
                            "continent %d, cells %d-%d: %d sides carried",
                            continent.continent(), span.fromSite(), span.toSite(), carried));
                    }
                }
            }

            assertThat(miscounted)
                .as("%s: an edge span with captured water on other than one side", sector)
                .isEmpty();
        }
    }

    // How many of a span's two sides a piece of captured water closes on. Each side is a line
    // half a channel off the span, and a side facing captured water has that water's outline
    // running along it.
    //
    // Asked of the side's MIDDLE against the outline's edges, rather than of its two ends
    // against the outline's corners. A span's mouth on a cell can be shared with a neighbouring
    // wall, and the walk then cuts that side's ends back - the outline still runs the length of
    // the side, but it no longer turns at either end of it. Asking about the corners calls such
    // a side uncarried, which is a fact about a crowded mouth rather than about what the span
    // holds.
    private static int countSidesCarriedBy(
            CellGap span,
            DiscUnion union,
            List<List<double[]>> captured) {
        var chord = DiscUnionBoundary.buildChordsFrom(List.of(span)).get(0);
        var carried = 0;

        for (var side : DiscUnionBoundary.findChordSides(
                union, chord, new DiscUnionBoundary.Walls(List.of(chord), PARAMETERS.borderInset()))) {
            for (var outline : captured) {
                if (isRunningAlong(outline, findMiddleOf(side))) {
                    carried++;
                    break;
                }
            }
        }
        return carried;
    }

    private static double[] findMiddleOf(List<double[]> side) {
        return new double[] {
            (side.get(0)[0] + side.get(1)[0]) / 2,
            (side.get(0)[1] + side.get(1)[1]) / 2};
    }

    // Whether an outline runs along a place, which is what carrying a span's side means.
    private static boolean isRunningAlong(List<double[]> outline, double[] point) {
        for (var index = 0; index < outline.size(); index++) {
            var along = Segments.computeDistanceToPoint(
                outline.get(index), outline.get((index + 1) % outline.size()), point);

            if (along <= ALONG_A_SIDE) {
                return true;
            }
        }
        return false;
    }

    // The water outside the middle of a stretch, as a point stepped out of the cell the stretch
    // runs along. The middle rather than an end, because the ends of a stretch are where a span
    // was anchored - the one place the water either side of the line meets.
    private static double[] probeOutsideMiddleOf(
            ContinentExposure.ExposedStretch stretch,
            Coastlines.TracedCoasts traced) {
        var site = traced.union().sites().get(stretch.cell());
        var middle = stretch.points().get(stretch.points().size() / 2);
        var out = PARAMETERS.cellRadius() * PROBE_SHARE_OF_RADIUS;
        var length = Points.computeDistance(site, middle);

        return new double[] {
            middle[0] + (middle[0] - site[0]) / length * out,
            middle[1] + (middle[1] - site[1]) / length * out};
    }

    // The continents none of whose cells ring water a span shut in, which are the ones with
    // nothing to subtract.
    private static Set<Integer> findContinentsHoldingNoCapturedWater(
            String sector,
            Coastlines.TracedCoasts traced) {
        var continentOf = Coastlines.mapCellsToContinents(traced);
        var touched = new LinkedHashSet<Integer>();

        for (var water : findCapturedWaterOf(sector)) {
            for (var cell : water.ringing()) {
                var continent = continentOf.get(cell);

                if (continent != null) {
                    touched.add(continent);
                }
            }
        }

        var untouched = new LinkedHashSet<Integer>();

        for (var continent = 0; continent < traced.silhouettes().size(); continent++) {
            if (!touched.contains(continent)) {
                untouched.add(continent);
            }
        }
        return untouched;
    }

    // Every point of one continent's frontage that the reading owes a stretch for, which is
    // every run of it long enough to be a stretch at all.
    private static int countFrontagePointsOf(
            int continent,
            Coastlines.TracedCoasts traced,
            Map<Integer, List<List<double[]>>> frontages) {
        var continentOf = Coastlines.mapCellsToContinents(traced);
        var points = 0;

        for (var entry : frontages.entrySet()) {
            if (!Integer.valueOf(continent).equals(continentOf.get(entry.getKey()))) {
                continue;
            }

            for (var frontage : entry.getValue()) {
                if (frontage.size() >= MIN_POINTS_IN_A_STRETCH) {
                    points += frontage.size();
                }
            }
        }
        return points;
    }

    private static int countPointsIn(List<ContinentExposure.ExposedStretch> stretches) {
        var points = 0;

        for (var stretch : stretches) {
            points += stretch.points().size();
        }
        return points;
    }

    // Whether a run of points appears unbroken, and in order, inside any of a cell's frontages.
    private static boolean isRunOfAny(List<double[]> run, List<List<double[]>> frontages) {
        for (var frontage : frontages) {
            if (isRunOf(run, frontage)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRunOf(List<double[]> run, List<double[]> frontage) {
        for (var start = 0; start + run.size() <= frontage.size(); start++) {
            var matches = true;

            for (var step = 0; step < run.size() && matches; step++) {
                matches = Arrays.equals(run.get(step), frontage.get(start + step));
            }

            if (matches) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInsideAny(double[] point, List<List<double[]>> outlines) {
        for (var outline : outlines) {
            if (PolygonRegions.isPointInsideRing(outline, point[0], point[1])) {
                return true;
            }
        }
        return false;
    }

    private static List<List<double[]>> collectCapturedOutlines(String sector) {
        var outlines = new ArrayList<List<double[]>>();

        for (var water : findCapturedWaterOf(sector)) {
            outlines.add(water.boundary());
        }
        return outlines;
    }

    // The water the spans shut in, read at the reach the exposure itself is read at - the
    // cells' own. Measured a channel out, an outline would sit inside the water it stands for
    // and a probe in that channel would report open void where a span had closed the water.
    private static List<VoidHole> findCapturedWaterOf(String sector) {
        return VoidBridgePockets.findBridgeWalledHoles(
            loadFixture(sector).getSites(),
            laySpansOn(sector),
            PARAMETERS,
            VoidPockets.PocketShaping.AT_TRUE_EXTENT);
    }

    private static List<ContinentExposure.ExposedContinent> findExposureOf(String sector) {
        return ContinentExposure.findContinentExposure(
            traceContinentCoast(sector), laySpansOn(sector), PARAMETERS);
    }

    private static List<CellGap> laySpansOn(String sector) {
        return SPANS.computeIfAbsent(sector, named -> ContinentBridges.findAnchoredBridges(
            traceContinentCoast(named), CoastFrontages.Shore.EXTERIOR, PARAMETERS, SPAN_RULES));
    }
}
