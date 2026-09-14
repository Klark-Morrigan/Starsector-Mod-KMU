package kmu.maplayers;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.SectorWalkCounters;
import kmlib.testfixtures.profiling.ProfileCounts;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.maplayers.base.refresh.MapSubstrateRefreshInstaller;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static kmu.maplayers.PollWalkFixtures.ALPHA_ID;
import static kmu.maplayers.PollWalkFixtures.buildSettledSectorWithADerelict;
import static kmu.maplayers.PollWalkFixtures.captureUnscopedCountsWhile;
import static kmu.maplayers.PollWalkFixtures.findOnlyDerelictIn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Pins that the record of what a place's own inhabitants can see accrues with no map layer
 * standing at all, and what one sweep of it costs.
 *
 * <p>Only the substrate is installed here - no layer, no layer's poll, nothing that draws - which
 * is the arrangement the write was moved for: the register is shared by every map family, so its
 * gaps must not be decided by which map the player last chose to look at. A unit cannot make that
 * claim, having no machinery to leave a layer out of; and neither can it make the cost one,
 * since how many times a system was walked is a fact about the composition rather than about any
 * part of it.
 *
 * <p>So the installer, the script, the poll, the colony index and the register are all real, and
 * the sweep is driven the way the engine drives it - by advancing the script that was registered.
 * Only the logger is stubbed, no running game answering it.
 */
final class SubstrateObservationSweepIntegrationTest {

    // Comfortably past the 4-5s poll interval, so one advance elapses it and drives one sweep.
    private static final float ADVANCE_PAST_POLL_INTERVAL = 10f;

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
            var sweep = captureUnscopedCountsWhile(
                () -> runSweepsOver(buildSettledSectorWithADerelict(), ONE_SWEEP));

            assertThat(ProfileCounts.readTotalOf(sweep, SectorWalkCounters.COLONIES_READ))
                .isEqualTo(EACH_SYSTEM_SELECTED_ONCE);
        }

        @Test
        void opensAFreshReadingOnEverySweep() {
            // The index's other half: it is discarded with the poll that opened it. One kept
            // between sweeps would answer the second off the sector the first saw, so a colony
            // that arrived among witnesses in between would never be recorded at all.
            var sweeps = captureUnscopedCountsWhile(
                () -> runSweepsOver(buildSettledSectorWithADerelict(), TWO_SWEEPS));

            assertThat(ProfileCounts.readTotalOf(sweeps, SectorWalkCounters.COLONIES_READ))
                .isEqualTo(EACH_SYSTEM_SELECTED_ONCE_PER_SWEEP);
        }
    }

    // Stands the substrate up on the sector and advances the script it registered past one poll
    // interval per sweep, which is the whole of how a sweep happens in play. Driven through the
    // registered script rather than by calling the source: what this suite claims is that
    // installing the substrate alone is enough, and a case building the source itself would pass
    // with the installer unwired.
    private static void runSweepsOver(SectorAPI sector, int sweepCount) {

        try (var globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers()) {

            MapSubstrateRefreshInstaller.installAll(sector);

            var registeredScript = ArgumentCaptor.forClass(EveryFrameScript.class);

            verify(sector)
                .addTransientScript(registeredScript.capture());

            for (var sweep = 0; sweep < sweepCount; sweep++) {
                registeredScript.getValue().advance(ADVANCE_PAST_POLL_INTERVAL);
            }
        }
    }
}
