package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.layout.TabsControlLayout;
import kmlib.starsector.ui.render.gl.style.AccentColours;
import kmlib.starsector.ui.render.gl.style.BoxColours;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.widgets.tabs.style.HotkeyStyle;
import kmlib.starsector.ui.widgets.tabs.style.TabChrome;
import kmlib.starsector.ui.widgets.tabs.style.TabPalette;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;

import kmu.settings.KmuMapLayerSettings;

import java.awt.Color;

/**
 * Composes the look bundles a sidebar host wears: the tab style its band is laid out and painted from,
 * and the widget style the panel around it is painted from. Which look a screen wears is its host's
 * answer, but a look built from the running game's colours and the player's live scheme choice cannot be
 * a constant a host holds, so the composition sits here as a factory each host calls with its own
 * dimensions - one place the sidebar's shades and faces are written down, so two screens sharing a look
 * cannot drift into two spellings of it.
 *
 * <p>Which palette those shades come from is the player's, not a host's: the frame, the control accents,
 * the notch, and the tab row's chrome rule all resolve from one scheme choice, so the panel cannot end
 * up framed in one palette and ruled in another. That is also why the choice is read here rather than
 * per part - the parts have no business each asking.
 *
 * <p>Both bundles come as a pair of named factories rather than as one taking the difference as an
 * argument, because the differences are conventions and not free choices: a row wears the strip chrome
 * with the underlined key or the raised-button chrome with the plain one, and a panel is framed in its own
 * accent or in the surrounding chrome's grey. Naming the two ends is what keeps a host from composing a
 * look vanilla has no counterpart for.
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
     * The strip tab style at the given band height: the sector map's seamless run of abutting tabs, and
     * the underlined-key hotkey convention that goes with it.
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
    public static TabStyle buildStripTabStyle(float headerBandHeight) {
        return composeTabStyle(TabChrome.STRIP, HotkeyStyle.createUnderlined(), headerBandHeight);
    }

    /**
     * The raised-button tab style at the given band height: each tab a framed button standing clear of
     * its neighbours, and the plain-key hotkey convention that goes with it.
     *
     * <p>The pair is the intel screen's own: its row of map toggles stands as separate buttons and lights
     * a bound key by colour alone, so a panel overlaying that screen's visor takes both together. Marking
     * a key one way while the buttons beside it mark it another is the mismatch the styled hotkey exists
     * to avoid, so the chrome and the convention are chosen in one place rather than wired separately.
     *
     * @param headerBandHeight how tall the band carrying the tabs stands - see
     *                         {@link #buildStripTabStyle}
     * @return the tab style to lay the band out with and paint it from
     */
    public static TabStyle buildRaisedButtonTabStyle(float headerBandHeight) {
        return composeTabStyle(TabChrome.RAISED_BUTTON, HotkeyStyle.createPlain(), headerBandHeight);
    }

    /**
     * The sidebar's look framed in its own control accent: whichever base shade the player's colour
     * scheme rules the controls in rules the frame too. What a panel floating free on its screen wants,
     * having no neighbouring chrome to match - so the one colour it does carry is its own.
     *
     * @param tabStyle the tab style the host laid its band out with, so the row is painted from the
     *                 value it was measured against
     * @return the look to paint this sidebar's panel from
     */
    public static WidgetStyle buildAccentFramedStyle(TabStyle tabStyle) {

        // Resolved once and spent twice, so the frame is demonstrably the same shade as the controls
        // rather than a second read that a scheme change could catch part-way.
        var accents = resolveAccentColours();

        return composeStyle(tabStyle, accents, accents.base());
    }

    /**
     * The sidebar's look framed in the surrounding UI's own grey: the frame matches the vanilla chrome
     * the panel is drawn among rather than the accent its controls take.
     *
     * <p>What a panel overlaying another screen's chrome wants. Its frame abuts that screen's frames, and
     * two boxes sharing an edge in two colours read as one laid over the other rather than as part of the
     * same surface; the grey is the fixed UI role those frames answer to, which no player faction moves.
     * The controls inside keep the panel's own accent, that being what the player chose rather than what
     * the panel abuts - which is the whole reason the frame is its own knob.
     *
     * @param tabStyle the tab style the host laid its band out with, so the row is painted from the
     *                 value it was measured against
     * @return the look to paint this sidebar's panel from
     */
    public static WidgetStyle buildChromeFramedStyle(TabStyle tabStyle) {
        return composeStyle(
            tabStyle,
            resolveAccentColours(),
            StarsectorUiColour.VANILLA_GRAY.resolve());
    }

    // The accent pair the whole panel is ruled in, under whichever palette the player pointed it at.
    // Read here rather than at each use so the frame, the controls, the notch, and the tab row cannot
    // end up describing themselves from different halves of one scheme change.
    private static AccentColours resolveAccentColours() {
        return SidebarPalettes.resolveAccentColours(KmuMapLayerSettings.getMapSidebarColourScheme());
    }

    // A tab style over the shared paint: the vanilla map-tab palette - its per-state fills and its
    // interaction lifts - resolved live so it tracks a restyled install, and the orbitron face at the
    // layout's tab size. Only the chrome and the hotkey convention part the two screens' rows, and they
    // part together, so the values every row shares are written once here.
    //
    // The row's chrome rule is the panel's accent rather than the palette's own pick: KMLib works a tab's
    // fills out from the engine's tab colours but has no vanilla counterpart to copy for the rule around
    // them, so the shade a strip is ruled in travels down from the panel it belongs to and follows the
    // same scheme its frame and controls do.
    private static TabStyle composeTabStyle(
            TabChrome chrome,
            HotkeyStyle hotkey,
            float headerBandHeight) {

        return new TabStyle(
            chrome,
            headerBandHeight,
            TabPalette.createMapTabPalette(resolveAccentColours().base()),
            hotkey,
            new TextFace(TAB_FONT, TabsControlLayout.TAB_FONT_SIZE));
    }

    // The sidebar's look built fresh from the live colours, framed in the given colour: a black body
    // backdrop, the given accent pair for the controls, the insignia body face, the given tab style, the
    // collapse handle's chevron shades for the colour the player picked, and the vanilla button sounds
    // its controls answer by. Everything but the frame is shared by every screen the sidebar draws on,
    // so a screen choosing its frame chooses nothing else by accident.
    private static WidgetStyle composeStyle(
            TabStyle tabStyle,
            AccentColours accentColours,
            Color frameColour) {

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
                frameColour),
            accentColours,
            BODY_FONT,
            tabStyle,
            SidebarPalettes.resolveNotchColours(
                KmuMapLayerSettings.getMapSidebarChevronColour(),
                accentColours.base(),
                accentColours.bright()),
            SIDEBAR_SOUND_SCHEME);
    }
}
