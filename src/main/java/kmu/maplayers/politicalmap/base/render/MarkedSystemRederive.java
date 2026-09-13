package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
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
     * @param territories      the built map whose occupancy is folded
     * @param geometryCache    the cells, read for a flipped system's neighbours
     * @param pass             the batch's one reading of the sector, shared by all three reads
     * @param markedSystemKeys the systems to re-derive, each of which draws a cell
     * @return what the batch disturbed: the cells to redraw, and the factions to rebuild
     */
    static StalePoliticsDisturbance rederiveMarkedSystems(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            DominancePass pass,
            Set<SystemKey> markedSystemKeys) {

        // Off the batch's own reading rather than a traversal opened here: every other read below
        // goes through that pass, and a second traversal for the systems alone is what the bound on
        // a batch counts against it. By key rather than by ID, so a marked system that shares an ID
        // with another is re-derived as itself rather than as whichever of them comes first.
        var systemByKey = pass.holding().sectorIndex().readSystemsByKey();
        var disturbance = new StalePoliticsDisturbance();

        for (var systemKey : markedSystemKeys) {

            // Null for a system the sector no longer lists, which each read below answers for
            // itself - the key stays the address whether or not a system still stands behind it.
            var markedSystem = new MarkedSystem(systemKey, systemByKey.get(systemKey));

            rederiveSystemHolder(territories, geometryCache, pass, markedSystem, disturbance);
            rederiveSystemInhabitation(territories, pass.holding(), markedSystem, disturbance);
        }
        rederiveSpotlitPresence(territories, pass, markedSystemKeys, disturbance);

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
            MarkedSystem marked,
            StalePoliticsDisturbance disturbance) {

        // Re-derived under the pass this batch opened, which carries the grouping the full build
        // resolved this system's holder with, so a single-system refresh lands the same winning
        // bloc the bulk pass would.
        var newHolder = SectorPolitics.resolveDominantHolder(marked.system(), pass);
        var oldHolder = territories.getOccupancy().getHolderBySystemKey().get(marked.systemKey());

        // DominantHolder is a record, so equality covers the faction and its palette: a
        // resize that leaves the same winner leaves the drawing identical.
        if (Objects.equals(oldHolder, newHolder)) {
            return;
        }
        territories.getOccupancy().recordHolderOf(marked.systemKey(), newHolder);
        disturbance.recordFlip(
            marked.systemKey(),
            neighbourSystemKeysOf(geometryCache, marked.systemKey()),
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
    //
    // Handed the batch's reading of the sector rather than the whole pass, on the same terms the
    // presence read below is: whether anybody lives in a system is a question no weighting rule
    // takes part in answering.
    private static void rederiveSystemInhabitation(
            PoliticalMapTerritories territories,
            HolderPass holding,
            MarkedSystem marked,
            StalePoliticsDisturbance disturbance) {

        var isInhabited = PoliticalMapInhabitation.isSystemInhabited(holding, marked.system());

        if (territories.getOccupancy().foldInhabitationOf(marked.systemKey(), isInhabited)) {
            disturbance.recordRestyle(marked.systemKey());
        }
    }

    // Re-reads where the spotlit bloc lives among the marked systems and folds each answer into
    // the live presence set, disturbing the cells whose answer moved.
    //
    // Read for the whole batch at once because the read walks the sector to find its candidates,
    // and off filter it returns before touching an economy at all - so a batch on an unfiltered
    // map pays a call that decides nothing rather than a per-system branch stating the same thing.
    //
    // Handed the batch's reading of the sector rather than the whole pass: presence is a question
    // about who is in a system, which no weighting rule takes part in answering.
    //
    // Asked of the marked systems the updated holders left unheld, as the full build asks it of
    // the unheld inhabited ones: presence is what spares a cell no bloc holds, so a system that
    // has just been given to somebody drops out of the set rather than being carried in it under
    // a holder that draws it anyway.
    private static void rederiveSpotlitPresence(
            PoliticalMapTerritories territories,
            DominancePass pass,
            Set<SystemKey> markedSystemKeys,
            StalePoliticsDisturbance disturbance) {

        var occupancy = territories.getOccupancy();
        var presentSystemKeys = FilteredPolitics.findPresentSystemKeys(
            pass.holding(),
            territories.getBuildInputs().contentInputs().selectedBlocId(),
            occupancy.selectUnheldSystemKeysAmong(markedSystemKeys));

        for (var systemKey : markedSystemKeys) {

            var isPresent = presentSystemKeys.contains(systemKey);

            if (occupancy.foldSpotlitPresenceOf(systemKey, isPresent)) {
                disturbance.recordRestyle(systemKey);
            }
        }
    }

    // The systems whose cell borders this one, read from the adjacency graph. When this
    // system's holder flips, each neighbour's shared edge flips between a same-faction
    // seam and a national border, so every neighbour re-shapes too.
    private static Set<SystemKey> neighbourSystemKeysOf(
            CellGeometryCache geometryCache,
            SystemKey systemKey) {

        var neighbours = new LinkedHashSet<SystemKey>();
        var edges = geometryCache.getCellEdgesByCellKey().get(systemKey);
        if (edges != null) {
            for (var edge : edges) {
                if (edge.target() instanceof EdgeTarget.AcrossSystem acrossSystem) {
                    neighbours.add(acrossSystem.systemKey());
                }
            }
        }
        return neighbours;
    }

    /**
     * One system a batch was told to re-derive: the key it was marked under, and the system that
     * key still resolves to.
     *
     * <p>The two travel together because each read below needs both and neither can be derived from
     * the other here. The key is the address every cell on the drawn map is written under, and it
     * stays the address whether or not a system still stands behind it; the system is null exactly
     * when the sector no longer lists one, which each read answers for itself rather than by
     * skipping the mark - a system that has gone is a change the map has to record.
     *
     * @param systemKey the key the system was marked stale under
     * @param system    the system that key resolves to, or null where the sector no longer lists
     *                  one
     */
    private record MarkedSystem(
        SystemKey systemKey,
        StarSystemAPI system) {
    }
}
