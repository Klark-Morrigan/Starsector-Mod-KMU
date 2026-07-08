package kmu.maplayers.politicalmap.factions;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;
import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The political-map tab: the one tab that hosts the political-map control panel and its
 * view-selector radio. The faction view is one of the views that radio offers (the only one so
 * far); which view actually paints, and whether the map is on at all, is the shared
 * {@link kmu.maplayers.politicalmap.base.PoliticalMapViewRegistry}'s business, not this tab's. Its
 * body is the tab's view-agnostic sub-options over the view-selector radio.
 */
public final class FactionsLayer implements MapLayer {
    /** The one shared instance; the tab registration and the view registry's host tab reference it. */
    public static final FactionsLayer INSTANCE = new FactionsLayer();

    // Jumps here on P by default. Mirrors the Keycode default in LunaSettings.csv.
    private static final String SHORTCUT_FIELD = "kmu_politicalMapFactionsKey";

    private FactionsLayer() {
    }

    @Override
    public String getId() {
        return "factions";
    }

    @Override
    public String getTabLabelKey() {
        return KmuStrings.POLITICAL_MAP_TAB_POLITICAL_MAP;
    }

    @Override
    public List<SidebarControlSpec> getBodyControls() {
        // The tab's view-agnostic sub-options (uninhabited checkbox, name-format radio), then the
        // view-selector radio that picks which view paints - one segment per registered view, and
        // the map's on/off since clicking the lit view deselects it.
        var controls = new ArrayList<>(PoliticalMapBodyControls.buildSharedControls());
        controls.add(PoliticalMapBodyControls.buildViewSelector());
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
