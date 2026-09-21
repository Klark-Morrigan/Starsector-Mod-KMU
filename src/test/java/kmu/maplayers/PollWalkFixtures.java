package kmu.maplayers;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.profiling.ProfileSection;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.profiling.snapshot.ProfileNode;
import kmlib.testfixtures.profiling.RecordedCapture;

import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.listSystemMarkets;

/**
 * The sector a poll-walk suite counts over, and how the count is taken.
 *
 * <p>Every poll that sweeps the sector is pinned the same way: a small staged sector with one thing
 * for the poll to find, driven for real, with the cost read off the library's own walk counters on
 * the profiler's unscoped row. Shared so the two polls are measured over the same sector - a claim
 * about what one costs beside the other is only a claim if both were asked the same question.
 *
 * <p>Final class with a private constructor: fixture of static wiring, no instances.
 */
public final class PollWalkFixtures {

    /** The settled system: an open colony with a derelict standing beside it. */
    public static final String ALPHA_ID = "alpha";

    /** The empty neighbour, which a later poll can settle. */
    public static final String BETA_ID = "beta";

    // The size the staged colony carries. Nothing a poll walk counts weighs a colony, so a case
    // varying this would vary nothing the count can see.
    public static final int COLONY_SIZE = 5;

    // The size vanilla builds a derelict at: one nobody lives on is created at nought.
    private static final int DERELICT_SIZE = 0;

    // Nothing a poll-walk suite claims is a duration, so one reading answers every clock read the
    // capture makes - and a poll that took no time is still a poll that traversed what it traversed.
    private static final long FIXED_CLOCK_NANOS = 0L;

    private PollWalkFixtures() {
        // fixture of static wiring, no instances.
    }

    /**
     * Two star systems: one settled by an open colony with a derelict standing beside it, and one
     * empty. The derelict is what gives an observation write something to record, and the empty
     * neighbour is what makes "once per system" distinguishable from "once per sweep" and what a
     * later poll can settle.
     *
     * <p>Both carry a hyperspace position, without which the political poll's motion walk skips
     * them before ever asking the drawn-set rule about them - and a count taken over that walk would
     * be blind to the traversal it most needs to watch.
     *
     * @return the staged sector
     */
    public static SectorAPI buildSettledSectorWithADerelict() {

        var hegemony = SectorPoliticsFixtures.buildFaction("hegemony");
        var colony = SectorPoliticsFixtures.buildVisibleMarket(hegemony, COLONY_SIZE);

        var sector = SectorPoliticsFixtures.buildSectorWithSystems(
            List.of(hegemony),
            listSystemMarkets(ALPHA_ID, colony),
            listSystemMarkets(BETA_ID));

        SectorPoliticsFixtures.placeMarketsOnSystemEntities(
            SectorPoliticsFixtures.findSystemIn(sector, ALPHA_ID),
            SectorPoliticsFixtures.buildAbandonedStationMarket(DERELICT_SIZE));

        SectorPoliticsFixtures.placeEverySystemInHyperspace(sector);
        return sector;
    }

    /**
     * The one market hung on a system entity rather than listed - the derelict, since a listed one
     * is an outpost rather than a wreck. Read back off the fixture so a case names the register's
     * key without a second builder stating what was staged.
     *
     * @param sector a sector built by {@link #buildSettledSectorWithADerelict}
     * @return the derelict's market
     */
    public static MarketAPI findOnlyDerelictIn(SectorAPI sector) {

        return SectorPoliticsFixtures.findSystemIn(sector, ALPHA_ID)
            .getAllEntities()
            .get(0)
            .getMarket();
    }

    /**
     * What {@code walk} traversed, read off the row every count made with no scope open lands on.
     *
     * <p>That reserved row is where a poll's counts land in play as well: a poll runs on the
     * campaign thread rather than inside a profiled frame, so nothing brackets it. The profiler is
     * bound and taken back by the capture, the holder being process state that would otherwise
     * follow one suite into whatever runs next.
     *
     * @param walk the poll or sweep to count
     * @return the unscoped row of the capture
     */
    public static ProfileNode captureUnscopedCountsWhile(Runnable walk) {

        var profiler = new RecordingProfiler(() -> FIXED_CLOCK_NANOS);

        return RecordedCapture
            .recordWhile(profiler, walk)
            .findNode(ProfileSection.UNSCOPED_COUNTS.getName());
    }
}
