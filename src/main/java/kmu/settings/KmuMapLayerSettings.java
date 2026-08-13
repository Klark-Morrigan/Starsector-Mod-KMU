package kmu.settings;

/**
 * The map-layer framework's own LunaLib knobs: the chrome and the geometry every map
 * layer shares, whichever layer is drawing.
 *
 * <p>One of the knob classes {@link KmuLunaSettings} splits by reader. Everything here is
 * read by {@code kmu.maplayers.base} and nothing else, so a knob one layer owns cannot
 * arrive through this class - which is what stops the framework depending on a feature for
 * its own appearance.
 *
 * <p>Many field ids read as the political map's - {@code kmu_politicalMapSidebar*},
 * {@code kmu_politicalMapAnchor*}, {@code kmu_politicalMapBorderWeldTolerance} - while the
 * getters over them are named for the framework that reads them. The ids predate the
 * framework and cannot follow it, being frozen for the reason {@link KmuLunaSettings}
 * gives. A getter is a Java name and costs nothing to change, so the mismatch is parked
 * where it does no harm - the Java side says which half of the map code owns a knob, the
 * stored key stays put.
 *
 * <p>The knobs lay out across four tabs. {@code Map - Visuals} carries the overlay
 * sidebar, the map labels, the two upper hover tiers and how tightly a hover box is set -
 * what every layer shares. {@code Map - Sound} carries how loudly each moment the panel and
 * the map answer audibly plays, which is a component of the look for the same reason a fill
 * colour is but browsed by a player who has come looking for the volume rather than for the
 * shades.
 * {@code Map - Keybinds} carries the layer shortcuts, which are controls rather than
 * appearance. {@code Map - Dev} carries the tuning surfaces a player does not browse:
 * the national border's tracing tolerances, the label-anchor search's modifiers, and the
 * band and axis diagnostics that draw the search's own workings on the map.
 */
public final class KmuMapLayerSettings {

    // Overlay sidebar fields (Map - Visuals tab): the small on-map box carrying
    // the overlay's tabs and controls. Padding places the box from the screen's top-left
    // corner; border width frames it (0 = no border); opacity is its background
    // translucency.
    private static final String SIDEBAR_PADDING_TOP_FIELD =
        "kmu_politicalMapSidebarPaddingTop";
    private static final String SIDEBAR_PADDING_LEFT_FIELD =
        "kmu_politicalMapSidebarPaddingLeft";
    private static final String SIDEBAR_PADDING_BOTTOM_FIELD =
        "kmu_politicalMapSidebarPaddingBottom";
    private static final String SIDEBAR_BORDER_WIDTH_FIELD =
        "kmu_politicalMapSidebarBorderWidth";
    private static final String SIDEBAR_OPACITY_FIELD =
        "kmu_politicalMapSidebarOpacity";

    // How long the sidebar's collapse handle takes to fold the body to its docked rail (and
    // unfold it), in seconds; 0 snaps it instantly. Player-facing animation pace, so it sits
    // in the Overlay sidebar section of the Map - Visuals tab beside the box's other
    // appearance knobs.
    private static final String SIDEBAR_COLLAPSE_SECONDS_FIELD =
        "kmu_politicalMapSidebarCollapseSeconds";

    // Overlay sidebar colour fields (Map - Visuals tab), their own section below the box's
    // dimensions: which palette the panel is coloured from, and which colour the collapse
    // handle's chevron draws in. The two sit together because the chevron's "Panel accent"
    // option resolves through whichever scheme is chosen, so a player changing one is very
    // likely looking for the other.
    private static final String SIDEBAR_COLOUR_SCHEME_FIELD =
        "kmu_politicalMapSidebarColourScheme";
    private static final String SIDEBAR_CHEVRON_COLOR_FIELD =
        "kmu_politicalMapSidebarChevronColor";

    // Intel-screen overlay field (Map - Visuals tab): the same sidebar box drawn on the intel
    // screen sits flush against the left edge of that screen's map preview (the "visor") and
    // hangs from the visor top; this top padding pushes it down to clear the vanilla Starscape
    // / fuel-range toggles at the top of the intel map. The visor's height caps the box, so no
    // left or bottom knob is needed.
    private static final String INTEL_SIDEBAR_PADDING_TOP_FIELD =
        "kmu_politicalMapIntelSidebarPaddingTop";

    // Diagnostics (Map - Dev tab): how hard each sidebar row's hard-edged labels read. Both rows are
    // lettered in atlases carrying no antialiasing of their own, which the font loader hands over
    // interpolated - so each is drawn between the two samplings, and where between is a look rather than a
    // number. One knob per row, because each is read against the vanilla chrome it stands beside: the
    // intel row against that screen's raised buttons, the map row against the Sector/System tabs a
    // tab-height above it, and the two settled at opposite ends of the range.
    private static final String INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD =
        "kmu_dev_map_ui_fontSharpness_tabHeaders_sidebar_intelScreen";
    private static final String MAP_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD =
        "kmu_dev_map_ui_fontSharpness_tabHeaders_sidebar_mMap";

    // Hover tiers, the top two (Map - Visuals tab): whether the map answers the cursor at all,
    // and then whether each kind of answer does - both across every map layer. The master gates
    // both kinds, so with it off no layer reads the cursor and nothing hover-driven is drawn or
    // paid for. Under it sits one switch per kind of feedback: effects covers the halo and cell
    // wash a layer paints, the tooltip covers the info box naming what is under the cursor. Every
    // layer carries its own pair below these, so switching one layer's feedback off does not take
    // the other layers' with it. The lower two ids read as the political map's because they
    // predate the framework - the mismatch this class's note above explains.
    private static final String HOVERING_ENABLED_FIELD =
        "kmu_mapVisualsHoveringEnabled";
    private static final String HOVER_EFFECTS_ENABLED_FIELD =
        "kmu_politicalMapHoverEnabled";
    private static final String HOVER_TOOLTIP_ENABLED_FIELD =
        "kmu_politicalMapHoverTooltipEnabled";

    // Hover tooltip density (Map - Visuals tab): how tightly a hover box is set, across every map
    // layer. The shrink is how much smaller each step of indent draws than the step above it; the
    // three gaps are the room left under a line before the next one - the box's baseline, and the
    // two depths a listing runs long at, which are tightened on their own. Stated by depth rather
    // than by what a layer lists there, since the box is shared: on the political map they land on
    // a colony's terms and on the tiers one term breaks into, and another layer's box of the same
    // shape is set by the same knobs. Player-facing appearance rather than tuning, so they sit with
    // the hover switches above rather than among the Map - Dev diagnostics: a player who finds the
    // box too tall reaches for these next.
    private static final String TOOLTIP_NESTING_LEVEL_SHRINK_FIELD =
        "kmu_map_visuals_tooltips_density_nestingLevelShrink";
    private static final String TOOLTIP_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_lineGap";
    private static final String TOOLTIP_TIER_2_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_tier2LineGap";
    private static final String TOOLTIP_TIER_3_LINE_GAP_FIELD =
        "kmu_map_visuals_tooltips_density_tier3LineGap";

    // The line a hover box runs from a label across to its value (Map - Visuals tab). Its weight is the
    // one thing about the box that cannot be settled in code: how heavy a solid run looks beside a line
    // of glyphs turns on the face, the size it draws at, and how that atlas was rasterised, so where it
    // sits against the text is a judgement made on screen. Appearance rather than tuning, so it sits with
    // the density knobs above.
    private static final String TOOLTIP_LEADER_THICKNESS_FIELD =
        "kmu_map_visuals_tooltips_leader_thickness";
    private static final String TOOLTIP_LEADER_OPACITY_FIELD =
        "kmu_map_visuals_tooltips_leader_opacity";

    // How loudly the sidebar answers the cursor reaching something (Map - Sound tab), one level per kind
    // of thing there is to reach. Each scales the level the engine already holds for the sample, so 1 is
    // vanilla's own mouseover and anything below it is the panel answering more quietly than the chrome
    // around it. Three levels rather than one because what vanilla balanced its mouseover for is a screen
    // carrying a handful of hit targets, and the sidebar packs a column of them: a listed item is crossed
    // several at a time on the way to the one thing the player aimed at, so it is pitched under the two
    // that are aimed at. The kinds are named for what the player is reaching rather than for the widget
    // classes, which is what keeps the set closed as widgets are added.
    private static final String SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_panelChrome";
    private static final String SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_singleOptionControl";
    private static final String SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_arrival_listedItem";

    // How loudly the sidebar's list sounds as the wheel moves it (Map - Sound tab). Its own knob and not
    // one of the arrival levels above, because scrolling is not an arrival: the rows travel under a parked
    // cursor, so the movement is answered once for the whole turn of the wheel rather than once per row
    // that passed. It is also what makes the listed-item level liveable, a fast wheel otherwise ticking
    // down the whole list.
    private static final String SIDEBAR_LIST_SCROLL_VOLUME_FIELD =
        "kmu_map_sound_sidebar_listScroll";

    // How loudly the map ticks as the cursor reaches a new system's cell (Map - Sound tab). Beside the
    // sidebar's levels rather than among them: a cell is not a control and the tick is its own sample, so
    // what it shares with them is the moment being answered at all and not the balance they are tuned to.
    private static final String MAP_CELL_ARRIVAL_VOLUME_FIELD =
        "kmu_map_sound_map_cellArrival";

    // Border tracing (Map - Dev tab): the raw ring chaining and miter inset that turn a cluster's
    // cell edges into one outline. Always applied - it is upstream of the smoothing passes a
    // layer may gate - so it has no switch of its own.
    private static final String BORDER_WELD_TOLERANCE_FIELD =
        "kmu_politicalMapBorderWeldTolerance";
    private static final String BORDER_MITER_LIMIT_FIELD =
        "kmu_politicalMapBorderMiterLimit";

    // Label anchors (Map - Dev tab): the modifiers of the per-cluster label-anchor search -
    // the straight line a cluster's name will sit on, chosen by scoring many candidate
    // lines swept across the cluster (a fan of directions times a family of parallel
    // offsets), each fit inside the national border and clear of the system icons. Live
    // knobs rather than constants so the search can be tuned on the open map; all feed
    // the drawables rebuild, which re-runs the search for every anchor. The direction and
    // offset counts size the candidate grid; the vertical-penalty strength and exponent
    // shape how much a line straying from the cluster's own lean may sacrifice in length
    // and still win, so slant preference is decided on measured lengths rather than by
    // bending any direction before the fit; the max-slant degrees cap that lean short of
    // vertical (and it fades to level for round clusters whose axis is meaningless); the
    // font-height tolerance is how finely each candidate's font is resolved, the third
    // cost knob beside the two counts and the one that says how much of the search is
    // spent per candidate rather than how many there are.
    private static final String ANCHOR_DIRECTION_COUNT_FIELD =
        "kmu_politicalMapAnchorDirectionCount";
    private static final String ANCHOR_OFFSET_COUNT_FIELD =
        "kmu_politicalMapAnchorOffsetCount";
    private static final String ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD =
        "kmu_politicalMapAnchorVerticalPenaltyStrength";
    private static final String ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD =
        "kmu_politicalMapAnchorVerticalPenaltyExponent";
    private static final String ANCHOR_MAX_SLANT_DEGREES_FIELD =
        "kmu_politicalMapAnchorMaxSlantDegrees";
    private static final String ANCHOR_END_INSET_MULTIPLE_FIELD =
        "kmu_politicalMapAnchorEndInsetMultiple";
    private static final String ANCHOR_ICON_CLEARANCE_FIELD =
        "kmu_politicalMapAnchorIconClearance";
    private static final String ANCHOR_FONT_HEIGHT_TOLERANCE_FIELD =
        "kmu_politicalMapAnchorFontHeightTolerance";

    // Debug band-quad knobs (Map - Dev tab, Label anchors): the opacity is the band quad's
    // fill alpha, and the line opacity the separate alpha of that box's strokes so the
    // outline can read stronger than the fill it sits on.
    private static final String ANCHOR_BAND_OPACITY_FIELD =
        "kmu_politicalMapAnchorBandOpacity";
    private static final String ANCHOR_BAND_LINE_OPACITY_FIELD =
        "kmu_politicalMapAnchorBandLineOpacity";

    // Name-fit knobs (Map - Visuals tab, Map labels): a label is a box with
    // girth, sized to the space it sits in and to the owner's actual name - player-facing
    // appearance, so they live beside the name toggle and format, not among the Map - Dev
    // diagnostics. Min/max font size clamp the per-line height the fit searches (the
    // readability floor and the oversize ceiling); max lines and line spacing let a
    // length-poor cluster wrap the name into a taller-font block instead of shrinking it.
    private static final String NAME_MIN_FONT_SIZE_FIELD =
        "kmu_politicalMapNameMinFontSize";
    private static final String NAME_MAX_FONT_SIZE_FIELD =
        "kmu_politicalMapNameMaxFontSize";
    private static final String NAME_MAX_LINES_FIELD =
        "kmu_politicalMapNameMaxLines";
    private static final String NAME_LINE_SPACING_FIELD =
        "kmu_politicalMapNameLineSpacing";

    // Diagnostics (Map - Dev tab): the two extra anchor lines layered under the accepted one,
    // each behind its own toggle so the anchor overlay stays readable by default. The
    // rejected line (red) shows the best candidate a collapsed fit had before the
    // border, icon, or end-margin trim discarded it; the unbiased line (yellow) shows
    // what the fit would accept with no horizontal bias, so the bias knob's effect is
    // visible directly. Both meaningful only while the anchors themselves draw, which is
    // the drawing layer's own toggle.
    private static final String SHOW_REJECTED_AXES_FIELD =
        "kmu_politicalMapShowRejectedAxes";
    private static final String SHOW_UNBIASED_AXES_FIELD =
        "kmu_politicalMapShowUnbiasedAxes";

    // Fallbacks used only when a setting is read before LunaLib has loaded it;
    // the live values come from LunaLib. These mirror the defaultValue column in
    // data/config/LunaSettings.csv and must be kept in step with it.
    // Placement from the screen's top-left corner, pixels. The top padding clears the
    // sector map's own tab strip; the left padding gives a small margin.
    private static final int DEFAULT_SIDEBAR_PADDING_TOP = 46;
    private static final int DEFAULT_SIDEBAR_PADDING_LEFT = 12;

    // Kept clear at the screen bottom, pixels: the panel body caps its height so the box
    // never runs past this margin, and the bloc list scrolls within what is left. A small
    // margin like the left padding. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_SIDEBAR_PADDING_BOTTOM = 12;

    // Intel overlay top padding from the visor's top edge, pixels: clears the vanilla
    // Starscape / fuel-range toggles at the top of the intel map by default, leaving the same
    // three-pixel channel those toggles keep between themselves - the row below them reads as
    // another row of that set rather than as a box parked under them. Measured against the real
    // row rather than added up from it: the vanilla toggles' own reach past their band is theirs
    // to know, so the offset that lands our row one channel below theirs is a read, not a sum.
    // Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_INTEL_SIDEBAR_PADDING_TOP = 21;

    // Settled in game, and at opposite ends: what matches vanilla's buttons on the intel screen is nearly
    // all of the hard edge, where the map row read against the Sector/System tabs directly above it wants
    // none of it - anything above nothing came out brighter than those tabs. That the same trade lands so
    // far apart is the argument for the two knobs: it is the chrome each row abuts that decides, not the
    // face, and both rows are lettered in hard-edged atlases either way.
    private static final double DEFAULT_INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS = 0.8;
    private static final double DEFAULT_MAP_SIDEBAR_PIXEL_FONT_SHARPNESS = 0.0;

    // A one-pixel outer border by default; 0 hides it. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_SIDEBAR_BORDER_WIDTH = 1;

    // 80% opaque by default: readable over the map without fully masking what is behind it.
    // Stored 0..100 in the CSV, exposed 0..1. Mirrors the CSV row's defaultValue.
    private static final int DEFAULT_SIDEBAR_OPACITY_PERCENT = 80;
    private static final int MIN_SIDEBAR_OPACITY_PERCENT = 0;
    private static final int MAX_SIDEBAR_OPACITY_PERCENT = 100;

    // A quarter-second collapse by default. Kept a literal mirroring the CSV defaultValue and
    // TabPanelCollapse.DEFAULT_DURATION_SECONDS - the KMLib holder's own default pace - like the
    // other fallbacks, so this class stays decoupled from the widget library.
    private static final float DEFAULT_SIDEBAR_COLLAPSE_SECONDS = 0.25f;

    // The fixed UI palette by default: the sidebar is drawn among vanilla chrome, all of which
    // answers to that palette whatever faction the player flies, so a panel following the faction
    // is the one thing on the screen that moves when the others do not. On a stock install the two
    // schemes resolve to the same shades, so this default only shows itself on a recoloured player
    // faction - which is the case it exists for. Mirrors the CSV row's defaultValue.
    private static final SidebarColourSchemeChoice DEFAULT_SIDEBAR_COLOUR_SCHEME =
        SidebarColourSchemeChoice.UI_PALETTE;

    // The highlight gold by default: the collapse handle hangs off the frame over the map, so its
    // chevron reads clearest pitched against the panel's accents rather than painted in them.
    // Mirrors the CSV row's defaultValue.
    private static final NotchChevronColourChoice DEFAULT_SIDEBAR_CHEVRON_COLOUR =
        NotchChevronColourChoice.GOLD;

    // Both upper hover tiers on by default. A tiered gate that shipped with any level off would
    // read to a player as a feature that is broken rather than switched off, and defaulting them
    // on is what keeps the tiering invisible to an existing player: the two ids they may already
    // have switched off still switch the same feedback off.
    private static final boolean DEFAULT_HOVERING_ENABLED = true;
    private static final boolean DEFAULT_HOVER_EFFECTS_ENABLED = true;
    private static final boolean DEFAULT_HOVER_TOOLTIP_ENABLED = true;

    // No shrink by default: every line of a hover box draws at the body's own size, which is the
    // size its atlas is crisp at, and how deep a line sits is said by its indent alone. A box is
    // read at a glance while the cursor rests on a system, so the default trades height for the
    // most legible reading of it and the slider buys the height back.
    //
    // The gaps then tighten only the depths a listing runs long at: lines two steps in stand
    // closer to each other than the things they belong to do, and lines three steps in closer
    // still, so each run reads as one thing without the box's own spacing changing. Mirror the CSV
    // defaultValue column like every fallback here.
    private static final float DEFAULT_TOOLTIP_NESTING_LEVEL_SHRINK = 0f;
    private static final float DEFAULT_TOOLTIP_LINE_GAP = 4f;
    private static final float DEFAULT_TOOLTIP_TIER_2_LINE_GAP = 3f;
    private static final float DEFAULT_TOOLTIP_TIER_3_LINE_GAP = 1f;

    // The connecting line's shipped weights: a whole pixel of thickness, let down to roughly two thirds
    // of the box's own opacity so it reads level with the greyed-out text either side of it rather than
    // above it. Mirror the CSV defaultValue column like every fallback here.
    private static final float DEFAULT_TOOLTIP_LEADER_THICKNESS = 1f;
    private static final float DEFAULT_TOOLTIP_LEADER_OPACITY = 0.65f;

    // The shipped balance: vanilla's own mouseover level halved for anything the player aims at, and
    // halved again for the items a sweep crosses several of on its way there. The gap between the two
    // numbers is the whole of what stops a column of listed rows reading as chatter, so they are only
    // meaningful against each other - a player raising one has retuned the balance rather than turned up
    // a part of it. The scroll and the map's cell tick are single moments and sit at the aimed-at level.
    // Mirror the CSV defaultValue column like every fallback here.
    private static final float DEFAULT_SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME = 0.5f;
    private static final float DEFAULT_SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME = 0.5f;
    private static final float DEFAULT_SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME = 0.25f;
    private static final float DEFAULT_SIDEBAR_LIST_SCROLL_VOLUME = 0.5f;
    private static final float DEFAULT_MAP_CELL_ARRIVAL_VOLUME = 0.5f;

    // Border-tracing knobs (ungated).
    private static final double DEFAULT_BORDER_WELD_TOLERANCE = 100.0;
    private static final double DEFAULT_BORDER_MITER_LIMIT = 4.0;

    // Label-anchor search knobs.
    private static final int DEFAULT_ANCHOR_DIRECTION_COUNT = 9;
    private static final int DEFAULT_ANCHOR_OFFSET_COUNT = 15;
    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH = 0.2;
    private static final double DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT = 2.0;
    private static final double DEFAULT_ANCHOR_MAX_SLANT_DEGREES = 22.0;
    private static final double DEFAULT_ANCHOR_END_INSET_MULTIPLE = 4.0;
    private static final double DEFAULT_ANCHOR_ICON_CLEARANCE = 750.0;
    // One world unit, on a font clamped between 200 and 1200 of them: already far below
    // what a map pixel resolves at any zoom, so the halvings this stops the font search
    // short of would have bought a height difference no one can see.
    private static final double DEFAULT_ANCHOR_FONT_HEIGHT_TOLERANCE = 1.0;

    // Name-fit defaults, in world units where a distance. The font-size clamp brackets
    // a readable line against the map's scale (the border inset channel is 150); three
    // lines is the HOI4-style ceiling; 1.15 leads the lines with a little air.
    private static final double DEFAULT_NAME_MIN_FONT_SIZE = 200.0;
    private static final double DEFAULT_NAME_MAX_FONT_SIZE = 1200.0;
    private static final int DEFAULT_NAME_MAX_LINES = 3;
    private static final double DEFAULT_NAME_LINE_SPACING = 1.15;
    private static final double DEFAULT_ANCHOR_BAND_OPACITY = 0.35;
    private static final double DEFAULT_ANCHOR_BAND_LINE_OPACITY = 0.9;

    // Both extra anchor lines off by default: the overlay they layer under stays readable
    // until the reader asks for the candidate the fit discarded.
    private static final boolean DEFAULT_SHOW_REJECTED_AXES = false;
    private static final boolean DEFAULT_SHOW_UNBIASED_AXES = false;

    private KmuMapLayerSettings() {
    }

    /**
     * @return whether the map answers the cursor at all - the master over both kinds of hover
     *         feedback on every layer, so with it off no cursor read runs and nothing hover-driven
     *         is drawn; on by default
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
     * @return whether the hover tooltip - the box naming what the cursor is over - is on across
     *         every map layer; on by default. Under the hovering master, and over each layer's own
     *         tooltip switch. Read live each frame, so toggling it needs no rebuild
     */
    public static boolean getMapHoverTooltipEnabled() {
        return KmuLunaSettings.readBoolean(
            HOVER_TOOLTIP_ENABLED_FIELD,
            DEFAULT_HOVER_TOOLTIP_ENABLED);
    }

    /**
     * @return how much smaller each step of indent inside a hover tooltip draws than the step
     *         above it, in UI units; 0 by default, which draws every line of the box at its own
     *         kind's size and leaves the indent to say how deep a line sits
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
     * @return the room left under a line two steps in from a hover box's own heading, in UI units
     *         - where a box states the terms one listed thing's number was summed from, so it is
     *         a run long enough to be worth tightening on its own; 3 by default
     */
    public static float getMapTooltipTier2LineGap() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_TIER_2_LINE_GAP_FIELD,
            DEFAULT_TOOLTIP_TIER_2_LINE_GAP);
    }

    /**
     * @return the room left under a line three steps in, in UI units - where one of those terms
     *         breaks down further, the deepest run a hover box lists and so the one tightened
     *         hardest; 1 by default
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
     *         default, which sets it level with the greyed-out text either side of it rather than
     *         above it, and 0 to draw no such lines at all
     */
    public static float getMapTooltipLeaderOpacity() {
        return KmuLunaSettings.readFloat(
            TOOLTIP_LEADER_OPACITY_FIELD,
            DEFAULT_TOOLTIP_LEADER_OPACITY);
    }

    /**
     * @return how loudly the sidebar's own furniture - a header tab, the collapse handle - sounds as
     *         the cursor reaches it, as a multiple of the level the engine holds for the sample; 0.5
     *         by default, which is vanilla's mouseover halved, and 0 to reach it silently
     */
    public static float getMapSidebarPanelChromeArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_PANEL_CHROME_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly a sidebar control with one answer to give - a tick box, a switch - sounds as
     *         the cursor reaches it, on the same scale; 0.5 by default, level with the panel's own
     *         furniture because both are aimed at rather than crossed
     */
    public static float getMapSidebarSingleOptionControlArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_SINGLE_OPTION_CONTROL_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly one of many alike - one option of a row of them, one row of a list - sounds
     *         as the cursor reaches it, on the same scale; 0.25 by default, under the two levels above
     *         because a sweep crosses several of these on the way to the one thing aimed at
     */
    public static float getMapSidebarListedItemArrivalVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_LISTED_ITEM_ARRIVAL_VOLUME);
    }

    /**
     * @return how loudly the sidebar's list sounds as the wheel moves it, on the same scale; 0.5 by
     *         default. One answer for the whole movement rather than one per row that passed, which
     *         is what the moment honestly is - the player turned the wheel once
     */
    public static float getMapSidebarListScrollVolume() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_LIST_SCROLL_VOLUME_FIELD,
            DEFAULT_SIDEBAR_LIST_SCROLL_VOLUME);
    }

    /**
     * @return how loudly the map ticks as the cursor reaches a new system's cell, on the same scale;
     *         0.5 by default. Silenced by 0 here and, with every other answer the map makes to the
     *         cursor, by the hovering switches above
     */
    public static float getMapCellArrivalVolume() {
        return KmuLunaSettings.readFloat(
            MAP_CELL_ARRIVAL_VOLUME_FIELD,
            DEFAULT_MAP_CELL_ARRIVAL_VOLUME);
    }

    /**
     * @return how far apart two outline points may be and still weld into one corner
     *         when chaining the national border, in world units; raised if borders
     *         go missing, lowered if distinct corners merge
     */
    public static double getMapBorderWeldTolerance() {
        return KmuLunaSettings.readDouble(
            BORDER_WELD_TOLERANCE_FIELD,
            DEFAULT_BORDER_WELD_TOLERANCE);
    }

    /**
     * @return the multiple of the border inset past which a sharp corner's miter is
     *         bevelled instead of pointed; lower bevels sooner (rounder corners,
     *         no inward spikes), higher keeps crisper points
     */
    public static double getMapBorderMiterLimit() {
        return KmuLunaSettings.readDouble(BORDER_MITER_LIMIT_FIELD, DEFAULT_BORDER_MITER_LIMIT);
    }

    /**
     * @return how many directions the label-anchor search fans over the half-circle
     *         (0..180 degrees, since a label line is undirected); more directions
     *         let the accepted line align more closely with the cluster's open space
     *         at a higher search cost. Pure horizontal, the cluster's own principal
     *         axis, and the preferred slant are always searched on top of the fan
     */
    public static int getMapAnchorDirectionCount() {
        return KmuLunaSettings.readInt(
            ANCHOR_DIRECTION_COUNT_FIELD,
            DEFAULT_ANCHOR_DIRECTION_COUNT);
    }

    /**
     * @return how many parallel lines the search sweeps across the cluster per
     *         direction, spaced evenly over the cluster's extent perpendicular to
     *         that direction; this is what lets the accepted line slide off the
     *         centroid into a roomier part of the cluster. 1 degenerates to a single
     *         centred line per direction
     */
    public static int getMapAnchorOffsetCount() {
        return KmuLunaSettings.readInt(
            ANCHOR_OFFSET_COUNT_FIELD,
            DEFAULT_ANCHOR_OFFSET_COUNT);
    }

    /**
     * @return how close the anchor search must land to the largest font height each
     *         candidate line holds, in world units. The fit grows the font by halving
     *         the size clamp and re-measures the label's band against the border and the
     *         system icons at every halving, so a coarser tolerance buys those
     *         measurements back one for one; too coarse and a label visibly under-fills
     *         the space its cluster had for it. Returned as stored, so a reader that
     *         cannot take a nonsensical value holds it to a floor of its own
     */
    public static double getMapAnchorFontHeightTolerance() {
        return KmuLunaSettings.readDouble(
            ANCHOR_FONT_HEIGHT_TOLERANCE_FIELD,
            DEFAULT_ANCHOR_FONT_HEIGHT_TOLERANCE);
    }

    /**
     * @return how much length a shallower (more horizontal) candidate line may give
     *         up and still win the search, 0..1: a candidate's clear length is scaled
     *         by {@code 1 - strength * sin(angle)^exponent}, so 0 picks the pure
     *         longest line regardless of slope and 1 scores a vertical line zero
     */
    public static double getMapAnchorVerticalPenaltyStrength() {
        return KmuLunaSettings.readDouble(
            ANCHOR_VERTICAL_PENALTY_STRENGTH_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_STRENGTH);
    }

    /**
     * @return how sharply the vertical penalty concentrates toward vertical: the
     *         exponent on {@code sin(angle)} in the score, at least 1. A higher
     *         exponent leaves already-shallow lines almost unpenalised and bites only
     *         as a line approaches vertical, the falloff a flat vertical-component
     *         scale got backwards
     */
    public static double getMapAnchorVerticalPenaltyExponent() {
        return KmuLunaSettings.readDouble(
            ANCHOR_VERTICAL_PENALTY_EXPONENT_FIELD,
            DEFAULT_ANCHOR_VERTICAL_PENALTY_EXPONENT);
    }

    /**
     * @return how far a label may lean off level to follow its cluster's long axis, in
     *         degrees. The anchor search prefers this cluster-specific slant over
     *         screen-horizontal, capped here so a tall cluster never stands its name
     *         vertical and faded toward level as a cluster gets rounder; 0 forces
     *         dead-horizontal labels, 90 lets a label follow its axis to vertical
     */
    public static double getMapAnchorMaxSlantDegrees() {
        return KmuLunaSettings.readDouble(
            ANCHOR_MAX_SLANT_DEGREES_FIELD,
            DEFAULT_ANCHOR_MAX_SLANT_DEGREES);
    }

    /**
     * @return how far short of the national border each end of a label anchor stops,
     *         in multiples of the border inset channel - the gap a cluster's name
     *         needs so it does not touch the border; a clear line shorter than twice
     *         this collapses to the dot
     */
    public static double getMapAnchorEndInsetMultiple() {
        return KmuLunaSettings.readDouble(
            ANCHOR_END_INSET_MULTIPLE_FIELD,
            DEFAULT_ANCHOR_END_INSET_MULTIPLE);
    }

    /**
     * @return the keep-out radius around each system icon that a label anchor must
     *         not cross, in world units - a tuned approximation of the icon's on-map
     *         footprint, since icons draw at a fixed pixel size while the anchor is
     *         fitted once in world space
     */
    public static double getMapAnchorIconClearance() {
        return KmuLunaSettings.readDouble(
            ANCHOR_ICON_CLEARANCE_FIELD,
            DEFAULT_ANCHOR_ICON_CLEARANCE);
    }

    /**
     * @return the smallest per-line font height (world units) a cluster's name may
     *         render at - the readability floor. A placement that cannot hold even one
     *         line this tall anywhere collapses to the dot and shows no name
     */
    public static double getMapNameMinFontSize() {
        return KmuLunaSettings.readDouble(NAME_MIN_FONT_SIZE_FIELD, DEFAULT_NAME_MIN_FONT_SIZE);
    }

    /**
     * @return the largest per-line font height (world units) the name fit will grow to,
     *         so a roomy cluster does not mint an oversized label; the upper bound of
     *         the font-height search
     */
    public static double getMapNameMaxFontSize() {
        return KmuLunaSettings.readDouble(NAME_MAX_FONT_SIZE_FIELD, DEFAULT_NAME_MAX_FONT_SIZE);
    }

    /**
     * @return the most lines the fit may wrap a name into: a length-poor but girth-rich
     *         cluster wraps the name to shorten its widest line and spends the spare
     *         girth, chosen only when that renders a strictly larger font than fewer
     *         lines would. 1 forces single-line names
     */
    public static int getMapNameMaxLines() {
        return KmuLunaSettings.readInt(NAME_MAX_LINES_FIELD, DEFAULT_NAME_MAX_LINES);
    }

    /**
     * @return the line-height multiple between a multi-line name's stacked lines, so a
     *         two- or three-line block is that much taller than the raw line heights;
     *         at least 1 (lines flush)
     */
    public static double getMapNameLineSpacing() {
        return KmuLunaSettings.readDouble(NAME_LINE_SPACING_FIELD, DEFAULT_NAME_LINE_SPACING);
    }

    /**
     * @return the fill opacity of the debug band quad, 0..1 - low enough that the
     *         national border reads through the band so an overflow is visible, since
     *         the whole point of drawing the band is to see it kiss or clear the border
     */
    public static double getMapAnchorBandOpacity() {
        return KmuLunaSettings.readDouble(ANCHOR_BAND_OPACITY_FIELD, DEFAULT_ANCHOR_BAND_OPACITY);
    }

    /**
     * @return the opacity of the debug band box's strokes - its outline, the line-count
     *         divider rules, the centreline, and the anchor dot - 0..1, kept separate
     *         from the fill alpha so the outline stays legible over a faint band wash
     */
    public static double getMapAnchorBandLineOpacity() {
        return KmuLunaSettings.readDouble(
            ANCHOR_BAND_LINE_OPACITY_FIELD,
            DEFAULT_ANCHOR_BAND_LINE_OPACITY);
    }

    /**
     * @return whether a cluster whose accepted label line collapsed to the dot also
     *         draws, in red, the best candidate line its fit found before the border,
     *         icon, or end-margin trim discarded it - how close the cluster came to
     *         carrying a line; off by default, meaningful only while the cluster
     *         anchors themselves draw
     */
    public static boolean getMapShowRejectedAxes() {
        return KmuLunaSettings.readBoolean(SHOW_REJECTED_AXES_FIELD, DEFAULT_SHOW_REJECTED_AXES);
    }

    /**
     * @return whether each cluster also draws, in yellow, the label line its fit would
     *         accept with no horizontal bias applied - the anchor bias knob's effect
     *         made visible by contrast. Drawn only when the unbiased direction actually
     *         differs from the accepted line's (with the bias at 1, or an axis already
     *         horizontal, the accepted line is the unbiased line); off by default,
     *         meaningful only while the cluster anchors themselves draw
     */
    public static boolean getMapShowUnbiasedAxes() {
        return KmuLunaSettings.readBoolean(SHOW_UNBIASED_AXES_FIELD, DEFAULT_SHOW_UNBIASED_AXES);
    }

    /**
     * @return how far down from the top edge of the screen the overlay sidebar box sits,
     *         in pixels; 46 by default (clearing the sector map's own tab strip)
     */
    public static int getMapSidebarPaddingTop() {
        return KmuLunaSettings.readInt(SIDEBAR_PADDING_TOP_FIELD, DEFAULT_SIDEBAR_PADDING_TOP);
    }

    /**
     * @return how far in from the left edge of the screen the overlay sidebar box sits,
     *         in pixels; 12 by default
     */
    public static int getMapSidebarPaddingLeft() {
        return KmuLunaSettings.readInt(SIDEBAR_PADDING_LEFT_FIELD, DEFAULT_SIDEBAR_PADDING_LEFT);
    }

    /**
     * @return how far above the bottom edge of the screen the overlay sidebar box must stay,
     *         in pixels; the panel body caps its height to this margin and the bloc list
     *         scrolls within the room left; 12 by default
     */
    public static int getMapSidebarPaddingBottom() {
        return KmuLunaSettings.readInt(
            SIDEBAR_PADDING_BOTTOM_FIELD,
            DEFAULT_SIDEBAR_PADDING_BOTTOM);
    }

    /**
     * @return how far down from the top edge of the intel screen's map preview (the "visor") the
     *         overlay sidebar box sits, in pixels, clearing the vanilla Starscape and fuel-range
     *         toggles at the top of that map; 21 by default. The box sits flush against the visor's
     *         left edge and its height caps to the visor's bottom, so only this top offset is exposed
     */
    public static int getMapIntelSidebarPaddingTop() {
        return KmuLunaSettings.readInt(
            INTEL_SIDEBAR_PADDING_TOP_FIELD,
            DEFAULT_INTEL_SIDEBAR_PADDING_TOP);
    }

    /**
     * @return the line width of the outer border framing the overlay sidebar box, in
     *         pixels; 1 by default, 0 draws no border
     */
    public static int getMapSidebarBorderWidth() {
        return KmuLunaSettings.readInt(SIDEBAR_BORDER_WIDTH_FIELD, DEFAULT_SIDEBAR_BORDER_WIDTH);
    }

    /**
     * @return the overlay sidebar box's background opacity as a 0..1 fraction (the CSV
     *         stores it as a 0..100 percentage); 0.8 by default
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
     * @return how long the overlay sidebar's collapse handle takes to fold the body to its
     *         docked rail (and to unfold it), in seconds; 0 snaps it instantly with no
     *         animation, up to 2 seconds; 0.25 by default. Fed to the collapse holder's
     *         per-frame advance so the player sets the animation pace
     */
    public static float getMapSidebarCollapseSeconds() {
        return KmuLunaSettings.readFloat(
            SIDEBAR_COLLAPSE_SECONDS_FIELD,
            DEFAULT_SIDEBAR_COLLAPSE_SECONDS);
    }

    /**
     * @return which palette the overlay sidebar's frame, control accents, and tab-row chrome are
     *         coloured from: the fixed UI palette, the neutral chrome greys, or the player
     *         faction's own accents; the UI palette by default. One answer for all three reads,
     *         since a panel framed in one palette and ruled in another reads worse than either
     */
    public static SidebarColourSchemeChoice getMapSidebarColourScheme() {
        return KmuLunaSettings.readChoice(
            SIDEBAR_COLOUR_SCHEME_FIELD,
            DEFAULT_SIDEBAR_COLOUR_SCHEME);
    }

    /**
     * @return which colour the overlay sidebar's collapse-handle chevron draws in: the vanilla
     *         highlight gold, or the panel's own accents under the chosen colour scheme
     *         (brightening under the pointer); the gold by default
     */
    public static NotchChevronColourChoice getMapSidebarChevronColour() {
        return KmuLunaSettings.readChoice(
            SIDEBAR_CHEVRON_COLOR_FIELD,
            DEFAULT_SIDEBAR_CHEVRON_COLOUR);
    }

    /**
     * Resolves a layer tab's shortcut keycode from its LunaLib Keycode field, so the player
     * can rebind which key jumps to that layer.
     *
     * <p>The field id is the caller's rather than a constant here: each layer owns the id its
     * own shortcut is stored under, so the framework can read a shortcut for a layer it does
     * not know about.
     *
     * <p>A keycode of 0 (LWJGL's {@code KEY_NONE}) means the player cleared the binding
     * with Escape, so the layer has no shortcut; callers treat that as unbound rather
     * than a real key.
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
     *         interpolated as the font loader hands them over and 1 drawing them unfiltered; a dial for
     *         settling the amount against the vanilla raised buttons that row stands beside
     */
    public static double getIntelSidebarPixelFontSharpness() {
        return KmuLunaSettings.readDouble(
            INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD,
            DEFAULT_INTEL_SIDEBAR_PIXEL_FONT_SHARPNESS);
    }

    /**
     * @return the same dial for the sector-map sidebar's tab labels, lettered in a different hard-edged
     *         atlas and read against the vanilla Sector/System tabs directly above them - so the two rows
     *         answer separately and dialling one never moves the other
     */
    public static double getMapSidebarPixelFontSharpness() {
        return KmuLunaSettings.readDouble(
            MAP_SIDEBAR_PIXEL_FONT_SHARPNESS_FIELD,
            DEFAULT_MAP_SIDEBAR_PIXEL_FONT_SHARPNESS);
    }
}
