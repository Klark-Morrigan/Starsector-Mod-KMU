package kmu.settings;

/**
 * Whether the map answers the cursor, and over which surfaces.
 *
 * <p>Two questions rather than one, and they are here together because the second only ever
 * narrows the first: the three tiers say whether hovering, its effects and its box are wanted at
 * all, and the surface knobs say where that permission reaches once granted. A caller asking
 * either alone would still have to ask the other before drawing. *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why a default is the number it is, and what a caller
 * has to know to use the value.
 */
public final class KmuMapHoverSettings {

    // The upper two hover tiers, over the pair KmuOwnerMapHighlightSettings holds for every
    // owner-painted layer. That lower pair's IDs spell the political map's section because a
    // LunaLib field ID is frozen once shipped.
    private static final String HOVERING_ENABLED_FIELD =
        "kmu_map_visuals_hovering_isEnabled";

    private static final String HOVER_EFFECTS_ENABLED_FIELD =
        "kmu_map_visuals_hovering_areEffectsEnabled";

    private static final String HOVER_TOOLTIP_ENABLED_FIELD =
        "kmu_map_visuals_hovering_areTooltipsEnabled";

    private static final String MAP_LAYERS_ONLY_ON_THEIR_HOSTS_FIELD =
        "kmu_map_compatibility_foreignMapSurfaces_areLayersConstrainedToTheirHosts";

    // Two permissions rather than one restriction, because a tab holding a switch that grants next
    // to one that restricts cannot be read as a set. They overlap deliberately rather than forming
    // a three-way choice: with the global permission on, the game-space one adds nothing and says
    // nothing wrong, so no control has to prevent the combination.
    private static final String MAP_LAYER_MOUSEOVER_IS_GLOBAL_FIELD =
        "kmu_map_compatibility_foreignMapSurfaces_isMouseoverGlobal";

    private static final String MAP_LAYER_MOUSEOVER_IN_GAME_SPACE_FIELD =
        "kmu_map_compatibility_foreignMapSurfaces_isMouseoverEnabledInGameSpace";

    // The one foreign map surface named rather than described, because naming the mod is how a
    // player arrives at this tab. The ID files the player's choice, so it is fixed by what is
    // already saved rather than by what the mode is called here.
    private static final String RANDOM_ASSORTMENT_OF_THINGS_COMPATIBILITY_MODE_FIELD =
        "kmu_map_compatibility_foreignMapSurfaces_isRandomAssortmentOfThingsModeEnabled";

    // A tiered gate that shipped with any level off would read to a player as a feature that is
    // broken rather than switched off, and defaulting them on is what keeps the tiering invisible
    // to an existing player: the two IDs they may already have switched off still switch the same
    // feedback off.
    private static final boolean DEFAULT_HOVERING_ENABLED = true;

    private static final boolean DEFAULT_HOVER_EFFECTS_ENABLED = true;

    private static final boolean DEFAULT_HOVER_TOOLTIP_ENABLED = true;

    // Off: a foreign map drawing the layers is a picture the player can see and judge, and one
    // several players will have come to expect. Taking it away by default would remove a working
    // sight to pre-empt a complaint most installs never raise.
    private static final boolean DEFAULT_MAP_LAYERS_ONLY_ON_THEIR_HOSTS = false;

    // Off, unlike the picture above: the same pass answering the cursor is not a sight but a claim,
    // and a wrong one - a system named that the pointer is not on, a tick sounded for reaching it.
    // Granted over every surface on every screen, that is heard where no map is drawn at all.
    private static final boolean DEFAULT_MAP_LAYER_MOUSEOVER_IS_GLOBAL = false;

    // Both on, for one reason: each is inert wherever there is nothing to answer, so what it
    // defaults to is only ever read by a player who has docked a map surface or runs that minimap -
    // and to that player, layers going quiet over the map under the pointer read as the mod failing
    // rather than as a setting waiting to be found. A switch to turn off, not one to discover.
    private static final boolean DEFAULT_MAP_LAYER_MOUSEOVER_IS_ENABLED_IN_GAME_SPACE = true;

    private static final boolean DEFAULT_RANDOM_ASSORTMENT_OF_THINGS_COMPATIBILITY_MODE = true;

    private KmuMapHoverSettings() {
    }

    /**
     * @return whether the map answers the cursor at all; on by default. The master over both kinds
     *         of hover feedback on every layer, so with it off no cursor read runs and nothing
     *         hover-driven is drawn
     */
    public static boolean isMapHoveringEnabled() {
        return KmuLunaSettings.readBoolean(HOVERING_ENABLED_FIELD, DEFAULT_HOVERING_ENABLED);
    }

    /**
     * @return whether hover effects - the halo over the hovered cluster and the wash on its cell -
     *         are on across every map layer; on by default. Under the hovering master, and over
     *         each layer's own effects switch
     */
    public static boolean areMapHoverEffectsEnabled() {
        return KmuLunaSettings.readBoolean(
            HOVER_EFFECTS_ENABLED_FIELD,
            DEFAULT_HOVER_EFFECTS_ENABLED);
    }

    /**
     * @return whether the hover tooltip is on across every map layer; on by default. Under the
     *         hovering master, and over each layer's own tooltip switch. Read live each frame, so
     *         toggling it needs no rebuild
     */
    public static boolean isMapHoverTooltipEnabled() {
        return KmuLunaSettings.readBoolean(
            HOVER_TOOLTIP_ENABLED_FIELD,
            DEFAULT_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return whether the layers may only draw while one of the two vanilla map hosts is showing,
     *         so a map built by another mod does not paint them; off by default
     */
    public static boolean areMapLayersOnlyOnTheirHosts() {
        return KmuLunaSettings.readBoolean(
            MAP_LAYERS_ONLY_ON_THEIR_HOSTS_FIELD,
            DEFAULT_MAP_LAYERS_ONLY_ON_THEIR_HOSTS);
    }

    /**
     * @return whether the layers may answer the cursor on every pass there is, foreign map surfaces
     *         on any screen included; off by default, which permits the vanilla hosts alone
     */
    public static boolean isMapLayerMouseoverGlobal() {
        return KmuLunaSettings.readBoolean(
            MAP_LAYER_MOUSEOVER_IS_GLOBAL_FIELD,
            DEFAULT_MAP_LAYER_MOUSEOVER_IS_GLOBAL);
    }

    /**
     * @return whether the layers may additionally answer the cursor on the frames where the player
     *         is looking at the campaign world itself - no core screen open and no dialog up, which
     *         is where a mod's docked map surface is the only map on screen; on by default, being
     *         inert on an install that has no such surface to draw a pass there
     */
    public static boolean isMapLayerMouseoverEnabledInGameSpace() {
        return KmuLunaSettings.readBoolean(
            MAP_LAYER_MOUSEOVER_IN_GAME_SPACE_FIELD,
            DEFAULT_MAP_LAYER_MOUSEOVER_IS_ENABLED_IN_GAME_SPACE);
    }

    /**
     * @return whether the layers may adapt to the minimap Random Assortment of Things draws in the
     *         campaign radar's place - answering the cursor on it rather than standing clear of it;
     *         on by default. The player's half of the mode only: whether there is a minimap to
     *         adapt to is the other half, and both are ANDed before anything behaves differently
     */
    public static boolean isRandomAssortmentOfThingsCompatibilityModeEnabled() {
        return KmuLunaSettings.readBoolean(
            RANDOM_ASSORTMENT_OF_THINGS_COMPATIBILITY_MODE_FIELD,
            DEFAULT_RANDOM_ASSORTMENT_OF_THINGS_COMPATIBILITY_MODE);
    }
}
