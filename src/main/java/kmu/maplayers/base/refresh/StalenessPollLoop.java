package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

import org.apache.log4j.Logger;

/**
 * The cadence one staleness poll runs on: a throttle, a fault guard, and the campaign-thread call
 * into a {@link MapLayerStalenessSource}.
 *
 * <p>Held by a per-frame script rather than being one, which is what lets several polls run side by
 * side. The engine registers and clears transient scripts by exact class, so two polls that have to
 * be installed and taken back independently have to be two classes - and what each of those classes
 * then owns is that identity alone, the loop being this.
 *
 * <p>Throttled to a few seconds rather than run per frame: the transitions a poll catches are rare,
 * and the map is usually reopened after one, so it need not be instant.
 */
final class StalenessPollLoop {

    // Poll cadence in campaign seconds. These are rare story/exploration events, so
    // a coarse interval keeps the per-frame cost negligible while still catching
    // them within a few seconds.
    private static final float POLL_MIN_SECONDS = 4f;
    private static final float POLL_MAX_SECONDS = 5f;

    private static final Logger LOG = Global.getLogger(StalenessPollLoop.class);

    private final IntervalUtil pollInterval = new IntervalUtil(POLL_MIN_SECONDS, POLL_MAX_SECONDS);
    private final MapLayerStalenessSource stalenessSource;

    // One-shot guard: this polls on the campaign thread every few seconds, so a
    // recurring fault would flood the log. The first failure is recorded, the
    // rest silenced.
    private boolean hasLoggedPollError;

    /**
     * @param stalenessSource the source whose staleness this poll drives
     */
    StalenessPollLoop(MapLayerStalenessSource stalenessSource) {
        this.stalenessSource = stalenessSource;
    }

    /**
     * Advances the throttle and polls the source on the frame its interval elapses.
     *
     * @param amount the campaign seconds since the last frame, as the driving script was handed
     *               them
     */
    void advancePoll(float amount) {

        pollInterval.advance(amount);
        if (!pollInterval.intervalElapsed()) {
            return;
        }
        // Guarded so an uncaught fault in the source's own read (a malformed system, an
        // API change) is recorded rather than lost in the per-frame engine noise, and
        // does not stop the poll from running on the next interval.
        //
        // The source is named in the line because several polls run at once: which one
        // faulted is the first thing a reader of the log needs, and the driving script's
        // name says only that some poll did.
        try {
            stalenessSource.markChangesSinceLastPoll();
        } catch (RuntimeException exception) {
            if (!hasLoggedPollError) {
                hasLoggedPollError = true;
                LOG.error(
                    "Map layer staleness poll failed; source="
                        + stalenessSource.getClass().getSimpleName(),
                    exception);
            }
        }
    }
}
