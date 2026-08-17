package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a drained batch of stale systems disturbed: the cells that must redraw, and the factions
 * whose territories must rebuild.
 *
 * <p>Two kinds of change land here. A <em>flip</em> - a system changing hands - writes both sets
 * at once, and they are one fact rather than two filled beside each other: the ring of cells
 * around it redraws because their shared edges changed class, and both sides of the transfer
 * rebuild because their outlines moved, so recording a flip into one and not the other leaves a
 * redraw half done, which shows as a border drawn down one side only or a territory still holding
 * the shape it lost. A <em>restyle</em> - what a system is settled from moving without its holder
 * following - writes the cell alone: no seam moved, so no neighbour and no territory is owed
 * anything.
 *
 * <p>Accumulated over the whole batch rather than per change, because two adjacent systems
 * flipping in one frame disturb overlapping rings and each cell is worth redrawing once.
 */
final class StalePoliticsDisturbance {

    // The factions whose territory outlines this batch moved: both sides of every transfer.
    // Insertion-ordered so the rebuild below runs in a fixed order rather than a hash's.
    private final Set<String> affectedFactionIds = new LinkedHashSet<>();

    // The cells this batch obliges to redraw: each flipped system and every neighbour whose
    // shared edge flipped between a same-faction seam and a national border, plus each system
    // whose cell draws in a different style than it did.
    private final Set<String> cellIdsToRedraw = new LinkedHashSet<>();

    /** @return the factions whose territory outlines moved, in first-recorded order */
    Set<String> getAffectedFactionIds() {
        return Collections.unmodifiableSet(affectedFactionIds);
    }

    /** @return the cells that must redraw against the updated holders, in first-recorded order */
    Set<String> getCellIdsToRedraw() {
        return Collections.unmodifiableSet(cellIdsToRedraw);
    }

    /**
     * Whether this batch moved any holding at all. Read off the factions because every flip
     * names at least one - a system that changed hands either lost a holder, gained one, or
     * both - so a batch with no faction to rebuild had no flip in it, and the clustering,
     * territory and name work below the cell redraw is skipped.
     *
     * @return whether any flip was recorded
     */
    boolean hasFlips() {
        return !affectedFactionIds.isEmpty();
    }

    /**
     * Records one system changing hands: the cells its flip obliges to redraw, and the
     * factions on either side of the transfer. Either holder may be absent - a system taking its
     * first colony has no old one, a decivilised one has no new one - and only the sides that
     * exist have a territory to rebuild.
     *
     * @param systemId            the system that changed hands, whose own cell redraws
     * @param neighbourSystemIds  the systems whose cells share an edge with it
     * @param oldHolder           who held it before, or null if nobody did
     * @param newHolder           who holds it now, or null if nobody does
     */
    void recordFlip(
            String systemId,
            Collection<String> neighbourSystemIds,
            DominantHolder oldHolder,
            DominantHolder newHolder) {

        if (oldHolder != null) {
            affectedFactionIds.add(oldHolder.factionId());
        }
        if (newHolder != null) {
            affectedFactionIds.add(newHolder.factionId());
        }
        cellIdsToRedraw.add(systemId);
        cellIdsToRedraw.addAll(neighbourSystemIds);
    }

    /**
     * Records one system's cell drawing in a different style than it was: what stands in it
     * changed, or the spotlit bloc arrived in it or left it, while its holder stayed as it was.
     *
     * <p>Its own cell and nothing more. A cell's shape is settled by which of its edges are
     * same-owner seams and neither of those facts moves one, so no neighbour changes shape, no
     * bloc's outline moves, and the batch owes no rebuild on this system's account.
     *
     * @param systemId the system whose cell must redraw
     */
    void recordRestyle(String systemId) {
        cellIdsToRedraw.add(systemId);
    }
}
