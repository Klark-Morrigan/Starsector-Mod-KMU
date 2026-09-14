package kmu.maplayers.base.chrome.arrange;

/**
 * What a press on one of an arranging row's controls means.
 *
 * <p>An enum carried as a vanilla button's own data rather than a string, so a press arriving with
 * something else on it - another mod's button, or one left over from a column already rebuilt - is not
 * mistaken for one of these.
 *
 * <p>Its own type rather than a member of either half it passes between: the column that builds the
 * buttons puts it on them, and the dialog that owns the editor acts on it, so a nested type would make
 * one of those two the other's owner.
 */
enum ArrangementRowAction {

    /** Swap this row with the one above it. */
    MOVE_UP,

    /** Swap this row with the one below it. */
    MOVE_DOWN,

    /** Take this row's tab off the bar, or put it back on. */
    TOGGLE_SHOWN
}
