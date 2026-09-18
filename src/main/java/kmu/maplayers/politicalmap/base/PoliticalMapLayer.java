package kmu.maplayers.politicalmap.base;

import kmlib.starsector.ui.controls.ControlSpec;

import kmu.KmuMod;
import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.layer.MapLayerStanding;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.render.MapLayerRenderer;
import kmu.maplayers.base.sidebar.ListPickerBinder;
import kmu.maplayers.base.sidebar.ScreenSelectionSlot;
import kmu.maplayers.base.sidebar.SelectionSlot;
import kmu.maplayers.politicalmap.base.render.PoliticalMapLayerRenderer;
import kmu.maplayers.politicalmap.base.sidebar.BodyControlTarget;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;
import kmu.maplayers.politicalmap.base.sidebar.RecedeControl;
import kmu.maplayers.politicalmap.base.sidebar.SelectableBlocCache;
import kmu.settings.KmuMapKeybindSettings;
import kmu.util.KmuStringKeys;

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

    // The tab's save-serialised layer ID: the political map, not a specific view. The value stored
    // in the active-tab memory key.
    private static final String LAYER_ID = "political_map";

    // LunaLib stores this tab's key under the field ID; the row it names is where the key is decided.
    private static final String SHORTCUT_SETTING_FIELD = "kmu_map_keybinds_layers_factions";

    // What this tab runs on a sector while it is on the bar. One for the tab rather than one per
    // ask, the pair holding nothing and every sector arriving as an argument.
    private static final MapLayerStanding STANDING = new PoliticalMapStanding();

    private PoliticalMapLayer() {
    }

    @Override
    public String getId() {
        return LAYER_ID;
    }

    @Override
    public String resolveTabLabelText() {
        return KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_TAB_POLITICAL_MAP);
    }

    @Override
    public boolean isOfferedAsDefaultPick() {
        // The layer a fresh save opens on, and what a stored pick naming a layer since removed falls
        // back to: the overlay is the point of the mod, so it is up the first time the sector map is
        // opened rather than waiting to be found on the strip.
        return true;
    }

    @Override
    public List<ControlSpec> getBodyControls(ScreenMemoryScope memoryScope) {

        // Which sector this body is being built for has to be resolved off the running game here: a
        // body build is an adapter onto a vanilla screen, which hands it none. This is the one
        // resolution the whole build makes, and it is paired with the asking panel's screen into the
        // target every control below is handed - a control writes a sidebar-only preference, which
        // repaints by raising a signal, so a sector or a screen found at the click would repaint
        // whichever map was running by then and file the write under whichever panel was showing,
        // rather than the map the control was placed over.
        var machinery = SectorMapMachineryIndex.resolveMachineryForLiveSector();
        var target = new BodyControlTarget(machinery.resolveRefreshBoard(), memoryScope);

        // The tab's view-agnostic sub-options (uninhabited checkbox, name-format radio), then the
        // view-selector radio that picks which view paints - one segment per registered view. The
        // radio only switches between views; turning the map off is the tab bar's No Layer pick. The
        // selector takes the screen alone, since a view switch raises nothing on the board.
        var controls = new ArrayList<>(PoliticalMapBodyControls.buildSharedControls(target));
        controls.add(PoliticalMapBodyControls.buildViewSelector(memoryScope));

        // Then the spotlight picker and the selected view's own controls, so the body shows the
        // filter list plus any widgets specific to the active view (the alliances view's
        // Mute/Desaturate checkboxes) and the layout grows downward to fit them. Use the asking
        // panel's selected view - the one its radio lights while the tab is up - not the active view,
        // since getBodyControls is only reached for the active tab and the body belongs to the screen
        // that asked rather than to whichever is showing. No view selected means the map is off, so
        // there is nothing to append.
        var selectedView = PoliticalMapViewRegistry.getSelectedView(memoryScope);

        if (selectedView != null) {
            controls.addAll(buildSpotlightControls(selectedView, machinery, target));
            controls.addAll(selectedView.getViewBodyControls(target));
        }
        return List.copyOf(controls);
    }

    @Override
    public int resolveShortcutKeycode() {
        // The framework asks for the key in force rather than for a field to read, so the LunaLib
        // lookup is this tab's own: the field ID is a row in KMU's settings file, which is a fact
        // about this mod rather than about the bar the tab stands in.
        return KmuMapKeybindSettings.getMapLayerShortcut(SHORTCUT_SETTING_FIELD);
    }

    @Override
    public MapLayerStanding resolveStanding() {
        // The save heal, the four refresh listeners and the staleness poll, as the pair the
        // framework stands up and takes back. One instance for the tab, the pair holding nothing:
        // which sector each half acts on arrives with the call.
        return STANDING;
    }

    @Override
    public MapLayerRenderer resolveRenderer(SectorMapMachinery machinery) {
        // View-neutral here as everywhere else on this tab: the renderer resolves which view is up,
        // so the tab hands over one renderer rather than branching on the view roster.
        //
        // Held by the machinery rather than by this tab, because everything behind the renderer -
        // the cut cells, the territories, the fitted labels - is one sector's. This tab is
        // registered once for the process and would otherwise be where two sectors met.
        //
        // The ID goes over beside it because the renderer reports its frame's rows under it. Handed
        // down rather than looked up, so there is one spelling of it.
        return machinery.resolveMachinery(
            PoliticalMapLayerRenderer.class,
            () -> PoliticalMapLayerRenderer.createForLiveScreen(machinery, LAYER_ID));
    }

    // The spotlight picker for the selected view: its selectable blocs under the player's live
    // dominance and visibility settings, bundled with the vocabulary that ranks them, so this layer
    // names neither the metrics a view's blocs carry nor the modes that sort them and a view painted
    // by another mechanic needs no edit here. Empty (no present bloc) contributes no picker.
    //
    // KMLib's picker composes the block and the binder ties its picks to this mod's save slots; what
    // pairs with its sort selector is this layer's to decide, and the political map pairs it with
    // the filter recede - how the rest of the sector fades behind a spotlight. It is always shown: a
    // change there simply has no visible effect until a bloc is spotlighted, so the knobs stay put
    // whether or not a filter is active. It is the same reusable control the alliances view places
    // under its own caption, here bound to the filter recede set rather than the non-allied one.
    private static List<ControlSpec> buildSpotlightControls(
            PoliticalMapView selectedView,
            SectorMapMachinery machinery,
            BodyControlTarget target) {

        // The list is read through the memo the sector's installed machinery holds, so this
        // per-frame body build reads a cached list rather than re-walking the economy every frame
        // the map is open. The machinery goes over whole for the same reason the target does: the
        // memo, the picker's own writers and the recede control below must not end up naming two
        // different sectors, which passing a board beside it would allow.
        var blocCache = SelectableBlocCache.resolveBlocCacheIn(machinery);

        // The asking panel's screen goes to the picker's stores as well as to the controls above it,
        // so a spotlight, a sort or a column count picked here is that panel's own. It travels under
        // this mod's store namespace, which is what keeps these picks off another mod's layer, and
        // paired with the view's ID, since a view keeps its own picks: the three are the picker's
        // whole address. Stated once and once only - every store the picker reads or writes is
        // addressed off this one value, so nothing here can leave one of its answers somewhere else.
        return ListPickerBinder.buildPicker(
            new SelectionSlot(
                new ScreenSelectionSlot(KmuMod.MAP_STORE_NAMESPACE, target.memoryScope()),
                selectedView.getId()),
            blocCache.resolveBlocPickerRead(selectedView).picker(),
            RecedeControl.buildControls(
                RecedePreferences.FILTER,
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION),
                target),
            machinery);
    }
}
