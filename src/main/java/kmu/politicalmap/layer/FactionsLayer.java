package kmu.politicalmap.layer;

import kmu.util.KmuStrings;

import org.lwjgl.input.Keyboard;

/**
 * The faction-territory view: selecting it paints each system in its dominant faction's
 * colours - the political map proper. This is the layer the terrain plugin keys on
 * ({@link PoliticalMapLayers#isFactionTerritoryActive()}) to draw or stay dark. It shows no
 * caption; the painted map is the whole message.
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
        return KmuStrings.POLITICAL_MAP_TAB_FACTIONS;
    }

    @Override
    public String getCaptionKey() {
        return null;
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
