package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.colour.StarsectorUiColour;
import kmlib.starsector.ui.render.gl.style.AccentColours;
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

    // Resolves only; never instantiated.
    private SidebarPalettes() {
    }

    /**
     * The accent pair the panel's controls are drawn in - the wash-and-label shade and the brighter step
     * a tick has to read against - for the player's colour scheme choice.
     *
     * <p>Each scheme is two steps of one palette rather than two colours chosen apart, because the pair
     * has to stay a pair: a bright step that did not sit above its base would leave a checkbox's tick
     * indistinguishable from the chrome it is drawn on. The UI palette's pair is the engine's own button
     * roles, and the stock install gives them the same values a default player faction gives its base and
     * bright - so that scheme is not a new look on an unmodded game, only one that stops moving when a
     * faction's colours do.
     *
     * @param choice which palette the player pointed the panel at
     * @return the base and bright accents the panel's controls take
     */
    public static AccentColours resolveAccentColours(SidebarColourSchemeChoice choice) {

        return switch (choice) {
            case UI_PALETTE -> new AccentColours(
                StarsectorUiColour.VANILLA_BUTTON_TEXT.resolve(),
                StarsectorUiColour.VANILLA_LIGHT_HIGHLIGHT.resolve());
            case CHROME_GREY -> new AccentColours(
                StarsectorUiColour.VANILLA_GRAY.resolve(),
                StarsectorUiColour.VANILLA_TEXT.resolve());
            case PLAYER_FACTION -> new AccentColours(
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
