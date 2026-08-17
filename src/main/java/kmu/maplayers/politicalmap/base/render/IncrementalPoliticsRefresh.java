package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.StarSystems;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterNameDisturbance;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterLabelStylingSnapshot;
import kmu.maplayers.politicalmap.base.render.ribbon.CellRibbonsBaker;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritoryBuilder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.StyledCellBuilder;

import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Folds the per-system changes a colony resize marked into the standing draw lists,
 * re-deriving and redrawing only the affected systems and their neighbours rather than
 * rebuilding the whole map.
 *
 * <p>Three facts settle how a marked system's cell draws, and all three are re-derived here: who
 * holds it, whether anything stands in it, and - while a bloc is spotlighted - whether the pick is
 * one of the things standing in it. They move independently, so each is read on its own account: a
 * system this layer's holding never accounted for can gain or lose its last colony with no holder
 * moving anywhere, which is the unclaimed pirate haven on the claims layer and the case a full
 * rebuild is otherwise the only cure for.
 *
 * <p>What a change then costs depends on which of the three moved. A flip re-shapes the ring of
 * cells around the system (each neighbour's shared edge flips between a same-faction seam and a
 * national border) and rebuilds the two factions' territories (the old holder's and the new
 * one's); the other two are cell-local, since neither moves a seam. Both go through the same
 * {@link StyledCellBuilder} and {@link FactionTerritoryBuilder} primitives a full rebuild uses, so
 * the incremental result matches a full rebuild.
 *
 * <p>The presence bands are baked last here for the reason they are baked last in a full
 * rebuild: they are laid around the cluster names, so they follow the re-fit rather than the
 * re-shape. What that costs differs between the two paths, and only because a re-fit does: a
 * batch that flipped nothing moved no name, so only the marked systems' bands are re-baked, while
 * a flip re-fits and so owes a band to every cell one of the moved names reaches as well.
 */
final class IncrementalPoliticsRefresh {
    private static final Logger LOG = Global.getLogger(IncrementalPoliticsRefresh.class);

    // Refreshes only; never instantiated.
    private IncrementalPoliticsRefresh() {
    }

    // Drains the systems a colony resize marked stale and folds their changes into the standing
    // territories, the placements, and the name labels. The stale drain runs first, so a frame
    // with nothing marked returns before touching the sector - the cheap per-frame path. The
    // placement and label lists ride along because they are the plugin's own overlays, not part
    // of the territories, yet must track the same holding the cells do.
    //
    // The placements arrive paired with what they were fitted under and are left that way: a
    // re-fit here is partial the same way a full rebuild's is - the placements of every cluster
    // a flip left alone are carried rather than searched again - so the record has to reach the
    // fit, and the fit leaves its own in the same pair. A frame that re-fits none touches
    // neither half, which is what makes the early returns below safe to take. The geometry
    // revision arrives paired with the cells it names, and goes back out to the fit that way,
    // because this path only ever re-shapes cells within a partition it never recut - so what it
    // re-fits is sound against the very geometry that revision speaks for.
    static void applyStalePoliticsUpdates(
            PoliticalMapTerritories territories,
            StandingClusterAnchors standingAnchors,
            List<Label> factionLabels,
            RevisedCellGeometry cellGeometry) {

        var staleSystemIds = MapLayerRefresh.drainStaleGroupingSystemIds();
        if (staleSystemIds.isEmpty()) {
            return;
        }
        KmuProfiling.getProfiler().measure(
            "politicalMap.applyPoliticsUpdates",
            () -> applyDrainedPoliticsUpdates(
                territories,
                standingAnchors,
                factionLabels,
                cellGeometry,
                staleSystemIds));
    }

    // The batch itself, once the drain has found something to do: re-derive what was marked over
    // one reading of the sector, then redraw whatever that disturbed.
    //
    // The batch opens one pass, so every question asked about a marked system - its holder, and
    // the spotlit bloc's presence in it - is answered off a single walk of it. Resolving a holder
    // per system used to open a pass apiece, which paid a settings read and a colony walk for each
    // of them.
    private static void applyDrainedPoliticsUpdates(
            PoliticalMapTerritories territories,
            StandingClusterAnchors standingAnchors,
            List<Label> factionLabels,
            RevisedCellGeometry cellGeometry,
            Set<String> staleSystemIds) {

        var sector = Global.getSector();
        var pass = DominancePass.readFromLunaSettings(sector, territories.getGrouping());

        // A system with no cell seeds no drawing, so it is left out of the whole re-derive: a
        // resize changes what is in systems already on the map, never map membership.
        var markedSystemIds = selectDrawnSystemIds(cellGeometry.cells(), staleSystemIds);

        var disturbance = rederiveMarkedSystems(
            territories,
            cellGeometry.cells(),
            pass,
            markedSystemIds);

        redrawDisturbedCells(
            territories,
            standingAnchors,
            factionLabels,
            cellGeometry,
            sector,
            markedSystemIds,
            disturbance);
    }

    // The marked systems that draw a cell, in the order they were marked.
    private static Set<String> selectDrawnSystemIds(
            CellGeometryCache geometryCache,
            Set<String> staleSystemIds) {

        var drawnSystemIds = new LinkedHashSet<String>();

        for (var systemId : staleSystemIds) {
            if (geometryCache.getCellEdgesByCellId().containsKey(systemId)) {
                drawnSystemIds.add(systemId);
            }
        }
        return drawnSystemIds;
    }

    // Re-derives every marked system, folding what changed into one record of what the batch
    // disturbed.
    //
    // What the batch disturbed is accumulated as one value rather than sets filled side by side:
    // every flip owes both a re-shape and a territory rebuild, so recording one without the other
    // is exactly the half-done redraw this fold has to avoid.
    //
    // Every marked system is re-derived before anything is redrawn, so the redraw below reads a
    // fully updated holder map even when two adjacent systems flipped in one batch.
    private static StalePoliticsDisturbance rederiveMarkedSystems(
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

    // Redraws what the batch disturbed: every cell it named re-shaped against the updated holders,
    // and - only where holding actually moved - the clustering, the two sides' territories and the
    // names that follow from it. The bands are laid last, around wherever those names ended up.
    //
    // The cell redraw runs off the cells the batch named rather than off there having been a flip,
    // because the two are no longer the same question: a system whose last colony went, or one the
    // spotlit bloc just settled, draws differently with its holder exactly where it was. What the
    // flips still decide is everything below the cells, all of which is a consequence of holding
    // having moved.
    //
    // Takes the batch's sector rather than reaching for the global one, so every half of one
    // redraw is answered off the reading the re-derive above already worked from.
    private static void redrawDisturbedCells(
            PoliticalMapTerritories territories,
            StandingClusterAnchors standingAnchors,
            List<Label> factionLabels,
            RevisedCellGeometry cellGeometry,
            SectorAPI sector,
            Set<String> markedSystemIds,
            StalePoliticsDisturbance disturbance) {

        var geometryCache = cellGeometry.cells();

        for (var cellId : disturbance.getCellIdsToRedraw()) {
            reshapeCellInPlace(territories, geometryCache, cellId);
        }
        var nameDisturbance = disturbance.hasFlips()
            ? rebuildFlippedHolding(
                territories,
                standingAnchors,
                factionLabels,
                cellGeometry,
                sector,
                disturbance)
            : ClusterNameDisturbance.NONE;

        // A marked system's band counts what is in it, and the events that mark a system are
        // exactly the ones that add or remove a colony - so its band is re-baked whether or not
        // anything about its cell moved.
        var cellIdsToBake = collectCellIdsToBake(
            territories.getFillPolygonByCellId(),
            markedSystemIds,
            disturbance,
            nameDisturbance);

        bakeBandsOf(territories, cellGeometry, sector, standingAnchors, cellIdsToBake);

        LOG.debug("Political map politics updated incrementally; marked="
            + markedSystemIds.size()
            + " redrawnCells=" + disturbance.getCellIdsToRedraw().size()
            + " rebuiltFactions=" + disturbance.getAffectedFactionIds().size()
            + " rebakedBands=" + cellIdsToBake.size());
    }

    // What a flip owes beyond the cells themselves: the cluster index and the two sides'
    // territories rebuilt off the updated holders, and the names re-fitted over them. Reports what
    // that re-fit moved, since the bands below have to be re-laid wherever a name did.
    private static ClusterNameDisturbance rebuildFlippedHolding(
            PoliticalMapTerritories territories,
            StandingClusterAnchors standingAnchors,
            List<Label> factionLabels,
            RevisedCellGeometry cellGeometry,
            SectorAPI sector,
            StalePoliticsDisturbance disturbance) {

        var geometryCache = cellGeometry.cells();

        // A flip changes which systems are contiguous - it can sever one territory in two or
        // bridge two into one - so the cursor read's cluster index is re-derived off the
        // updated holders here, in step with the cells that just re-shaped.
        territories.reindexClusters(
            geometryCache.getCellEdgesByCellId(),
            geometryCache.getSystemIdByCellId());

        rebuildAffectedFactionTerritories(territories, geometryCache, disturbance);

        // A flip can split or merge clusters (a lost system severs one, a gained
        // one bridges two), so the whole placement list is re-derived off the updated
        // holders rather than the touched factions' anchors being patched alone. What that
        // costs is settled by the matching inside the rebuild, not here: a cluster the flip
        // re-partitioned no longer matches any standing placement and is searched again,
        // while every cluster it left alone is carried over - which is why naming the
        // affected factions would buy nothing this does not already get. A no-op while both
        // consumers are off. The name labels then rebuild from the placements so a renamed or
        // relocated cluster's name follows.
        var nameDisturbance = ClusterAnchorsBuilder.rebuildClusterAnchors(
            standingAnchors,
            cellGeometry,
            sector,
            ClusterLabelStylingSnapshot.resolveFrom(territories));

        LabelsBuilder.rebuildLabels(
            factionLabels,
            standingAnchors.getAnchors(),
            NameFormatPreference.getSelectedNameFormat().areNamesDrawn());

        return nameDisturbance;
    }

    // Rebuilds the territory of each faction the batch's flips moved. Only the old and new
    // holders' territories can have changed shape; every other faction's rings trace unchanged
    // cells, so they are left as-is. A faction's members are the cells it draws, so they are
    // grouped from the cells here to match what buildFactionTerritory traces.
    private static void rebuildAffectedFactionTerritories(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            StalePoliticsDisturbance disturbance) {

        var cellsByFaction = DominantHolder.mapCellGrouping(
                geometryCache.getSystemIdByCellId(),
                territories.getHolderBySystemId())
            .groupCellIdsByOwner();

        for (var factionId : disturbance.getAffectedFactionIds()) {
            rebuildFactionTerritoryInPlace(
                territories,
                geometryCache,
                factionId,
                cellsByFaction.get(factionId));
        }
    }

    // One bake of the named cells' bands, which every batch ends on. Held in one place because a
    // second copy of the pass construction could bake from a different snapshot of the same map.
    private static void bakeBandsOf(
            PoliticalMapTerritories territories,
            RevisedCellGeometry cellGeometry,
            SectorAPI sector,
            StandingClusterAnchors standingAnchors,
            Collection<String> cellIds) {

        CellRibbonsBaker
            .createForPass(
                territories,
                cellGeometry.cells(),
                sector,
                standingAnchors.getAnchors())
            .bakeCellRibbonsOf(cellIds);
    }

    // Which cells owe a fresh band, from the three separate reasons one can.
    //
    // A marked system's own count may have moved, whether or not anything about its cell did -
    // what marks a system is a colony appearing, growing or changing hands, which is exactly what
    // a band counts. A redrawn cell has a new ring and lost the band that was laid in the old one,
    // so it owes a band even where nothing it holds changed. And a cell a moved name reaches has
    // the same ring and the same holdings but different room to lay them in: the name may have
    // taken ring the band was using, or given back ring it was keeping clear of.
    //
    // The third is the one that reaches beyond what the batch touched. A re-fit places a name
    // wherever its new cluster is roomiest, which can be a cell this batch never went near - so
    // the alternative to naming those cells is re-baking the whole sector, which is what this did
    // before the fit reported what it moved.
    private static Set<String> collectCellIdsToBake(
            Map<String, List<double[]>> fillPolygonByCellId,
            Set<String> markedSystemIds,
            StalePoliticsDisturbance disturbance,
            ClusterNameDisturbance nameDisturbance) {

        var cellIdsToBake = new LinkedHashSet<>(markedSystemIds);

        cellIdsToBake.addAll(disturbance.getCellIdsToRedraw());
        cellIdsToBake.addAll(nameDisturbance.selectDisturbedCellIds(fillPolygonByCellId));

        return cellIdsToBake;
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

        var presentSystemIds = FilteredPolitics.findPresentSystemIds(
            pass,
            territories.getSelectedBlocId(),
            selectUnheldSystemIds(territories, markedSystemIds));

        for (var systemId : markedSystemIds) {

            var isPresent = presentSystemIds.contains(systemId);

            if (territories.getOccupancy().foldSpotlitPresenceOf(systemId, isPresent)) {
                disturbance.recordRestyle(systemId);
            }
        }
    }

    // The marked systems this batch's re-derive left with no holder - the only ones a spotlit
    // bloc's presence can change anything for, since a system somebody holds already draws in
    // that bloc's territory.
    private static Set<String> selectUnheldSystemIds(
            PoliticalMapTerritories territories,
            Set<String> markedSystemIds) {

        var unheldSystemIds = new LinkedHashSet<String>();

        for (var systemId : markedSystemIds) {
            if (!territories.getHolderBySystemId().containsKey(systemId)) {
                unheldSystemIds.add(systemId);
            }
        }
        return unheldSystemIds;
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

    // Re-shapes one cell against the now-updated holders and replaces its draw record, or
    // drops it when the cell contributes nothing (inset-collapsed, or factionless with
    // neither its fill nor its outline drawn). Every disturbance goes through here, including the
    // cell-local ones that re-shape to the shape the cell already has: what the batch has to end
    // with is the cell a full rebuild would have built, and building it through the rebuild's own
    // primitive is what makes that so by construction rather than by two builders agreeing.
    //
    // The cell's band is not laid here: replacing the shape drops it, and the band pass at the end
    // of the batch lays a fresh one inside the new shape, once the names it has to keep clear of
    // have been re-fitted.
    private static void reshapeCellInPlace(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            String cellId) {

        var edges = geometryCache.getCellEdgesByCellId().get(cellId);
        if (edges == null) {
            territories.removeStyledCell(cellId);
            return;
        }
        // The system the cell draws as, whose holder colours and keys it. Every cell here is a
        // star's own, so it resolves to that star, but the resolve is explicit so an
        // absorbed cell would key by its holder rather than its own missing star.
        var drawnSystemId = geometryCache.getSystemIdByCellId().get(cellId);
        var holder = territories.getHolderBySystemId().get(drawnSystemId);
        var ownerFactionId = holder == null ? null : holder.factionId();
        var shaped = CellShaper.shapeCell(
            edges,
            ownerFactionId,
            DominantHolder.mapFactionIdBySystemId(territories.getHolderBySystemId()),
            CellShaper.BORDER_INSET_DISTANCE);

        var styled = StyledCellBuilder.buildStyledCellForSystem(territories, drawnSystemId, shaped);
        if (styled == null) {
            territories.removeStyledCell(cellId);
        } else {
            territories.putStyledCell(cellId, styled, shaped.fillPolygon());
        }
    }

    // Rebuilds (or drops) one faction's territory entry from its current members. A
    // faction that lost its last member, or whose cluster no longer yields drawable
    // geometry, is removed so its fill and border stop drawing.
    private static void rebuildFactionTerritoryInPlace(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            String factionId,
            List<String> memberCellIds) {

        var territory = memberCellIds == null || memberCellIds.isEmpty()
            ? null
            : FactionTerritoryBuilder.buildFactionTerritory(
                territories,
                geometryCache,
                factionId,
                memberCellIds);

        if (territory == null) {
            territories.getStyledClusterGroupByOwnerId().remove(factionId);
        } else {
            territories.getStyledClusterGroupByOwnerId().put(factionId, territory);
        }
    }
}
