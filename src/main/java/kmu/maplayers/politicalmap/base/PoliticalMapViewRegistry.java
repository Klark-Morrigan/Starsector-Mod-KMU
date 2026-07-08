package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.memory.SectorMemoryAccess;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.sidebar.SidebarControlSpec;

import java.util.List;

/**
 * The political map's view registry and the single source of truth for which view paints - the
 * generalisation of the old faction on/off toggle into a selection among the registered views
 * (faction, later alliances). The view-selector radio composes its segments from
 * {@link #getViews()}, lights the one {@link #getSelectedViewIndex()} reports, and writes a click
 * through {@link #toggleView}; the shared terrain plugin reads {@link #getActiveView()} to decide
 * both whether to paint and which view's rules to paint under - so the plugin names no concrete
 * view and stays view-neutral.
 *
 * <p>Two selections are distinguished. The stored pick ({@link #getSelectedView}) is what the
 * player last chose in the radio, independent of which tab is open - the radio lights it whenever
 * the political-map tab is up. The active view ({@link #getActiveView}) additionally requires that
 * tab to be the active pick, so switching to another tab (No Layer) stops the paint without
 * disturbing the stored view, which the political-map tab restores when reselected. The host tab is
 * a plain {@link MapLayer} the composition root supplies, so this framework half names no concrete
 * tab any more than it names a concrete view.
 *
 * <p>The pick lives in sector memory under a stable key, so it serialises into the save and
 * survives reload. A key that was never written resolves to the registered default view (the map is
 * up the first time the sector map opens rather than dark until the player finds the control); the
 * off sentinel resolves to no view (dark while the tab stays open).
 */
public final class PoliticalMapViewRegistry {
    // Save-serialised id of the active view, or the off sentinel; frozen once shipped, since
    // renaming it silently resets every existing save to the default.
    private static final String ACTIVE_VIEW_KEY = "$kmu_political_active_view";

    // The stored value meaning "no view paints" - the map is off while the political-map tab stays
    // open. Empty because no view id is empty, so it never collides with a real pick.
    private static final String OFF_SELECTION = "";

    // The registered views, in radio-segment order, the pick an untouched save resolves to, and the
    // tab that hosts the view radio. Empty until a composition root registers them at startup,
    // before any sector map can open.
    private static List<PoliticalMapView> orderedViews = List.of();
    private static PoliticalMapView defaultView;
    private static MapLayer hostTab;

    private PoliticalMapViewRegistry() {
    }

    /**
     * Records the views the selector radio shows, the pick an untouched save resolves to, and the
     * tab whose body hosts the radio. Called once by the composition root at startup: it is the one
     * place a concrete view or tab is named, so the framework here stays agnostic to which exist.
     *
     * @param views       the registered views in radio-segment order
     * @param defaultView the pick an untouched save (or a stale stored id) resolves to
     * @param hostTab     the tab whose active-ness gates the paint (the political-map tab)
     */
    public static void registerViews(List<PoliticalMapView> views, PoliticalMapView defaultView,
            MapLayer hostTab) {
        orderedViews = List.copyOf(views);
        PoliticalMapViewRegistry.defaultView = defaultView;
        PoliticalMapViewRegistry.hostTab = hostTab;
    }

    /** @return the registered views in radio-segment order. */
    public static List<PoliticalMapView> getViews() {
        return orderedViews;
    }

    /**
     * @return the stored view pick regardless of which tab is open - the view the radio lights when
     *         the political-map tab is up; the default when the save holds no pick yet or an id from
     *         an older build no longer registered; null when the off sentinel is stored
     */
    public static PoliticalMapView getSelectedView() {
        var storedId = readStoredViewId();
        if (storedId == null) {
            return defaultView;
        }
        if (OFF_SELECTION.equals(storedId)) {
            return null;
        }
        for (var view : orderedViews) {
            if (view.getId().equals(storedId)) {
                return view;
            }
        }
        // Stale id from a build that shipped a view since removed: fall back rather than paint
        // nothing while the player expects a view.
        return defaultView;
    }

    /**
     * @return the index of the selected view in {@link #getViews()} - the radio's lit segment - or
     *         {@link kmu.maplayers.base.sidebar.SidebarControlSpec#NO_SELECTION} when off
     */
    public static int getSelectedViewIndex() {
        var selected = getSelectedView();
        // Off resolves to no view, which an immutable view list cannot be asked to index (it
        // rejects null), so the off state maps straight to the radio's no-selection sentinel.
        if (selected == null) {
            return SidebarControlSpec.NO_SELECTION;
        }
        return orderedViews.indexOf(selected);
    }

    /**
     * @return the view that paints right now: the selected view when the political-map tab is the
     *         active pick, or null when that tab is not active or no view is selected. This is the
     *         plugin's one read - it gates the paint and supplies the view's rules in one call.
     */
    public static PoliticalMapView getActiveView() {
        if (hostTab == null || !MapLayerRegistry.isActive(hostTab)) {
            return null;
        }
        return getSelectedView();
    }

    /**
     * Toggles the view a radio segment names: selecting it when it is not the current pick, or
     * turning the map off when it already is - the view radio's "click the lit row to switch off"
     * behaviour. A no-op before the sector exists, since there is no save to write into yet.
     */
    public static void toggleView(PoliticalMapView view) {
        if (getSelectedView() == view) {
            writeSelection(OFF_SELECTION);
        } else {
            writeSelection(view.getId());
        }
    }

    // Writes the active-view selection to sector memory, or does nothing before the sector exists.
    private static void writeSelection(String selection) {
        var memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null) {
            return;
        }
        memory.set(ACTIVE_VIEW_KEY, selection);
    }

    // Reads the stored view id (or the off sentinel), or null when the sector is absent or the key
    // was never written - the caller resolves either to the default.
    private static String readStoredViewId() {
        MemoryAPI memory = SectorMemoryAccess.readSectorMemory();
        if (memory == null || !memory.contains(ACTIVE_VIEW_KEY)) {
            return null;
        }
        return memory.getString(ACTIVE_VIEW_KEY);
    }
}
