package kmu.maplayers.politicalmap.base.dominance;

/**
 * Source port for a {@link HolderGrouping} - what a caller holds when it needs the grouping the
 * sector is under at the moment it asks, rather than one fixed when the caller was built.
 *
 * <p>A grouping is a reading of a live alliance set, and alliances form and dissolve while the game
 * runs. A caller that outlives a single read and held a grouping instead would go on answering off
 * an alliance set the sector left behind. Holding this makes that impossible: there is no grouping
 * to keep, only the means of taking one when there is something to take it for.
 *
 * <p>A port rather than a direct call on whatever supplies the alliances, for the usual reason - a
 * caller depending on this can be handed a grouping with no running game behind it, and the mod the
 * alliances come from stays named where the binding is made rather than where the grouping is read.
 */
@FunctionalInterface
public interface HolderGroupingSource {

    /**
     * Takes the grouping the sector is under as this call is made.
     *
     * @return the live grouping, which is {@link HolderGrouping#identity()} wherever nothing groups
     *         factions at all
     */
    HolderGrouping resolveGrouping();
}
