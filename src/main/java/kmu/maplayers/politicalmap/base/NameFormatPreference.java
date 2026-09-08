package kmu.maplayers.politicalmap.base;

import kmlib.starsector.memory.SectorMemoryString;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

/**
 * How cluster labels spell their holders' names - full, short, or not at all - persisted per save and per
 * screen. Layer-agnostic: every view labels its clusters the same way, so one stored choice backs them all
 * rather than each view holding its own.
 *
 * <p>Per screen because the format is a pick the player made on one panel: the two screens frame the sector
 * at different sizes, so the form that fits one is not the form that fits the other, and a pick made on
 * either is that panel's own. The screen arrives as a {@link ScreenMemoryScope} on every call rather than
 * being resolved here, so a control writes the panel it was placed on and a bake reads the screen it is
 * painting for.
 *
 * <p>Sidebar-only: the choice is driven solely by the overlay's Full/Short/No radio, never a
 * settings-screen control, so it persists in sector memory (each save keeps its own choice and it
 * survives reload) rather than as a LunaLib field - a LunaLib field would render on a settings tab,
 * duplicating the sidebar radio, and an unregistered key would not round-trip.
 *
 * <p>Unlike the sidebar's list controls this fires a refresh: the choice decides both whether the
 * labels build at all and the text each is fitted and wrapped against, which is baked into the
 * drawables at rebuild, so a pick has to invalidate them to show.
 */
public final class NameFormatPreference {

    // The base key the screen's segment composes onto; frozen once shipped, since renaming it silently
    // resets every existing save's choice back to the default. Absent until the player first picks a
    // format on that screen, which the read resolves to the default.
    private static final String SELECTED_NAME_FORMAT_KEY = "$kmu_political_name_format";

    // Full names by default: the long form is the authored name a player recognises, and the short
    // form is the deliberate trade for fitting a tighter cluster at a larger font.
    private static final FactionNameFormatChoice DEFAULT_NAME_FORMAT = FactionNameFormatChoice.FULL;

    private NameFormatPreference() {
    }

    /**
     * @param memoryScope the screen whose choice is read
     * @return how that screen's cluster labels spell their holders' names, including whether they draw
     *         at all; full names before a save exists or when no choice was ever picked there
     */
    public static FactionNameFormatChoice getSelectedNameFormat(ScreenMemoryScope memoryScope) {
        return FactionNameFormatChoice.fromKeyOrDefault(
            resolveSlot(memoryScope).get(),
            DEFAULT_NAME_FORMAT);
    }

    /**
     * Persists the chosen name format in this save, against the screen it was picked on, and restyles the
     * overlay so the labels re-fit (or disappear) at once. A no-op before the sector exists, since there
     * is no save to write into yet.
     *
     * @param memoryScope the screen whose panel made the pick
     * @param choice      how cluster labels should spell their holders' names, or that they draw none
     * @param board       the refresh board of the sector whose radio made the pick, raised on so that
     *                    sector's labels re-fit
     */
    public static void selectNameFormat(
            ScreenMemoryScope memoryScope,
            FactionNameFormatChoice choice,
            MapLayerRefreshBoard board) {

        // Repaint only on a real write: before the sector exists the write no-ops and reports no
        // write, so nothing bumps a revision no overlay would read. The refresh stands in for
        // settingsRevision, which this sidebar-only choice never moves since it is not a LunaLib
        // field.
        if (resolveSlot(memoryScope).set(choice.persistenceKey())) {
            board.requestRefresh(MapLayerCommonRefreshSignal.MAP_STYLE);
        }
    }

    // The sector-memory slot holding one screen's name format. A fresh wrapper per call - the wrapper
    // only holds its key, the value lives in sector memory - so no per-screen instance has to be cached
    // here.
    private static SectorMemoryString resolveSlot(ScreenMemoryScope memoryScope) {
        return new SectorMemoryString(memoryScope.resolveKeyFor(SELECTED_NAME_FORMAT_KEY));
    }
}
