package kmu.maplayers.politicalmap.base.render;

import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * What a batch of holder flips disturbed: the cells that must re-shape, and the factions whose
 * territories must rebuild.
 *
 * <p>The two are one fact rather than two sets filled beside each other. Every flip writes both
 * - the ring of cells around it re-shapes because their shared edges changed class, and both
 * sides of the transfer rebuild because their outlines moved - so recording a flip into one and
 * not the other leaves a redraw half done, which shows as a border drawn down one side only or a
 * territory still holding the shape it lost. Recorded together, neither half can be the one
 * somebody forgot.
 *
 * <p>Accumulated over the whole batch rather than per flip, because two adjacent systems
 * flipping in one frame disturb overlapping rings and each cell is worth re-shaping once.
 */
final class HolderFlipDisturbance {

    // The factions whose territory outlines this batch moved: both sides of every transfer.
    // Insertion-ordered so the rebuild below runs in a fixed order rather than a hash's.
    private final Set<String> affectedFactionIds = new LinkedHashSet<>();

    // The cells this batch obliges to re-shape: each flipped system and every neighbour whose
    // shared edge flipped between a same-faction seam and a national border.
    private final Set<String> cellIdsToReshape = new LinkedHashSet<>();

    /** @return the factions whose territory outlines moved, in first-recorded order */
    Set<String> getAffectedFactionIds() {
        return Collections.unmodifiableSet(affectedFactionIds);
    }

    /** @return the cells that must re-shape against the updated holders, in first-recorded order */
    Set<String> getCellIdsToReshape() {
        return Collections.unmodifiableSet(cellIdsToReshape);
    }

    /**
     * Whether this batch disturbed anything at all. Read off the factions because every flip
     * names at least one - a system that changed hands either lost a holder, gained one, or
     * both - so a batch with no faction to rebuild had no flip in it, and the whole redraw below
     * the re-derive is skipped.
     *
     * @return whether any flip was recorded
     */
    boolean hasFlips() {
        return !affectedFactionIds.isEmpty();
    }

    /**
     * Records one system changing hands: the cells its flip obliges to re-shape, and the
     * factions on either side of the transfer. Either holder may be absent - a system taking its
     * first colony has no old one, a decivilised one has no new one - and only the sides that
     * exist have a territory to rebuild.
     *
     * @param systemId            the system that changed hands, whose own cell re-shapes
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
        cellIdsToReshape.add(systemId);
        cellIdsToReshape.addAll(neighbourSystemIds);
    }
}
