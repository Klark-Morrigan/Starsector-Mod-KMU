package kmu.maplayers.politicalmap.base.render.labels.anchor.specifications;

import kmu.settings.FactionPaletteChoice;

/**
 * How one owner group's cluster names are coloured and faded: the outer-border palette
 * choice the name inherits its colour from, and the opacity that group's names draw at.
 *
 * <p>Held once per group (core factions, independent space) so the styling picks a whole
 * group as a unit rather than reading a colour and an opacity that could drift apart.
 *
 * @param outerColor  the outer-border palette choice this group's name inherits its colour
 *                    from, resolved against the owner's palette
 * @param nameOpacity the opacity this group's names draw at, 0..1, fading only this group's
 *                    names
 */
public record NameGroupStyle(
        FactionPaletteChoice outerColor,
        double nameOpacity) {
}
