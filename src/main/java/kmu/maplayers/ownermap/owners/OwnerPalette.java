package kmu.maplayers.ownermap.owners;

import java.awt.Color;

/**
 * The two shades an owner-painted cell picks its elements' colours from: an owner's own pair, or
 * the stand-in pair a cell nobody owns draws in.
 *
 * <p>Named by slot rather than by element - the player points each element (fill, outer border,
 * inner seam) at one slot or the other through the settings - so the pair stays neutral about
 * which element uses which. It names nothing about where the shades came from either: a faction's
 * authored pair is one source of it, and a layer painting owners that are not factions brings
 * shades of its own. That is why the tier carries this type rather than a faction's palette.
 *
 * @param primaryColour   the bright slot
 * @param secondaryColour the dark slot
 */
public record OwnerPalette(
    Color primaryColour,
    Color secondaryColour) {
}
