package kmu.maplayers.base.sidebar.style;

import kmlib.colour.Colours;
import kmlib.starsector.ui.colour.AccentColours;
import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.render.gl.style.NotchColours;

import kmu.settings.NotchChevronColourChoice;
import kmu.settings.SidebarColourSchemeChoice;

import java.awt.Color;

/**
 * Turns the sidebar's player colour choices into the concrete shades the widget library paints with.
 * Kept apart from the renderer so the "which colour does this choice mean" rules are a pure lookup a
 * test can pin, leaving the renderer to the wiring that needs a live GL context and a live screen.
 */
public final class SidebarPalettes {

    // How much of its base each channel of the neutral scheme's dark step keeps. That scheme is the one
    // with no engine shade to take: the fixed palette's dark role is the button teal, and a scheme whose
    // point is dropping the accent hue cannot take a tinted shade, so it steps its own grey down instead.
    // The depth is roughly where the two tinted schemes' darks sit below their bases, so the three
    // schemes recede by the same amount; the grey's own translucency rides along untouched, a dark step
    // in this family being a shade drawn over live content rather than a surface of its own.
    private static final float GREY_DARK_SCALE = 0.3f;

    // Resolves only; never instantiated.
    private SidebarPalettes() {
    }

    /**
     * The accent steps the panel's controls and its frame are drawn in - the recessive shade a frame and
     * a button interior take, the wash-and-label shade, and the brighter step a tick has to read against
     * - for the player's colour scheme choice.
     *
     * <p>Each scheme is three steps of one palette rather than three colours chosen apart, because the
     * set has to stay a set: a bright step that did not sit above its base would leave a checkbox's tick
     * indistinguishable from the chrome it is drawn on, and a dark step that did not sit below it would
     * stop a frame receding behind what it encloses. The UI palette's steps are the engine's own button
     * roles, which is what the engine builds its own controls from, and the stock install gives them the
     * same values a default player faction gives its own three - so that scheme is not a new look on an
     * unmodded game, only one that stops moving when a faction's colours do.
     *
     * @param choice which palette the player pointed the panel at
     * @return the dark, base, and bright accents the panel takes
     */
    public static AccentColours resolveAccentColours(SidebarColourSchemeChoice choice) {

        return switch (choice) {
            case UI_PALETTE -> new AccentColours(
                StarsectorUiColour.VANILLA_BUTTON_BG_DARK.resolve(),
                StarsectorUiColour.VANILLA_BUTTON_TEXT.resolve(),
                StarsectorUiColour.VANILLA_LIGHT_HIGHLIGHT.resolve());
            case CHROME_GREY -> {
                var grey = StarsectorUiColour.VANILLA_GRAY.resolve();
                yield new AccentColours(
                    Colours.darken(grey, GREY_DARK_SCALE),
                    grey,
                    StarsectorUiColour.VANILLA_TEXT.resolve());
            }
            case PLAYER_FACTION -> new AccentColours(
                StarsectorUiColour.VANILLA_PLAYER_DARK.resolve(),
                StarsectorUiColour.VANILLA_PLAYER_BASE.resolve(),
                StarsectorUiColour.VANILLA_PLAYER_BRIGHT.resolve());
        };
    }

    /**
     * The two shades the collapse handle's chevron draws in - at rest and under the pointer - for the
     * player's chevron colour choice.
     *
     * <p>The vanilla highlight gold answers hover with the notch's own accent wash alone, so it holds one
     * shade across both states: gold has no brighter sibling in the palette, and swapping to a different
     * hue on hover would read as the cue changing meaning rather than lighting up. The panel-accent choice
     * has a brighter sibling to step up to, so it brightens the glyph the way the panel's other accented
     * chrome does.
     *
     * @param choice       which colour the player pointed the chevron at
     * @param accent       the panel's own accent, taken when the choice follows the panel
     * @param brightAccent the panel's brighter accent, taken on hover when the choice follows the panel
     * @return the handle's resting and hovered chevron shades
     */
    public static NotchColours resolveNotchColours(
            NotchChevronColourChoice choice,
            Color accent,
            Color brightAccent) {

        return switch (choice) {
            case GOLD -> {
                var gold = StarsectorUiColour.VANILLA_HIGHLIGHT_GOLD.resolve();
                yield new NotchColours(gold, gold);
            }
            case PANEL_ACCENT -> new NotchColours(accent, brightAccent);
        };
    }
}
