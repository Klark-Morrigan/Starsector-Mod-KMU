package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.util.IntervalUtil;

import kmlib.logging.SessionWarning;

import kmu.settings.KmuLunaSettings;
import kmu.settings.KmuMapRefreshSettings;

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
 * and the map is usually reopened after one, so it need not be instant. How coarse is the player's
 * to set, since what the throttle trades is a reading of the whole sector against how long a change
 * takes to show - a trade only a sector large enough to argue about can settle.
 */
final class StalenessPollLoop {

    // How far past the floor the far end of the poll window sits, so several polls advanced by the
    // same frame do not all elapse on one. Derived from the one knob rather than shipped as a
    // second row: the spread is jitter, and a row for it would only ever be set a little past the
    // first. The shipped 4-5 second window is exactly this ratio over the shipped floor, so the
    // untouched setting polls as it always has.
    private static final float POLL_JITTER_RATIO = 1.25f;

    private static final Logger LOG = Global.getLogger(StalenessPollLoop.class);

    // Said once per session: this polls on the campaign thread every few seconds, so a recurring
    // fault would otherwise flood the log, and the second line says nothing the first did not.
    private final SessionWarning pollFaultWarning = new SessionWarning(LOG);
    private final IntervalUtil pollInterval;
    private final MapLayerStalenessSource stalenessSource;

    // What the interval above was built from: the announcement it was built on, and the reading it
    // was built to. Both are what a retune is decided against rather than state of their own.
    private int actedOnSettingsRevision;
    private int installedPollSeconds;

    /**
     * @param stalenessSource the source whose staleness this poll drives
     */
    StalenessPollLoop(MapLayerStalenessSource stalenessSource) {

        this.stalenessSource = stalenessSource;
        // Read at construction rather than on the first retune, so a poll installed mid-campaign
        // runs at the cadence in force rather than at the shipped one until the player next touches
        // the settings screen. Outside a running game the read answers with its own fallback.
        this.installedPollSeconds = KmuMapRefreshSettings.getMapRefreshPollSeconds();
        this.actedOnSettingsRevision = KmuLunaSettings.getSettingsRevision();
        this.pollInterval = new IntervalUtil(
            installedPollSeconds,
            resolveJitteredCeilingSeconds(installedPollSeconds));
    }

    /**
     * Advances the throttle and polls the source on the frame its interval elapses.
     *
     * @param amount the campaign seconds since the last frame, as the driving script was handed
     *               them
     */
    void advancePoll(float amount) {

        applyRetunedCadence();
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
            pollFaultWarning.warnOnce(
                "Map layer staleness poll failed; source="
                    + stalenessSource.getClass().getSimpleName(),
                exception);
        }
    }

    // The far end of the window a floor of this many seconds jitters over.
    private static float resolveJitteredCeilingSeconds(int pollSeconds) {
        return pollSeconds * POLL_JITTER_RATIO;
    }

    // Installs a retuned cadence, and only a retuned one.
    //
    // Gated twice. On the revision first, so the settings are not read on every campaign frame; and
    // then on the value, because LunaLib announces that the settings changed rather than which one,
    // so every knob the player moves reaches here.
    //
    // The second gate is what makes this safe at all: IntervalUtil.setInterval draws a fresh
    // interval and zeroes the elapsed time with it, so a loop that re-applied the reading it already
    // held would reset its own timer and never reach an interval's end.
    private void applyRetunedCadence() {

        var settingsRevision = KmuLunaSettings.getSettingsRevision();

        if (settingsRevision == actedOnSettingsRevision) {
            return;
        }
        actedOnSettingsRevision = settingsRevision;

        var pollSeconds = KmuMapRefreshSettings.getMapRefreshPollSeconds();

        if (pollSeconds == installedPollSeconds) {
            return;
        }
        installedPollSeconds = pollSeconds;
        pollInterval.setInterval(pollSeconds, resolveJitteredCeilingSeconds(pollSeconds));
    }
}
