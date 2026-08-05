package kmu.maplayers.base.tooltip;

/**
 * How much detail a hover box states: the normal box a layer draws by default, or the richer
 * counterpart a tooltip may define for the same subject.
 *
 * <p>The mode is one global fact rather than a per-tooltip one - whatever is hovered, the mode
 * selects which of its boxes is drawn - so a tooltip with no richer counterpart draws its normal
 * box under either constant rather than failing to draw.
 *
 * <p>Named modes rather than a boolean, so a later third amount of detail is an added constant
 * instead of a flag every reader has to re-read as a tri-state.
 */
public enum HoverTooltipDetailMode {
    NORMAL,
    EXPANDED
}
