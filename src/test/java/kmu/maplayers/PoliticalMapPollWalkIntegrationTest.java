package kmu.maplayers;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.profiling.ProfileCounter;
import kmlib.profiling.ProfileSection;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.starsector.SectorWalkCounters;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.visibility.colonies.FactionAllianceRegistry;
import kmu.maplayers.base.visibility.colonies.FactionAllianceSource;
import kmu.maplayers.base.visibility.colonies.FactionAlliances;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapStalenessSource;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins what one poll of the political map's staleness source costs: each system walked once for
 * the whole poll however many passengers ride it, and each sector-wide fact folded once for the
 * whole poll however many systems it walks.
 *
 * <p>Three of them ask each system who lives there - the snapshot that fingerprints the drawn
 * set, the motion walk that follows a drifting system, and the observation write that records
 * what a system's own inhabitants can see - and each is handed the poll's own pass rather than
 * the sector it was opened over. What that costs, if it is ever undone, is a walk of every
 * entity in every system in the sector, twice over, every four to five campaign seconds.
 *
 * <p>No unit can make this claim. How many times a system was walked is a fact about the
 * composition rather than about any part of it: each passenger, handed a mock, walks precisely
 * what it is told to and passes either way. So the poll, the snapshot, the motion walk and the
 * register are all real here, and the count is read off the profiler's own walk counters, which
 * the library's shared reads add as they traverse - the same witness a reader of a capture has in
 * play, rather than however many times one vanilla method happened to be called.
 *
 * <p>Those counts land on the profiler's reserved row, because a poll opens no section: it runs on
 * the campaign thread on a coarse interval rather than inside a profiled frame, so what it
 * traverses is exactly what a capture reports as unscoped.
 *
 * <p>It cannot be a case on {@link PoliticalMapStalenessSource}'s own suite either. That one
 * static-mocks the scan and the motion tracker, which is right for what it pins - each axis
 * routed to its own refresh, off snapshots a case states outright - and is exactly what leaves
 * it blind to how the sector was read.
 *
 * <p>Only the reads by which the poll reaches its inputs at all are stubbed: the two live settings
 * reads and the logger, none of which anything but a running game answers. The sector is not among
 * them - a poll walks the one its installation was made over, so it is staged by installing on it.
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

    // Nothing this suite claims is a duration, so one reading answers every clock read the capture
    // makes - and a poll that took no time is still a poll that traversed what it traversed.
    private static final long FIXED_CLOCK_NANOS = 0L;

    // What the poll opens over the sector: one traversal for the whole poll, however many
    // passengers ride it.
    private static final long ONE_SECTOR_WALK = 1L;

    // And two for two polls, each reading the sector as it stands rather than off the last one's.
    private static final long ONE_SECTOR_WALK_PER_POLL = 2L;

    // The staged sector's colonies - the open one and the derelict beside it - each selected once
    // for the whole poll. A passenger opening a selection of its own reads both again, so the
    // count doubles rather than moving by one.
    private static final long TWO_COLONIES_SELECTED_ONCE = 2L;

    @Nested
    class MarkChangesSinceLastPoll {

        @Test
        void selectsEachSystemsColoniesOnceForTheWholePoll() {
            // The step's own claim. Every passenger asks the same question of the same systems,
            // so the poll opens one reading of the sector and hands it down; three readers each
            // opening a selection of their own is what this stops, and each would show here as
            // another reading of every colony in the sector.
            var poll = capturePollCountsOver(buildSettledSectorWithAnEmptyNeighbour(), ONE_POLL);

            assertThat(readCount(poll, SectorWalkCounters.COLONIES_READ))
                .isEqualTo(TWO_COLONIES_SELECTED_ONCE);
        }

        @Test
        void walksTheSectorOnceForTheWholePoll() {
            // The traversal the selection above is opened over, counted where the library actually
            // makes it. A passenger going looking for the system list on its own is a second walk
            // whether or not it then re-selects anybody's colonies.
            var poll = capturePollCountsOver(buildSettledSectorWithAnEmptyNeighbour(), ONE_POLL);

            assertThat(readCount(poll, SectorWalkCounters.SECTOR_WALKS))
                .isEqualTo(ONE_SECTOR_WALK);
        }

        @Test
        void walksTheSectorAgainOnASecondPoll() {
            // The counter's other half: one walk per poll rather than one walk ever. A reading kept
            // between polls would answer the second off the sector the first saw, which is the
            // change a poll exists to notice - and would show here as the walk that never happened.
            var polls = capturePollCountsOver(buildSettledSectorWithAnEmptyNeighbour(), TWO_POLLS);

            assertThat(readCount(polls, SectorWalkCounters.SECTOR_WALKS))
                .isEqualTo(ONE_SECTOR_WALK_PER_POLL);
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
                    .readObservation(derelict.getId())
                    .locationId())
                .isEqualTo(ALPHA_ID);
        }

        @Test
        void foldsTheAllianceSetAsOftenForALargeSectorAsForASmallOne() {
            // Who would keep a colony's secret is a sector-wide fact, folded where a reading of
            // the sector is opened - so the count follows how many readings the poll opens, and
            // never how many systems it walks. Asserted as a comparison rather than against a
            // number, since the poll opening one more reading later is a change to this claim's
            // arithmetic and not to the claim.
            var foldsOverTwoSystems = countAllianceFoldsInOnePollOver(
                buildSettledSectorWithAnEmptyNeighbour());

            var foldsOverFiveSystems = countAllianceFoldsInOnePollOver(
                buildSettledSectorWithEmptyNeighbours(4));

            assertThat(foldsOverFiveSystems)
                .isEqualTo(foldsOverTwoSystems);
        }

        @Test
        void pollsWithoutASectorWithoutFaultingOrRefreshing() {
            // Mid-load, before the sector stands up. Every passenger is handed a reading opened
            // over nothing, so each has to answer emptily rather than fault - and a poll that
            // saw nothing must not report the drawn set as having changed.
            var geometryRevision = runPollsAndReadGeometryRevision(null, TWO_POLLS, () -> { });

            assertThat(geometryRevision)
                .isZero();
        }

        @Test
        void readsTheSectorAsItStandsOnEachPollRatherThanOffTheLastOnesReading() {
            // The other half of holding one reading per poll: it has to be this poll's. An index
            // kept between polls would answer the second one off the sector the first saw, which
            // is precisely the change a poll exists to notice - so a system settled between the
            // two would never join the drawn set and the geometry would never rebuild for it.
            var sector = buildSettledSectorWithAnEmptyNeighbour();

            var geometryRevision = runPollsAndReadGeometryRevision(
                sector,
                TWO_POLLS,
                () -> settleTheEmptyNeighbour(sector));

            assertThat(geometryRevision)
                .isEqualTo(1);
        }
    }

    // One poll of the real source over a sector nothing disturbs - what a case asserting on
    // what the poll read, rather than on what it decided, wants.
    private static void runOnePoll(SectorAPI sector) {
        runPollsAndReadGeometryRevision(sector, ONE_POLL, () -> { });
    }

    // What pollCount polls over sector traversed, read off the row every count made with no scope
    // open lands on. That reserved row is where a poll's counts land in play as well: the watcher
    // runs on the campaign thread rather than inside a profiled frame, so nothing brackets it.
    //
    // The profiler is bound and taken back by the capture, the holder being process state that
    // would otherwise follow this suite into whatever runs next.
    private static ProfileNode capturePollCountsOver(SectorAPI sector, int pollCount) {

        var profiler = new RecordingProfiler(() -> FIXED_CLOCK_NANOS);

        return RecordedCapture
            .recordWhile(
                profiler,
                () -> runPollsAndReadGeometryRevision(sector, pollCount, () -> { }))
            .findNode(ProfileSection.UNSCOPED_COUNTS.getName());
    }

    // What one row counted of one counter. A counter nothing touched is absent from the row rather
    // than present at nought, and reading it as nought is what turns "never counted" into the
    // number a case names - which is the failure a case about a walk that stopped happening wants.
    private static long readCount(ProfileNode row, ProfileCounter counter) {

        var count = row.findCount(counter);

        return count == null ? 0L : count.getTotals().getTotal();
    }

    // Drives the real poll pollCount times and reports the geometry revision it left standing. The
    // poll is built against an installation of its own over the run's sector - which is where it
    // reads that sector from, where it raises what it found, and what leaves each run's motion
    // observations to itself rather than to whatever ran before it.
    //
    // The interlude runs after the first poll, which is where a case moves the sector under a
    // poll that has already read it.
    private static int runPollsAndReadGeometryRevision(
            SectorAPI sector,
            int pollCount,
            Runnable moveTheSectorAfterTheFirstPoll) {

        try (var globalMock = mockStatic(Global.class);
                var visibilityRulesMock = mockStatic(MapVisibilityRules.class);
                var dominanceRulesMock = mockStatic(DominanceRules.class)) {

            // Only the logger: a poll walks the sector its installation names, so the lookup the
            // running game answers reaches nothing here.
            globalMock
                .when(() -> Global.getLogger(any(Class.class)))
                .thenReturn(mock(Logger.class));

            visibilityRulesMock
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(MapVisibilityRules.BASE);

            dominanceRulesMock
                .when(DominanceRules::readFromLunaSettings)
                .thenReturn(STABILITY_WEIGHTED);

            // The board is this run's installation's, made with it, so the count is read straight
            // rather than as a delta: nothing else can have raised on it.
            var installation = new MapLayerInstallation(sector);
            var stalenessSource = new PoliticalMapStalenessSource(installation);

            for (var poll = 0; poll < pollCount; poll++) {

                stalenessSource.markChangesSinceLastPoll();

                if (poll == 0) {
                    moveTheSectorAfterTheFirstPoll.run();
                }
            }
            return installation
                .resolveRefreshBoard()
                .getRevision(MapLayerCommonRefreshSignal.GEOMETRY);
        }
    }

    // How many times one poll asks who stands with whom, counted through the registry the rule
    // reads alliances from. Registered and taken back around the poll, the registry being process
    // state that would otherwise answer for whatever suite runs next.
    private static int countAllianceFoldsInOnePollOver(SectorAPI sector) {

        var foldCount = new AtomicInteger();

        FactionAllianceRegistry.registerAllianceSource(() -> {
            foldCount.incrementAndGet();
            return FactionAlliances.NONE;
        });
        try {
            runOnePoll(sector);

        } finally {
            FactionAllianceRegistry.registerAllianceSource(FactionAllianceSource.NO_ALLIANCES);
        }
        return foldCount.get();
    }

    // The same sector with more empty systems around it, for a case claiming what the poll costs
    // does not follow how many systems there are.
    private static SectorAPI buildSettledSectorWithEmptyNeighbours(int neighbourCount) {

        var hegemony = SectorPoliticsFixtures.buildFaction("hegemony");
        var colony = SectorPoliticsFixtures.buildVisibleMarket(hegemony, COLONY_SIZE);

        var systems = new ArrayList<SectorPoliticsFixtures.SystemMarkets>();
        systems.add(listSystemMarkets(ALPHA_ID, colony));

        for (var neighbour = 0; neighbour < neighbourCount; neighbour++) {
            systems.add(listSystemMarkets(BETA_ID + neighbour));
        }
        var sector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(hegemony),
            systems.toArray(new SectorPoliticsFixtures.SystemMarkets[0]));

        SectorPoliticsFixtures.placeEverySystemInHyperspace(sector);
        return sector;
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

        var alpha = SectorPoliticsFixtures.findSystemIn(sector, ALPHA_ID);

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(
            alpha,
            SectorPoliticsFixtures.buildAbandonedStationMarket(DERELICT_SIZE));

        SectorPoliticsFixtures.placeEverySystemInHyperspace(sector);
        return sector;
    }

    // Settles the empty neighbour, which puts it on the drawn set and so moves the visibility
    // fingerprint - the change a stale reading of the sector could not report.
    private static void settleTheEmptyNeighbour(SectorAPI sector) {

        var beta = SectorPoliticsFixtures.findSystemIn(sector, BETA_ID);
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
        return SectorPoliticsFixtures.findSystemIn(sector, ALPHA_ID)
            .getAllEntities()
            .get(0)
            .getMarket();
    }

}
