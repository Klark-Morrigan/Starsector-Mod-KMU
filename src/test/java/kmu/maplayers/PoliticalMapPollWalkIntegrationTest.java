package kmu.maplayers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.colonies.SectorColonySightings;

import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapStalenessSource;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how many times the political map's staleness poll reads the sector: once per system, for
 * the whole poll, however many passengers ride it.
 *
 * <p>Three of them ask each system who lives there - the snapshot that fingerprints the drawn
 * set, the motion walk that follows a drifting system, and the observation write that records
 * what a system's own inhabitants can see - and each is handed the poll's colony index rather
 * than the sector it was opened over. What that costs, if it is ever undone, is a walk of every
 * entity in every system in the sector, twice over, every four to five campaign seconds.
 *
 * <p>No unit can make this claim. How many times a system was walked is a fact about the
 * composition rather than about any part of it: each passenger, handed a mock, walks precisely
 * what it is told to and passes either way. So the poll, the snapshot, the motion walk and the
 * register are all real here, and the count is taken off {@code getAllEntities} - what the
 * unregistered-market half of a colony selection reaches for, and so what a second selection
 * shows up as.
 *
 * <p>It cannot be a case on {@link PoliticalMapStalenessSource}'s own suite either. That one
 * static-mocks the scan and the motion tracker, which is right for what it pins - each axis
 * routed to its own refresh, off snapshots a case states outright - and is exactly what leaves
 * it blind to how the sector was read.
 *
 * <p>Only the three reads by which the poll reaches its inputs at all are stubbed: the sector
 * lookup and the two live settings reads, neither of which anything but a running game answers.
 * Nothing standing in for a collaborator.
 */
final class PoliticalMapPollWalkIntegrationTest {

    // The stability-only weighting, standing in for the live LunaLib read. Which weights the
    // dominance fold applies reaches nothing this suite asserts; it is named only because the
    // scan resolves one.
    private static final DominanceRules STABILITY_WEIGHTED =
        SectorPoliticsFixtures.buildStabilityWeightedRules();

    private static final String ALPHA_ID = "alpha";
    private static final String BETA_ID = "beta";

    // The size the staged colony carries. Nothing here weighs a colony, so a case varying this
    // would vary nothing the poll can see.
    private static final int COLONY_SIZE = 5;

    // The size vanilla builds a derelict at: a hulk nobody lives on is created at nought.
    private static final int DERELICT_SIZE = 0;

    // Two polls, the first of which only seeds the baselines - so a change between them is the
    // only thing the second can report.
    private static final int TWO_POLLS = 2;

    private static final int ONE_POLL = 1;

    @BeforeEach
    void clearInheritedMotionObservations() {
        // The tracker is one instance for the process, so a suite driving it for real inherits
        // whatever ran before it and leaves its own behind. reset() is what a save load calls
        // for the same reason.
        MovingSystems.getInstance().reset();
    }

    @AfterEach
    void clearMotionObservationsLeftBehind() {
        MovingSystems.getInstance().reset();
    }

    @Nested
    class MarkChangesSinceLastPoll {

        @Test
        void selectsEachSystemsColoniesOnceForTheWholePoll() {
            // The step's own claim. Every passenger asks the same question of the same systems,
            // so the poll opens one reading of the sector and hands it down; three readers each
            // opening a walk of their own is what this stops.
            var sector = buildSettledSectorWithAnEmptyNeighbour();

            runOnePoll(sector);

            for (var system : sector.getStarSystems()) {
                verify(system, times(1)).getAllEntities();
            }
        }

        @Test
        void recordsWhatEachSystemsInhabitantsSeeFromThatSameSelection() {
            // The passenger that inverted: the register stopped sweeping the sector for itself
            // and now writes over the set the poll hands it, place by place. Asserted through
            // the register because that is what says the write ran on the real walk rather than
            // merely being reachable from it.
            var sector = buildSettledSectorWithAnEmptyNeighbour();
            var derelict = findOnlyDerelictIn(sector);

            SectorPoliticsFixtures.openSectorMemory(sector);
            runOnePoll(sector);

            assertThat(SectorColonySightings.readSightings(sector)
                    .readSightedLocationId(derelict.getId()))
                .isEqualTo(ALPHA_ID);
        }

        @Test
        void pollsWithoutASectorWithoutFaultingOrRefreshing() {
            // Mid-load, before the sector stands up. Every passenger is handed a reading opened
            // over nothing, so each has to answer emptily rather than fault - and a poll that
            // saw nothing must not report the drawn set as having changed.
            var geometryDelta = runPollsAndReadGeometryDelta(null, TWO_POLLS, () -> { });

            assertThat(geometryDelta)
                .isZero();
        }

        @Test
        void readsTheSectorAsItStandsOnEachPollRatherThanOffTheLastOnesReading() {
            // The other half of holding one reading per poll: it has to be this poll's. An index
            // kept between polls would answer the second one off the sector the first saw, which
            // is precisely the change a poll exists to notice - so a system settled between the
            // two would never join the drawn set and the geometry would never rebuild for it.
            var sector = buildSettledSectorWithAnEmptyNeighbour();

            var geometryDelta = runPollsAndReadGeometryDelta(
                sector,
                TWO_POLLS,
                () -> settleTheEmptyNeighbour(sector));

            assertThat(geometryDelta)
                .isEqualTo(1);
        }
    }

    // One poll of the real source over a sector nothing disturbs - what a case asserting on
    // what the poll read, rather than on what it decided, wants.
    private static void runOnePoll(SectorAPI sector) {
        runPollsAndReadGeometryDelta(sector, ONE_POLL, () -> { });
    }

    // Drives the real poll pollCount times and reports how far the geometry revision moved.
    // MapLayerRefresh is left real, so the revision is read as a delta and the stale set drained
    // either side, isolating the run from whatever else marked the shared counters.
    //
    // The interlude runs after the first poll, which is where a case moves the sector under a
    // poll that has already read it.
    private static int runPollsAndReadGeometryDelta(
            SectorAPI sector,
            int pollCount,
            Runnable moveTheSectorAfterTheFirstPoll) {

        try (var globalMock = mockStatic(Global.class);
                var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                var dominanceRulesMock = mockStatic(DominanceRules.class)) {

            globalMock
                .when(Global::getSector)
                .thenReturn(sector);
            globalMock
                .when(() -> Global.getLogger(any(Class.class)))
                .thenReturn(mock(Logger.class));

            visibilityRulesMock
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(MapVisibilityRules.BASE);

            dominanceRulesMock
                .when(DominanceRules::readFromLunaSettings)
                .thenReturn(STABILITY_WEIGHTED);

            MapLayerRefresh.drainStaleGroupingSystemIds();

            var geometryBefore = MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY);
            var stalenessSource = new PoliticalMapStalenessSource();

            for (var poll = 0; poll < pollCount; poll++) {

                stalenessSource.markChangesSinceLastPoll();

                if (poll == 0) {
                    moveTheSectorAfterTheFirstPoll.run();
                }
            }
            MapLayerRefresh.drainStaleGroupingSystemIds();

            return MapLayerRefresh.getRevision(MapLayerCommonRefreshSignal.GEOMETRY)
                - geometryBefore;
        }
    }

    // Two star systems: one settled by an open colony with a derelict standing beside it, and one
    // empty. The derelict is what gives the observation write something to record, and the empty
    // neighbour is what a later poll can settle.
    //
    // Both carry a hyperspace position, without which the motion walk skips them before ever
    // asking the drawn-set rule about them - and the count this suite takes would then be blind
    // to the walk it most needs to watch.
    private static SectorAPI buildSettledSectorWithAnEmptyNeighbour() {

        var hegemony = SectorPoliticsFixtures.buildFaction("hegemony");
        var colony = SectorPoliticsFixtures.buildVisibleMarket(hegemony, COLONY_SIZE);

        var sector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(hegemony),
            listSystemMarkets(ALPHA_ID, colony),
            listSystemMarkets(BETA_ID));

        var alpha = findSystemIn(sector, ALPHA_ID);

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(
            alpha,
            SectorPoliticsFixtures.buildAbandonedStationMarket(DERELICT_SIZE));

        for (var system : sector.getStarSystems()) {
            placeSystemInHyperspace(system);
        }
        return sector;
    }

    // Settles the empty neighbour, which puts it on the drawn set and so moves the visibility
    // fingerprint - the change a stale reading of the sector could not report.
    private static void settleTheEmptyNeighbour(SectorAPI sector) {

        var beta = findSystemIn(sector, BETA_ID);
        var colony = SectorPoliticsFixtures.buildVisibleMarket(
            SectorPoliticsFixtures.buildFaction("tritachyon"),
            COLONY_SIZE);

        when(sector.getEconomy().getMarkets(beta))
            .thenReturn(List.of(colony));
    }

    // The one market hung on a system entity rather than listed - the derelict, since a listed
    // hulk is an outpost rather than a wreck. Read back off the fixture so the case names the
    // register's key without a second builder stating what was staged.
    private static MarketAPI findOnlyDerelictIn(SectorAPI sector) {
        return findSystemIn(sector, ALPHA_ID).getAllEntities().get(0).getMarket();
    }

    private static StarSystemAPI findSystemIn(SectorAPI sector, String systemId) {

        for (var system : sector.getStarSystems()) {

            if (systemId.equals(system.getId())) {
                return system;
            }
        }
        throw new IllegalArgumentException("No system staged under the id " + systemId);
    }

    // A hyperspace position, distinct per system so no two share a site. Any position will do -
    // nothing here moves - so the id's hash is spread across the two axes rather than a case
    // being handed coordinates that look like they mean something.
    private static void placeSystemInHyperspace(StarSystemAPI system) {

        var idHash = system.getId().hashCode();

        when(system.getLocation())
            .thenReturn(new Vector2f(idHash, -idHash));
    }
}
