package kmu.settings;

import kmlib.math.ranges.Ranges;

/**
 * How often the map layers' staleness polls re-read the sector.
 *
 * <p>The framework's own knob rather than any layer's: the cadence is shared by every poll the
 * framework installs, so a layer that could move it would be setting the pace for polls it does not
 * own.
 *
 * <p>One row rather than the window the polls actually run on. The spread between the two ends is
 * jitter, so several polls installed at once do not all land on one frame, and a second row for the
 * far end would be a number with no reading of its own - it is only ever "a little past the first".
 * What that spread is derived as belongs to the loop that jitters rather than here, this class
 * holding the field ID, the fallback and the accessor and nothing else.
 */
public final class KmuMapRefreshSettings {

    private static final String POLL_SECONDS_FIELD = "kmu_map_dev_refresh_pollSeconds";

    private static final int DEFAULT_POLL_SECONDS = 4;

    // Floored above zero because zero is the one value a cadence must not offer: a poll set to it
    // would run its sector read on the campaign thread every frame, making a diagnostics row a
    // frame-cost hazard.
    private static final int MIN_POLL_SECONDS = 1;

    // Capped low because the polls are chained rather than independent - the substrate writes what
    // a system's inhabitants saw, the revelation gate reads that, and a layer's own pass reads the
    // gate - so a fact reaches the map after as much as two periods. A generous ceiling would read
    // as a latency the player chose and actually be twice it.
    private static final int MAX_POLL_SECONDS = 30;

    private KmuMapRefreshSettings() {
    }

    /**
     * @return how long each map-layer staleness poll waits between sector reads, in campaign
     *         seconds; 4 by default, held between 1 and 30. Clamped here as well as bounded on the
     *         slider because LunaLib prunes nothing: a value left behind by an earlier spelling of
     *         a row is handed to whatever later reads that ID
     */
    public static int getMapRefreshPollSeconds() {

        var pollSeconds = KmuLunaSettings.readInt(POLL_SECONDS_FIELD, DEFAULT_POLL_SECONDS);

        return Ranges.clampInto(pollSeconds, MIN_POLL_SECONDS, MAX_POLL_SECONDS);
    }
}
