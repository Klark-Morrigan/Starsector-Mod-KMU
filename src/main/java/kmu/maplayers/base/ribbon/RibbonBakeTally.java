package kmu.maplayers.base.ribbon;

/**
 * What one bake did with the cells it laid bands for, counted as it went.
 *
 * <p>A cell drawing no band looks the same on the map whatever the reason, and a bake's duration
 * says how long it ran but not where the time went. These counts answer both: how many cells came
 * back banded, how many planned nothing, how many planned a band their ring had no room for, and
 * of the cells that reached for a ring, how many had to walk it and how many found it standing.
 *
 * <p>Only the cells a layer offered a band are counted. A cell the layer refused before planning
 * never reaches the band, so it is the layer's to account for.
 *
 * <p>One per bake call rather than per pass, since the counts describe the call they are written
 * on. Single-threaded, like the bake that fills it.
 */
public final class RibbonBakeTally {

    private int bandedCells;
    private int refusedCells;
    private int reusedRingCells;
    private int tracedRingCells;
    private int unplannedCells;

    /**
     * @return the counts as the call tag a bake writes, one {@code name=count} pair per count
     */
    public String describeTally() {

        return "banded=" + bandedCells
            + " traced=" + tracedRingCells
            + " reused=" + reusedRingCells
            + " unplanned=" + unplannedCells
            + " refusedForRoom=" + refusedCells;
    }

    // Counts a cell whose plan had length, by what its ring made of it: banded, or refused the
    // room to draw what it planned.
    void countLaidCell(CellRibbon ribbon) {

        if (ribbon.isEmpty()) {
            refusedCells++;
        } else {
            bandedCells++;
        }
    }

    // Counts a cell that reached for its ring, by whether any rung of it had to be walked.
    void countRingReach(boolean hasTracedRing) {

        if (hasTracedRing) {
            tracedRingCells++;
        } else {
            reusedRingCells++;
        }
    }

    // Counts a cell whose layer planned it nothing to draw.
    void countUnplannedCell() {
        unplannedCells++;
    }
}
