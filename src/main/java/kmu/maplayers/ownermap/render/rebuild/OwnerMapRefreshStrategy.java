package kmu.maplayers.ownermap.render.rebuild;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.SectorWalk;

import java.util.Set;

/**
 * What an owner map does with the systems a change marked between two rebuilds: the rebuild's
 * slot, which a layer fills.
 *
 * <p>{@link #INCREMENTAL} is the default, since every layer can take it without cost: it reads
 * nothing of a layer but the owner source the standing build was resolved by, which every layer
 * states anyway. A layer takes {@link #FULL_REBUILD} when its rule's answer for one system moves
 * with systems nobody marked - a network or reach rule - where re-deriving only the marked systems
 * would draw them wrong.
 *
 * <p>An enum because the set of fillers is closed: a layer picks one of these rather than bringing
 * its own, and each has a name a log line can print.
 *
 * <p>Asked on a frame that owes no rebuild of its own and drained at least one marked system, and
 * only while the standing map can take a fold at all - a production build that ran. A frame owing a
 * rebuild reads the marked systems in that rebuild, and the debug overlay has no draw lists to fold
 * into, so neither reaches the slot.
 */
public enum OwnerMapRefreshStrategy {

    /**
     * The incremental re-derive: the marked systems are re-derived through the owner source the
     * standing build was resolved by, and what that disturbed is redrawn in place.
     */
    INCREMENTAL {

        @Override
        public MarkedSystemsOutcome refreshMarkedSystems(
                SectorWalk walk,
                StandingOwnerMap standingMap,
                Set<SystemKey> markedSystemKeys) {

            IncrementalOwnerRefresh.applyStaleOwnerUpdates(walk, standingMap, markedSystemKeys);

            return MarkedSystemsOutcome.FOLDED;
        }
    },

    /**
     * The full walk: every marked batch owes the next frame a full rebuild, which reads the marked
     * systems afresh with everything else.
     */
    FULL_REBUILD {

        @Override
        public MarkedSystemsOutcome refreshMarkedSystems(
                SectorWalk walk,
                StandingOwnerMap standingMap,
                Set<SystemKey> markedSystemKeys) {

            return MarkedSystemsOutcome.REBUILD_OWED;
        }
    };

    /**
     * Brings the standing map in step with the systems marked since the last frame, or leaves them
     * for a rebuild.
     *
     * @param walk             a walk of the sector opened for this batch by the cache that drained it,
     *                         under the visibility rules the standing cells were cut under - see
     *                         {@link OwnerMapCache}
     * @param standingMap      the map on screen, which a fold edits in place
     * @param markedSystemKeys the systems marked stale, already drained from the board - so a batch
     *                         this does not fold is one only the outcome remembers
     * @return whether the batch was folded or the next frame owes a rebuild
     */
    public abstract MarkedSystemsOutcome refreshMarkedSystems(
        SectorWalk walk,
        StandingOwnerMap standingMap,
        Set<SystemKey> markedSystemKeys);
}
