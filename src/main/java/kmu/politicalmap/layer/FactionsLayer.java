package kmu.politicalmap.layer;

import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

/**
 * The faction-territory view: selecting it paints each system in its dominant faction's
 * colours - the political map proper. This is the layer the terrain plugin keys on
 * ({@link PoliticalMapLayers#isFactionTerritoryActive()}) to draw or stay dark. Its tab opens
 * the political-map control panel ({@link SidebarBodyKind#POLITICAL_MAP_CONTROLS}).
 */
public final class FactionsLayer implements PoliticalMapLayer {
    // Jumps here on P by default. Mirrors the Keycode default in LunaSettings.csv.
    private static final String SHORTCUT_FIELD = "kmu_politicalMapFactionsKey";

    @Override
    public String getId() {
        return "factions";
    }

    @Override
    public String getTabLabelKey() {
        return KmuStrings.POLITICAL_MAP_TAB_POLITICAL_MAP;
    }

    @Override
    public SidebarBodyKind getBodyKind() {
        return SidebarBodyKind.POLITICAL_MAP_CONTROLS;
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
