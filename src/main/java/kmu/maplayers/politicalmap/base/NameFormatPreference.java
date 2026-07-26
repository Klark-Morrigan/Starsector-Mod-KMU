package kmu.maplayers.politicalmap.base;

import kmlib.starsector.memory.SectorMemoryString;

import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;

/**
 * Whether cluster labels spell their owners' full or short names, persisted per save. Layer-agnostic:
 * every view labels its clusters the same way, so one stored choice backs them all rather than each
 * view holding its own.
 *
 * <p>Sidebar-only: the format is driven solely by the overlay's Short/Full radio, never a
 * settings-screen control, so it persists in sector memory (each save keeps its own choice and it
 * survives reload) rather than as a LunaLib field - a LunaLib field would render on a settings tab,
 * duplicating the sidebar radio, and an unregistered key would not round-trip.
 *
 * <p>Unlike the sidebar's list controls this fires a refresh: the format decides the text each label
 * is fitted and wrapped against, which is baked into the drawables at rebuild, so a flip has to
 * invalidate them to show.
 */
public final class NameFormatPreference {
    // Save-serialised key of the chosen format; frozen once shipped, since renaming it silently
    // resets every existing save's choice back to the default. Absent until the player first picks a
    // format, which the read resolves to the default.
    private static final SectorMemoryString selectedNameFormat =
            new SectorMemoryString("$kmu_political_name_format");

    // Full names by default: the long form is the authored name a player recognises, and the short
    // form is the deliberate trade for fitting a tighter cluster at a larger font.
    private static final FactionNameFormatChoice DEFAULT_NAME_FORMAT = FactionNameFormatChoice.FULL;

    private NameFormatPreference() {
    }

    /**
     * @return the format cluster labels spell their owners' names in; full names before a save exists
     *         or when no format was ever picked
     */
    public static FactionNameFormatChoice getSelectedNameFormat() {
        return FactionNameFormatChoice.fromKeyOrDefault(selectedNameFormat.get(),
                DEFAULT_NAME_FORMAT);
    }

    /**
     * Persists the chosen name format in this save and restyles the overlay so the labels re-fit at
     * once. A no-op before the sector exists, since there is no save to write into yet.
     *
     * @param choice the format cluster labels should spell their owners' names in
     */
    public static void selectNameFormat(FactionNameFormatChoice choice) {
        // Repaint only on a real write: before the sector exists the write no-ops and reports no
        // write, so nothing bumps a revision no overlay would read. The refresh stands in for
        // settingsRevision, which this sidebar-only choice never moves since it is not a LunaLib
        // field.
        if (selectedNameFormat.set(choice.persistenceKey())) {
            PoliticalMapRefresh.requestMapStyleRefresh();
        }
    }
}
