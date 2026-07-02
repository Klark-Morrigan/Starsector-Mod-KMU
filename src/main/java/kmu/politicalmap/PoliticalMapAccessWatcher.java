package kmu.politicalmap;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

import kmu.politicalmap.domain.visibility.PoliticalMapVisibility;

import org.apache.log4j.Logger;

/**
 * Refreshes the political map when the set of on-map systems changes - a gate
 * activating, a jump point being established, a system being cut off, a colony
 * founded, or a dead colony surveyed.
 *
 * <p>The engine has no event for most of these (gate state flips lazily as a
 * gate is scanned, a ruin reveals on survey), so this polls a cheap fingerprint
 * instead - an identity hash of the on-map systems
 * ({@link PoliticalMapVisibility#computeVisibilityFingerprint}) - and requests a
 * refresh when it moves. A hash rather than a count so the fingerprint still
 * shifts when one system joins as another leaves in the same poll (a count would
 * net to the same value and miss the swap). Throttled to a few seconds: these
 * transitions are rare, and the map is usually reopened after one, so it need
 * not be instant. A transient script: pure runtime logic, re-added on load,
 * never serialized.
 */
public class PoliticalMapAccessWatcher implements EveryFrameScript {
    // Poll cadence in campaign seconds. Access changes are rare story/exploration
    // events, so a coarse interval keeps the per-frame cost negligible while
    // still catching them within a few seconds.
    private static final float POLL_MIN_SECONDS = 4f;
    private static final float POLL_MAX_SECONDS = 5f;

    private static final Logger LOG = Global.getLogger(PoliticalMapAccessWatcher.class);

    private final IntervalUtil pollInterval = new IntervalUtil(POLL_MIN_SECONDS, POLL_MAX_SECONDS);
    // Visibility fingerprint last seen; 0 is also the empty-sector value, so a
    // boolean guards the very first poll establishing the baseline.
    private boolean hasPolled;
    private int lastVisibilityFingerprint;
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
            pollVisibilityFingerprint();
        } catch (RuntimeException exception) {
            if (!hasLoggedPollError) {
                hasLoggedPollError = true;
                LOG.error("Political map access watcher failed to poll visibility",
                        exception);
            }
        }
    }

    // Re-reads the visibility fingerprint and requests a geometry refresh when it
    // moves. The transition is logged so a province that wrongly appears or
    // vanishes can be traced to the poll that did (or did not) see the change.
    private void pollVisibilityFingerprint() {
        var fingerprint = PoliticalMapVisibility.computeVisibilityFingerprint(Global.getSector());
        if (!hasPolled || fingerprint != lastVisibilityFingerprint) {
            LOG.debug("Political map visibility fingerprint changed; old="
                    + (hasPolled ? lastVisibilityFingerprint : 0) + " new=" + fingerprint
                    + " firstPoll=" + !hasPolled);
            hasPolled = true;
            lastVisibilityFingerprint = fingerprint;
            PoliticalMapRefresh.requestGeometryRefresh();
        }
    }
}
