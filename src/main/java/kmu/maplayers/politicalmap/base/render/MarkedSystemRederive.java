package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.StarSystems;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Brings the systems a colony event marked back into step with the sector, and reports what that
 * obliges the map to redraw.
 *
 * <p>Three facts settle how a marked system's cell draws, and all three are re-derived here: who
 * holds it, whether anything stands in it, and - while a bloc is spotlighted - whether the pick is
 * one of the things standing in it. They move independently, so each is read on its own account: a
 * system this layer's holding never accounted for can gain or lose its last colony with no holder
 * moving anywhere, which is the unclaimed pirate haven on the claims layer and the case a full
 * rebuild is otherwise the only cure for.
 *
 * <p>Every one of them is answered off the batch's single pass, so a marked system is walked once
 * however many questions are asked about it, and all three are answered about one reading of the
 * sector. Written through the occupancy's folds rather than into collections of its own, so a fact
 * cannot be brought up to date without the redraw it owes being recorded beside it.
 *
 * <p>Nothing here draws. What each change costs is the caller's, and differs by which fact moved -
 * a flip re-shapes a ring of cells and rebuilds two territories, where the other two are cell-local
 * - so what this reports is the disturbance rather than the work.
 */
final class MarkedSystemRederive {

    // Re-derives only; never instantiated.
    private MarkedSystemRederive() {
    }

    /**
     * Re-derives every marked system, folding what changed into one record of what the batch
     * disturbed.
     *
     * <p>What the batch disturbed is accumulated as one value rather than sets filled side by
     * side: every flip owes both a re-shape and a territory rebuild, so recording one without the
     * other is exactly the half-done redraw this has to avoid.
     *
     * <p>Every marked system is re-derived before the caller redraws anything, so the redraw reads
     * a fully updated holder map even when two adjacent systems flipped in one batch.
     *
     * @param territories     the built map whose occupancy is folded
     * @param geometryCache   the cells, read for a flipped system's neighbours
     * @param pass            the batch's one reading of the sector, shared by all three reads
     * @param markedSystemIds the systems to re-derive, each of which draws a cell
     * @return what the batch disturbed: the cells to redraw, and the factions to rebuild
     */
    static StalePoliticsDisturbance rederiveMarkedSystems(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            DominancePass pass,
            Set<String> markedSystemIds) {

        var systemById = StarSystems.indexById(pass.sector());
        var disturbance = new StalePoliticsDisturbance();

        for (var systemId : markedSystemIds) {

            var system = systemById.get(systemId);

            rederiveSystemHolder(territories, geometryCache, pass, system, systemId, disturbance);
            rederiveSystemInhabitation(territories, pass.sector(), system, systemId, disturbance);
        }
        rederiveSpotlitPresence(territories, pass, markedSystemIds, disturbance);

        return disturbance;
    }

    // Re-derives one system's holder and, when it actually changed, records the flip against
    // what this batch disturbed: the cells to re-shape (the system and its neighbours, whose
    // edge against it flips between a same-faction seam and a national border) and the factions
    // whose territory must rebuild (the old and the new holder).
    private static void rederiveSystemHolder(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            DominancePass pass,
            StarSystemAPI system,
            String systemId,
            StalePoliticsDisturbance disturbance) {

        // Re-derived under the pass this batch opened, which carries the grouping the full build
        // resolved this system's holder with, so a single-system refresh lands the same winning
        // bloc the bulk pass would.
        var newHolder = SectorPolitics.resolveDominantHolder(system, pass);
        var oldHolder = territories.getHolderBySystemId().get(systemId);

        // DominantHolder is a record, so equality covers the faction and its palette: a
        // resize that leaves the same winner leaves the drawing identical.
        if (Objects.equals(oldHolder, newHolder)) {
            return;
        }
        territories.getOccupancy().recordHolderOf(systemId, newHolder);
        disturbance.recordFlip(
            systemId,
            neighbourSystemIdsOf(geometryCache, systemId),
            oldHolder,
            newHolder);
    }

    // Re-derives whether anything still stands in one marked system and folds the answer into the
    // live set the cells are classified from, disturbing its own cell where it moved.
    //
    // Independent of the holder above, and that is the whole of why it is here. The inhabited set
    // is scanned once where a rebuild begins, so between rebuilds it goes stale exactly over the
    // systems the events have already moved - and on a layer whose holding cannot account for a
    // system, its cell is the only surface that reports the change at all.
    private static void rederiveSystemInhabitation(
            PoliticalMapTerritories territories,
            SectorAPI sector,
            StarSystemAPI system,
            String systemId,
            StalePoliticsDisturbance disturbance) {

        var isInhabited = PoliticalMapInhabitation.isSystemInhabited(sector, system);

        if (territories.getOccupancy().foldInhabitationOf(systemId, isInhabited)) {
            disturbance.recordRestyle(systemId);
        }
    }

    // Re-reads where the spotlit bloc lives among the marked systems and folds each answer into
    // the live presence set, disturbing the cells whose answer moved.
    //
    // Read for the whole batch at once because the read walks the sector to find its candidates,
    // and off filter it returns before touching an economy at all - so a batch on an unfiltered
    // map pays a call that decides nothing rather than a per-system branch stating the same thing.
    //
    // Asked of the marked systems the updated holders left unheld, as the full build asks it of
    // the unheld inhabited ones: presence is what spares a cell no bloc holds, so a system that
    // has just been given to somebody drops out of the set rather than being carried in it under
    // a holder that draws it anyway.
    private static void rederiveSpotlitPresence(
            PoliticalMapTerritories territories,
            DominancePass pass,
            Set<String> markedSystemIds,
            StalePoliticsDisturbance disturbance) {

        var occupancy = territories.getOccupancy();
        var presentSystemIds = FilteredPolitics.findPresentSystemIds(
            pass,
            territories.getSelectedBlocId(),
            occupancy.selectUnheldSystemIdsAmong(markedSystemIds));

        for (var systemId : markedSystemIds) {

            var isPresent = presentSystemIds.contains(systemId);

            if (occupancy.foldSpotlitPresenceOf(systemId, isPresent)) {
                disturbance.recordRestyle(systemId);
            }
        }
    }

    // The systems whose cell borders this one, read from the adjacency graph. When this
    // system's holder flips, each neighbour's shared edge flips between a same-faction
    // seam and a national border, so every neighbour re-shapes too.
    private static Set<String> neighbourSystemIdsOf(
            CellGeometryCache geometryCache,
            String systemId) {

        var neighbours = new LinkedHashSet<String>();
        var edges = geometryCache.getCellEdgesByCellId().get(systemId);
        if (edges != null) {
            for (var edge : edges) {
                if (edge.target() instanceof EdgeTarget.AcrossSystem acrossSystem) {
                    neighbours.add(acrossSystem.systemId());
                }
            }
        }
        return neighbours;
    }
}
