package kmu.maplayers.ownermap.render.ribbon;

/**
 * What one cell's ring lets a band do with it, as the trace that looks for room already knows.
 *
 * <p>Three outcomes on one axis - what happens to a band on this ring - rather than two facts
 * crossed. Which inset the path came back at and whether a band may use it are the same question
 * asked once: a cell too narrow for the authored inset gets a band only where the player has said
 * a planned band is always drawn, so the inset the trace fell to and the answer the map gives
 * cannot be read apart.
 *
 * <p>Only ever read by the diagnostic overlay. The band pass asks for a path and lays on whatever
 * it is handed, which is what keeps the outcome a thing the overlay reports rather than a branch
 * production takes.
 */
public enum RibbonPathVerdict {

    /** The ring held the authored inset, so a band runs clear of the border by the whole pad. */
    LAID_AT_PAD,

    /**
     * The ring was too narrow for the pad and the half width together, so the pad was given up to
     * put a band on the cell at all - its near edge sitting on the border rather than clear of it.
     */
    LAID_UNPADDED,

    /**
     * No band lies on this ring: either the pad would have had to be given up and the player has
     * that off, or the cell is narrower than the band is wide, where there is no shallower trace
     * left to fall back to.
     */
    REFUSED
}
