package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

import org.apache.log4j.Logger;

/**
 * Drives one map layer's staleness poll, so a change the engine fires no event for is still
 * caught a few seconds after it happens rather than only on the next reload.
 *
 * <p>The loop is all this owns: a throttle, a fault guard, and the campaign-thread call into
 * {@link MapLayerStalenessSource}. Which changes matter and which refresh each one earns are
 * the layer's answers, so nothing here has to name a layer or a signal.
 *
 * <p>Throttled to a few seconds rather than run per frame: the transitions this catches are
 * rare, and the map is usually reopened after one, so it need not be instant. Installed per
 * layer that has a source to poll, and always as a transient script: pure runtime logic,
 * re-added on load, never serialized.
 */
public class MapLayerSectorWatcher implements EveryFrameScript {

    // Poll cadence in campaign seconds. These are rare story/exploration events, so
    // a coarse interval keeps the per-frame cost negligible while still catching
    // them within a few seconds.
    private static final float POLL_MIN_SECONDS = 4f;
    private static final float POLL_MAX_SECONDS = 5f;

    private static final Logger LOG = Global.getLogger(MapLayerSectorWatcher.class);

    private final IntervalUtil pollInterval = new IntervalUtil(POLL_MIN_SECONDS, POLL_MAX_SECONDS);
    private final MapLayerStalenessSource stalenessSource;

    // One-shot guard: this polls on the campaign thread every few seconds, so a
    // recurring fault would flood the log. The first failure is recorded, the
    // rest silenced.
    private boolean hasLoggedPollError;

    /**
     * @param stalenessSource the layer whose staleness this poll drives
     */
    public MapLayerSectorWatcher(MapLayerStalenessSource stalenessSource) {
        this.stalenessSource = stalenessSource;
    }

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
        // Guarded so an uncaught fault in the layer's own read (a malformed system, an
        // API change) is recorded rather than lost in the per-frame engine noise, and
        // does not stop the watcher from polling on the next interval.
        try {
            stalenessSource.markChangesSinceLastPoll();
        } catch (RuntimeException exception) {
            if (!hasLoggedPollError) {
                hasLoggedPollError = true;
                LOG.error("Map layer sector watcher failed to poll staleness", exception);
            }
        }
    }
}
