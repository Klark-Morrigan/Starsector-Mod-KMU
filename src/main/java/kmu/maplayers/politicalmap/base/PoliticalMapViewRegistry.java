package kmu.maplayers.politicalmap.base;

import kmlib.starsector.memory.AddressedMemoryString;
import kmlib.starsector.ui.controls.specs.ControlSpec;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScope;

import java.util.List;

/**
 * The political map's view registry and the single source of truth for which view paints - the
 * generalisation of the old faction on/off toggle into a selection among the registered views
 * (faction, later alliances). The view-selector radio composes its segments from
 * {@link #getViews()}, lights the one {@link #getSelectedViewIndex} reports, and writes a click
 * through {@link #selectView}; the shared terrain plugin reads {@link #getActiveView()} to decide
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
 * <p>The pick is one per screen, like every other choice made on a sidebar panel: the radio sits on
 * each screen's own body, so which view a screen paints is that screen's. Every read and write names
 * the screen it means, and the live reads name the screen showing this frame.
 *
 * <p>The pick lives in sector memory under a stable key, so it serialises into the save and
 * survives reload. A key that was never written resolves to the registered default view (the map is
 * up the first time the sector map opens rather than dark until the player finds the control); the
 * off sentinel resolves to no view (dark while the tab stays open).
 */
public final class PoliticalMapViewRegistry {

    // Save-serialised ID of the active view, or the off sentinel, before the screen's own segment;
    // frozen once shipped, since renaming it silently resets every existing save to the default.
    private static final String ACTIVE_VIEW_KEY = "$kmu_political_active_view";

    // The stored value meaning "no view paints" - the map is dark while the political-map tab stays
    // open. Empty because no view ID is empty, so it never collides with a real pick. Only the
    // legacy-overlay self-heal writes it: the view radio always lands on a view, and turning the map
    // off is the No Layer tab's job.
    private static final String OFF_SELECTION = "";

    // The active-view pick, held once per screen under the base key above; reads resolve an absent
    // key to null (the caller then falls to the default). The screen's segment is composed by the
    // holder, so this class states what it stores and never how the key is spelled.
    private static final AddressedMemoryString activeViewSelection =
        new AddressedMemoryString(ACTIVE_VIEW_KEY);

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
     * @param defaultView the pick an untouched save (or a stale stored ID) resolves to
     * @param hostTab     the tab whose active-ness gates the paint (the political-map tab)
     */
    public static void registerViews(
            List<PoliticalMapView> views,
            PoliticalMapView defaultView,
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
     * @return the view an untouched save (or a stale stored ID) resolves to; null before the
     *         composition root has registered the views at startup
     */
    public static PoliticalMapView getDefaultView() {
        return defaultView;
    }

    /**
     * @param memoryScope the screen whose pick is wanted - the panel the radio in question sits on
     * @return that screen's stored view pick regardless of which tab is open - the view its radio
     *         lights when the political-map tab is up; the default when the save holds no pick yet
     *         for that screen or an ID from an older build no longer registered; null when the off
     *         sentinel is stored
     */
    public static PoliticalMapView getSelectedView(ScreenMemoryScope memoryScope) {
        var storedId = activeViewSelection.get(memoryScope);
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
        // Stale ID from a build that shipped a view since removed: fall back rather than paint
        // nothing while the player expects a view.
        return defaultView;
    }

    /**
     * @param memoryScope the screen whose radio is being lit
     * @return the index of that screen's selected view in {@link #getViews()} - its radio's lit
     *         segment - or {@link kmlib.starsector.ui.controls.ControlSpec#NO_SELECTION} when off
     */
    public static int getSelectedViewIndex(ScreenMemoryScope memoryScope) {
        var selected = getSelectedView(memoryScope);
        // Off resolves to no view, which an immutable view list cannot be asked to index (it
        // rejects null), so the off state maps straight to the radio's no-selection sentinel.
        if (selected == null) {
            return ControlSpec.NO_SELECTION;
        }
        return orderedViews.indexOf(selected);
    }

    /**
     * @return the view that paints right now on the screen showing this frame: that screen's selected
     *         view when the political-map tab is its active pick, or null when that tab is not active
     *         there or no view is selected on it. This is the plugin's one read - it gates the paint
     *         and supplies the view's rules in one call
     */
    public static PoliticalMapView getActiveView() {
        return resolveActiveViewOn(MapLayerScreens.resolveLivePicks());
    }

    /**
     * The same answer as {@link #getActiveView()} for a screen already in hand.
     *
     * <p>For the caller that needs the view and the screen's scope together: the frame paints one
     * screen's view under that screen's preferences, so both come off one reading of which screen is
     * showing. Resolved separately they could name two screens, which is a picture neither panel was
     * ever set to.
     *
     * @param screenPicks the screen being answered for
     * @return that screen's selected view while the political-map tab is its active pick, else null
     */
    public static PoliticalMapView resolveActiveViewOn(ScreenLayerPicks screenPicks) {
        if (hostTab == null || !MapLayerRegistry.isDrawnLayerOn(screenPicks, hostTab)) {
            return null;
        }
        return getSelectedView(screenPicks.memoryScope());
    }

    /**
     * Stores the view a radio segment names as one screen's pick. Selecting the already-selected view
     * rewrites the same ID rather than clearing, so the radio always lands on a view - the map is
     * turned off by switching to the No Layer tab, not by re-clicking the lit view. A no-op before the
     * sector exists, since there is no save to write into yet.
     *
     * @param memoryScope the screen whose radio was clicked, which is the screen the pick is filed
     *                    against - the panel the control sits on, never whichever is showing when the
     *                    click lands
     * @param view        the view that segment names
     */
    public static void selectView(ScreenMemoryScope memoryScope, PoliticalMapView view) {
        activeViewSelection.set(memoryScope, view.getId());
    }
}
