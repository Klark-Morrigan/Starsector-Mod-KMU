package kmu.diagnostics;

import kmlib.starsector.ui.map.probes.MapProbeWarnings;

import kmu.settings.KmuLoggingSettings;
import kmu.settings.KmuLunaSettings;

/**
 * Keeps the reflective UI traces answerable to a player who switches them on mid-session.
 *
 * <p>The traces themselves are gated where they are read, which is what stops a walk running - and a
 * broken walk spending its one warning - while nobody is listening. That covers the failure a trace
 * causes itself. It does not cover the one it inherits: the reaches those walks are built from are
 * shared with the overlay's ordinary work, so one of them can break during normal play, warn once,
 * and be silent by the time the traces are switched on. The trace then reports nothing, and the line
 * saying why was spent hours earlier on a reader who was not asking.
 *
 * <p>So the switch moving to on is treated as a new reader arriving, and the probes' warnings are let
 * speak again. On the edge rather than on every settings change, because LunaLib announces the
 * settings rather than the setting: re-arming on each announcement would let an unrelated knob
 * unmute a warning nobody asked to hear.
 */
public final class ReflectionTracingInstaller {

    // What the switch last read as, so an announcement can be told from a movement.
    private static boolean wasEnabled;

    private ReflectionTracingInstaller() {
    }

    /**
     * Registers the listener that re-arms the probes' warnings when the traces are switched on.
     *
     * <p>Call once at load, after KMU's settings bindings are in place.
     */
    public static void installAll() {

        // Seeded from the switch as it stands rather than from false, so a player who already had
        // the traces on is not treated as having just turned them on by the first unrelated settings
        // change. Read here rather than at class-init because a LunaLib read needs the settings
        // loaded, which is what calling this after the bindings guarantees.
        wasEnabled = KmuLoggingSettings.areReflectionProbesEnabled();

        KmuLunaSettings.runOnSettingsChange(ReflectionTracingInstaller::rearmWarningsWhenSwitchedOn);
    }

    // Acts only on the off-to-on edge. Switching the traces off arms nothing: a reader who has just
    // stopped listening is owed no line, and the warnings stay as spent as they were.
    private static void rearmWarningsWhenSwitchedOn() {

        var isEnabled = KmuLoggingSettings.areReflectionProbesEnabled();

        if (isEnabled && !wasEnabled) {
            MapProbeWarnings.rearmAllWarnings();
        }
        wasEnabled = isEnabled;
    }
}
