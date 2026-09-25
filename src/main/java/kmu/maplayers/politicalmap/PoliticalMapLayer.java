package kmu.maplayers.politicalmap;

import kmlib.starsector.ui.controls.specs.ControlSpec;

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
import kmu.maplayers.ownermap.MapLayerViewRegistry;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.preferences.NameFormatPreference;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.maplayers.ownermap.preferences.RecedePreferences;
import kmu.maplayers.ownermap.preferences.UninhabitedOutlinePreference;
import kmu.maplayers.ownermap.render.OwnerMapLayerRenderer;
import kmu.maplayers.ownermap.render.hover.SharedOwnerMapHoverGates;
import kmu.maplayers.ownermap.render.hover.SpotlightPreviewHighlightRenderer;
import kmu.maplayers.ownermap.sidebar.BodyControlTarget;
import kmu.maplayers.ownermap.sidebar.OwnerMapBodyControls;
import kmu.maplayers.ownermap.sidebar.RecedeControl;
import kmu.maplayers.ownermap.sidebar.SelectableBlocCache;
import kmu.maplayers.politicalmap.holders.DefaultHolderProvider;
import kmu.maplayers.politicalmap.holders.DominanceSystemHolderResolve;
import kmu.maplayers.politicalmap.render.PoliticalMapBandLayoutReader;
import kmu.settings.KmuMapKeybindSettings;
import kmu.util.KmuStringKeys;

import java.util.ArrayList;
import java.util.List;

/**
 * The political-map tab: the one on-map tab that opens the political-map control panel and its
 * view-selector radio. It is view-neutral - which view actually paints, and whether the map is on
 * at all, is its {@link MapLayerViewRegistry}'s business, not this tab's. Its body is the tab's
 * view-agnostic sub-options over the view-selector radio, so the faction, alliances and claims views
 * are options on the radio rather than tabs of their own.
 *
 * <p>It holds its own registry, and every piece of per-sector machinery it draws through is held
 * under its own ID: a second owner-painted layer standing beside it has a roster, a pick and a
 * renderer of its own, and nothing this layer holds is reachable from that one.
 */
public final class PoliticalMapLayer implements MapLayer {

    // The tab's save-serialised layer ID: the political map, not a specific view. The value stored
    // in the active-tab memory key, and the key this layer's per-sector machinery is held under.
    private static final String LAYER_ID = "political_map";

    // Save-serialised ID of the active view, or the off sentinel, before each screen's own segment;
    // frozen once shipped, since renaming it silently resets every existing save to the default.
    private static final String ACTIVE_VIEW_KEY = "$kmu_political_active_view";

    // LunaLib stores this tab's key under the field ID; the row it names is where the key is decided.
    private static final String SHORTCUT_SETTING_FIELD = "kmu_map_keybinds_layers_factions";

    // This layer's body preferences, each under the sector-memory key its choice has always been
    // saved under - frozen, since renaming one silently resets every save's choice to the default.
    private static final OwnerMapBodyPreferences BODY_PREFERENCES = new OwnerMapBodyPreferences(
        new NameFormatPreference("$kmu_political_name_format"),
        new UninhabitedOutlinePreference("$kmu_political_uninhabited_outline"),
        new RecedePreferences(
            "$kmu_political_filter_recede_mute",
            "$kmu_political_filter_recede_desaturate"));

    // This layer's views and the pick among them.
    private final MapLayerViewRegistry viewRegistry;

    // What this tab runs on a sector while it is on the bar. One for the tab rather than one per
    // ask, every sector arriving as an argument.
    private final MapLayerStanding standing;

    /**
     * The political map over its view roster.
     *
     * <p>The registry is made here rather than handed in, because this tab is its host: the
     * registry gates the paint on this tab being the active pick, so the two are made together and
     * cannot come to name different tabs. It only holds the tab, and asks it nothing until a frame
     * does.
     *
     * @param views       the views the radio offers, in segment order
     * @param defaultView the view a save that has never picked one paints
     */
    public PoliticalMapLayer(List<OwnerPaintedView> views, OwnerPaintedView defaultView) {
        this.viewRegistry = new MapLayerViewRegistry(ACTIVE_VIEW_KEY, views, defaultView, this);
        this.standing = new PoliticalMapStanding(viewRegistry);
    }

    /**
     * @return this layer's views and the pick among them, for the wiring that heals this layer's
     *         spotlights when the settings move
     */
    public MapLayerViewRegistry resolveViewRegistry() {
        return viewRegistry;
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
        // selector takes the screen and this body's sector rather than the target, since a view switch
        // raises nothing on the board - the sector is what its spotlight heal judges against.
        var controls = new ArrayList<>(OwnerMapBodyControls.buildSharedControls(BODY_PREFERENCES, target));
        controls.add(OwnerMapBodyControls.buildViewSelector(viewRegistry, machinery.resolveSector(), memoryScope));

        // Then the spotlight picker and the selected view's own controls, so the body shows the
        // filter list plus any widgets specific to the active view (the alliances view's
        // Mute/Desaturate checkboxes) and the layout grows downward to fit them. Use the asking
        // panel's selected view - the one its radio lights while the tab is up - not the active view,
        // since getBodyControls is only reached for the active tab and the body belongs to the screen
        // that asked rather than to whichever is showing. No view selected means the map is off, so
        // there is nothing to append.
        var selectedView = viewRegistry.getSelectedView(memoryScope);

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
        // The save heal, the five refresh listeners and the staleness poll, as the pair the
        // framework stands up and takes back. One instance for the tab: which sector each half acts
        // on arrives with the call.
        return standing;
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
        // Held under this layer's ID as well as the renderer's class: another owner-painted layer
        // draws through a renderer of the same class, and keyed by the class alone it would be handed
        // this one. The ID goes over to the renderer too, which reports its frame's rows under it.
        // Handed down rather than looked up, so there is one spelling of it.
        return machinery.resolveLayerMachinery(
            LAYER_ID,
            OwnerMapLayerRenderer.class,
            () -> OwnerMapLayerRenderer.createForLiveScreen(
                machinery,
                LAYER_ID,
                viewRegistry,
                BODY_PREFERENCES,
                new SpotlightPreviewHighlightRenderer(machinery, LAYER_ID, KmuMod.MAP_STORE_NAMESPACE),
                SharedOwnerMapHoverGates.INSTANCE,
                PoliticalMapBandLayoutReader::readChosenLayout,
                DefaultHolderProvider.INSTANCE,
                DominanceSystemHolderResolve::openResolveOver));
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
            OwnerPaintedView selectedView,
            SectorMapMachinery machinery,
            BodyControlTarget target) {

        // The list is read through the memo the sector's installed machinery holds, so this
        // per-frame body build reads a cached list rather than re-walking the economy every frame
        // the map is open. The machinery goes over whole for the same reason the target does: the
        // memo, the picker's own writers and the recede control below must not end up naming two
        // different sectors, which passing a board beside it would allow.
        var blocCache = SelectableBlocCache.resolveBlocCacheIn(machinery, LAYER_ID);

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
                BODY_PREFERENCES.filterRecede(),
                KmuStringKeys.get(KmuStringKeys.POLITICAL_MAP_CTL_FILTER_RECEDE_CAPTION),
                target),
            machinery);
    }
}
