package kmu.maplayers.ownermap.render.rebuild;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.SectorWalk;

import java.util.Set;

/**
 * What an owner map does with the systems a change marked between two rebuilds: the rebuild's
 * slot, which a layer fills.
 *
 * <p>A slot because the right answer depends on what the layer's owner rule costs. A rule cheap to
 * run over the whole sector loses nothing by rebuilding, so {@link #FULL_REBUILD} is the default: it
 * folds nothing and leaves every marked batch to the full walk. A rule dear enough that one
 * system's change should not walk the sector takes {@link #INCREMENTAL}, which re-derives only the
 * marked systems and redraws what they disturbed.
 *
 * <p>Asked on a frame that owes no rebuild of its own and drained at least one marked system, and
 * only while the standing map can take a fold at all - a production build that ran. A frame owing a
 * rebuild reads the marked systems in that rebuild, and the debug overlay has no draw lists to fold
 * into, so neither reaches the slot.
 */
@FunctionalInterface
public interface OwnerMapRefreshStrategy {

    /**
     * The full walk: every marked batch owes the next frame a full rebuild, which reads the marked
     * systems afresh with everything else.
     */
    OwnerMapRefreshStrategy FULL_REBUILD = (walk, standingMap, markedSystemKeys) -> MarkedSystemsOutcome.REBUILD_OWED;

    /**
     * The incremental re-derive: the marked systems are re-derived through the owner source the
     * standing build was resolved by, and what that disturbed is redrawn in place. It reads nothing
     * of a layer but that source, so any layer may take it.
     */
    OwnerMapRefreshStrategy INCREMENTAL = (walk, standingMap, markedSystemKeys) -> {

        IncrementalOwnerRefresh.applyStaleOwnerUpdates(walk, standingMap, markedSystemKeys);

        return MarkedSystemsOutcome.FOLDED;
    };

    /**
     * Brings the standing map in step with the systems marked since the last frame, or leaves them
     * for a rebuild.
     *
     * @param walk             a walk of the sector opened for this batch, under the visibility rules
     *                         the standing cells were cut under
     * @param standingMap      the map on screen, which a fold edits in place
     * @param markedSystemKeys the systems marked stale, already drained from the board - so a batch
     *                         this does not fold is one only the outcome remembers
     * @return whether the batch was folded or the next frame owes a rebuild
     */
    MarkedSystemsOutcome refreshMarkedSystems(
            SectorWalk walk,
            StandingOwnerMap standingMap,
            Set<SystemKey> markedSystemKeys);
}
