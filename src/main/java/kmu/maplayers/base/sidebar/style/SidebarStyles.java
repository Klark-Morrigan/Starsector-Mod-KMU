package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.layout.TabsControlLayout;
import kmlib.starsector.ui.render.gl.AccentColours;
import kmlib.starsector.ui.render.gl.BoxColours;
import kmlib.starsector.ui.render.gl.WidgetStyle;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.widgets.tabs.HotkeyStyle;
import kmlib.starsector.ui.widgets.tabs.TabChrome;
import kmlib.starsector.ui.widgets.tabs.TabPalette;
import kmlib.starsector.ui.widgets.tabs.TabStyle;

import kmu.settings.KmuMapLayerSettings;

/**
 * Composes the look bundles a sidebar host wears: the tab style its band is laid out and painted from,
 * and the widget style the panel around it is painted from. Which look a screen wears is its host's
 * answer, but a look built from live player colours cannot be a constant a host holds, so the
 * composition sits here as a factory each host calls with its own dimensions - one place the sidebar's
 * shades and faces are written down, so two screens sharing a look cannot drift into two spellings of
 * it.
 *
 * <p>Out of the render pass for the same reason {@link SidebarPalettes} beside it is: a look is a value,
 * so building one needs no live GL context and no drawn frame - only the running game's colours.
 */
public final class SidebarStyles {

    /**
     * How the sidebar answers a press and the pointer arriving: the engine's own scheme, the sidebar
     * being drawn to sit among vanilla chrome.
     *
     * <p>Public because the panel's controller is handed this directly rather than reading it off the
     * widget style each frame - the moments it answers are pointer events, not paint passes - so the
     * scheme has to be one named value both sides take, or the panel would look and sound from two.
     */
    public static final UiSoundScheme SIDEBAR_SOUND_SCHEME = UiSoundScheme.createVanillaSoundScheme();

    // The body face: the insignia body font, the narrower face the body-control labels read in. The tab
    // face is separate below, since the tabs are both measured and drawn in theirs.
    private static final StarsectorFont BODY_FONT = StarsectorFont.VANILLA_INSIGNIA_15;

    // The tabs read in the sector map's own orbitron face - the AA orbitron atlas vanilla uses for its
    // map tabs, scaled to the tab size. It travels inside the tab style, which both the layout's measurer
    // and the paint pass read, so a snapped tab width matches the text drawn into it.
    private static final StarsectorFont TAB_FONT = StarsectorFont.VANILLA_ORBITRON_20AA;

    // Composes only; never instantiated.
    private SidebarStyles() {
    }

    /**
     * A tab style at the given band height, over the shared paint: the sector map's own tab chrome, the
     * vanilla map-tab palette - its per-state fills and its interaction lifts - resolved live so it tracks
     * a restyled install, the underlined-key hotkey convention, and the orbitron face at the layout's tab
     * size.
     *
     * <p>The underlined key is the sector map's own convention: the on-map strip sits one tab-height
     * below the vanilla Sector/System tabs, which mark their bound key wherever it falls - lit inside
     * the label where its letter stands there, spelt out in brackets only where it does not - so a key
     * marked by colour alone reads as a mismatch against the row above it. The line costs no width - it
     * is a quad under a glyph, not part of the measured display string - so no tab moves for it.
     *
     * @param headerBandHeight how tall the band carrying the tabs stands, the one dimension the two
     *                         screens set apart: each sits in different company and matches the weight
     *                         of the chrome beside it
     * @return the tab style to lay the band out with and paint it from
     */
    public static TabStyle buildTabStyle(float headerBandHeight) {
        return new TabStyle(
            TabChrome.STRIP,
            headerBandHeight,
            TabPalette.createMapTabPalette(),
            HotkeyStyle.createUnderlined(),
            new TextFace(TAB_FONT, TabsControlLayout.TAB_FONT_SIZE));
    }

    /**
     * The sidebar's look in the player's own accents, built fresh from the live colours: a black body
     * backdrop, the frame in that same accent, the base and bright player accents for the controls, the
     * insignia body face, the given tab style, the collapse handle's chevron shades for the colour the
     * player picked, and the vanilla button sounds its controls answer by.
     *
     * <p>The frame takes the accent because a panel floating free on its screen has no neighbouring
     * chrome to match; a host drawn against another panel's frame supplies its own colour there instead,
     * which is why the box's shades and the controls' travel as separate values at all.
     *
     * @param tabStyle the tab style the host laid its band out with, so the row is painted from the
     *                 value it was measured against
     * @return the look to paint this sidebar's panel from
     */
    public static WidgetStyle buildPlayerAccentedStyle(TabStyle tabStyle) {

        var accent = StarsectorUiColour.VANILLA_PLAYER_BASE.resolve();
        var brightAccent = StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve();

        return new WidgetStyle(
            new BoxColours(
                // The body backdrop is black; the opacity the render pass fades it by leaves the body a
                // translucent-black pane the map shows through rather than a solid block. Black, not the
                // player-dark tint, so the body stays neutral - only the tabs header, accents, and the
                // notch carry colour. This is the body fill alone; the tabs' own fills live in the tab
                // style, a separate field, so the body's colour never couples to the header's. The header
                // also opts out of that opacity fade and paints opaque, so the tabs read solid over the
                // faded body.
                StarsectorUiColour.BLACK.resolve(),
                accent),
            new AccentColours(accent, brightAccent),
            BODY_FONT,
            tabStyle,
            SidebarPalettes.resolveNotchColours(
                KmuMapLayerSettings.getMapSidebarChevronColour(),
                accent,
                brightAccent),
            SIDEBAR_SOUND_SCHEME);
    }
}
