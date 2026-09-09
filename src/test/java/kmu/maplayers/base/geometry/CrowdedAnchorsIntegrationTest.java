package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static kmu.maplayers.base.geometry.SectorPipeline.PARAMETERS;
import static kmu.maplayers.base.geometry.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for spreading crowded span feet, over real sectors.
 *
 * <p>The pass only ever MOVES a foot, so what is worth pinning is the four things a move must
 * not break and the one thing it is for. It must keep every foot on the drawn coast, it must lay
 * no line across another, it must leave the set of spans exactly as it found it, and it must not
 * fidget - a foot with room already stays where the search put it. What it is for is that fewer
 * feet stand within the separation of another afterwards than before.
 *
 * <p>Asked of the pass directly, over a laying the shipped pipeline produced with the spreading
 * switched off. That is the one comparison that isolates it: the same spans, in the same order,
 * differing in nothing but where their ends stand. Two whole pipelines cannot be compared that
 * way any more, since spreading runs before the thinning and so changes which spans the
 * thinning drops - which is a claim of its own, and the one test here that asks it.
 */
class CrowdedAnchorsIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    private static final boolean SHOULD_THIN_FORMATIONS = true;

    // How far off a wall already down a span may run and still count as doubling it, in map
    // units. The shipped setting; nothing below turns on the number.
    private static final double COAST_SLACK = 120;

    // How close two feet may stand before one moves. Well above what the map ships with, and
    // deliberately so: the shipped setting only separates feet that are coincident, and a suite
    // that asked about that alone would exercise the rule for choosing a place hardly at all.
    // Several of the coast's own sampling steps, so the pass has real decisions to make.
    private static final double ANCHOR_SEPARATION = 800;

    // What a laying is asked for with the spreading off, which is what a non-positive
    // separation means to the pass.
    private static final double NO_SPREADING = 0;

    // How close two feet have to be to be one place rather than two, which is only ever asked
    // to tell whether a foot moved at all. What counts as CROWDED is the separation, hundreds
    // of times this.
    private static final double ONE_PLACE = DiscUnion.TOUCHING_TOLERANCE;

    // What is passed as "which of these feet is the one being measured" when it is not one of
    // them, so no entry is skipped.
    private static final int NOT_IN_THE_LIST = -1;

    // How far off the drawn coast a foot may be read as standing on it, in map units. Only the
    // arithmetic of interpolating along a segment is being absorbed - a foot genuinely off the
    // line misses by a sagitta, which on these cells is tens of units.
    private static final double ON_THE_LINE = 1e-6;

    private static final Map<String, List<CellGap>> SPREAD = new ConcurrentHashMap<>();
    private static final Map<String, List<CellGap>> UNSPREAD = new ConcurrentHashMap<>();

    @Nested
    class SpreadCrowdedAnchors {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void fewer_feet_stand_within_the_separation_than_before(String sector) {
            // What the pass is for. Not all of them: a frontage of one point has nowhere to
            // step, one hemmed in on both sides has nowhere better, and a place that would put
            // the line across another span is no place at all - so what is claimed is that it
            // helps, not that it cures.
            var before = countCrowdedFeet(layUnspreadOn(sector));
            var after = countCrowdedFeet(laySpreadOn(sector));

            assertThat(before)
                .as("%s: no crowded feet to spread, so this check asks nothing", sector)
                .isPositive();

            assertThat(after)
                .as("%s: spreading left as many feet crowded as it found", sector)
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
            // The invariant every anchor rests on: a foot sits ON the traced line, so the
            // pieces are cut against the line rather than near it. A place worked out on the
            // cell's true arc would sit off its chord by the sagitta - near the coast, not on
            // it - which is a fault no drawing shows and every measurement inherits.
            //
            // On the line, not at one of its corners. A foot moves to wherever along its own
            // stretch the room is, which is generally between two of the points the line was
            // sampled at.
            // The islands' rims among them, since a cell alone in the void has no coast and
            // its whole border is where a foot may stand.
            var traced = traceContinentCoast(sector);
            var frontages = new java.util.LinkedHashMap<>(
                CoastFrontages.Shore.EXTERIOR.collectFrontages(traced));

            frontages.putAll(CoastFrontages.collectIslandFrontages(
                traced, PARAMETERS.measureArcSegments()));

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
        void a_foot_only_moves_off_a_place_something_stands_too_close_to(String sector) {
            // The pass must not fidget: a foot moves because another is inside the separation
            // of it, so one that left a place with room to spare would be the rule firing where
            // it was not owed - a span drawn somewhere other than where the search put it, for
            // no reason a reader could find on the map.
            //
            // The crowd is looked for in the FINAL laying rather than the one the pass started
            // from, because a foot is crowded by whatever is beside it WHEN IT IS LOOKED AT.
            // Resolving one fan can put a foot next to a neighbour that had room until then,
            // and that neighbour moving in its turn is the pass carrying on rather than
            // fidgeting.
            var spread = laySpreadOn(sector);
            var unspread = layUnspreadOn(sector);
            var fidgeted = new ArrayList<String>();

            for (var index = 0; index < spread.size(); index++) {
                var others = collectFeetExcept(spread, index);

                for (var side = 0; side < 2; side++) {
                    var from = side == 0
                        ? unspread.get(index).start() : unspread.get(index).end();

                    var to = side == 0 ? spread.get(index).start() : spread.get(index).end();

                    if (!isSamePlace(from, to)
                            && measureClearance(from, others) >= ANCHOR_SEPARATION) {
                        fidgeted.add(String.format(
                            "%d-%d foot %d, which had %.0f of room",
                            spread.get(index).fromSite(), spread.get(index).toSite(), side,
                            measureClearance(from, others)));
                    }
                }
            }

            assertThat(fidgeted)
                .as("%s: a foot moved off a place that had room", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_moved_foot_stands_better_than_where_it_left(String sector) {
            // The rule itself, and the whole of it. A foot goes to the nearest place clear of
            // every other by the separation, or failing that to the place with the most room on
            // its stretch - so a move that did not buy room is a move made for some other
            // reason, and there is no other reason to make one.
            //
            // Both places are weighed against where the feet FINALLY stand, which is the only
            // reading a viewer of the map can take. Weighed against the laying the pass started
            // from, a foot that moved early and was then crowded by a later arrival reads as
            // having lost room - a fact about the order the spans were looked at rather than
            // about the map.
            var spread = laySpreadOn(sector);
            var unspread = layUnspreadOn(sector);
            var pointless = new ArrayList<String>();

            for (var index = 0; index < spread.size(); index++) {
                // Its own span's two feet left out: a foot is nought from itself, and the far
                // end of its own span moves with it.
                var settled = collectFeetExcept(spread, index);

                for (var side = 0; side < 2; side++) {
                    var from = side == 0
                        ? unspread.get(index).start() : unspread.get(index).end();

                    var to = side == 0 ? spread.get(index).start() : spread.get(index).end();

                    if (!isSamePlace(from, to)
                            && measureClearance(to, settled) <= measureClearance(from, settled)) {
                        pointless.add(String.format(
                            "%d-%d foot %d: left %.0f of room for %.0f",
                            spread.get(index).fromSite(), spread.get(index).toSite(), side,
                            measureClearance(from, settled), measureClearance(to, settled)));
                    }
                }
            }

            assertThat(pointless)
                .as("%s: a foot moved without gaining room", sector)
                .isEmpty();
        }
    }

    // How many feet of a laying stand within the separation of another - which is what the pass
    // means by crowded, and so what "helped" has to be counted in.
    //
    // Every foot is compared with every other BY POSITION IN THE LIST rather than by distance,
    // which matters at exactly the case this is about: two feet on one point are nought apart,
    // and a counter that skipped everything nearer than a hair would call the worst crowding on
    // the map no crowding at all - then count the same pair as crowded once the pass had moved
    // them a few hundred units apart, and report the cure as the disease.
    private static int countCrowdedFeet(List<CellGap> spans) {
        var feet = collectFeet(spans);
        var crowded = 0;

        for (var index = 0; index < feet.size(); index++) {
            if (measureClearanceApartFrom(feet.get(index), feet, index) < ANCHOR_SEPARATION) {
                crowded++;
            }
        }
        return crowded;
    }

    // The distance to the nearest other foot.
    private static double measureClearance(double[] foot, List<double[]> feet) {
        return measureClearanceApartFrom(foot, feet, NOT_IN_THE_LIST);
    }

    private static double measureClearanceApartFrom(
            double[] foot,
            List<double[]> feet,
            int own) {
        var nearest = Double.MAX_VALUE;

        for (var index = 0; index < feet.size(); index++) {
            if (index != own) {
                nearest = Math.min(nearest, Points.computeDistance(foot, feet.get(index)));
            }
        }
        return nearest;
    }

    // Every foot of a laying but the two belonging to one span, which is what that span's own
    // feet have to be measured against - a foot is always nought from itself.
    private static List<double[]> collectFeetExcept(List<CellGap> spans, int own) {
        var feet = new ArrayList<double[]>(spans.size() * 2);

        for (var index = 0; index < spans.size(); index++) {
            if (index != own) {
                feet.add(spans.get(index).start());
                feet.add(spans.get(index).end());
            }
        }
        return feet;
    }

    private static List<double[]> collectFeet(List<CellGap> spans) {
        var feet = new ArrayList<double[]>(spans.size() * 2);

        for (var span : spans) {
            feet.add(span.start());
            feet.add(span.end());
        }
        return feet;
    }

    // Whether a foot lies on a cell's own drawn coast, corner or not.
    private static boolean isPointOfFrontage(double[] foot, List<List<double[]>> runs) {
        if (runs == null) {
            return false;
        }

        for (var run : runs) {
            for (var index = 1; index < run.size(); index++) {
                if (Segments.computeDistanceToPoint(
                        run.get(index - 1), run.get(index), foot) <= ON_THE_LINE) {
                    return true;
                }
            }

            if (run.size() == 1 && Points.computeDistance(run.get(0), foot) <= ON_THE_LINE) {
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
            CoastFrontages.Shore.EXTERIOR.collectFrontages(traceContinentCoast(named)),
            traceContinentCoast(named).union(),
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

        var traced = traceContinentCoast(sector);

        var inlets = ContinentBridges.findAnchoredBridges(
            traced, CoastFrontages.Shore.EXTERIOR, PARAMETERS, rules);

        var laid = new ArrayList<>(inlets);

        laid.addAll(IntercontinentalBridges.findIntercontinentalBridges(
            traced, inlets, PARAMETERS, rules));

        return List.copyOf(laid);
    }
}
