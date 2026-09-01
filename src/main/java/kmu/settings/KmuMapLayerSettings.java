package kmu.settings;

import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;

/**
 * The map-layer framework's own LunaLib knobs: the chrome and the geometry every map layer
 * shares, whichever layer is drawing.
 *
 * <p>One of the knob classes {@link KmuLunaSettings} splits by reader. Everything here is
 * read by {@code kmu.maplayers.base} and nothing else, so a knob one layer owns cannot
 * arrive through this class - which is what stops the framework depending on a feature for
 * its own appearance.
 *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The
 * prose here answers only what that column cannot: why a default is the number it is, and
 * what a caller has to know to use the value.
 */
public final class KmuMapLayerSettings {

    private static final String MAP_LAYER_HIDE_FADE_SECONDS_FIELD =
        "kmu_map_visuals_layers_hideFadeSeconds";

    // Whether the control that drives the fade above is put on the vanilla filter row at all. Dev
    // rather than visuals: what it governs is a reach into another party's widget, so it is the hatch
    // a player opens when that reach misbehaves, not a knob they set to taste.
    private static final String FILTER_ROW_TOGGLE_ENABLED_FIELD =
        "kmu_map_dev_ui_filters_mapLayersToggle_isEnabled";

    private static final String SIDEBAR_PADDING_TOP_FIELD =
        "kmu_map_visuals_sidebar_paddingTop";
    private static final String SIDEBAR_PADDING_LEFT_FIELD =
        "kmu_map_visuals_sidebar_paddingLeft";
    private static final String SIDEBAR_PADDING_BOTTOM_FIELD =
        "kmu_map_visuals_sidebar_paddingBottom";
    private static final String SIDEBAR_BORDER_WIDTH_FIELD =
        "kmu_map_visuals_sidebar_borderWidth";
    private static final String SIDEBAR_OPACITY_FIELD =
        "kmu_map_visuals_sidebar_opacity";
    private static final String SIDEBAR_COLLAPSE_SECONDS_FIELD =
        "kmu_map_visuals_sidebar_collapseSeconds";

    private static final String SIDEBAR_COLOUR_SCHEME_FIELD =
        "kmu_map_visuals_sidebar_colours_scheme";
    private static final String SIDEBAR_CHEVRON_COLOR_FIELD =
        "kmu_map_visuals_sidebar_colours_chevron";

    // The same sidebar box drawn on the intel screen sits flush against the left edge of that
    // screen's map preview (the "visor") and hangs from the visor top, whose height caps the box -
    // so a top offset is the only placement knob that screen needs.
    private static final String INTEL_SIDEBAR_PADDING_TOP_FIELD =
        "kmu_map_visuals_sidebar_intelPaddingTop";

    // One knob per lettered row, because each is read against the vanilla chrome it stands beside
    // rather than against the face it is lettered in.
    private static final String INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD =
        "kmu_map_dev_ui_fontSharpness_tabHeaders_sidebar_intelScreen";
    private static final String MAP_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD =
        "kmu_map_dev_ui_fontSharpness_tabHeaders_sidebar_mMap";

    // The upper two hover tiers, over every layer's own pair. The lower two ids read as the
    // political map's because they predate the framework.
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
    // player arrives at this tab. The id files the player's choice, so it is fixed by what is
    // already saved rather than by what the mode is called here.
    private static final String RANDOM_ASSORTMENT_OF_THINGS_COMPATIBILITY_MODE_FIELD =
        "kmu_map_compatibility_foreignMapSurfaces_isRandomAssortmentOfThingsModeEnabled";

    // Stated by depth rather than by what a layer lists at that depth, the hover box being shared:
    // one layer's box lands them on a colony's terms, another's box of the same shape is set by the
    // same knobs.
    private static final String TOOLTIP_NESTING_LEVEL_SHRINK_FIELD =
        "kmu_map_visuals_tooltips_density_nestingLevelShrink";
    private static final String TOOLTIP_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_lineGap_tier1";
    private static final String TOOLTIP_TIER_2_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_lineGap_tier2";
    private static final String TOOLTIP_TIER_3_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_lineGap_tier3";

    // The two solid marks a hover box draws among its glyphs. Both are knobs rather than constants
    // for the same reason: how heavy a solid run looks beside a line of letters turns on the face,
    // the size it draws at, and how that atlas was rasterised, so where it sits against the text is
    // a judgement made on screen.
    private static final String TOOLTIP_LEADER_THICKNESS_FIELD =
        "kmu_map_visuals_tooltips_leader_thickness";
    private static final String TOOLTIP_LEADER_OPACITY_FIELD =
        "kmu_map_visuals_tooltips_leader_opacity";
    private static final String TOOLTIP_REDACTION_DARKENING_STRENGTH_FIELD =
        "kmu_map_visuals_tooltips_redacted_darkeningStrength";

    // One level per kind of thing there is to reach, named for what the player is reaching rather
    // than for the widget classes, which is what keeps the set closed as widgets are added.
    private static final String SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_panelChrome";
    private static final String SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_singleOptionControl";
    private static final String SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_listedItem";

    // Its own knob and not one of the arrival levels, because scrolling is not an arrival: the rows
    // travel under a parked cursor.
    private static final String SIDEBAR_LIST_SCROLL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_listScroll";
    private static final String MAP_CELL_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_map_arrival_cell";

    // Always applied - upstream of the smoothing passes a layer may gate - so the tracing has no
    // switch of its own.
    private static final String BORDER_WELD_TOLERANCE_FIELD =
        "kmu_map_dev_borderTracing_weldTolerance";
    private static final String BORDER_MITER_LIMIT_FIELD =
        "kmu_map_dev_borderTracing_miterLimit";

    // The per-cluster label-anchor search: the straight line a cluster's name sits on, chosen by
    // scoring candidate lines swept across the cluster (a fan of directions times a family of
    // parallel offsets), each fit inside the national border and clear of the system icons. Live
    // knobs rather than constants so the search can be tuned on the open map; all feed the
    // drawables rebuild, which re-runs the search for every anchor.
    private static final String ANCHOR_DIRECTION_COUNT_FIELD =
        "kmu_map_dev_labelAnchors_directionCount";
    private static final String ANCHOR_OFFSET_COUNT_FIELD =
        "kmu_map_dev_labelAnchors_offsetCount";
    private static final String ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD =
        "kmu_map_dev_labelAnchors_verticalPenalty_strength";
    private static final String ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD =
        "kmu_map_dev_labelAnchors_verticalPenalty_exponent";
    private static final String ANCHOR_MAX_SLANT_DEGREES_FIELD =
        "kmu_map_dev_labelAnchors_maxSlantDegrees";
    private static final String ANCHOR_END_INSET_MULTIPLE_FIELD =
        "kmu_map_dev_labelAnchors_endInsetMultiple";
    private static final String ANCHOR_ICON_CLEARANCE_FIELD =
        "kmu_map_dev_labelAnchors_iconClearance";
    private static final String ANCHOR_FONT_HEIGHT_TOLERANCE_FIELD =
        "kmu_map_dev_labelAnchors_fontHeightTolerance";
    private static final String ANCHOR_BAND_OPACITY_FIELD =
        "kmu_map_dev_labelAnchors_bandOpacity";
    private static final String ANCHOR_BAND_LINE_OPACITY_FIELD =
        "kmu_map_dev_labelAnchors_bandLineOpacity";

    private static final String NAME_MIN_FONT_SIZE_FIELD =
        "kmu_map_visuals_labels_fontSize_min";
    private static final String NAME_MAX_FONT_SIZE_FIELD =
        "kmu_map_visuals_labels_fontSize_max";
    private static final String NAME_MAX_LINES_FIELD =
        "kmu_map_visuals_labels_maxLines";
    private static final String NAME_LINE_SPACING_FIELD =
        "kmu_map_visuals_labels_lineSpacing";

    private static final String SHOW_REJECTED_AXES_FIELD =
        "kmu_map_dev_diagnostics_labels_boxes_areRejectedShown";
    private static final String SHOW_UNBIASED_AXES_FIELD =
        "kmu_map_dev_diagnostics_labels_boxes_areUnbiasedShown";

    // Each override reaches exactly the one thing it names: a knob that also cleared a neighbour's
    // gate would show a player a second thing they never asked for, with nothing on screen saying
    // why. What each does to the colony rule is RevelationGate's and ColonyVisibility's word.
    //
    // The first two are the spoiler gates, over the shapes the discovery fog alone leaks - an
    // unowned station nobody ever lived on, and a market that conceals itself. Both sit on entities
    // that were never discoverable, so the fog admits them from the first day of a campaign. The
    // last three widen rather than gate, and all three reseed the geometry, so moving one forces a
    // geometry rebuild rather than the restyle a styling knob triggers.
    private static final String SHOW_UNSEEN_ABANDONED_STATIONS_FIELD =
        "kmu_map_visibility_overrides_shouldShowUnseenAbandonedStations";
    private static final String SHOW_UNSEEN_HIDDEN_MARKETS_FIELD =
        "kmu_map_visibility_overrides_shouldShowUnseenHiddenMarkets";
    private static final String SHOW_UNDISCOVERED_MARKETS_FIELD =
        "kmu_map_visibility_overrides_shouldShowUndiscoveredMarkets";
    private static final String SHOW_DECIVILISED_WORLDS_AT_SURVEY_LEVEL_FIELD =
        "kmu_map_visibility_overrides_decivilisedWorldSurveyLevel";
    private static final String SHOW_HIDDEN_SYSTEMS_FIELD =
        "kmu_map_visibility_overrides_shouldShowHiddenSystems";

    // Fallbacks answering only while a setting is read before LunaLib has loaded it; the live
    // values come from LunaLib. Each mirrors the defaultValue column in
    // data/config/LunaSettings.csv, which is the number a fresh player is actually given, and is
    // held against it by the settings suite. Only the numbers whose reasoning is not in that
    // column carry a note.
    private static final int DEFAULT_SIDEBAR_PADDING_TOP = 36;
    private static final int DEFAULT_SIDEBAR_PADDING_LEFT = 9;
    private static final int DEFAULT_SIDEBAR_PADDING_BOTTOM = 79;

    // Leaves the same three-pixel channel the vanilla Starscape and fuel-range toggles keep between
    // themselves, so the row below them reads as another row of that set rather than as a box
    // parked under them. Measured against the real row rather than added up from it: how far those
    // toggles reach past their band is theirs to know.
    private static final int DEFAULT_INTEL_SIDEBAR_PADDING_TOP = 21;

    // Settled in game, and at opposite ends: what matches vanilla's buttons on the intel screen is
    // nearly all of the hard edge, where the map row read against the Sector/System tabs directly
    // above it wants none of it - anything above nothing came out brighter than those tabs. That the
    // same trade lands so far apart is the argument for the two knobs: it is the chrome each row
    // abuts that decides, not the face.
    private static final double DEFAULT_INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS = 0.8;
    private static final double DEFAULT_MAP_SIDEBAR_PIXEL_FONT_SHARPNESS = 0.0;

    private static final int DEFAULT_SIDEBAR_BORDER_WIDTH = 1;

    // Stored 0..100 in the CSV, exposed 0..1.
    private static final int DEFAULT_SIDEBAR_OPACITY_PERCENT = 80;
    private static final int MIN_SIDEBAR_OPACITY_PERCENT = 0;
    private static final int MAX_SIDEBAR_OPACITY_PERCENT = 100;

    // Mirrors TabPanelCollapse.DEFAULT_DURATION_SECONDS, the KMLib holder's own default pace;
    // restated as a literal like every fallback here so this class stays decoupled from the widget
    // library.
    private static final float DEFAULT_SIDEBAR_COLLAPSE_SECONDS = 0.25f;

    // The same 0.25 as the collapse pace above, so the two chrome movements on that screen agree out of
    // the box. Restated rather than read off the collapse knob: a LunaLib slider always holds a concrete
    // value, so there is no unset state a dynamic default could fall back from, and a sentinel for
    // "follow the other slider" would collide with 0 already meaning snap.
    private static final float DEFAULT_MAP_LAYER_HIDE_FADE_SECONDS = 0.25f;

    // On: switched off no screen is given the control, and a screen with no control holds its layers
    // shown - so a player who never opens the settings would get a feature that draws with no way to
    // put it away. An escape hatch is reached for after something misbehaves, so it ships open.
    private static final boolean DEFAULT_FILTER_ROW_TOGGLE_ENABLED = true;

    // The sidebar is drawn among vanilla chrome, all of which answers to the fixed UI palette
    // whatever faction the player flies, so a panel following the faction is the one thing on the
    // screen that moves when the others do not. On a stock install the two schemes resolve to the
    // same shades, so this default only shows itself on a recoloured player faction - which is the
    // case it exists for.
    private static final SidebarColourSchemeChoice DEFAULT_SIDEBAR_COLOUR_SCHEME =
        SidebarColourSchemeChoice.UI_PALETTE;

    // The collapse handle hangs off the frame over the map, so its chevron reads clearest pitched
    // against the panel's accents rather than painted in them.
    private static final NotchChevronColourChoice DEFAULT_SIDEBAR_CHEVRON_COLOUR =
        NotchChevronColourChoice.GOLD;

    // A tiered gate that shipped with any level off would read to a player as a feature that is
    // broken rather than switched off, and defaulting them on is what keeps the tiering invisible
    // to an existing player: the two ids they may already have switched off still switch the same
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

    // The gaps tighten only the depths a listing runs long at: lines two steps in stand closer to
    // each other than the things they belong to do, and lines three steps in closer still, so each
    // run reads as one thing without the box's own spacing changing.
    private static final float DEFAULT_TOOLTIP_NESTING_LEVEL_SHRINK = 0.75f;
    private static final float DEFAULT_TOOLTIP_LINE_GAP = 4f;
    private static final float DEFAULT_TOOLTIP_TIER_2_LINE_GAP = 0.5f;
    private static final float DEFAULT_TOOLTIP_TIER_3_LINE_GAP = 0f;

    // Both marks are let down until they sit level with the text either side of them rather than
    // above it: a whole pixel of leader at roughly two thirds of the box's opacity, and two fifths
    // of a line's colour taken off the blocks a withheld name draws as.
    private static final float DEFAULT_TOOLTIP_LEADER_THICKNESS = 1f;
    private static final float DEFAULT_TOOLTIP_LEADER_OPACITY = 0.65f;
    private static final float DEFAULT_TOOLTIP_REDACTION_DARKENING_STRENGTH = 0.4f;

    // The shipped balance: vanilla's own mouseover level halved for anything the player aims at,
    // and halved again for the items a sweep crosses several of on its way there. The gap between
    // the two numbers is the whole of what stops a column of listed rows reading as chatter, so
    // they are only meaningful against each other - a player raising one has retuned the balance
    // rather than turned up a part of it. The scroll and the map's cell tick are single moments and
    // sit at the aimed-at level.
    private static final float DEFAULT_SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME = 0.5f;
    private static final float DEFAULT_SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME = 0.5f;
    private static final float DEFAULT_SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME = 0.25f;
    private static final float DEFAULT_SIDEBAR_LIST_SCROLL_VOLUME = 0.5f;
    private static final float DEFAULT_MAP_CELL_ARRIVAL_VOLUME = 0.5f;

    private static final double DEFAULT_BORDER_WELD_TOLERANCE = 100.0;
    private static final double DEFAULT_BORDER_MITER_LIMIT = 4.0;

    private static final int DEFAULT_ANCHOR_DIRECTION_COUNT = 8;
    private static final int DEFAULT_ANCHOR_OFFSET_COUNT = 10;
    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH = 0.3;
    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT = 2.0;
    private static final double DEFAULT_ANCHOR_MAX_SLANT_DEGREES = 45.0;
    private static final double DEFAULT_ANCHOR_END_INSET_MULTIPLE = 4.0;
    private static final double DEFAULT_ANCHOR_ICON_CLEARANCE = 100.0;

    // One world unit, on a font clamped between 200 and 1200 of them: already far below what a map
    // pixel resolves at any zoom, so the halvings this stops the font search short of would have
    // bought a height difference no one can see.
    private static final double DEFAULT_ANCHOR_FONT_HEIGHT_TOLERANCE = 1.0;

    // In world units where a distance: the font-size clamp brackets a readable line against the
    // map's scale (the border inset channel is 150), three lines is the HOI4-style ceiling, and
    // 1.15 leads the lines with a little air.
    private static final double DEFAULT_NAME_MIN_FONT_SIZE = 200.0;
    private static final double DEFAULT_NAME_MAX_FONT_SIZE = 4000.0;
    private static final int DEFAULT_NAME_MAX_LINES = 3;
    private static final double DEFAULT_NAME_LINE_SPACING = 1.15;
    private static final double DEFAULT_ANCHOR_BAND_OPACITY = 0.35;
    private static final double DEFAULT_ANCHOR_BAND_LINE_OPACITY = 0.9;

    private static final boolean DEFAULT_SHOW_REJECTED_AXES = false;
    private static final boolean DEFAULT_SHOW_UNBIASED_AXES = false;

    // Off is the safe way round for the fallbacks as well as for the shipped defaults: these
    // constants answer while LunaLib has no stored value, and a map that spoiled a sector during
    // that window could not take it back.
    private static final boolean DEFAULT_SHOW_UNSEEN_ABANDONED_STATIONS = false;
    private static final boolean DEFAULT_SHOW_UNSEEN_HIDDEN_MARKETS = false;
    private static final boolean DEFAULT_SHOW_UNDISCOVERED_MARKETS = false;
    private static final boolean DEFAULT_SHOW_HIDDEN_SYSTEMS = false;

    // Not an "off" the way its neighbours are - a survey level always applies - and this one is the
    // level vanilla itself shows a condition from, so the shipped state says exactly what the game
    // does and no more.
    private static final SurveyLevelChoice DEFAULT_DECIVILISED_WORLD_SURVEY_LEVEL =
        SurveyLevelChoice.SEEN;

    private KmuMapLayerSettings() {
    }

    /**
     * @return whether the map answers the cursor at all; on by default. The master over both kinds
     *         of hover feedback on every layer, so with it off no cursor read runs and nothing
     *         hover-driven is drawn
     */
    public static boolean getMapHoveringEnabled() {
        return KmuLunaSettings.readBoolean(HOVERING_ENABLED_FIELD, DEFAULT_HOVERING_ENABLED);
    }

    /**
     * @return whether hover effects - the halo over the hovered cluster and the wash on its cell -
     *         are on across every map layer; on by default. Under the hovering master, and over
     *         each layer's own effects switch
     */
    public static boolean getMapHoverEffectsEnabled() {
        return KmuLunaSettings.readBoolean(
            HOVER_EFFECTS_ENABLED_FIELD,
            DEFAULT_HOVER_EFFECTS_ENABLED);
    }

    /**
     * @return whether the hover tooltip is on across every map layer; on by default. Under the
     *         hovering master, and over each layer's own tooltip switch. Read live each frame, so
     *         toggling it needs no rebuild
     */
    public static boolean getMapHoverTooltipEnabled() {
        return KmuLunaSettings.readBoolean(
            HOVER_TOOLTIP_ENABLED_FIELD,
            DEFAULT_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return whether the layers may only draw while one of the two vanilla map hosts is showing,
     *         so a map built by another mod does not paint them; off by default
     */
    public static boolean getMapLayersOnlyOnTheirHosts() {
        return KmuLunaSettings.readBoolean(
            MAP_LAYERS_ONLY_ON_THEIR_HOSTS_FIELD,
            DEFAULT_MAP_LAYERS_ONLY_ON_THEIR_HOSTS);
    }

    /**
     * @return whether the layers may answer the cursor on every pass there is, foreign map surfaces
     *         on any screen included; off by default, which permits the vanilla hosts alone
     */
    public static boolean getMapLayerMouseoverIsGlobal() {
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
    public static boolean getMapLayerMouseoverIsEnabledInGameSpace() {
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
    public static boolean getRandomAssortmentOfThingsCompatibilityModeEnabled() {
        return KmuLunaSettings.readBoolean(
            RANDOM_ASSORTMENT_OF_THINGS_COMPATIBILITY_MODE_FIELD,
            DEFAULT_RANDOM_ASSORTMENT_OF_THINGS_COMPATIBILITY_MODE);
    }

    /**
     * @return how much smaller each step of indent inside a hover tooltip draws than the step above
     *         it, as a multiple of its own size; 0.75 by default
     */
    public static float getMapTooltipNestingLevelShrink() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_NESTING_LEVEL_SHRINK_FIELD,
            DEFAULT_TOOLTIP_NESTING_LEVEL_SHRINK);
    }

    /**
     * @return the room left under a line of a hover tooltip before the next one, in UI units,
     *         wherever neither depth below applies; 4 by default
     */
    public static float getMapTooltipLineGap() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_LINE_GAP_FIELD,
            DEFAULT_TOOLTIP_LINE_GAP);
    }

    /**
     * @return the room left under a line two steps in from a hover box's own heading, in UI units -
     *         a run long enough to be worth tightening on its own; 0.5 by default
     */
    public static float getMapTooltipTier2LineGap() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_TIER_2_LINE_GAP_FIELD,
            DEFAULT_TOOLTIP_TIER_2_LINE_GAP);
    }

    /**
     * @return the room left under a line three steps in, in UI units - the deepest run a hover box
     *         lists and so the one tightened hardest; 0 by default
     */
    public static float getMapTooltipTier3LineGap() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_TIER_3_LINE_GAP_FIELD,
            DEFAULT_TOOLTIP_TIER_3_LINE_GAP);
    }

    /**
     * @return how thick the line a hover box runs from a label across to its value draws, in UI
     *         units; 1 by default, and 0 to draw no such lines at all
     */
    public static float getMapTooltipLeaderThickness() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_LEADER_THICKNESS_FIELD,
            DEFAULT_TOOLTIP_LEADER_THICKNESS);
    }

    /**
     * @return how strongly that line draws, as a fraction of the box's own opacity; 0.65 by
     *         default, and 0 to draw no such lines at all
     */
    public static float getMapTooltipLeaderOpacity() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_LEADER_OPACITY_FIELD,
            DEFAULT_TOOLTIP_LEADER_OPACITY);
    }

    /**
     * @return how much of a line's colour is taken off the blocks a withheld name draws as, 0..1;
     *         0.4 by default, and 0 to fill them in the line's own colour
     */
    public static float getMapTooltipRedactionDarkeningStrength() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_REDACTION_DARKENING_STRENGTH_FIELD,
            DEFAULT_TOOLTIP_REDACTION_DARKENING_STRENGTH);
    }

    /**
     * @return how loudly the sidebar's own furniture - a header tab, the collapse handle - sounds
     *         as the cursor reaches it, as a multiple of the level the engine holds for the sample;
     *         0.5 by default, which is vanilla's mouseover halved, and 0 to reach it silently
     */
    public static float getMapSidebarPanelChromeArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly a sidebar control with one answer to give - a tick box, a switch - sounds
     *         as the cursor reaches it, on the same scale; 0.5 by default, level with the panel's
     *         own furniture because both are aimed at rather than crossed
     */
    public static float getMapSidebarSingleOptionControlArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly one of many alike - one option of a row of them, one row of a list -
     *         sounds as the cursor reaches it, on the same scale; 0.25 by default, under the two
     *         levels above because a sweep crosses several of these on the way to the one thing
     *         aimed at
     */
    public static float getMapSidebarListedItemArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly the sidebar's list sounds as the wheel moves it, on the same scale; 0.5 by
     *         default. One answer for the whole movement rather than one per row that passed
     */
    public static float getMapSidebarListScrollVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_LIST_SCROLL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_LIST_SCROLL_VOLUME);
    }

    /**
     * @return how loudly the map ticks as the cursor reaches a new system's cell, on the same
     *         scale; 0.5 by default. Silenced by 0 here and, with every other answer the map makes
     *         to the cursor, by the hovering switches above
     */
    public static float getMapCellArrivalVolume() {
        return KmuLunaSettings.readFloat(
            MAP_CELL_ARRIVAL_VOLUME_FIELD,
            DEFAULT_MAP_CELL_ARRIVAL_VOLUME);
    }

    /**
     * @return how far apart two outline points may be and still weld into one corner when chaining
     *         the national border, in world units; 100.0 by default. Raised if borders go missing,
     *         lowered if distinct corners merge
     */
    public static double getMapBorderWeldTolerance() {
        return KmuLunaSettings.readDouble(
            BORDER_WELD_TOLERANCE_FIELD,
            DEFAULT_BORDER_WELD_TOLERANCE);
    }

    /**
     * @return the multiple of the border inset past which a sharp corner's miter is bevelled
     *         instead of pointed; 4.0 by default. Lower bevels sooner (rounder corners, no inward
     *         spikes), higher keeps crisper points
     */
    public static double getMapBorderMiterLimit() {
        return KmuLunaSettings.readDouble(BORDER_MITER_LIMIT_FIELD, DEFAULT_BORDER_MITER_LIMIT);
    }

    /**
     * @return how many directions the label-anchor search fans over the half-circle (0..180
     *         degrees, a label line being undirected); 8 by default. Pure horizontal, the cluster's
     *         own principal axis, and the preferred slant are always searched on top of the fan
     */
    public static int getMapAnchorDirectionCount() {
        return KmuLunaSettings.readInt(
            ANCHOR_DIRECTION_COUNT_FIELD,
            DEFAULT_ANCHOR_DIRECTION_COUNT);
    }

    /**
     * @return how many parallel lines the search sweeps across the cluster per direction, spaced
     *         evenly over the cluster's extent perpendicular to that direction; 10 by default. This
     *         is what lets the accepted line slide off the centroid into a roomier part of the
     *         cluster, and 1 degenerates to a single centred line per direction
     */
    public static int getMapAnchorOffsetCount() {
        return KmuLunaSettings.readInt(
            ANCHOR_OFFSET_COUNT_FIELD,
            DEFAULT_ANCHOR_OFFSET_COUNT);
    }

    /**
     * @return how close the anchor search must land to the largest font height each candidate line
     *         holds, in world units; 1.0 by default. The fit grows the font by halving the size
     *         clamp and re-measures the label's band at every halving, so a coarser tolerance buys
     *         those measurements back one for one. Returned as stored, so a reader that cannot take
     *         a nonsensical value holds it to a floor of its own
     */
    public static double getMapAnchorFontHeightTolerance() {
        return KmuLunaSettings.readDouble(
            ANCHOR_FONT_HEIGHT_TOLERANCE_FIELD,
            DEFAULT_ANCHOR_FONT_HEIGHT_TOLERANCE);
    }

    /**
     * @return how much length a shallower (more horizontal) candidate line may give up and still
     *         win the search, 0..1; 0.3 by default. A candidate's clear length is scaled by
     *         {@code 1 - strength * sin(angle)^exponent}, so 0 picks the pure longest line
     *         regardless of slope and 1 scores a vertical line zero
     */
    public static double getMapAnchorVerticalPenaltyStrength() {
        return KmuLunaSettings.readDouble(
            ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH);
    }

    /**
     * @return how sharply the vertical penalty concentrates toward vertical - the exponent on
     *         {@code sin(angle)} in the score, at least 1; 2.0 by default. A higher exponent leaves
     *         already-shallow lines almost unpenalised and bites only as a line approaches vertical
     */
    public static double getMapAnchorVerticalPenaltyExponent() {
        return KmuLunaSettings.readDouble(
            ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT);
    }

    /**
     * @return how far a label may lean off level to follow its cluster's long axis, in degrees; 45
     *         by default. The search prefers this cluster-specific slant over screen-horizontal,
     *         capped here so a tall cluster never stands its name vertical and faded toward level
     *         as a cluster gets rounder; 0 forces dead-horizontal labels
     */
    public static double getMapAnchorMaxSlantDegrees() {
        return KmuLunaSettings.readDouble(
            ANCHOR_MAX_SLANT_DEGREES_FIELD,
            DEFAULT_ANCHOR_MAX_SLANT_DEGREES);
    }

    /**
     * @return how far short of the national border each end of a label anchor stops, in multiples
     *         of the border inset channel; 4.0 by default. A clear line shorter than twice this
     *         collapses to the dot
     */
    public static double getMapAnchorEndInsetMultiple() {
        return KmuLunaSettings.readDouble(
            ANCHOR_END_INSET_MULTIPLE_FIELD,
            DEFAULT_ANCHOR_END_INSET_MULTIPLE);
    }

    /**
     * @return the keep-out radius around each system icon that a label anchor must not cross, in
     *         world units; 100.0 by default. A tuned approximation of the icon's on-map footprint,
     *         icons drawing at a fixed pixel size while the anchor is fitted once in world space
     */
    public static double getMapAnchorIconClearance() {
        return KmuLunaSettings.readDouble(
            ANCHOR_ICON_CLEARANCE_FIELD,
            DEFAULT_ANCHOR_ICON_CLEARANCE);
    }

    /**
     * @return the smallest per-line font height a cluster's name may render at, in world units;
     *         200.0 by default. A placement that cannot hold even one line this tall anywhere
     *         collapses to the dot and shows no name
     */
    public static double getMapNameMinFontSize() {
        return KmuLunaSettings.readDouble(NAME_MIN_FONT_SIZE_FIELD, DEFAULT_NAME_MIN_FONT_SIZE);
    }

    /**
     * @return the largest per-line font height the name fit will grow to, in world units; 4000.0 by
     *         default, so a roomy cluster does not mint an oversized label
     */
    public static double getMapNameMaxFontSize() {
        return KmuLunaSettings.readDouble(NAME_MAX_FONT_SIZE_FIELD, DEFAULT_NAME_MAX_FONT_SIZE);
    }

    /**
     * @return the most lines the fit may wrap a name into; 3 by default. A length-poor but
     *         girth-rich cluster wraps the name to shorten its widest line, chosen only when that
     *         renders a strictly larger font than fewer lines would; 1 forces single-line names
     */
    public static int getMapNameMaxLines() {
        return KmuLunaSettings.readInt(NAME_MAX_LINES_FIELD, DEFAULT_NAME_MAX_LINES);
    }

    /**
     * @return the line-height multiple between a multi-line name's stacked lines; 1.15 by default,
     *         and at least 1 (lines flush)
     */
    public static double getMapNameLineSpacing() {
        return KmuLunaSettings.readDouble(NAME_LINE_SPACING_FIELD, DEFAULT_NAME_LINE_SPACING);
    }

    /**
     * @return the fill opacity of the debug band quad, 0..1; 0.35 by default - low enough that the
     *         national border reads through the band, an overflow being the whole point of drawing
     *         it
     */
    public static double getMapAnchorBandOpacity() {
        return KmuLunaSettings.readDouble(ANCHOR_BAND_OPACITY_FIELD, DEFAULT_ANCHOR_BAND_OPACITY);
    }

    /**
     * @return the opacity of the debug band box's strokes - its outline, the line-count divider
     *         rules, the centreline, and the anchor dot - 0..1; 0.9 by default, kept apart from the
     *         fill alpha so the outline stays legible over a faint band wash
     */
    public static double getMapAnchorBandLineOpacity() {
        return KmuLunaSettings.readDouble(
            ANCHOR_BAND_LINE_OPACITY_FIELD,
            DEFAULT_ANCHOR_BAND_LINE_OPACITY);
    }

    /**
     * @return whether a cluster whose accepted label line collapsed to the dot also draws, in red,
     *         the best candidate its fit found before the border, icon, or end-margin trim
     *         discarded it; off by default, meaningful only while the cluster anchors themselves
     *         draw
     */
    public static boolean getMapShowRejectedAxes() {
        return KmuLunaSettings.readBoolean(SHOW_REJECTED_AXES_FIELD, DEFAULT_SHOW_REJECTED_AXES);
    }

    /**
     * @return whether each cluster also draws, in yellow, the label line its fit would accept with
     *         no horizontal bias applied; off by default, meaningful only while the cluster anchors
     *         themselves draw. Drawn only where the unbiased direction actually differs from the
     *         accepted line's
     */
    public static boolean getMapShowUnbiasedAxes() {
        return KmuLunaSettings.readBoolean(SHOW_UNBIASED_AXES_FIELD, DEFAULT_SHOW_UNBIASED_AXES);
    }

    /**
     * @return whether an unowned market carrying vanilla's abandoned-station condition is drawn,
     *         counted and named as soon as its entity is found, rather than waiting until the
     *         player has been in its star system or somebody lives there who would have seen it;
     *         off by default. A station a faction keeps wears the same condition and is not one of
     *         these
     */
    public static boolean shouldShowUnseenAbandonedStations() {
        return KmuLunaSettings.readBoolean(
            SHOW_UNSEEN_ABANDONED_STATIONS_FIELD,
            DEFAULT_SHOW_UNSEEN_ABANDONED_STATIONS);
    }

    /**
     * @return whether a market marked hidden is drawn, counted and named wherever it currently
     *         stands as soon as its entity is found, rather than waiting until somebody has seen it
     *         standing there; off by default
     */
    public static boolean shouldShowUnseenHiddenMarkets() {
        return KmuLunaSettings.readBoolean(
            SHOW_UNSEEN_HIDDEN_MARKETS_FIELD,
            DEFAULT_SHOW_UNSEEN_HIDDEN_MARKETS);
    }

    /**
     * @return whether a colony the player has not discovered yet still counts - bypassing the
     *         known-to-player gate, so such a colony marks its system as inhabited and, on a layer
     *         that weighs colonies, folds into what that layer weighs; off by default
     */
    public static boolean shouldShowUndiscoveredMarkets() {
        return KmuLunaSettings.readBoolean(
            SHOW_UNDISCOVERED_MARKETS_FIELD,
            DEFAULT_SHOW_UNDISCOVERED_MARKETS);
    }

    /**
     * @return how far a decivilised world must have been surveyed before the map will say its
     *         colony has collapsed; SEEN by default, which is what vanilla itself asks before
     *         showing a condition. A separate axis from discovery: a planet flown past is
     *         discovered whatever its survey level says
     */
    public static SurveyLevel getDecivilisedWorldSurveyLevel() {
        return KmuLunaSettings
            .readChoice(
                SHOW_DECIVILISED_WORLDS_AT_SURVEY_LEVEL_FIELD,
                DEFAULT_DECIVILISED_WORLD_SURVEY_LEVEL)
            .resolveSurveyLevel();
    }

    /**
     * @return whether a star system the map would otherwise omit still seeds a cell - bypassing the
     *         map's own admission rule, so a system that is unreachable, unseen or uninhabited gets
     *         geometry like any other; off by default
     */
    public static boolean shouldShowHiddenSystems() {
        return KmuLunaSettings.readBoolean(
            SHOW_HIDDEN_SYSTEMS_FIELD,
            DEFAULT_SHOW_HIDDEN_SYSTEMS);
    }

    /**
     * @return how far down from the top edge of the screen the overlay sidebar box sits, in pixels;
     *         36 by default, clearing the sector map's own tab strip
     */
    public static int getMapSidebarPaddingTop() {
        return KmuLunaSettings.readInt(SIDEBAR_PADDING_TOP_FIELD, DEFAULT_SIDEBAR_PADDING_TOP);
    }

    /**
     * @return how far in from the left edge of the screen the overlay sidebar box sits, in pixels;
     *         9 by default
     */
    public static int getMapSidebarPaddingLeft() {
        return KmuLunaSettings.readInt(SIDEBAR_PADDING_LEFT_FIELD, DEFAULT_SIDEBAR_PADDING_LEFT);
    }

    /**
     * @return how far above the bottom edge of the screen the overlay sidebar box must stay, in
     *         pixels; 79 by default. The panel body caps its height to this margin and the bloc
     *         list scrolls within the room left
     */
    public static int getMapSidebarPaddingBottom() {
        return KmuLunaSettings.readInt(
            SIDEBAR_PADDING_BOTTOM_FIELD,
            DEFAULT_SIDEBAR_PADDING_BOTTOM);
    }

    /**
     * @return how far down from the top edge of the intel screen's map preview (the "visor") the
     *         overlay sidebar box sits, in pixels; 21 by default, clearing the vanilla Starscape
     *         and fuel-range toggles at the top of that map. The box sits flush against the visor's
     *         left edge and its height caps to the visor's bottom, so only this top offset is
     *         exposed
     */
    public static int getMapIntelSidebarPaddingTop() {
        return KmuLunaSettings.readInt(
            INTEL_SIDEBAR_PADDING_TOP_FIELD,
            DEFAULT_INTEL_SIDEBAR_PADDING_TOP);
    }

    /**
     * @return the line width of the outer border framing the overlay sidebar box, in pixels; 1 by
     *         default, 0 drawing no border
     */
    public static int getMapSidebarBorderWidth() {
        return KmuLunaSettings.readInt(SIDEBAR_BORDER_WIDTH_FIELD, DEFAULT_SIDEBAR_BORDER_WIDTH);
    }

    /**
     * @return the overlay sidebar box's background opacity as a 0..1 fraction, the CSV storing it
     *         as a 0..100 percentage; 0.8 by default
     */
    public static float getMapSidebarBackgroundOpacity() {

        var percent = KmuLunaSettings.readInt(
            SIDEBAR_OPACITY_FIELD,
            DEFAULT_SIDEBAR_OPACITY_PERCENT);

        var clamped = Math.max(
            MIN_SIDEBAR_OPACITY_PERCENT,
            Math.min(MAX_SIDEBAR_OPACITY_PERCENT, percent));

        return clamped / (float) MAX_SIDEBAR_OPACITY_PERCENT;
    }

    /**
     * @return how long the overlay sidebar's collapse handle takes to fold the body to its docked
     *         rail (and to unfold it), in seconds; 0.25 by default, 0 snapping it instantly. Fed to
     *         the collapse holder's per-frame advance so the player sets the animation pace
     */
    public static float getMapSidebarCollapseSeconds() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_COLLAPSE_SECONDS_FIELD,
            DEFAULT_SIDEBAR_COLLAPSE_SECONDS);
    }

    /**
     * @return how long the map layers take to dissolve off a screen when they are hidden (and to
     *         come back when they are shown), in seconds; 0.25 by default, 0 cutting between the two
     *         with no fade. Multiplied into what the layers paint, so the whole overlay, its labels
     *         and its sidebar thin together rather than one snapping out from under another
     */
    public static float getMapLayerHideFadeSeconds() {
        return KmuLunaSettings.readFloat(
            MAP_LAYER_HIDE_FADE_SECONDS_FIELD,
            DEFAULT_MAP_LAYER_HIDE_FADE_SECONDS);
    }

    /**
     * @return whether the tick box that shows and hides the map layers is added to the vanilla map
     *         filter row on either map screen; on by default. Off makes no attempt on either screen,
     *         so both rows are left exactly as the game builds them, and a screen with no control of
     *         its own never hides its layers whatever the save holds
     */
    public static boolean getMapFilterRowToggleEnabled() {
        return KmuLunaSettings.readBoolean(
            FILTER_ROW_TOGGLE_ENABLED_FIELD,
            DEFAULT_FILTER_ROW_TOGGLE_ENABLED);
    }

    /**
     * @return which palette the overlay sidebar's frame, control accents, and tab-row chrome are
     *         coloured from: the fixed UI palette (the default), the neutral chrome greys, or the
     *         player faction's own accents. One answer for all three reads, since a panel framed in
     *         one palette and ruled in another reads worse than either
     */
    public static SidebarColourSchemeChoice getMapSidebarColourScheme() {
        return KmuLunaSettings.readChoice(
            SIDEBAR_COLOUR_SCHEME_FIELD,
            DEFAULT_SIDEBAR_COLOUR_SCHEME);
    }

    /**
     * @return which colour the overlay sidebar's collapse-handle chevron draws in: the vanilla
     *         highlight gold (the default), or the panel's own accents under the chosen colour
     *         scheme, brightening under the pointer
     */
    public static NotchChevronColourChoice getMapSidebarChevronColour() {
        return KmuLunaSettings.readChoice(
            SIDEBAR_CHEVRON_COLOR_FIELD,
            DEFAULT_SIDEBAR_CHEVRON_COLOUR);
    }

    /**
     * Resolves a layer tab's shortcut keycode from its LunaLib Keycode field, so the player can
     * rebind which key jumps to that layer.
     *
     * <p>The field id is the caller's rather than a constant here: each layer owns the id its own
     * shortcut is stored under, so the framework can read a shortcut for a layer it does not know
     * about.
     *
     * <p>A keycode of 0 (LWJGL's {@code KEY_NONE}) means the player cleared the binding with
     * Escape, so the layer has no shortcut; callers treat that as unbound rather than a real key.
     *
     * @param settingKey     the LunaLib field id holding the rebound keycode
     * @param defaultKeycode the LWJGL keycode used when the field is unset or unreadable
     * @return the LWJGL keycode the layer's tab jumps to, or 0 when the shortcut is unbound
     */
    public static int getMapLayerShortcut(String settingKey, int defaultKeycode) {
        return KmuLunaSettings.readInt(settingKey, defaultKeycode);
    }

    /**
     * @return how much of the intel sidebar's hard-edged label strokes survives, 0 leaving them
     *         interpolated as the font loader hands them over and 1 drawing them unfiltered; 0.8 by
     *         default, settled against the vanilla raised buttons that row stands beside
     */
    public static double getIntelSidebarPixelFontSharpness() {
        return KmuLunaSettings.readDouble(
            INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD,
            DEFAULT_INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS);
    }

    /**
     * @return the same dial for the sector-map sidebar's tab labels, read against the vanilla
     *         Sector/System tabs directly above them; 0 by default. The two rows answer separately,
     *         so dialling one never moves the other
     */
    public static double getMapSidebarPixelFontSharpness() {
        return KmuLunaSettings.readDouble(
            MAP_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD,
            DEFAULT_MAP_SIDEBAR_PIXEL_FONT_SHARPNESS);
    }
}
