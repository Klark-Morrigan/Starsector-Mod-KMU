package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.colour.AccentColours;
import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.layout.TabsControlLayout;
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
 * accent or in the dark step the chrome around it is framed in. Naming the two ends is what keeps a host
 * from composing a look vanilla has no counterpart for.
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

    // The strip reads in the sector map's own orbitron face - the AA orbitron atlas vanilla uses for its
    // map tabs, scaled to the tab size. It travels inside the tab style, which both the layout's measurer
    // and the paint pass read, so a snapped tab width matches the text drawn into it.
    private static final TextFace STRIP_FACE = new TextFace(
        StarsectorFont.VANILLA_ORBITRON_20AA,
        TabsControlLayout.TAB_FONT_SIZE);

    // The buttons read in the pixel face vanilla letters its own intel-screen map toggles in, at the size
    // that atlas was drawn at: a bitmap face is crisp at one size only, and a row copying those buttons
    // wants the same glyphs on the same grid rather than a scaled approximation of them. Its capitals
    // come with the face - every glyph sits on one cell whatever case it is written in - so the labels
    // need no upper-casing pass to match the row beside them.
    private static final TextFace RAISED_BUTTON_FACE = new TextFace(
        StarsectorFont.VANILLA_VICTOR_10,
        StarsectorFont.VANILLA_VICTOR_10.getNativeSize());

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
        return composeStyle(tabStyle, SidebarFraming.OWN_ACCENT);
    }

    /**
     * The sidebar's look framed in the scheme's dark step: the frame matches the vanilla chrome the panel
     * is drawn among rather than the accent its controls take.
     *
     * <p>What a panel overlaying another screen's chrome wants. Its frame abuts that screen's frames, and
     * two boxes sharing an edge in two colours read as one laid over the other rather than as part of the
     * same surface. The dark step is what those frames are drawn in: the engine builds a control from a
     * three-colour set and frames it in the dark member, so a panel matching that chrome matches it at
     * that step and not at the base its labels are written in. The controls inside keep the base, that
     * being what a control is ruled in either way - which is the whole reason the frame is its own knob.
     *
     * @param tabStyle the tab style the host laid its band out with, so the row is painted from the
     *                 value it was measured against
     * @return the look to paint this sidebar's panel from
     */
    public static WidgetStyle buildChromeFramedStyle(TabStyle tabStyle) {
        return composeStyle(tabStyle, SidebarFraming.CHROME_DARK);
    }

    // The accent steps the panel is ruled and framed in, under whichever palette the player pointed it
    // at. One place
    // the choice is turned into shades, so the widget style and the tab row cannot come to read it
    // differently - but two call sites, since a tab style is also built for the layout pass, where no
    // panel style exists to hand one down from. Both land in the same frame on a live read of one stored
    // value, so the two agree; what the single seam buys is that they agree by construction rather than
    // by two spellings of the same switch.
    private static AccentColours resolveAccentColours() {
        return SidebarPalettes.resolveAccentColours(KmuMapLayerSettings.getMapSidebarColourScheme());
    }

    // Which step of the panel's accent a framing convention paints the border in. Both conventions read
    // the same resolved set rather than one of them resolving a colour of its own, which is what makes a
    // panel framed in one palette and ruled in another unrepresentable rather than merely avoided: the
    // two framings differ by which step they take, never by which palette.
    private static Color resolveFrameColour(SidebarFraming framing, AccentColours accentColours) {
        return switch (framing) {
            case OWN_ACCENT -> accentColours.base();
            case CHROME_DARK -> accentColours.dark();
        };
    }

    // The paint a row wears, which is the chrome's own: the engine paints a tab and a button from
    // different colours, so a palette shared between the two would leave one of them copying shades its
    // vanilla counterpart never wears. Both are resolved from the one scheme the panel answers to, so the
    // rows part by which vanilla control they imitate and never by which palette.
    private static TabPalette composeTabPalette(TabChrome chrome, AccentColours accentColours) {
        return switch (chrome) {
            // A vanilla tab takes no accent at all beyond the rule around the row, which has no vanilla
            // counterpart to copy - so the panel's own base is what the strip is ruled in.
            case STRIP -> TabPalette.createMapTabPalette(accentColours.base());
            // A vanilla button takes nothing but its accent, and takes all of it - so the whole set goes
            // over rather than the steps picked out one at a time.
            case RAISED_BUTTON -> TabPalette.createRaisedButtonPalette(accentColours);
        };
    }

    // The face a row is lettered in, which is the chrome's own for the same reason its palette is: the
    // vanilla tabs a strip copies and the vanilla buttons a raised row copies are set in different faces,
    // so a row sharing one would letter itself unlike the very chrome it was drawn to match. It is the
    // one field both tiers read - the layout snaps a tab to it and the paint pass draws in it - so the
    // choice made here is what a tab is measured by as well as what it says.
    private static TextFace resolveTabFace(TabChrome chrome) {
        return switch (chrome) {
            case STRIP -> STRIP_FACE;
            case RAISED_BUTTON -> RAISED_BUTTON_FACE;
        };
    }

    // A tab style over the shared paint: the chrome's own palette - its per-state fills and its
    // interaction lifts - resolved live so it tracks a restyled install, and the chrome's own face. Only
    // the chrome, and the palette, face, and hotkey convention that belong to it, part the two screens'
    // rows, so the values every row shares are written once here.
    private static TabStyle composeTabStyle(
            TabChrome chrome,
            HotkeyStyle hotkey,
            float headerBandHeight) {

        return new TabStyle(
            chrome,
            headerBandHeight,
            composeTabPalette(chrome, resolveAccentColours()),
            hotkey,
            resolveTabFace(chrome));
    }

    // The sidebar's look built fresh from the live colours, framed by the given convention: a black body
    // backdrop, the colour scheme's accent steps for the controls, the insignia body face, the given tab
    // style, the collapse handle's chevron shades for the colour the player picked, and the vanilla
    // button sounds its controls answer by. Everything but the framing is shared by every screen the
    // sidebar draws on, so a screen choosing its frame chooses nothing else by accident.
    //
    // The set is resolved here and spent on both the controls and the frame, so the one scheme reaches
    // every part of the panel. A frame colour taken as an argument beside the set would let the two
    // arrive from different schemes - the mismatch the scheme exists to remove - so the caller names its
    // convention and nothing else.
    private static WidgetStyle composeStyle(TabStyle tabStyle, SidebarFraming framing) {

        var accentColours = resolveAccentColours();

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
                resolveFrameColour(framing, accentColours)),
            accentColours,
            BODY_FONT,
            tabStyle,
            SidebarPalettes.resolveNotchColours(
                KmuMapLayerSettings.getMapSidebarChevronColour(),
                accentColours.base(),
                accentColours.bright()),
            SIDEBAR_SOUND_SCHEME);
    }

    /**
     * Which of the two framing conventions a look is composed with. A convention rather than a colour,
     * because neither one is free to name its shade: each names a step of the accent the panel's controls
     * are already ruled in, and a caller allowed to pass a colour instead could hand over one from another
     * palette entirely.
     *
     * <p>Private, and so not a third thing a host chooses. The two public factories are the surface a
     * host picks its convention through, this being the argument they differ by once the pair of them
     * has already named the choice.
     */
    private enum SidebarFraming {

        /** Framed in the panel's own control accent - what a panel with no neighbouring chrome takes. */
        OWN_ACCENT,

        /**
         * Framed in the accent's dark step - what a panel abutting another screen's frames takes, that
         * being the step the engine frames its own controls in.
         */
        CHROME_DARK
    }
}
