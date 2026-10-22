package kmu.maplayers.base.ribbon;

/**
 * The two insets a cell's band path is traced at, in the order a trace tries them.
 *
 * <p>Named rather than carried as distances, because a traced ring is kept per cell and per inset,
 * and a cell's two rings must be told apart by what each is for: the padded ring is where a band
 * lies on any cell with room for one, the unpadded ring is what a cell with no room for the pad
 * falls to. Which of them a band ends up on is the trace's answer, never the caller's.
 */
public enum RibbonPathInset {

    /** The pad plus the half width, the inset a band is laid at wherever the cell has room. */
    PADDED,

    /** The half width alone, the pad given up, for a cell with no room for it anywhere. */
    UNPADDED;

    /**
     * How far inside the cell's ring a path at this inset runs, under the given sizes.
     *
     * @param style the sizes the band is laid out at
     * @return the inset the band's centreline is traced at
     */
    double computeInsetDistance(RibbonStyle style) {

        return switch (this) {
            case PADDED -> style.computeCentrelineInset();
            case UNPADDED -> style.computeUnpaddedCentrelineInset();
        };
    }
}
