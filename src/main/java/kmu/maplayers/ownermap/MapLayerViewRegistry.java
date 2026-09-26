package kmu.maplayers.ownermap;

import kmlib.starsector.memory.AddressedMemoryString;
import kmlib.starsector.ui.controls.specs.ControlSpec;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.layer.ScreenMemoryScope;

import java.util.List;

/**
 * A layer's view registry and the single source of truth for which of its views paints. The
 * view-selector radio composes its segments from {@link #getViews()}, lights the one
 * {@link #getSelectedViewIndex} reports, and writes a click through {@link #selectView}; the frame
 * sequence asks {@link #resolveActiveViewOn} for the screen showing to decide both whether to paint
 * and which view's rules to paint under - so the frame names no concrete view and stays view-neutral.
 *
 * <p>Two selections are distinguished. The stored pick ({@link #getSelectedView}) is what the
 * player last chose in the radio, independent of which tab is open - the radio lights it whenever
 * the layer's tab is up. The active view ({@link #resolveActiveViewOn}) additionally requires that
 * tab to be the active pick, so switching to another tab (No Layer) stops the paint without
 * disturbing the stored view, which the layer's tab restores when reselected. The host tab is
 * a plain {@link MapLayer} the composition root supplies, so this framework half names no concrete
 * tab any more than it names a concrete view.
 *
 * <p>The pick is one per screen, like every other choice made on a sidebar panel: the radio sits on
 * each screen's own body, so which view a screen paints is that screen's. Every read and write names
 * the screen it means, and the live reads name the screen showing this frame.
 *
 * <p>The pick lives in sector memory under a key the layer names, so it serialises into the save and
 * survives reload. A key that was never written resolves to the registered default view (the map is
 * up the first time the sector map opens rather than dark until the player finds the control); the
 * off sentinel resolves to no view (dark while the tab stays open).
 *
 * <p>One per layer, and held by it. Two owner-painted layers each have a roster, a default and a tab
 * of their own, and a pick made on one's radio must not move the other's map - so nothing here is
 * process-wide. The instance holds no state that changes: the pick itself lives in the save, under
 * the key the layer handed over, which is what parts two layers' picks as surely as the screen's
 * scope parts two panels'.
 */
public final class MapLayerViewRegistry {

    // The stored value meaning "no view paints" - the map is dark while the layer's tab stays
    // open. Empty because no view ID is empty, so it never collides with a real pick. Nothing in the
    // mod writes it - the view radio always lands on a view, and turning the map off is the No Layer
    // tab's job - so it is only ever read, off a save whose stored pick already holds it.
    private static final String OFF_SELECTION = "";

    // The active-view pick, held once per screen under the layer's base key; reads resolve an absent
    // key to null (the caller then falls to the default). The screen's segment is composed by the
    // holder, so this class states what it stores and never how the key is spelled.
    private final AddressedMemoryString activeViewSelection;

    // The registered views, in radio-segment order, the pick an untouched save resolves to, and the
    // tab that hosts the view radio.
    private final List<OwnerPaintedView> orderedViews;
    private final OwnerPaintedView defaultView;
    private final MapLayer hostTab;

    /**
     * A layer's registry over its own roster, stored under its own key.
     *
     * <p>The layer is the one place its concrete views and its tab are named, so the framework here
     * stays agnostic to which exist.
     *
     * @param activeViewKey the sector-memory key the pick is stored under, before each screen's own
     *                      segment; save-serialised, so a layer freezes it once shipped - renaming it
     *                      silently resets every existing save to the default
     * @param views         the layer's views in radio-segment order
     * @param defaultView   the pick an untouched save (or a stale stored ID) resolves to
     * @param hostTab       the tab whose active-ness gates the paint (the layer's own tab)
     */
    public MapLayerViewRegistry(
            String activeViewKey,
            List<OwnerPaintedView> views,
            OwnerPaintedView defaultView,
            MapLayer hostTab) {

        this.activeViewSelection = new AddressedMemoryString(activeViewKey);
        this.orderedViews = List.copyOf(views);
        this.defaultView = defaultView;
        this.hostTab = hostTab;
    }

    /** @return the registered views in radio-segment order. */
    public List<OwnerPaintedView> getViews() {
        return orderedViews;
    }

    /**
     * @param memoryScope the screen whose pick is wanted - the panel the radio in question sits on
     * @return that screen's stored view pick regardless of which tab is open - the view its radio
     *         lights when the layer's tab is up; the default when the save holds no pick yet
     *         for that screen or an ID from an older build no longer registered; null when the off
     *         sentinel is stored
     */
    public OwnerPaintedView getSelectedView(ScreenMemoryScope memoryScope) {
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
    public int getSelectedViewIndex(ScreenMemoryScope memoryScope) {
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
     *         view when the layer's tab is its active pick, or null when that tab is not active
     *         there or no view is selected on it. This is the renderer's one read - it gates the paint
     *         and supplies the view's rules in one call
     */
    public OwnerPaintedView getActiveView() {
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
     * @return that screen's selected view while the layer's tab is its active pick, else null
     */
    public OwnerPaintedView resolveActiveViewOn(ScreenLayerPicks screenPicks) {
        if (!MapLayerRegistry.isDrawnLayerOn(screenPicks, hostTab)) {
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
    public void selectView(ScreenMemoryScope memoryScope, OwnerPaintedView view) {
        activeViewSelection.set(memoryScope, view.getId());
    }
}
