package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for spreading crowded span feet, over real sectors.
 *
 * <p>The pass only ever MOVES a foot, so what is worth pinning is the four things a move must
 * not break and the one thing it is for. It must keep every foot on the drawn coast, it must
 * lay no line across another, it must leave the set of spans exactly as it found it, and it
 * must not fidget - a foot nothing else wanted stays where the search put it. What it is for is
 * that fewer feet stand on one place afterwards than before.
 *
 * <p>Asked of the pass directly, over a laying the shipped pipeline produced with the spreading
 * switched off. That is the one comparison that isolates it: the same spans, in the same order,
 * differing in nothing but where their ends stand. Two whole pipelines cannot be compared that
 * way any more, since spreading runs before the thinning and so changes which spans the
 * thinning drops - which is a claim of its own, and the one test here that asks it.
 */
class CrowdedAnchorsIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.CrowdedAnchorsIntegrationTest#provideSectorNames";

    private static final boolean SHOULD_THIN_FORMATIONS = true;

    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    // How far off a wall already down a span may run and still count as doubling it, in map
    // units. The shipped setting; nothing below turns on the number.
    private static final double COAST_SLACK = 120;

    // How far along its frontage a foot steps off one another span holds. The shipped setting,
    // which is several of the coast's own sampling steps - a foot lands on one of the traced
    // line's vertices, so a separation under one step selects the same vertex as any other.
    private static final double ANCHOR_SEPARATION = 800;

    // What a laying is asked for with the spreading off, which is what a non-positive
    // separation means to the pass.
    private static final double NO_SPREADING = 0;

    // How close two feet have to be to count as standing on one place. The tolerance two spans
    // are said to share an anchor at, since that is the state the pass exists to undo.
    private static final double ONE_PLACE = DiscUnion.TOUCHING_TOLERANCE;

    // Slack on "did this foot move at least the separation along its frontage", in map units.
    // A foot lands on one of the run's own points rather than at an exact distance along it, so
    // the rule is met to within one sampling step; this is well under the shortest step the
    // coast is sampled at and only absorbs the arithmetic.
    private static final double ALONG_SLACK = 1e-6;

    private static final Map<String, SectorFixture> FIXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Coastlines.TracedCoasts> TRACES = new ConcurrentHashMap<>();
    private static final Map<String, List<CellGap>> SPREAD = new ConcurrentHashMap<>();
    private static final Map<String, List<CellGap>> UNSPREAD = new ConcurrentHashMap<>();

    static List<String> provideSectorNames() {

        var names = SectorFixture.listSectorNames();

        assertThat(names)
            .as("no sector fixtures on the classpath")
            .isNotEmpty();

        return names;
    }

    @Nested
    class SpreadCrowdedAnchors {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void fewer_feet_stand_on_one_place_than_before(String sector) {
            // What the pass is for. Not all of them: a frontage of one point has nowhere to
            // step, and a run whose every point is taken or would put the line across another
            // span has nowhere worth stepping - so what is claimed is that it helps, not that
            // it cures.
            var before = countCrowdedFeet(layUnspreadOn(sector));
            var after = countCrowdedFeet(laySpreadOn(sector));

            assertThat(before)
                .as("%s: no crowded feet to spread, so this check asks nothing", sector)
                .isPositive();

            assertThat(after)
                .as("%s: spreading left as many feet on one place as it found", sector)
                .isLessThan(before);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void no_span_crosses_another_after_a_foot_moves(String sector) {
            // Moving a foot swings the whole span, so a move is exactly the operation that can
            // lay a line across one already down. The selection refused every crossing before
            // the feet moved; the count has to still be nought afterwards.
            var crossing = new ArrayList<String>();
            var spans = laySpreadOn(sector);

            for (var one = 0; one < spans.size(); one++) {
                for (var other = one + 1; other < spans.size(); other++) {

                    var first = spans.get(one);
                    var second = spans.get(other);

                    if (!isSharingAnAnchor(first, second)
                            && Segments.intersectSegments(
                                first.start(), first.end(),
                                second.start(), second.end()) != null) {

                        crossing.add(String.format(
                            "%d-%d over %d-%d",
                            first.fromSite(), first.toSite(),
                            second.fromSite(), second.toSite()));
                    }
                }
            }

            assertThat(crossing)
                .as("%s: a span crossing another once the feet had moved", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_foot_still_stands_on_the_drawn_coast(String sector) {
            // The invariant every anchor rests on: a foot is one of the traced line's own
            // points, so the pieces are cut against the line rather than near it. A move that
            // computed a place at an exact distance along the arc would sit off its chord.
            var frontages = CoastFrontages.gatherFrontagePoints(
                CoastFrontages.Shore.EXTERIOR.collectFrontages(traceCoastOf(sector)));

            var strayed = new ArrayList<String>();

            for (var span : laySpreadOn(sector)) {

                if (!isPointOfFrontage(span.start(), frontages.get(span.fromSite()))
                        || !isPointOfFrontage(span.end(), frontages.get(span.toSite()))) {

                    strayed.add(String.format("%d-%d", span.fromSite(), span.toSite()));
                }
            }

            assertThat(strayed)
                .as("%s: a foot off the coast it is supposed to stand on", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void moving_a_foot_saves_a_span_the_thinning_would_have_dropped(String sector) {
            // Why the spreading runs before the thinning. A span given a foot of its own is
            // still on the map; a span dropped for sharing one is a piece of void nothing
            // holds. So a laying that spreads first has to carry MORE spans than one that
            // thins first - every extra one is a span that had somewhere to stand.
            assertThat(layBothSetsOn(sector, ANCHOR_SEPARATION))
                .as("%s: spreading saved no span from the thinning", sector)
                .hasSizeGreaterThan(layUnspreadOn(sector).size());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void no_span_is_dropped_or_rejoined_by_moving_its_feet(String sector) {
            // The pass moves feet and nothing else. Which spans exist and which cells each
            // joins are settled before it runs, so a set that came back a different size, or
            // in a different order, would mean the spreading had started making that decision
            // too.
            var spread = laySpreadOn(sector);
            var unspread = layUnspreadOn(sector);

            assertThat(spread).hasSameSizeAs(unspread);

            for (var index = 0; index < spread.size(); index++) {

                assertThat(spread.get(index).fromSite())
                    .as("%s: span %d joins a different cell once spread", sector, index)
                    .isEqualTo(unspread.get(index).fromSite());

                assertThat(spread.get(index).toSite())
                    .as("%s: span %d joins a different cell once spread", sector, index)
                    .isEqualTo(unspread.get(index).toSite());
            }
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_foot_nothing_else_wanted_is_left_where_it_stood(String sector) {
            // The pass must not fidget. A foot moves because something is already standing on
            // it, so a foot that moved off a place nothing else held would be the rule firing
            // where it was not owed - and every such move is a span drawn somewhere other than
            // where the search put it, for no reason a reader could find on the map.
            var spread = laySpreadOn(sector);
            var unspread = layUnspreadOn(sector);
            var feet = collectFeet(unspread);
            var fidgeted = new ArrayList<String>();

            for (var index = 0; index < spread.size(); index++) {

                var was = unspread.get(index);
                var now = spread.get(index);

                if (!isSamePlace(now.start(), was.start())
                        && countStandingOn(was.start(), feet) < 2) {

                    fidgeted.add(String.format("%d-%d, first foot", was.fromSite(), was.toSite()));
                }
                if (!isSamePlace(now.end(), was.end())
                        && countStandingOn(was.end(), feet) < 2) {

                    fidgeted.add(String.format("%d-%d, second foot", was.fromSite(), was.toSite()));
                }
            }

            assertThat(fidgeted)
                .as("%s: a foot moved off a place nothing else was standing on", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_moved_foot_is_one_of_the_places_the_rule_names(String sector) {
            // The rule itself: a foot moves the separation along its frontage, or to that
            // stretch's end when it has less room than that, or - when both ends are spoken
            // for - inward, toward the middle. Any other landing would mean the move had been
            // decided by something other than the room available.
            //
            // The inward case is not pinned to the middle POINT. Where everything better is
            // barred the foot takes what it can, which is often a single sampling place off the
            // crowded end; what makes that the same answer is the direction, since the only
            // room left on such a stretch is between its two ends.
            var runs = CoastFrontages.Shore.EXTERIOR.collectFrontages(traceCoastOf(sector));
            var spread = laySpreadOn(sector);
            var unspread = layUnspreadOn(sector);
            var landed = new ArrayList<String>();

            for (var index = 0; index < spread.size(); index++) {

                var was = unspread.get(index);
                var now = spread.get(index);

                if (!isSamePlace(now.start(), was.start())
                        && !isPlaceTheRuleAllows(
                            runs.get(was.fromSite()), was.start(), now.start())) {

                    landed.add(String.format("%d-%d, first foot", was.fromSite(), was.toSite()));
                }
                if (!isSamePlace(now.end(), was.end())
                        && !isPlaceTheRuleAllows(runs.get(was.toSite()), was.end(), now.end())) {

                    landed.add(String.format("%d-%d, second foot", was.fromSite(), was.toSite()));
                }
            }

            assertThat(landed)
                .as("%s: a foot moved somewhere the rule does not name", sector)
                .isEmpty();
        }
    }

    // Whether a moved foot is one of the landings the rule allows: the separation or more along
    // the same stretch of coast, one of that stretch's ends, or a place further into it than the
    // foot started - which is the inward answer for a stretch that can offer neither.
    private static boolean isPlaceTheRuleAllows(
            List<List<double[]>> runs,
            double[] was,
            double[] now) {

        var run = findRunHolding(runs, was);

        if (run == null) {
            return false;
        }

        var from = indexOfPoint(run, was);
        var to = indexOfPoint(run, now);

        if (to == -1) {
            return false;
        }

        var along = measureAlongRun(run);
        var middle = along[run.size() - 1] / 2;

        return Math.abs(along[to] - along[from]) >= ANCHOR_SEPARATION - ALONG_SLACK
            || to == 0
            || to == run.size() - 1
            || Math.abs(along[to] - middle) < Math.abs(along[from] - middle);
    }

    private static double[] measureAlongRun(List<double[]> run) {

        var along = new double[run.size()];

        for (var index = 1; index < run.size(); index++) {

            along[index] = along[index - 1]
                + Points.computeDistance(run.get(index - 1), run.get(index));
        }
        return along;
    }

    private static List<double[]> findRunHolding(List<List<double[]>> runs, double[] point) {

        if (runs == null) {
            return null;
        }

        for (var run : runs) {

            if (indexOfPoint(run, point) != -1) {
                return run;
            }
        }
        return null;
    }

    private static int indexOfPoint(List<double[]> run, double[] point) {

        for (var index = 0; index < run.size(); index++) {

            if (Arrays.equals(run.get(index), point)) {
                return index;
            }
        }
        return -1;
    }

    // How many feet of a laying stand where another one already does.
    private static int countCrowdedFeet(List<CellGap> spans) {

        var feet = collectFeet(spans);
        var crowded = 0;

        for (var foot : feet) {

            if (countStandingOn(foot, feet) > 1) {
                crowded++;
            }
        }
        return crowded;
    }

    private static int countStandingOn(double[] foot, List<double[]> feet) {

        var standing = 0;

        for (var other : feet) {

            if (isSamePlace(foot, other)) {
                standing++;
            }
        }
        return standing;
    }

    private static List<double[]> collectFeet(List<CellGap> spans) {

        var feet = new ArrayList<double[]>(spans.size() * 2);

        for (var span : spans) {

            feet.add(span.start());
            feet.add(span.end());
        }
        return feet;
    }

    private static boolean isPointOfFrontage(double[] foot, List<double[]> frontage) {

        if (frontage == null) {
            return false;
        }

        for (var point : frontage) {

            if (Arrays.equals(foot, point)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSharingAnAnchor(CellGap span, CellGap held) {

        return isSamePlace(span.start(), held.start())
            || isSamePlace(span.start(), held.end())
            || isSamePlace(span.end(), held.start())
            || isSamePlace(span.end(), held.end());
    }

    private static boolean isSamePlace(double[] one, double[] other) {
        return Points.computeDistance(one, other) <= ONE_PLACE;
    }

    // The pass run over a crowded laying, rather than two whole pipelines compared. Spreading
    // now runs BEFORE the thinning, so a laying with it on has spans a laying with it off had
    // dropped - the two no longer answer about the same set, and every claim below is about
    // what one span's foot did, which only means anything against the foot it started on.
    private static List<CellGap> laySpreadOn(String sector) {

        return SPREAD.computeIfAbsent(sector, named -> CrowdedAnchors.spreadCrowdedAnchors(
            layUnspreadOn(named),
            List.of(),
            CoastFrontages.Shore.EXTERIOR.collectFrontages(traceCoastOf(named)),
            traceCoastOf(named).union(),
            ANCHOR_SEPARATION));
    }

    // Both sets together, because a link and an inlet span crowd each other as readily as two
    // of a kind do - and the pass is only asked about the links once the inlet spans are down.
    private static List<CellGap> layUnspreadOn(String sector) {
        return UNSPREAD.computeIfAbsent(sector, named -> layBothSetsOn(named, NO_SPREADING));
    }

    private static List<CellGap> layBothSetsOn(String sector, double separation) {

        var rules = new ContinentBridges.BridgeRules(
            Coastlines.DEFAULT_RULES.bridgeReachMultiple(),
            COAST_SLACK,
            SHOULD_THIN_FORMATIONS,
            separation);

        var traced = traceCoastOf(sector);

        var inlets = ContinentBridges.findAnchoredBridges(
            traced, CoastFrontages.Shore.EXTERIOR, PARAMETERS, rules);

        var laid = new ArrayList<>(inlets);

        laid.addAll(IntercontinentalBridges.findIntercontinentalBridges(
            traced, inlets, PARAMETERS, rules));

        return List.copyOf(laid);
    }

    private static Coastlines.TracedCoasts traceCoastOf(String sector) {

        return TRACES.computeIfAbsent(sector, named -> Coastlines.traceContinentCoasts(
            buildFixtureFor(named).getSites(), PARAMETERS, Coastlines.DEFAULT_RULES));
    }

    private static SectorFixture buildFixtureFor(String sector) {
        return FIXTURES.computeIfAbsent(sector, SectorFixture::loadSector);
    }
}
