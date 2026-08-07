package kmu.maplayers.base.tooltip;

/**
 * What a line's {@linkplain CellTooltipEntryLine#indexText place} in an ordering did for it: nothing
 * so far, or the winning or losing of whatever that ordering settles.
 *
 * <p>A place is ordinarily a bare identifier - which of these is which - and reads as quietly as any
 * other working. It stops being one exactly when two lines cannot be told apart by anything else and
 * the ordering has to decide between them: at that moment the number is no longer a label but the
 * reason one line beat another, and a reader scanning for why wants to find it rather than parse it.
 *
 * <p>Stated as an outcome rather than as a colour so that which shade says "this won" stays with the
 * {@linkplain CellTooltipRows line vocabulary}, and a layer states only what happened. Two layers
 * marking a decided ordering would otherwise eventually mark it in two different greens.
 */
public enum CellTooltipIndexOutcome {

    /** Nothing turned on the place: it identifies the line and says no more. */
    UNCONTESTED,

    /** The place decided a contest in this line's favour - it was reached first. */
    WON,

    /** The place decided a contest against this line - something equal to it was reached first. */
    LOST
}
