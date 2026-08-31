package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.sidebar.ColumnSelectionBinder;
import kmu.maplayers.base.sidebar.FilterSelectionBinder;
import kmu.maplayers.politicalmap.base.render.PoliticalMapLayerRenderer;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;
import kmu.maplayers.politicalmap.base.sidebar.RecedeControl;
import kmu.maplayers.politicalmap.base.sidebar.SelectableBlocCache;
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

    // LunaLib stores a rebound key under the field id.
    private static final String SHORTCUT_SETTING_FIELD = "kmu_map_keybinds_layers_factions";

    private PoliticalMapLayer() {
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
        // view-selector radio that picks which view paints - one segment per registered view. The
        // radio only switches between views; turning the map off is the tab bar's No Layer pick.
        var controls = new ArrayList<>(PoliticalMapBodyControls.buildSharedControls());
        controls.add(PoliticalMapBodyControls.buildViewSelector());

        // Then the spotlight picker and the selected view's own controls, so the body shows the
        // filter list plus any widgets specific to the active view (the alliances view's
        // Mute/Desaturate checkboxes) and the layout grows downward to fit them. Use the selected
        // view - the one the radio lights while the tab is up - not the active view, since
        // getBodyControls is only reached for the active tab. No view selected means the map is off,
        // so there is nothing to append.
        var selectedView = PoliticalMapViewRegistry.getSelectedView();

        if (selectedView != null) {

            // The stored column count, resolved to the default (one column) when a save has never
            // picked one, so the list always lays out under a live count.
            var columns = ColumnSelectionBinder.resolveStoredColumns();
            var viewId = selectedView.getId();

            // The picker is the selected view's own - its selectable blocs under the player's live
            // dominance and visibility settings, bundled with the vocabulary that ranks them, so this
            // layer names neither the metrics a view's blocs carry nor the modes that sort them and a
            // view painted by another mechanic needs no edit here. Empty (no present bloc)
            // contributes no picker. Read through the memo so this per-frame body build reads a cached
            // list rather than re-walking the economy every frame the map is open.
            //
            // KMLib's picker composes the block and the binder ties its picks to this mod's save
            // slots; what pairs with its sort selector is this layer's to decide, and the political
            // map pairs it with the filter recede - how the rest of the sector fades behind a
            // spotlight. It is always shown: a change there simply has no visible effect until a bloc
            // is spotlighted, so the knobs stay put whether or not a filter is active. It is the same
            // reusable control the alliances view places under its own caption, here bound to the
            // filter recede set rather than the non-allied one.
            controls.addAll(FilterSelectionBinder.buildPicker(
                viewId,
                SelectableBlocCache.resolveBlocPickerRead(selectedView, Global.getSector()).picker(),
                columns,
                RecedeControl.buildControls(
                    RecedePreferences.FILTER,
                    KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION))));

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
        return SHORTCUT_SETTING_FIELD;
    }

    @Override
    public MapLayerRenderer resolveRenderer(MapLayerInstallation installation) {
        // View-neutral here as everywhere else on this tab: the renderer resolves which view is up,
        // so the tab hands over one renderer rather than branching on the view roster.
        //
        // Held by the installation rather than by this tab, because everything behind the renderer -
        // the cut cells, the territories, the fitted labels - is one sector's. This tab is
        // registered once for the process and would otherwise be where two sectors met.
        return installation.resolveMachinery(
            PoliticalMapLayerRenderer.class,
            () -> PoliticalMapLayerRenderer.createForLiveScreen(installation));
    }
}
