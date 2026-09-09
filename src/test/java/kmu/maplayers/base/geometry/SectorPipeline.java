package kmu.maplayers.base.geometry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The shipped construction run over each real sector, once for the whole suite.
 *
 * <p>Every integration test here asks about the same map: the sectors on the classpath, traced
 * under the knobs the map ships with, with the same spans laid over the result. Held in one
 * place because a copy per suite is a second answer to what that map is - the floors and the
 * slack are what decide which stretches and spans survive, and two suites reporting on numbers
 * taken under different ones cannot be read against each other.
 *
 * <p>Cached because the pipeline is the expensive part. Tracing a coast is quadratic in a
 * sector's systems and laying either span set walks every pair of frontages, so a suite
 * building its own paid for the whole thing again - eight of them did, over two fixtures.
 *
 * <p>The knobs are {@link ShippedMap}'s, which is the geometry layer's own statement of the
 * map a reader would open - the same one every report measures against, so a number here and
 * a number in a dump are about one map.
 */
final class SectorPipeline {

    /**
     * Where the parameterised tests take their sectors from.
     *
     * <p>A constant rather than the string at each use, because a {@code @MethodSource} names
     * its provider by text: spelled out per suite, a provider that moves leaves every copy
     * pointing at nothing, and the failure is a test that silently stops running.
     */
    static final String SECTORS =
        "kmu.maplayers.base.geometry.SectorPipeline#provideSectorNames";

    /** The knobs the cells are built under. */
    static final SectorGeometryParameters PARAMETERS = ShippedMap.KNOBS;

    /** How the coast is traced. */
    static final Coastlines.CoastRules COAST_RULES = ShippedMap.COAST_RULES;

    /** The laying the map ships, which is the one worth reporting on. */
    static final ContinentBridges.BridgeRules SPAN_RULES = ShippedMap.SPAN_RULES;

    private static final Map<String, SectorFixture> FIXTURES = new ConcurrentHashMap<>();

    // One laying per sector, which keeps each of its searches once it has run it - so the
    // trace, the spans and the links below are one answer each however many suites ask.
    private static final Map<String, BridgedContinents> LAYINGS = new ConcurrentHashMap<>();

    private SectorPipeline() {
    }

    /**
     * The sectors every parameterised test runs over.
     *
     * <p>Asserts they exist, because a missing fixture set makes every suite here pass without
     * running anything - which reads as green.
     *
     * @return the sector names on the classpath
     */
    static List<String> provideSectorNames() {

        var names = SectorFixture.listSectorNames();

        assertThat(names)
            .as("no sector fixtures on the classpath")
            .isNotEmpty();

        return names;
    }

    /**
     * @param sector which sector
     * @return its sites and their names, loaded once
     */
    static SectorFixture loadFixture(String sector) {
        return FIXTURES.computeIfAbsent(sector, SectorFixture::loadSector);
    }

    /**
     * @param sector which sector
     * @return its continent coasts, traced once under the shipped knobs
     */
    static Coastlines.TracedCoasts traceContinentCoast(String sector) {
        return layContinents(sector).traceCoasts();
    }

    /**
     * The spans across the void outside the continents, anchored on the exterior coasts.
     *
     * @param sector which sector
     * @return the inlet spans, laid once
     */
    static List<CellGap> layInletSpans(String sector) {
        return layContinents(sector).layInletSpans();
    }

    /**
     * The links between continents, judged against the inlet spans as the map judges them.
     *
     * @param sector which sector
     * @return the links, laid once
     */
    static List<CellGap> layLinks(String sector) {
        return layContinents(sector).layLinks();
    }

    /**
     * The coasts with every wall the construction lays, as the pieces of void are named off.
     *
     * @param sector which sector
     * @return the laid coast, with every span set down
     */
    static LaidCoast layEveryWall(String sector) {
        return layContinents(sector).layEveryWall();
    }

    // The whole laying for one sector, opened once. Its own bridge search rather than a shared
    // one: the puddle spans are the only thing that asks it, and one sector's cache is of no
    // use to another's.
    private static BridgedContinents layContinents(String sector) {

        return LAYINGS.computeIfAbsent(sector, named -> BridgedContinents.layContinents(
            loadFixture(named).getSites(),
            PARAMETERS,
            COAST_RULES,
            SPAN_RULES,
            new VoidBridgeCache()));
    }
}
