package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.settings.FactionPaletteChoice;

/**
 * Which of a bloc's two palette slots an element reads - the political map's
 * {@link ElementPaintSelection}, and the only form of its palette pick a style can hold.
 *
 * <p>A slot rather than a shade: the constants name a position in a palette, not a colour, and
 * the colour they stand for is a different one for every bloc on the map. One theme serves them
 * all, so what a style can hold is the position; the shade is what {@code MapPalettes} hands back
 * once a particular bloc's palette is in hand.
 *
 * <p>Deliberately narrower than {@link FactionPaletteChoice}, which the settings screen needs a
 * third option for ("No color"). A style expresses "paints nothing" by holding no selection at
 * all, so a value meaning that must not exist here: were the settings enum itself the paint, an
 * element could be built holding an explicit no-colour value, which reads as drawable to every
 * caller that tests for a selection and silently paints a cell meant to stay bare. Splitting the
 * two makes that state unconstructible rather than merely discouraged.
 *
 * <p>The settings enum stays the wire format the player's pick is read and stored in; this is
 * what the render pass runs on, and {@link #resolvePaintSelectionOf} is the one crossing between
 * them.
 */
public enum FactionPaletteSlot implements ElementPaintSelection {
    PRIMARY,
    SECONDARY;

    /**
     * The player's pick as a style can hold it: a slot, or no selection at all when the pick is
     * "No color" or has not resolved.
     *
     * <p>Null-tolerant because an unresolved setting and an explicit no-colour choice mean the
     * same thing to a style, and neither should force a guard on the caller. This lives beside
     * the slot rather than on the settings enum so that {@code kmu.settings} stays clear of the
     * render layer - the translation belongs to the side that knows what a slot is for.
     *
     * @param choice the player's pick, or null when the setting did not resolve
     * @return the matching slot, or null for {@link FactionPaletteChoice#NONE} or a null pick
     */
    public static ElementPaintSelection resolvePaintSelectionOf(FactionPaletteChoice choice) {
        if (choice == null) {
            return null;
        }
        return switch (choice) {
            case PRIMARY -> PRIMARY;
            case SECONDARY -> SECONDARY;
            case NONE -> null;
        };
    }
}
