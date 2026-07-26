package kmu.maplayers.base.sidebar.style;

import kmlib.starsector.ui.color.StarsectorUiColor;
import kmlib.starsector.ui.render.gl.NotchColors;

import kmu.settings.NotchChevronColorChoice;

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
    public static NotchColors resolveNotchColors(
            NotchChevronColorChoice choice,
            Color accent,
            Color brightAccent) {

        return switch (choice) {
            case GOLD -> {
                var gold = StarsectorUiColor.VANILLA_HIGHLIGHT_GOLD.resolve();
                yield new NotchColors(gold, gold);
            }
            case PANEL_ACCENT -> new NotchColors(accent, brightAccent);
        };
    }
}
