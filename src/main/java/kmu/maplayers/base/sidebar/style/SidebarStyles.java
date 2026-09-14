package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.colour.AccentColours;
import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.TextFace;
import kmlib.starsector.ui.render.gl.style.BoxColours;
import kmlib.starsector.ui.render.gl.style.ControlHoverWash;
import kmlib.starsector.ui.render.gl.style.ControlPressLight;
import kmlib.starsector.ui.render.gl.style.WidgetStyle;
import kmlib.starsector.ui.sound.PointerArrivalVolumes;
import kmlib.starsector.ui.sound.StarsectorUiSound;
import kmlib.starsector.ui.sound.UiSoundCue;
import kmlib.starsector.ui.sound.UiSoundScheme;
import kmlib.starsector.ui.widgets.tabs.style.HotkeyStyle;
import kmlib.starsector.ui.widgets.tabs.style.TabBox;
import kmlib.starsector.ui.widgets.tabs.style.TabChrome;
import kmlib.starsector.ui.widgets.tabs.style.TabPalette;
import kmlib.starsector.ui.widgets.tabs.style.TabStyle;
import kmlib.starsector.ui.widgets.tabs.style.TextHalo;

import kmu.settings.KmuMapSidebarSettings;
import kmu.settings.KmuMapSoundSettings;

import java.awt.Color;

/**
 * Composes the look bundles a sidebar host wears: the tab style its band is laid out and painted from,
 * the widget style the panel around it is painted from, and the sound scheme its moments answer by.
 * Which look a screen wears is its host's answer, but a look built from the running game's colours, the
 * player's live scheme choice, and the levels the player set cannot be a constant a host holds, so the
 * composition sits here as a factory each host calls with its own dimensions - one place the sidebar's
 * shades, faces, and volumes are written down, so two screens sharing a look cannot drift into two
 * spellings of it.
 *
 * <p>The sound scheme is a bundle of its own rather than something read off the widget style, because
 * the two halves of the panel take it at different moments: the paint pass carries it inside the style
 * it draws from, while the controller answering pointer events is handed one directly at the point it is
 * built. Both compose through the same factory, which is what keeps a panel from looking and sounding
 * from two.
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

    // The body face: the insignia body font, the narrower face the body-control labels read in. The tab
    // face is separate below, since the tabs are both measured and drawn in theirs.
    private static final StarsectorFont BODY_FONT = StarsectorFont.VANILLA_INSIGNIA_15;

    // The strip's tab box, taken from the sector map's own Sector/System row (com.fs.starfarer.coreui.A.G):
    // each tab a 130 x 18 box, the pair parted by a single pixel, standing in a band that reserves one
    // more pixel than the tab is tall for the line the row sits on. A fixed box rather than a snapped one
    // is the point of the numbers: the row that vanilla draws spans the same width whatever its tabs say,
    // which is what lets this row read as another of that set rather than as one sized by its own labels.
    private static final float STRIP_TAB_WIDTH = 130f;
    private static final float STRIP_TAB_HEIGHT = 18f;
    private static final float STRIP_TAB_GAP = 1f;

    // The strip reads in the condensed orbitron vanilla letters its own Sector/System map tabs in, at the
    // size that atlas draws at. A condensed face is narrower per glyph than the title orbitron at the same
    // height, and vanilla's tab box was sized against that width - so the face is what lets a label sit in
    // a box built to vanilla's measure rather than one grown to fit the text. It travels inside the tab
    // style, off which the layout's tab-face measurement is bound and from which the paint pass letters,
    // so the width a tab is measured at is the width its text draws at.
    private static final TextFace STRIP_FACE = new TextFace(
        StarsectorFont.VANILLA_ORBITRON_12_CONDENSED,
        StarsectorFont.VANILLA_ORBITRON_12_CONDENSED.getNativeSize());

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
     * The face the body controls letter in - the same value {@link #composeStyle} spends on the look the
     * paint pass reads, so a caller measuring a body row snaps it to the letters it will be drawn in.
     * Exposed because the tab face and this one are two different atlases: a strip measured wholly in the
     * tab face sizes every body row against letters it never wears, and the panel framed to the widest row
     * inherits that error.
     *
     * @return the atlas the body-control labels draw in
     */
    public static StarsectorFont resolveBodyFont() {
        return BODY_FONT;
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

    /**
     * How the sidebar answers a press, the pointer arriving, and its list moving under the wheel: the
     * engine's own roles, the panel being drawn to sit among vanilla chrome, at the levels the player set
     * them to.
     *
     * <p>The roles are vanilla's and the balance is not, which is the whole of what this composes. What
     * the engine mixed its mouseover for is a screen carrying a handful of hit targets, and this panel
     * packs a column of them into a strip - so matching the chrome around it means matching it under one
     * pointer sweep, which the IDs alone cannot do at any volume they ship with.
     *
     * <p>Read here rather than watched, on the rule the colours beside it follow: a scheme is composed
     * whenever the panel's look is, which is often enough that a slider moved mid-session is heard
     * shortly after. A binding that pushed changes over would be a second mechanism for a value already
     * re-read.
     *
     * <p>Public because the panel's controller takes the scheme directly rather than reading it off the
     * widget style each frame - the moments it answers are pointer events, not paint passes - so both
     * sides have to compose from this one factory, or the panel would look and sound from two.
     *
     * @return the scheme this sidebar's moments sound by
     */
    public static UiSoundScheme buildSidebarSoundScheme() {

        var arrivalVolumes = new PointerArrivalVolumes(
            KmuMapSoundSettings.getMapSidebarPanelChromeArrivalVolume(),
            KmuMapSoundSettings.getMapSidebarSingleOptionControlArrivalVolume(),
            KmuMapSoundSettings.getMapSidebarListedItemArrivalVolume());

        return new UiSoundScheme(
            // The press keeps the engine's own level, having no slider of its own: it is one act the
            // player asked for, so the case for quietening it - a sweep crossing many things at once -
            // never arises.
            UiSoundCue.createAtFullVolume(StarsectorUiSound.BUTTON_PRESSED),
            resolveArrivalSound(arrivalVolumes),
            arrivalVolumes,
            resolveListScrollCue());
    }

    // What the wheel makes as it moves the sidebar's list, or nothing at all once the player has pulled its
    // slider to the bottom - the library's own rule for a level composed from a slider, silence being
    // stated by naming no cue rather than by playing one at zero.
    //
    // A level of its own and not part of the arrival balance: the wheel is one act the player asked for,
    // sounding once however far the list travels, where the arrival levels are set against how many things
    // one sweep of the pointer crosses.
    private static UiSoundCue resolveListScrollCue() {
        return UiSoundCue.createIfAudible(
            StarsectorUiSound.LIST_SCROLLED,
            KmuMapSoundSettings.getMapSidebarListScrollVolume());
    }

    // Whether the pointer sounds at all, which is the balance's own answer: a player who has pulled every
    // level down to nothing has asked for a panel that is quiet under the pointer, and a look states that
    // by naming no role rather than by playing one at zero. Named per moment and not per kind because one
    // role covers every arrival - silencing one kind alone is a balance the scheme has no way to hold.
    private static StarsectorUiSound resolveArrivalSound(PointerArrivalVolumes arrivalVolumes) {
        return isEveryArrivalSilenced(arrivalVolumes) ? null : StarsectorUiSound.BUTTON_MOUSEOVER;
    }

    // Silence read off the levels rather than off a switch of its own, so the sliders are the only place
    // the panel's volume is stated. Compared against the floor rather than for equality with it: the
    // levels arrive from a stored double narrowed to a float, and a slider parked at its own minimum has
    // no business turning on how that landed.
    private static boolean isEveryArrivalSilenced(PointerArrivalVolumes arrivalVolumes) {
        return arrivalVolumes.panelChromeVolume() <= UiSoundCue.SILENT_VOLUME
            && arrivalVolumes.singleOptionControlVolume() <= UiSoundCue.SILENT_VOLUME
            && arrivalVolumes.listedItemVolume() <= UiSoundCue.SILENT_VOLUME;
    }

    // The accent steps the panel is ruled and framed in, under whichever palette the player pointed it
    // at. One place
    // the choice is turned into shades, so the widget style and the tab row cannot come to read it
    // differently - but two call sites, since a tab style is also built for the layout pass, where no
    // panel style exists to hand one down from. Both land in the same frame on a live read of one stored
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

    // How a row marks the key it answers to, which the chrome decides for the same reason it decides
    // the palette and the face: the vanilla tabs a strip copies underline their key and the vanilla
    // buttons a raised row copies leave theirs bare. Resolved from the chrome rather than handed in
    // beside it, so a row marked one way while wearing the other chrome cannot be composed at all - the
    // mismatch the styled hotkey exists to avoid is unrepresentable rather than merely avoided.
    private static HotkeyStyle resolveHotkeyStyle(TabChrome chrome) {
        return switch (chrome) {
            case STRIP -> HotkeyStyle.createUnderlined();
            case RAISED_BUTTON -> HotkeyStyle.createPlain();
        };
    }

    // Whether a row's labels stand inside a dark ring of themselves, which follows what the text is drawn
    // over rather than what it is drawn in: the buttons stand over whatever the visor is showing, so their
    // strokes want an edge to sit against, where the strip's tabs are opaque surfaces of their own and its
    // labels already have a fill of known shade behind them. Both rows are lettered in hard-edged atlases,
    // so the face is not what parts them here.
    private static TextHalo resolveTextHalo(TabChrome chrome) {
        return switch (chrome) {
            case STRIP -> TextHalo.NONE;
            case RAISED_BUTTON -> TextHalo.createBlackHairline();
        };
    }

    // The box a chrome stands its tabs in, following the chrome for the same reason its palette and face
    // do. A strip copies the sector map's Sector/System row, whose tabs are a fixed box the label centres
    // in rather than one grown to fit it; a raised row copies the intel screen's buttons, which are laid
    // to the row the layout measured and take their channel from inside their own tab.
    private static TabBox resolveTabBox(TabChrome chrome) {
        return switch (chrome) {
            case STRIP -> new TabBox(STRIP_TAB_WIDTH, STRIP_TAB_HEIGHT, STRIP_TAB_GAP);
            case RAISED_BUTTON -> TabBox.SNAPPED;
        };
    }

    // A tab style over the shared paint: the chrome's own palette - its per-state fills and its
    // interaction lifts - resolved live so it tracks a restyled install, plus the box it stands its tabs
    // in, the face, the ring around it, and the hotkey convention that chrome carries. All of them follow
    // from the chrome rather than travelling beside it, since each is a convention of the vanilla control
    // the row imitates and not a free choice; the band height alone is the host's, being the one
    // dimension the two screens genuinely set apart.
    private static TabStyle composeTabStyle(TabChrome chrome, float headerBandHeight) {
        return new TabStyle(
            chrome,
            headerBandHeight,
            resolveTabBox(chrome),
            composeTabPalette(chrome, resolveAccentColours()),
            resolveHotkeyStyle(chrome),
            resolveTabFace(chrome),
            resolveTextHalo(chrome),
            resolvePixelFaceSharpness(chrome));
    }

    // How hard a row's labels read. Both chromes are lettered in atlases that carry no antialiasing of
    // their own, so both answer this - and they answer it differently, because each is read against the
    // vanilla chrome it stands beside rather than against some one right amount for the mod. Read live so
    // the dial answers while the panel is on screen.
    private static float resolvePixelFaceSharpness(TabChrome chrome) {
        return switch (chrome) {
            case STRIP -> (float) KmuMapSidebarSettings.getMapSidebarPixelFontSharpness();
            case RAISED_BUTTON -> (float) KmuMapSidebarSettings.getIntelSidebarPixelFontSharpness();
        };
    }

    // The sidebar's look built fresh from the live colours, framed by the given convention: a black body
    // backdrop, the colour scheme's accent steps for the controls, the wash the pointer lifts one of those
    // controls by and the light a press adds over it, the insignia body face, the given tab
    // style, the collapse handle's chevron shades for the colour the player picked, and the vanilla
    // button sounds its controls answer by at the levels the player set them to. Everything but the
    // framing is shared by every screen the
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
            // The lift the pointer adds to a body control, taken from the same resolved set the controls
            // are ruled in: a hover is more of what the control already wears, so it cannot be a shade the
            // panel names nowhere else.
            ControlHoverWash.createAccentHoverWash(accentColours),
            // The light a pressed control lifts by, off that same set for the same reason - and a
            // channel of its own rather than more wash, since the cell a press lands on is already
            // fully washed by the pointer that pressed it.
            ControlPressLight.createAccentPressLight(accentColours),
            BODY_FONT,
            tabStyle,
            SidebarPalettes.resolveNotchColours(
                KmuMapSidebarSettings.getMapSidebarChevronColour(),
                accentColours.base(),
                accentColours.bright()),
            buildSidebarSoundScheme());
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
