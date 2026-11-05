package kmu.maplayers.ownermap.ribbon;

import kmlib.colour.ColourPair;

import java.util.List;

/**
 * One owner's share of one cell's presence ribbon: who it is, and the two shades each of its
 * stakes in the cell draws in.
 *
 * <p>The ribbon counts stakes rather than scaling weights, so an owner arrives here as one shade
 * pair per stake. A readout of "how many stakes, and whose" is something a reader can check
 * against the map; a summed weight is not, and no weight can be divided back into the count
 * that would be.
 *
 * <p>Which stakes those are is settled before this value is built, by the layer's own source: the
 * rule on {@link OwnerRibbonRuns} lays what it is handed and asks nothing about where it came from.
 * What reaches it per mechanic is the order, so a band cannot mean one thing under one painting
 * mechanic and another under the next.
 *
 * <p>A pair per stake rather than one per owner, because what a stake is coloured by need not be
 * what decides the owner. A layer folding several members into one owner may colour each member's
 * stakes apart inside the one run. A layer colouring by the owner alone hands the same pair for
 * every stake, which draws exactly as one pair for the whole run would.
 *
 * @param ownerId       the owner this share belongs to, or a group's owner ID where the view folds
 *                      several owners into one
 * @param stakePalettes one bright and dark pair per stake the player may be shown, in draw order:
 *                      the bright shade is the stake's segment and the dark one every parting that
 *                      stake goes out on; an owner with none is present in nothing the ribbon
 *                      reports, so it neither draws a run nor counts as presence
 */
public record OwnerPresence(
    String ownerId,
    List<ColourPair> stakePalettes) {

    /**
     * Takes an immutable copy of the pairs, so a presence handed to the rule cannot change its
     * stakes under it.
     */
    public OwnerPresence {
        stakePalettes = List.copyOf(stakePalettes);
    }

    /**
     * Whether the owner holds anything a band can report.
     *
     * <p>The one place the rule above is spelled: an owner holding no stake draws no run, does not
     * contest a cell, and does not keep one from being bare. Held here rather than as a test at each
     * reader, since three readers asking it three times is three chances for one of them to come to
     * mean something slightly different by presence.
     *
     * @return true where the owner has at least one stake the player may be shown
     */
    public boolean hasStakes() {
        return !stakePalettes.isEmpty();
    }
}
