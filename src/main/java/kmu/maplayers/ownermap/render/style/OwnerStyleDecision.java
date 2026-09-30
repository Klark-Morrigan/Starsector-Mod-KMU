package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.MapStyleCategory;

/**
 * The theme-free style decision the fill and label paths share: which category an owner draws in,
 * and its per-owner adjustment. Free of the concrete {@link CategoryStyle} so the label path - which
 * needs only the category to pick a name style, not a resolved bundle - reads the very same call as
 * the fills.
 *
 * @param category   the category the owner draws in, one of those its layer declares
 * @param adjustment the mute and desaturation applied over that category's style
 */
public record OwnerStyleDecision(
    MapStyleCategory category,
    ElementStyleAdjustment adjustment) {
}
