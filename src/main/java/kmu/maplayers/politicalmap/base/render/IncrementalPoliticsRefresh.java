package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.RevisedCellGeometry;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterNameDisturbance;
import kmu.maplayers.base.labels.anchor.StandingClusterAnchors;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
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
import java.util.Set;

/**
 * Redraws what a colony event moved, over the standing map rather than by rebuilding it:
 * {@link MarkedSystemRederive} brings the marked systems back into step with the sector, and this
 * turns what that disturbed into fresh cells, territories, names and bands.
 *
 * <p>What a change costs depends on which fact moved. A flip re-shapes the ring of cells around
 * the system (each neighbour's shared edge flips between a same-faction seam and a national
 * border) and rebuilds the two factions' territories (the old holder's and the new one's); a
 * system that merely became settled, or that the spotlit bloc just colonised, redraws its own cell
 * and nothing else, neither fact moving a seam. Both go through the same {@link StyledCellBuilder}
 * and {@link FactionTerritoryBuilder} primitives a full rebuild uses, so the incremental result
 * matches a full rebuild.
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
    // with nothing marked returns before touching the sector or gathering anything - the cheap
    // per-frame path, which is nearly every frame.
    //
    // The four halves of the standing map are taken loose here and bundled below, because this is
    // the boundary the plugin's cache hands them over at: the placement and label lists are its own
    // overlays rather than part of the territories, yet must track the same holding the cells do.
    //
    // The placements arrive paired with what they were fitted under and are left that way: a
    // re-fit here is partial the same way a full rebuild's is - the placements of every cluster
    // a flip left alone are carried rather than searched again - so the record has to reach the
    // fit, and the fit leaves its own in the same pair. A frame that re-fits none touches neither
    // half. The geometry revision arrives paired with the cells it names, and goes back out to the
    // fit that way, because this path only ever re-shapes cells within a partition it never recut -
    // so what it re-fits is sound against the very geometry that revision speaks for.
    static void applyStalePoliticsUpdates(
            PoliticalMapTerritories territories,
            StandingClusterAnchors standingAnchors,
            List<Label> factionLabels,
            RevisedCellGeometry cellGeometry) {

        var staleSystemIds = MapLayerRefresh.drainStaleGroupingSystemIds();
        if (staleSystemIds.isEmpty()) {
            return;
        }
        var standingMap = new StandingPoliticalMap(
            territories,
            standingAnchors,
            factionLabels,
            cellGeometry);

        KmuProfiling.getProfiler().measure(
            "politicalMap.applyPoliticsUpdates",
            () -> applyDrainedPoliticsUpdates(standingMap, staleSystemIds));
    }

    // The batch itself, once the drain has found something to do: re-derive what was marked over
    // one reading of the sector, then redraw whatever that disturbed.
    //
    // The batch opens one pass, so every question asked about a marked system - its holder, and
    // the spotlit bloc's presence in it - is answered off a single walk of it. Resolving a holder
    // per system used to open a pass apiece, which paid a settings read and a colony walk for each
    // of them.
    private static void applyDrainedPoliticsUpdates(
            StandingPoliticalMap standingMap,
            Set<String> staleSystemIds) {

        var pass = DominancePass.readFromLunaSettings(
            Global.getSector(),
            standingMap.territories().getGrouping());

        // A system with no cell seeds no drawing, so it is left out of the whole batch: a
        // resize changes what is in systems already on the map, never map membership.
        var markedSystemIds = selectDrawnSystemIds(
            standingMap.cellGeometry().cells(),
            staleSystemIds);

        var disturbance = MarkedSystemRederive.rederiveMarkedSystems(
            standingMap.territories(),
            standingMap.cellGeometry().cells(),
            pass,
            markedSystemIds);

        redrawDisturbedCells(standingMap, pass, markedSystemIds, disturbance);
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

    // Redraws what the batch disturbed: every cell it named re-shaped against the updated holders,
    // and - only where holding actually moved - the clustering, the two sides' territories and the
    // names that follow from it. The bands are laid last, around wherever those names ended up.
    //
    // The cell redraw runs off the cells the batch named rather than off there having been a flip,
    // because the two are not the same question: a system whose last colony went, or one the
    // spotlit bloc just settled, draws differently with its holder exactly where it was. What the
    // flips still decide is everything below the cells, all of which is a consequence of holding
    // having moved.
    //
    // Takes the batch's pass rather than reaching for the global sector, so every half of one
    // redraw is answered off the reading the re-derive already worked from - the names off its
    // sector, and the bands off its walk of each system rather than off a second one opened for a
    // sector that cannot have moved since.
    private static void redrawDisturbedCells(
            StandingPoliticalMap standingMap,
            DominancePass pass,
            Set<String> markedSystemIds,
            StalePoliticsDisturbance disturbance) {

        var territories = standingMap.territories();
        var geometryCache = standingMap.cellGeometry().cells();

        for (var cellId : disturbance.getCellIdsToRedraw()) {
            reshapeCellInPlace(territories, geometryCache, cellId);
        }
        var nameDisturbance = disturbance.hasFlips()
            ? rebuildFlippedHolding(standingMap, pass.sector(), disturbance)
            : ClusterNameDisturbance.NONE;

        // A marked system's band counts what is in it, and the events that mark a system are
        // exactly the ones that add or remove a colony - so its band is re-baked whether or not
        // anything about its cell moved.
        var cellIdsToBake = collectCellIdsToBake(
            territories.getFillPolygonByCellId(),
            markedSystemIds,
            disturbance,
            nameDisturbance);

        bakeBandsOf(standingMap, pass.holding(), cellIdsToBake);

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
            StandingPoliticalMap standingMap,
            SectorAPI sector,
            StalePoliticsDisturbance disturbance) {

        var territories = standingMap.territories();
        var geometryCache = standingMap.cellGeometry().cells();

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
            standingMap.standingAnchors(),
            standingMap.cellGeometry(),
            sector,
            ClusterLabelStylingSnapshot.resolveFrom(territories));

        LabelsBuilder.rebuildLabels(
            standingMap.factionLabels(),
            standingMap.standingAnchors().getAnchors(),
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
    //
    // Baked off the batch's own reading of the sector. The batch is one frame's work and the
    // sector cannot move within it, so a reading opened here could only report what that one
    // already holds, at the cost of walking every system a second time. Its grouping is the
    // territories' - the batch opened it from exactly that - which is the condition a shared
    // reading has to meet before bands are planned through it.
    private static void bakeBandsOf(
            StandingPoliticalMap standingMap,
            HolderPass pass,
            Collection<String> cellIds) {

        CellRibbonsBaker
            .createForPass(
                standingMap.territories(),
                standingMap.cellGeometry().cells(),
                pass,
                standingMap.standingAnchors().getAnchors())
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
