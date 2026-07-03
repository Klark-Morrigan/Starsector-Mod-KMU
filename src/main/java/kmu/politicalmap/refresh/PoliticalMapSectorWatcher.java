package kmu.politicalmap.refresh;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

import org.apache.log4j.Logger;

import java.util.Map;

/**
 * Refreshes the political map when a change the engine fires no event for slips
 * past the listeners - a gate activating, a system being cut off, a dead colony
 * surveyed, or an AI faction founding or capturing a colony in a system already
 * on the map.
 *
 * <p>Takes a cheap snapshot of the on-map systems ({@link PoliticalMapSectorSnapshot})
 * and reacts to each half on its own axis. The visibility fingerprint tracks which
 * systems are drawn; when its scalar hash moves the geometry is stale, so this
 * requests a whole-map geometry rebuild - the Voronoi partition depends on the
 * full set of sites, so there is nothing finer to act on. The owner map tracks who
 * holds each system; this diffs it against the last poll and marks exactly the
 * systems whose owner changed politics-stale, the same targeted signal the event
 * listeners raise. Feeding that shared set is what keeps the watcher from doubling
 * a listener's work: a colony a listener already marked and one this diff
 * re-discovers collapse to a single reshape, and a change no listener saw is
 * caught here and reshaped just as narrowly.
 *
 * <p>Throttled to a few seconds: these transitions are rare, and the map is
 * usually reopened after one, so it need not be instant. The first poll only
 * establishes the baselines. A transient script: pure runtime logic, re-added on
 * load, never serialized.
 */
public class PoliticalMapSectorWatcher implements EveryFrameScript {
    // Poll cadence in campaign seconds. These are rare story/exploration events, so
    // a coarse interval keeps the per-frame cost negligible while still catching
    // them within a few seconds.
    private static final float POLL_MIN_SECONDS = 4f;
    private static final float POLL_MAX_SECONDS = 5f;

    private static final Logger LOG = Global.getLogger(PoliticalMapSectorWatcher.class);

    private final IntervalUtil pollInterval = new IntervalUtil(POLL_MIN_SECONDS, POLL_MAX_SECONDS);
    // Last poll's state; 0 and an empty map are also the empty-sector values, so a
    // boolean guards the very first poll establishing both baselines.
    private boolean hasPolled;
    private int lastVisibilityFingerprint;
    private Map<String, String> lastOwnerBySystemId = Map.of();
    // One-shot guard: this polls on the campaign thread every few seconds, so a
    // recurring fault would flood the log. The first failure is recorded, the
    // rest silenced.
    private boolean hasLoggedPollError;

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }

    @Override
    public void advance(float amount) {
        pollInterval.advance(amount);
        if (!pollInterval.intervalElapsed()) {
            return;
        }
        // Guarded so an uncaught fault here (a malformed system, an API change)
        // is recorded rather than lost in the per-frame engine noise, and does
        // not stop the watcher from polling on the next interval.
        try {
            pollSnapshot();
        } catch (RuntimeException exception) {
            if (!hasLoggedPollError) {
                hasLoggedPollError = true;
                LOG.error("Political map sector watcher failed to poll snapshot",
                        exception);
            }
        }
    }

    // Re-reads the snapshot in one sector walk and routes each half to the refresh
    // it needs: a visibility move rebuilds geometry, an owner-map diff marks just
    // the changed systems stale. The first poll only establishes the baselines.
    private void pollSnapshot() {
        var snapshot = PoliticalMapSectorSnapshot.scan(Global.getSector());
        var isFirstPoll = !hasPolled;
        if (isFirstPoll || snapshot.visibilityFingerprint() != lastVisibilityFingerprint) {
            LOG.debug("Political map visibility fingerprint changed; old="
                    + (isFirstPoll ? 0 : lastVisibilityFingerprint) + " new="
                    + snapshot.visibilityFingerprint() + " firstPoll=" + isFirstPoll);
            lastVisibilityFingerprint = snapshot.visibilityFingerprint();
            if (!isFirstPoll) {
                PoliticalMapRefresh.requestGeometryRefresh();
            }
        }
        if (!isFirstPoll) {
            markChangedOwners(lastOwnerBySystemId, snapshot.ownerBySystemId());
        }
        lastOwnerBySystemId = snapshot.ownerBySystemId();
        hasPolled = true;
    }

    // Marks politics-stale every system whose owner differs between two polls: a
    // system that gained an owner or changed hands (present in current with a new
    // id), and one that lost its owner (dropped from current). Each mark funnels
    // into the same set the listeners raise, so an overlapping change reshapes once
    // and is traced by markSystemPoliticsStale's own log line.
    private static void markChangedOwners(Map<String, String> previousOwnerBySystemId,
            Map<String, String> currentOwnerBySystemId) {
        for (var entry : currentOwnerBySystemId.entrySet()) {
            if (!entry.getValue().equals(previousOwnerBySystemId.get(entry.getKey()))) {
                PoliticalMapRefresh.markSystemPoliticsStale(entry.getKey());
            }
        }
        for (var systemId : previousOwnerBySystemId.keySet()) {
            if (!currentOwnerBySystemId.containsKey(systemId)) {
                PoliticalMapRefresh.markSystemPoliticsStale(systemId);
            }
        }
    }
}
