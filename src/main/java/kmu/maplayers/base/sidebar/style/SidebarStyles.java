package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.colour.AccentColours;
import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.FontAtlas;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.font.installed.InstalledFaces;
import kmlib.starsector.ui.render.gl.style.BoxColours;
import kmlib.starsector.ui.render.gl.style.ControlHoverWash;
import kmlib.starsector.ui.render.gl.style.ControlPressLight;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.widgets.tabs.style.HotkeyStyle;
import kmlib.starsector.ui.widgets.tabs.style.TabBox;
import kmlib.starsector.ui.widgets.tabs.style.TabChrome;
import kmlib.starsector.ui.widgets.tabs.style.TabPalette;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;
import kmlib.starsector.ui.widgets.tabs.style.TextHalo;

import kmu.maplayers.base.faces.ProbedText;
import kmu.maplayers.base.faces.SettledFaces;
import kmu.settings.KmuMapSidebarSettings;

import java.awt.Color;
import java.util.Set;

/**
 * Composes the look bundles a sidebar host wears: the tab style its band is laid out and painted from, and
 * the widget style the panel around it is painted from. Which look a screen wears is its host's answer, but
 * a look built from the running game's colours and the player's live scheme choice cannot be a constant a
 * host holds, so the composition sits here as a factory each host calls with its own dimensions - one place
 * the sidebar's shades and faces are written down, so two screens sharing a look cannot drift into two
 * spellings of it. The widget style also carries how the panel sounds, which {@link SidebarSounds} composes.
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

    // The body face asked for: the insignia body font, the narrower face the body-control labels read in.
    // The tab face is separate below, since the tabs are both measured and drawn in theirs.
    private static final StarsectorFont BODY_FONT = StarsectorFont.VANILLA_INSIGNIA_15;

    // The strip's tab box, taken from the sector map's own Sector/System row (com.fs.starfarer.coreui.A.G):
    // each tab a 130 x 18 box, the pair parted by a single pixel, standing in a band that reserves one
    // more pixel than the tab is tall for the line the row sits on. A fixed box rather than a snapped one
    // is the point of the numbers: the row that vanilla draws spans the same width whatever its tabs say,
    // which is what lets this row read as another of that set rather than as one sized by its own labels.
    private static final float STRIP_TAB_WIDTH = 130f;
    private static final float STRIP_TAB_HEIGHT = 18f;
    private static final float STRIP_TAB_GAP = 1f;

    // The strip reads in the condensed orbitron vanilla letters its own Sector/System map tabs in. A
    // condensed face is narrower per glyph than the title orbitron at the same height, and vanilla's tab
    // box was sized against that width - so the face is what lets a label sit in a box built to vanilla's
    // measure rather than one grown to fit the text. It travels inside the tab style, off which the
    // layout's tab-face measurement is bound and from which the paint pass letters, so the width a tab is
    // measured at is the width its text draws at.
    private static final StarsectorFont STRIP_FONT = StarsectorFont.VANILLA_ORBITRON_12_CONDENSED;

    // The buttons read in the pixel face vanilla letters its own intel-screen map toggles in: a row
    // copying those buttons wants the same glyphs on the same grid rather than a scaled approximation of
    // them. Its capitals come with the face - every glyph sits on one cell whatever case it is written in
    // - so the labels need no upper-casing pass to match the row beside them.
    private static final StarsectorFont RAISED_BUTTON_FONT = StarsectorFont.VANILLA_VICTOR_10;

    // What each face has to draw, which is what it is settled against: the body lists factions, systems and
    // colonies among KMU's own words, and a tab carries a layer's name, which is KMU's own.
    private static final Set<ProbedText> BODY_TEXTS = ProbedText.EVERY_KIND;
    private static final Set<ProbedText> TAB_TEXTS = Set.of(ProbedText.MOD_STRINGS);

    // Composes only; never instantiated.
    private SidebarStyles() {
    }

    /**
     * The face the body controls letter in - the same value {@link #composeStyle} spends on the look the
     * paint pass reads, so a caller measuring a body row snaps it to the letters it will be drawn in.
     * Exposed because the tab face and this one are two different atlases: a strip measured wholly in the
     * tab face sizes every body row against letters it never wears, and the panel framed to the widest row
     * inherits that error.
     *
     * <p>The face the running sector settled the body on, which is the insignia body font wherever the
     * install's atlas holds what the body lists.
     *
     * @return the atlas the body-control labels draw in
     */
    public static FontAtlas settleBodyFace() {
        return SettledFaces.resolveFacesForLiveSector().settleFace(BODY_FONT, BODY_TEXTS);
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
        return composeTabStyle(TabChrome.STRIP, headerBandHeight);
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
        return composeTabStyle(TabChrome.RAISED_BUTTON, headerBandHeight);
    }

    /**
     * The look the band's own button wears: the host's own tab style, so the button stands in the same
     * chrome and colours as the tabs beside it, with one thing changed - its box is as wide as the image
     * it carries rather than as wide as a layer name.
     *
     * <p>The width is the only thing that can differ. A tab row states a box wide enough for the longest
     * label it will ever hold, which for the sector map is a fixed 130; a button showing a mark instead of
     * a word would stand in that box several times over. So the box is rebuilt at the image's own width -
     * the tab height scaled by the image's proportions, which is what "shrunk to fit the band" comes to -
     * while its height and the channel to its neighbour stay the row's, so the button sits level with the
     * tabs and is parted from them exactly as they are parted from each other.
     *
     * @param hostStyle   the tab style the panel's own row is drawn in
     * @param iconAspect  the image's width over its height, which the tab height is scaled by
     * @return the tab style the band button is measured and painted at
     */
    public static TabStyle buildBandButtonTabStyle(TabStyle hostStyle, float iconAspect) {

        var hostBox = hostStyle.tabBox();

        return hostStyle.withTabBox(new TabBox(
            hostStyle.resolveTabHeight() * iconAspect,
            hostBox.height(),
            hostBox.neighbourGap()));
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
    // at. One place the choice is turned into shades, so the widget style and the tab row cannot come to
    // read it differently - but two call sites, since a tab style is also built for the layout pass, where
    // no panel style exists to hand one down from. Both land in the same frame on a live read of one stored
    // value, so the two agree; what the single seam buys is that they agree by construction rather than
    // by two spellings of the same switch.
    private static AccentColours resolveAccentColours() {
        return SidebarPalettes.resolveAccentColours(KmuMapSidebarSettings.getMapSidebarColourScheme());
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

    // The band height is the one part of a tab style the host decides; the chrome decides the rest.
    private static TabStyle composeTabStyle(TabChrome chrome, float headerBandHeight) {
        return resolveChromeConvention(chrome).composeTabStyle(headerBandHeight);
    }

    // The one place the chrome is switched on. Composed afresh on every call rather than held per chrome:
    // the palette tracks a restyled install and the player's scheme, the face tracks the sector, and the
    // sharpness tracks a dial the player may move while the panel is on screen.
    private static ChromeConvention resolveChromeConvention(TabChrome chrome) {

        var accentColours = resolveAccentColours();

        return switch (chrome) {
            case STRIP -> composeStripConvention(accentColours);
            case RAISED_BUTTON -> composeRaisedButtonConvention(accentColours);
        };
    }

    // The sector map's own Sector/System row.
    private static ChromeConvention composeStripConvention(AccentColours accentColours) {

        return new ChromeConvention(
            TabChrome.STRIP,
            new TabBox(STRIP_TAB_WIDTH, STRIP_TAB_HEIGHT, STRIP_TAB_GAP),
            // A vanilla tab takes no accent at all beyond the rule around the row, which has no vanilla
            // counterpart to copy - so the panel's own base is what the strip is ruled in.
            TabPalette.createMapTabPalette(accentColours.base()),
            HotkeyStyle.createUnderlined(),
            settleTabFace(STRIP_FONT),
            // The tabs are opaque surfaces of their own, so the labels already have a fill of known shade
            // behind them.
            TextHalo.NONE,
            (float) KmuMapSidebarSettings.getMapSidebarPixelFontSharpness());
    }

    // The intel screen's row of map toggles.
    private static ChromeConvention composeRaisedButtonConvention(AccentColours accentColours) {

        return new ChromeConvention(
            TabChrome.RAISED_BUTTON,
            // The buttons are laid to the row the layout measured and take their channel from inside their
            // own tab.
            TabBox.SNAPPED,
            // A vanilla button takes nothing but its accent, and takes all of it - so the whole set goes
            // over rather than the steps picked out one at a time.
            TabPalette.createRaisedButtonPalette(accentColours),
            HotkeyStyle.createPlain(),
            settleTabFace(RAISED_BUTTON_FONT),
            // The buttons stand over whatever the visor is showing, so their strokes want an edge to sit
            // against.
            TextHalo.createBlackHairline(),
            (float) KmuMapSidebarSettings.getIntelSidebarPixelFontSharpness());
    }

    // Drawn at the size the installed atlas states, read each time a style is composed: a bitmap face is
    // crisp at one size only, both tab faces are hard-edged pixel atlases that visibly blur at any other,
    // and a localised install carries a taller atlas under the same basename than vanilla does. The face is
    // the one the running sector settled the chrome's own on.
    private static TextFace settleTabFace(StarsectorFont font) {

        return InstalledFaces.createNativeFace(
            SettledFaces.resolveFacesForLiveSector().settleFace(font, TAB_TEXTS));
    }

    // The sidebar's look built fresh from the live colours, framed by the given convention: a black body
    // backdrop, the colour scheme's accent steps for the controls, the wash the pointer lifts one of those
    // controls by and the light a press adds over it, the body face the sector settled, the given tab
    // style, the collapse handle's chevron shades for the colour the player picked, and the vanilla
    // button sounds its controls answer by at the levels the player set them to. Everything but the
    // framing is shared by every screen the sidebar draws on, so a screen choosing its frame chooses
    // nothing else by accident.
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
            // The lift the pointer adds to a body control, taken from the same resolved set the controls
            // are ruled in: a hover is more of what the control already wears, so it cannot be a shade the
            // panel names nowhere else.
            ControlHoverWash.createAccentHoverWash(accentColours),
            // The light a pressed control lifts by, off that same set for the same reason - and a
            // channel of its own rather than more wash, since the cell a press lands on is already
            // fully washed by the pointer that pressed it.
            ControlPressLight.createAccentPressLight(accentColours),
            settleBodyFace(),
            tabStyle,
            SidebarPalettes.resolveNotchColours(
                KmuMapSidebarSettings.getMapSidebarChevronColour(),
                accentColours.base(),
                accentColours.bright()),
            SidebarSounds.buildSoundScheme());
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
