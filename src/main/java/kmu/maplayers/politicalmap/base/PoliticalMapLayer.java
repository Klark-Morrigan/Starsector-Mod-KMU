package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerRegistry;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;
import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The political-map tab: the one on-map tab that opens the political-map control panel and its
 * view-selector radio. It is view-neutral - which view actually paints, and whether the map is on
 * at all, is the shared {@link PoliticalMapViewRegistry}'s business, not this tab's. Its body is the
 * tab's view-agnostic sub-options over the view-selector radio, so the faction view (and, later, the
 * alliances view) are options on the radio rather than tabs of their own. It lives in {@code base}
 * with the view seam and registry it composes, since the tab itself names no concrete view.
 */
public final class PoliticalMapLayer implements MapLayer {
    /** The one shared instance; the tab registration and the view registry's host tab reference it. */
    public static final PoliticalMapLayer INSTANCE = new PoliticalMapLayer();

    // The tab's save-serialised layer id: the political map, not a specific view. The value stored
    // in the active-tab memory key.
    private static final String LAYER_ID = "political_map";

    // The id this tab stored before it was generalised from the faction layer into the view-neutral
    // political-map tab. A pre-rename save holds this under the active-tab key; migrateLegacyStoredId
    // rewrites it to LAYER_ID so the stored value tracks the current implementation and no stale
    // spelling lingers in the save.
    private static final String LEGACY_LAYER_ID = "factions";

    // Jumps here on P by default. Mirrors the Keycode default in LunaSettings.csv. This is a LunaLib
    // settings field id, not sector memory; it keeps its original spelling until a settings-side
    // migration renames the CSV field and carries the player's stored keybind across.
    private static final String SHORTCUT_FIELD = "kmu_politicalMapFactionsKey";

    private PoliticalMapLayer() {
    }

    /**
     * Rewrites a pre-rename save's stored tab id ({@link #LEGACY_LAYER_ID}) to the current
     * {@link #LAYER_ID}, so the active-tab pick tracks the current implementation and the stale
     * spelling does not linger in the save. A no-op when the save holds the current id or no pick.
     * Call once on game load.
     */
    public static void migrateLegacyStoredId() {
        MapLayerRegistry.migrateStoredLayerId(LEGACY_LAYER_ID, LAYER_ID);
    }

    @Override
    public String getId() {
        return LAYER_ID;
    }

    @Override
    public String getTabLabelKey() {
        return KmuStrings.POLITICAL_MAP_TAB_POLITICAL_MAP;
    }

    @Override
    public List<ControlSpec> getBodyControls() {
        // The tab's view-agnostic sub-options (uninhabited checkbox, name-format radio), then the
        // view-selector radio that picks which view paints - one segment per registered view, and
        // the map's on/off since clicking the lit view deselects it.
        var controls = new ArrayList<>(PoliticalMapBodyControls.buildSharedControls());
        controls.add(PoliticalMapBodyControls.buildViewSelector());
        // Finally the selected view's own controls, so the body shows widgets specific to the active
        // view (the alliances view's Mute/Desaturate checkboxes) and the layout grows downward to
        // fit them. Use the selected view - the one the radio lights while the tab is up - not the
        // active view, since getBodyControls is only reached for the active tab. No view selected
        // means the map is off, so there is nothing to append.
        var selectedView = PoliticalMapViewRegistry.getSelectedView();
        if (selectedView != null) {
            controls.addAll(selectedView.getViewBodyControls());
        }
        return List.copyOf(controls);
    }

    @Override
    public int getDefaultShortcutKeycode() {
        return Keyboard.KEY_P;
    }

    @Override
    public String getShortcutSettingKey() {
        return SHORTCUT_FIELD;
    }
}
