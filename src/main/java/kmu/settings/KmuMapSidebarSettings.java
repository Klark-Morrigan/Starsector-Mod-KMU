package kmu.settings;

/**
 * The sidebar box's own chrome - where it sits, how it is drawn, how fast it moves - plus the two
 * hatches that are neither appearance nor geometry: whether the vanilla filter row is reached into
 * at all, and what one frame's layer work is allowed to cost.
 *
 * <p>The panel is drawn on two screens whose chrome differs, so several knobs come in pairs: what
 * reads correctly beside the intel screen's buttons is not what reads correctly under the sector
 * map's tabs, and it is the chrome each row abuts that decides rather than the face. *
 * <p>What each knob does for the player is stated once, in the description column of
 * data/config/LunaSettings.csv, which is the text the settings screen actually shows. The prose
 * here answers only what that column cannot: why a default is the number it is, and what a caller
 * has to know to use the value.
 */
public final class KmuMapSidebarSettings {

    private static final String MAP_LAYER_HIDE_FADE_SECONDS_FIELD =
        "kmu_map_visuals_layers_hideFadeSeconds";

    // Whether the control that drives the fade above is put on the vanilla filter row at all. Dev
    // rather than visuals: what it governs is a reach into another party's widget, so it is the hatch
    // a player opens when that reach misbehaves, not a knob they set to taste.
    private static final String FILTER_ROW_TOGGLE_ENABLED_FIELD =
        "kmu_map_dev_ui_filters_mapLayersToggle_isEnabled";

    // Dev rather than visuals: nothing on screen moves with it, and it is read only while a
    // profile capture is running.
    private static final String FRAME_BEAT_BUDGET_MILLIS_FIELD =
        "kmu_map_dev_profiling_frameBeat_budgetMillis";

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

    private static final String SIDEBAR_SCROLLBAR_THICKNESS_FIELD =
        "kmu_map_visuals_sidebar_scrollbarThickness";

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

    // Stored 50..100 in the CSV, exposed 0..1. The floor is not a taste: the body is what the controls
    // are read against, and its words no longer take its translucency, so a body faded much past half
    // leaves opaque text standing on whatever the map happens to show through - which is harder to read
    // than the same text on a dimmer panel, and unreadable over a bright cluster.
    private static final int DEFAULT_SIDEBAR_OPACITY_PERCENT = 80;

    private static final int MIN_SIDEBAR_OPACITY_PERCENT = 50;

    private static final int MAX_SIDEBAR_OPACITY_PERCENT = 100;

    // The thin bar the panel draws when the player leaves the slider alone.
    //
    // The floor is 1 rather than 0 because 0 is a state the widget library offers and this panel never
    // wants: it takes the bar away outright, which is a control gone from the panel with nothing on
    // screen to say where it went or how to get it back. Clamped here as well as bounded on the slider,
    // so a hand-edited settings file cannot reach the state the mod says it never enters - the same
    // reason the opacity above holds its own floor.
    private static final int DEFAULT_SIDEBAR_SCROLLBAR_THICKNESS = 3;

    private static final int MIN_SIDEBAR_SCROLLBAR_THICKNESS = 1;

    private static final int MAX_SIDEBAR_SCROLLBAR_THICKNESS = 12;

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

    // A sixtieth of a second is the whole frame, and the map layers are one thing drawn in it beside
    // the game's own sector map - so a quarter of it is the share a beat can take before the frame
    // it sits in is the layers' fault.
    private static final double DEFAULT_FRAME_BEAT_BUDGET_MILLIS = 4.0;

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

    private KmuMapSidebarSettings() {
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
     * @return how thick the overlay sidebar's scrollbar draws, in pixels; 3 by default, held between 1
     *         and 12 so the panel always stands a bar the player can find. The layout the value is handed
     *         to is what reserves the room a bar this wide needs
     */
    public static int getMapSidebarScrollbarThickness() {

        var pixels = KmuLunaSettings.readInt(
            SIDEBAR_SCROLLBAR_THICKNESS_FIELD,
            DEFAULT_SIDEBAR_SCROLLBAR_THICKNESS);

        return Math.max(
            MIN_SIDEBAR_SCROLLBAR_THICKNESS,
            Math.min(MAX_SIDEBAR_SCROLLBAR_THICKNESS, pixels));
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
    public static boolean isMapFilterRowToggleEnabled() {
        return KmuLunaSettings.readBoolean(
            FILTER_ROW_TOGGLE_ENABLED_FIELD,
            DEFAULT_FILTER_ROW_TOGGLE_ENABLED);
    }

    /**
     * @return how long one beat of a map frame - a preparation, a paint band, a cursor read, a
     *         tooltip - may take before a running profile capture reports it as over budget, in
     *         milliseconds; 4ms by default, and 0 for no bound at all. Read as each beat ends
     *         while a capture is running, so moving it holds the next frame to what it now says
     */
    public static double getMapFrameBeatBudgetMillis() {
        return KmuLunaSettings.readDouble(
            FRAME_BEAT_BUDGET_MILLIS_FIELD,
            DEFAULT_FRAME_BEAT_BUDGET_MILLIS);
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
