package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;

/**
 * The view-agnostic style decision the fill and label paths share: whether a bloc recedes to
 * the independent style, and its per-bloc adjustment. Free of the concrete {@link CategoryStyle}
 * so the label path - which needs only the boolean, not a resolved style - reads the very same
 * call as the fills.
 */
public record BlocStyleDecision(
    boolean usesIndependentStyle,
    ElementStyleAdjustment adjustment) {
}
