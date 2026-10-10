package kmu.maplayers.ownermap.ribbon;

import kmlib.colour.ColourPair;

/**
 * One owner's stake in one cell's presence ribbon: who it is, the two shades its runs draw
 * in, and how many holdings it has in the cell.
 *
 * <p>The ribbon counts holdings rather than scaling weights, so an owner arrives here as a
 * plain count. A readout of "how many holdings, and whose" is something a reader can check
 * against the map; a summed weight is not, and no weight can be divided back into the count
 * that would be.
 *
 * <p>Which holdings that count stands for is settled before this value is built, by the layer's
 * own source: the rule on {@link RibbonPlan} counts what it is handed and asks nothing about
 * where the number came from. What reaches it per mechanic
 * is the order, so a band cannot mean one thing under one painting mechanic and another under
 * the next.
 *
 * <p>The palette is the owner's own authored pair - bright into the holding segments, dark
 * into every parting, both the ones between its holdings and the divider closing its run
 * where another owner's follows - so a run draws in the same two shades the rest of the map
 * already gives that owner.
 *
 * @param ownerId     the owner this stake belongs to, or a group's owner ID where the view folds
 *                    several owners into one
 * @param palette     the owner's bright and dark shades, the segment and parting colours of
 *                    its run
 * @param marketCount how many holdings the owner has in the cell that the player may be
 *                    shown; an owner holding none is present in nothing the ribbon reports, so
 *                    it neither draws a run nor counts as presence
 */
public record OwnerPresence(
    String ownerId,
    ColourPair palette,
    int marketCount) {

    // An owner holding nothing the player may be shown, which is the count presence starts above.
    private static final int NO_MARKETS = 0;

    /**
     * Whether the owner holds anything a band can report.
     *
     * <p>The one place the rule above is spelled: an owner counted at nothing draws no run, does not
     * contest a cell, and does not keep one from being bare. Held here rather than as a comparison
     * at each reader, since three readers asking it three times is three chances for one of them to
     * come to mean something slightly different by presence.
     *
     * @return true where the owner has at least one holding the player may be shown
     */
    public boolean hasMarkets() {
        return marketCount > NO_MARKETS;
    }
}
