package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

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
 * Integration coverage for the links laid between the continents, over real sectors.
 *
 * <p>What the pass is is a set of gates - two continents, in range, on frontage, clear of what
 * is already down - so what is worth pinning is that each gate actually holds on a map big
 * enough to break it. Every check asks the geometry rather than the search's own bookkeeping: a
 * link's cells are looked up in the silhouettes, its anchors matched against the traced coast,
 * and its line weighed against the spans that were already there.
 *
 * <p>The set being non-empty is checked first and on its own, because every other claim here is
 * satisfied by laying nothing at all.
 */
class IntercontinentalBridgesIntegrationTest {

    private static final String SECTORS =
        "kmu.maplayers.base.geometry.IntercontinentalBridgesIntegrationTest"
            + "#provideSectorNames";

    // Whether the spans sharing an anchor are thinned, which is how the map lays the inlet
    // spans: the thinned set is the proposal, and an unthinned one is a different laying for the
    // links to be judged against.
    private static final boolean SHOULD_THIN_FORMATIONS = true;

    // One set of geometry knobs for the whole suite, for the reason the shipped pipeline keeps
    // one: a coast traced under one set and spans laid under another describe two maps.
    private static final SectorGeometryParameters PARAMETERS =
        SectorGeometryParameters.createDefaults();

    // How far off a wall already down a span may run and still count as doubling it. The width a
    // span is drawn at, which is the shipped setting: two lines closer than that overlap on
    // screen, which is the state a reader calls doubled. Stated here rather than read off the
    // drawing, which this package may not reach into.
    private static final double COAST_SLACK = 120;

    // How close two span feet may stand before one of them moves. The shipped setting, which
    // separates feet that are coincident and leaves the rest where the search put them.
    private static final double ANCHOR_SEPARATION = 120;

    // How far off the drawn coast an anchor may be read as standing on it, in map units. Only
    // the arithmetic of interpolating along a segment is being absorbed.
    private static final double ON_THE_LINE = 1e-6;

    // The laying the map ships, which is the one worth reporting on.
    private static final ContinentBridges.BridgeRules SPAN_RULES =
        new ContinentBridges.BridgeRules(
            Coastlines.DEFAULT_RULES.bridgeReachMultiple(),
            COAST_SLACK,
            SHOULD_THIN_FORMATIONS,
            ANCHOR_SEPARATION);

    // Built once per sector and shared: tracing a coast is O(n^2) in a sector's systems, and
    // laying either set walks every pair of frontages, so each check asking for its own would
    // pay for the pipeline several times over.
    private static final Map<String, SectorFixture> FIXTURES = new ConcurrentHashMap<>();
    private static final Map<String, Coastlines.TracedCoasts> TRACES = new ConcurrentHashMap<>();
    private static final Map<String, List<CellGap>> INLET_SPANS = new ConcurrentHashMap<>();
    private static final Map<String, List<CellGap>> LINKS = new ConcurrentHashMap<>();

    static List<String> provideSectorNames() {

        var names = SectorFixture.listSectorNames();

        assertThat(names)
            .as("no sector fixtures on the classpath")
            .isNotEmpty();

        return names;
    }

    @Nested
    class FindIntercontinentalBridges {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_sector_of_several_continents_is_linked_somewhere(String sector) {
            // Asked first and alone, because every other claim below is true of an empty set.
            // A sector whose trace left several continents standing has pairs within reach of
            // each other by construction - that is what a bridge would have joined - so laying
            // nothing at all is the pass having refused everything.
            assertThat(traceCoastOf(sector).silhouettes().size())
                .as("%s: one continent only, so there is nothing to link", sector)
                .isGreaterThan(1);

            assertThat(linkContinentsOf(sector))
                .as("%s: no continent linked to any other", sector)
                .isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_link_joins_two_different_shapes(String sector) {
            // The whole of what makes a link a link. A span between two cells of one shape is
            // an inlet span - the other pass's answer - and one leaving a cell on no shape at
            // all leaves a border that faces no open void.
            //
            // Shapes rather than continents, since an island is a shape of the sector with no
            // coastline: absent from the silhouettes, and still a thing a link may join.
            var continentOf = Coastlines.mapCellsToShapes(traceCoastOf(sector));
            var misjoined = new ArrayList<String>();

            for (var link : linkContinentsOf(sector)) {

                var from = continentOf.get(link.fromSite());
                var to = continentOf.get(link.toSite());

                if (from == null || to == null || from.equals(to)) {
                    misjoined.add(String.format(
                        "cells %d-%d on continents %s and %s",
                        link.fromSite(), link.toSite(), from, to));
                }
            }

            assertThat(misjoined)
                .as("%s: a link that does not join two different continents", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_link_is_anchored_on_bridgeable_frontage(String sector) {
            // A link has to end ON the drawn coast, since one ending short of it joins nothing
            // that is on the map. Measured against the line's segments rather than its corners,
            // because a foot the spreading has moved sits wherever along its stretch the room
            // was - which is generally between two of the points the line was sampled at.
            //
            // The islands' rims among the frontages, since a cell alone in the void has no
            // coast and its whole border is where a link may land.
            var frontages = collectAnchorableFrontagesOf(sector);
            var strayed = new ArrayList<String>();

            for (var link : linkContinentsOf(sector)) {

                if (!isPointOfFrontage(link.start(), frontages.get(link.fromSite()))
                        || !isPointOfFrontage(link.end(), frontages.get(link.toSite()))) {

                    strayed.add(String.format("cells %d-%d", link.fromSite(), link.toSite()));
                }
            }

            assertThat(strayed)
                .as("%s: a link anchored off its cells' bridgeable frontage", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void no_link_crosses_a_span_already_laid(String sector) {
            // The inlet spans were laid first and are on the map. A link crossing one claims
            // void that span already holds, and the two lines drawn over each other are two
            // claims a reader cannot tell apart.
            var inlets = layInletSpansOn(sector);
            var crossing = new ArrayList<String>();

            for (var link : linkContinentsOf(sector)) {
                for (var inlet : inlets) {

                    if (!isSharingAnAnchor(link, inlet)
                            && Segments.intersectSegments(
                                link.start(), link.end(),
                                inlet.start(), inlet.end()) != null) {

                        crossing.add(String.format(
                            "link %d-%d over inlet span %d-%d",
                            link.fromSite(), link.toSite(),
                            inlet.fromSite(), inlet.toSite()));
                    }
                }
            }

            assertThat(crossing)
                .as("%s: a link crossing a span already laid", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_pair_already_joined_is_not_joined_again(String sector) {
            // The gate that says a link is not worth offering. Asked by running the search a
            // second time with its own answer standing, which is the one arrangement where the
            // gate can bite: no other set on the map joins two cells of different continents,
            // so with the shipped inputs it is never reached.
            var links = linkContinentsOf(sector);
            var standing = new ArrayList<>(layInletSpansOn(sector));

            standing.addAll(links);

            var relaid = IntercontinentalBridges.findIntercontinentalBridges(
                traceCoastOf(sector), standing, PARAMETERS, SPAN_RULES);

            var joinedTwice = new ArrayList<String>();

            for (var again : relaid) {
                for (var link : links) {

                    if (isSamePair(again, link)) {
                        joinedTwice.add(String.format(
                            "cells %d-%d", link.fromSite(), link.toSite()));
                    }
                }
            }

            assertThat(joinedTwice)
                .as("%s: a pair offered a second link with one already between them", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void a_link_reaches_a_cell_that_has_no_coast(String sector) {
            // Islands are shapes of the sector like any other. They carry no coastline, since
            // the line round a cell touching nothing would be its own border drawn twice - but
            // that is a reason not to draw one, not a reason to leave the cell unreachable.
            var traced = traceCoastOf(sector);
            var islands = new LinkedHashSet<>(traced.islands());

            assertThat(islands)
                .as("%s: no cell is alone in the void, so this check asks nothing", sector)
                .isNotEmpty();

            var reached = new LinkedHashSet<Integer>();

            for (var link : linkContinentsOf(sector)) {

                if (islands.contains(link.fromSite())) {
                    reached.add(link.fromSite());
                }
                if (islands.contains(link.toSite())) {
                    reached.add(link.toSite());
                }
            }

            assertThat(reached)
                .as("%s: not one island was linked to anything", sector)
                .isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void an_island_left_unlinked_had_nothing_within_reach(String sector) {
            // The other direction: an island the pass passed over has to be one nothing could
            // have reached. An island within the reach of another shape and still unlinked
            // would be the pass refusing a cell for having no coastline, which is the whole
            // fault this admits.
            var traced = traceCoastOf(sector);
            var sites = traced.union().sites();
            var reach = PARAMETERS.cellRadius() * SPAN_RULES.reachMultiple();
            var linked = new LinkedHashSet<Integer>();

            for (var link : linkContinentsOf(sector)) {

                linked.add(link.fromSite());
                linked.add(link.toSite());
            }

            var overlooked = new ArrayList<String>();

            for (var island : traced.islands()) {

                if (linked.contains(island)) {
                    continue;
                }

                for (var cell : collectAnchorableFrontagesOf(sector).keySet()) {

                    if (cell != island
                            && Points.computeDistance(sites.get(cell), sites.get(island))
                                <= reach) {

                        overlooked.add(String.format("island %d, cell %d near it", island, cell));
                    }
                }
            }

            assertThat(overlooked)
                .as("%s: an island was left unlinked with something within reach of it", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void every_link_joins_cells_within_reach_of_each_other(String sector) {
            // Range-based, the way the settled bridges are: two cells hold the void between
            // them only while they sit near enough to trap it, measured centre to centre.
            var sites = traceCoastOf(sector).union().sites();
            var reach = PARAMETERS.cellRadius() * SPAN_RULES.reachMultiple();
            var overreached = new ArrayList<String>();

            for (var link : linkContinentsOf(sector)) {

                var separation = Points.computeDistance(
                    sites.get(link.fromSite()), sites.get(link.toSite()));

                if (separation > reach) {
                    overreached.add(String.format(
                        "cells %d-%d at %.0f apart, over %.0f",
                        link.fromSite(), link.toSite(), separation, reach));
                }
            }

            assertThat(overreached)
                .as("%s: a link between cells further apart than the reach", sector)
                .isEmpty();
        }
    }

    // Every stretch a link may anchor on: the continents' coasts, and the islands' whole rims.
    private static Map<Integer, List<List<double[]>>> collectAnchorableFrontagesOf(String sector) {

        var traced = traceCoastOf(sector);
        var frontages = new java.util.LinkedHashMap<>(
            CoastFrontages.Shore.EXTERIOR.collectFrontages(traced));

        frontages.putAll(CoastFrontages.collectIslandFrontages(
            traced, PARAMETERS.measureArcSegments()));

        return frontages;
    }

    // Whether a point lies on the traced coast a cell offers, which is what anchoring on the
    // frontage means - on the line rather than near it.
    private static boolean isPointOfFrontage(double[] anchor, List<List<double[]>> runs) {

        if (runs == null) {
            return false;
        }

        for (var run : runs) {
            for (var index = 1; index < run.size(); index++) {

                if (Segments.computeDistanceToPoint(
                        run.get(index - 1), run.get(index), anchor) <= ON_THE_LINE) {

                    return true;
                }
            }

            if (run.size() == 1 && Points.computeDistance(run.get(0), anchor) <= ON_THE_LINE) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSamePair(CellGap one, CellGap other) {

        return Math.min(one.fromSite(), one.toSite())
                == Math.min(other.fromSite(), other.toSite())
            && Math.max(one.fromSite(), one.toSite())
                == Math.max(other.fromSite(), other.toSite());
    }

    // Whether two spans meet at a point rather than cross. Two lines leaving one anchor touch
    // there by construction, and the search allows that - so a check on crossings has to allow
    // it too or it fails on the very case the search means to permit.
    private static boolean isSharingAnAnchor(CellGap span, CellGap held) {

        return isSamePlace(span.start(), held.start())
            || isSamePlace(span.start(), held.end())
            || isSamePlace(span.end(), held.start())
            || isSamePlace(span.end(), held.end());
    }

    private static boolean isSamePlace(double[] one, double[] other) {
        return Points.computeDistance(one, other) <= DiscUnion.TOUCHING_TOLERANCE;
    }

    private static List<CellGap> linkContinentsOf(String sector) {

        return LINKS.computeIfAbsent(sector, named ->
            IntercontinentalBridges.findIntercontinentalBridges(
                traceCoastOf(named), layInletSpansOn(named), PARAMETERS, SPAN_RULES));
    }

    // The spans the map lays first, which are what a link is judged against.
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
