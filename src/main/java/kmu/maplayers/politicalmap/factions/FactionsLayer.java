package kmu.maplayers.politicalmap.factions;

import kmu.maplayers.base.layer.MapLayer;
import kmu.maplayers.base.sidebar.SidebarControlKind;
import kmu.maplayers.base.sidebar.SidebarControlSpec;
import kmu.maplayers.politicalmap.base.sidebar.PoliticalMapBodyControls;
import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

/**
 * The faction-territory view: selecting it paints each system in its dominant faction's
 * colours - the political map proper. This is the layer whose overlay state
 * ({@link FactionOverlayState#isFactionTerritoryActive()}) the terrain plugin keys on to draw
 * or stay dark. Its tab opens the political-map control panel: the tab's shared controls plus
 * this layer's own faction-overlay toggle.
 */
public final class FactionsLayer implements MapLayer {
    /** The one shared instance; the registration and the overlay gate reference this pick. */
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
        // The tab's shared checkbox and name-format radio, then this layer's own overlay
        // toggle - lit when the faction paint is on, so no On/Off word is needed.
        var controls = new ArrayList<>(PoliticalMapBodyControls.buildSharedControls());
        controls.add(new SidebarControlSpec(SidebarControlKind.TOGGLE,
                List.of(KmuStrings.get(KmuStrings.POLITICAL_MAP_CTL_FACTIONS)), ""));
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
