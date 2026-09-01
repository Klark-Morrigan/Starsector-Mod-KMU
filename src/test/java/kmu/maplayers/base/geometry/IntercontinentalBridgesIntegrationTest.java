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

    // How far along its frontage a span's foot steps off one another span already holds. The
    // shipped setting, which is several of the coast's own sampling steps: a foot lands on one
    // of the traced line's vertices, so a shorter separation picks the same vertex anyway.
    private static final double ANCHOR_SEPARATION = 800;

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
        void every_link_joins_two_different_continents(String sector) {
            // The whole of what makes a link a link. A span between two cells of one continent
            // is an inlet span - the other pass's answer - and one leaving a cell on no
            // continent leaves a shore that faces no open void at all.
            var continentOf = Coastlines.mapCellsToContinents(traceCoastOf(sector));
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
            // that is on the map. Checked as identity against the traced points rather than as
            // a distance: an anchor is one of the line's own vertices, so anything else is the
            // search having computed a place on the arc instead of taking one off the line.
            var frontages = CoastFrontages.gatherFrontagePoints(
                CoastFrontages.Shore.EXTERIOR.collectFrontages(traceCoastOf(sector)));

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

    // Whether a point is one of the traced points a cell offers, which is what anchoring on the
    // frontage means - on the line rather than near it.
    private static boolean isPointOfFrontage(double[] anchor, List<double[]> frontage) {

        if (frontage == null) {
            return false;
        }

        for (var point : frontage) {

            if (Arrays.equals(anchor, point)) {
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
