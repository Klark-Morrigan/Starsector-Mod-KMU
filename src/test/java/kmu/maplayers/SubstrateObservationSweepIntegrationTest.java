package kmu.maplayers;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.profiling.ProfileSection;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.starsector.SectorWalkCounters;
import kmlib.testfixtures.profiling.ProfileCounts;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.base.refresh.MapSubstrateRefreshInstaller;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.apache.log4j.Logger;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * Pins that the record of what a place's own inhabitants can see accrues with no map layer
 * standing at all, and what one sweep of it costs.
 *
 * <p>Only the substrate is installed here - no layer, no layer's poll, nothing that draws - which
 * is the arrangement the write was moved for: the register is shared by every map family, so its
 * gaps must not be decided by which map the player last chose to look at. A unit cannot make that
 * claim, having no installation to leave a layer out of; and neither can it make the cost one,
 * since how many times a system was walked is a fact about the composition rather than about any
 * part of it.
 *
 * <p>So the installer, the script, the poll, the colony index and the register are all real, and
 * the sweep is driven the way the engine drives it - by advancing the script that was registered.
 * Only the logger is stubbed, no running game answering it.
 */
final class SubstrateObservationSweepIntegrationTest {

    private static final String ALPHA_ID = "alpha";
    private static final String BETA_ID = "beta";

    // Comfortably past the 4-5s poll interval, so one advance elapses it and drives one sweep.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;

    // The size the staged colony carries. Nothing here weighs a colony, so a case varying this
    // would vary nothing the sweep can see.
    private static final int COLONY_SIZE = 5;

    // The size vanilla builds a derelict at: a hulk nobody lives on is created at nought.
    private static final int DERELICT_SIZE = 0;

    // Nothing this suite claims is a duration, so one reading answers every clock read the capture
    // makes - and a sweep that took no time is still a sweep that traversed what it traversed.
    private static final long FIXED_CLOCK_NANOS = 0L;

    // One sweep, and two for the case about what a second one re-reads.
    private static final int ONE_SWEEP = 1;
    private static final int TWO_SWEEPS = 2;

    // The staged sector's two systems, each selected once for the whole sweep - a sweep opening an
    // index per place would read each of them once per system in the sector.
    private static final long EACH_SYSTEM_SELECTED_ONCE = 2L;

    // And twice over for two sweeps, each opening a reading of the sector as it then stands.
    private static final long EACH_SYSTEM_SELECTED_ONCE_PER_SWEEP = 4L;

    @Nested
    class InstallAll {

        @Test
        void recordsWhatASystemsInhabitantsSeeWithNoLayerStanding() {
            // The step's own claim, read through the register because that is what says the write
            // ran rather than merely being reachable. The derelict is on the map on its
            // neighbours' account alone, and the record is what keeps it there once they are gone.
            var sector = buildSettledSectorWithADerelict();

            SectorPoliticsFixtures.openSectorMemory(sector);
            runSweepsOver(sector, ONE_SWEEP);

            assertThat(SectorColonySightings.readSightings(sector)
                    .readObservation(findOnlyDerelictIn(sector).getId())
                    .locationId())
                .isEqualTo(ALPHA_ID);
        }

        @Test
        void selectsEachSystemsColoniesOnceForTheWholeSweep() {
            // What the second reading costs, now that this sweep opens one of its own rather than
            // riding the political map's pass. One index per place instead would read every colony
            // in the sector once per system in it, on the campaign thread, every few seconds.
            var sweep = captureSweepCountsOver(buildSettledSectorWithADerelict(), ONE_SWEEP);

            assertThat(ProfileCounts.readTotalOf(sweep, SectorWalkCounters.COLONIES_READ))
                .isEqualTo(EACH_SYSTEM_SELECTED_ONCE);
        }

        @Test
        void opensAFreshReadingOnEverySweep() {
            // The index's other half: it is discarded with the poll that opened it. One kept
            // between sweeps would answer the second off the sector the first saw, so a colony
            // that arrived among witnesses in between would never be recorded at all.
            var sweeps = captureSweepCountsOver(buildSettledSectorWithADerelict(), TWO_SWEEPS);

            assertThat(ProfileCounts.readTotalOf(sweeps, SectorWalkCounters.COLONIES_READ))
                .isEqualTo(EACH_SYSTEM_SELECTED_ONCE_PER_SWEEP);
        }
    }

    // Two star systems, one settled by an open colony with a derelict standing beside it. The
    // derelict is what gives the sweep something to record; the empty neighbour is what makes
    // "once per system" distinguishable from "once per sweep".
    private static SectorAPI buildSettledSectorWithADerelict() {

        var hegemony = SectorPoliticsFixtures.buildFaction("hegemony");
        var colony = SectorPoliticsFixtures.buildVisibleMarket(hegemony, COLONY_SIZE);

        var sector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(hegemony),
            listSystemMarkets(ALPHA_ID, colony),
            listSystemMarkets(BETA_ID));

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(
            SectorPoliticsFixtures.findSystemIn(sector, ALPHA_ID),
            SectorPoliticsFixtures.buildAbandonedStationMarket(DERELICT_SIZE));

        return sector;
    }

    // What one sweep over sector traversed, read off the row every count made with no scope open
    // lands on. That reserved row is where a poll's counts land in play as well: the script runs
    // on the campaign thread rather than inside a profiled frame, so nothing brackets it.
    //
    // The profiler is bound and taken back by the capture, the holder being process state that
    // would otherwise follow this suite into whatever runs next.
    private static ProfileNode captureSweepCountsOver(SectorAPI sector, int sweepCount) {

        var profiler = new RecordingProfiler(() -> FIXED_CLOCK_NANOS);

        return RecordedCapture
            .recordWhile(profiler, () -> runSweepsOver(sector, sweepCount))
            .findNode(ProfileSection.UNSCOPED_COUNTS.getName());
    }

    // Stands the substrate up on the sector and advances the script it registered past one poll
    // interval per sweep, which is the whole of how a sweep happens in play. Driven through the
    // registered script rather than by calling the source: what this suite claims is that
    // installing the substrate alone is enough, and a case building the source itself would pass
    // with the installer unwired.
    private static void runSweepsOver(SectorAPI sector, int sweepCount) {

        try (var globalMock = mockStatic(Global.class)) {

            globalMock
                .when(() -> Global.getLogger(any(Class.class)))
                .thenReturn(mock(Logger.class));

            MapSubstrateRefreshInstaller.installAll(sector);

            var registeredScript = ArgumentCaptor.forClass(EveryFrameScript.class);

            verify(sector)
                .addTransientScript(registeredScript.capture());

            for (var sweep = 0; sweep < sweepCount; sweep++) {
                registeredScript.getValue().advance(ADVANCE_PAST_POLL_INTERVAL);
            }
        }
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
