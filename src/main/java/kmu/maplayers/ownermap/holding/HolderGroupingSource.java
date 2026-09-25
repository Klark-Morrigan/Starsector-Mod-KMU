package kmu.maplayers.ownermap.holding;

/**
 * Source port for a {@link HolderGrouping} - what a caller holds when it needs the grouping the
 * sector is under at the moment it asks, rather than one fixed when the caller was built.
 *
 * <p>A grouping is a reading of live groups, and groups form and dissolve while the game runs. A
 * caller that outlives a single read and held a grouping instead would go on answering off groups
 * the sector left behind. Holding this makes that impossible: there is no grouping
 * to keep, only the means of taking one when there is something to take it for.
 *
 * <p>A port rather than a direct call on whatever supplies the groups, for the usual reason - a
 * caller depending on this can be handed a grouping with no running game behind it, and the mod the
 * groups come from stays named where the binding is made rather than where the grouping is read.
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
