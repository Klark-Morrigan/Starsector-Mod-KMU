package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapRefresh;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.territories.FactionTerritoryBuilder;
import kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories;
import kmu.maplayers.politicalmap.base.render.territories.StyledCellBuilder;

import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Folds the per-system ownership changes a colony resize marked into the standing
 * draw lists, re-deriving and re-shaping only the affected systems and their
 * neighbours rather than rebuilding the whole map.
 *
 * <p>A resize that does not flip a system's owner costs just the re-derivation; a flip
 * re-shapes the ring of cells around it (each neighbour's shared edge flips between a
 * same-faction seam and a national border) and rebuilds the two factions' territories
 * (the old owner's and the new one's) through the same {@link StyledCellBuilder} and
 * {@link FactionTerritoryBuilder} primitives a full rebuild uses, so the incremental result
 * matches a full rebuild.
 */
final class IncrementalPoliticsRefresh {
    private static final Logger LOG = Global.getLogger(IncrementalPoliticsRefresh.class);

    // Refreshes only; never instantiated.
    private IncrementalPoliticsRefresh() {
    }

    // Drains the systems a colony resize marked stale and folds their ownership changes
    // into the standing territories, the placements, and the name labels. The stale drain
    // runs first, so a frame with nothing marked returns before touching the sector - the
    // cheap per-frame path. The placement and label lists ride along because they are the
    // plugin's own overlays, not part of the territories, yet must track the same ownership
    // the cells do.
    static void applyStalePoliticsUpdates(
            PoliticalMapTerritories territories,
            List<ClusterAnchor> clusterAnchors,
            List<Label> factionLabels,
            PoliticalMapGeometryCache geometryCache) {

        var staleSystemIds = PoliticalMapRefresh.drainStalePoliticsSystemIds();
        if (staleSystemIds.isEmpty()) {
            return;
        }
        KmuProfiling.getProfiler().measure("politicalMap.applyPoliticsUpdates", () -> {
            var sector = Global.getSector();
            var systemById = indexSystemsById(sector);
            var cellsToReshape = new LinkedHashSet<String>();
            var affectedFactionIds = new LinkedHashSet<String>();

            // Re-derive every marked system first, so re-shaping below reads a fully
            // updated owner map even when two adjacent systems flipped in one batch.
            for (var systemId : staleSystemIds) {
                rederiveSystemOwner(
                        territories,
                        geometryCache,
                        sector,
                        systemById,
                        systemId,
                        cellsToReshape,
                        affectedFactionIds);
            }
            if (affectedFactionIds.isEmpty()) {
                // Every marked system resized without flipping its owner - nothing to
                // redraw. Logged so an un-updated colour can be confirmed a no-op flip
                // rather than a missed event.
                LOG.debug("Political map politics update: no owner changed; stale="
                        + staleSystemIds.size());
                return;
            }
            for (var cellId : cellsToReshape) {
                reshapeCellInPlace(territories, geometryCache, cellId);
            }

            // A flip changes which systems are contiguous - it can sever one territory in two or
            // bridge two into one - so the cursor read's cluster index is re-derived off the
            // updated owners here, in step with the cells that just re-shaped.
            territories.reindexClusters(
                    geometryCache.getCellEdgesByCellId(),
                    geometryCache.getSystemIdByCellId());

            // Only the old and new owners' territories can have changed shape; every
            // other faction's rings trace unchanged cells, so they are left as-is. A faction's
            // members are the cells it draws, so they are grouped from the cells here to match
            // what buildFactionTerritory traces.
            var cellsByFaction = DominantOwner.mapCellGrouping(
                    geometryCache.getSystemIdByCellId(),
                    territories.getOwnerBySystemId())
                    .groupCellIdsByKey();

            for (var factionId : affectedFactionIds) {
                rebuildFactionTerritoryInPlace(
                        territories,
                        geometryCache,
                        factionId,
                        cellsByFaction.get(factionId));
            }

            // A flip can split or merge clusters (a lost system severs one, a gained
            // one bridges two), so re-fit every placement off the updated owners rather than
            // patching the touched factions' anchors alone. A no-op while both consumers are
            // off. Runs only on a real flip - the early return above already left. The name
            // labels then rebuild from the re-fitted placements so a renamed or relocated
            // cluster's name follows.
            ClusterAnchorsBuilder.rebuildClusterAnchors(
                    clusterAnchors,
                    geometryCache,
                    territories.getOwnerBySystemId(),
                    sector,
                    territories.getDesaturationPalette(),
                    territories.getView(),
                    territories.getGrouping(),
                    territories.isFiltering(),
                    territories.getRecedeAdjustment(),
                    territories.getSelectedBlocId());

            LabelsBuilder.rebuildLabels(
                    factionLabels,
                    clusterAnchors,
                    NameFormatPreference.getSelectedNameFormat().areNamesDrawn());

            LOG.debug("Political map politics updated incrementally; stale="
                    + staleSystemIds.size() + " reshapedCells=" + cellsToReshape.size()
                    + " rebuiltFactions=" + affectedFactionIds.size());
        });
    }

    // Re-derives one system's owner and, when it actually changed, records the cells to
    // re-shape (the system and its neighbours, whose edge against it flips between a
    // same-faction seam and a national border) and the factions whose territory must
    // rebuild (the old and new owner). A system with no cell seeds no drawing, so it is
    // skipped: a resize changes ownership over existing cells, never map membership.
    private static void rederiveSystemOwner(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache,
            SectorAPI sector,
            Map<String, StarSystemAPI> systemById,
            String systemId,
            Set<String> cellsToReshape,
            Set<String> affectedFactionIds) {

        if (!geometryCache.getCellEdgesByCellId().containsKey(systemId)) {
            return;
        }

        // Re-derive under the grouping the full build resolved this system's owner with,
        // so a single-system refresh lands the same winning bloc the bulk pass would.
        var newOwner = SectorPolitics.resolveDominantOwner(
                sector,
                systemById.get(systemId),
                territories.getGrouping());

        var oldOwner = territories.getOwnerBySystemId().get(systemId);

        // DominantOwner is a record, so equality covers the faction and its palette: a
        // resize that leaves the same winner leaves the drawing identical.
        if (Objects.equals(oldOwner, newOwner)) {
            return;
        }
        if (newOwner == null) {
            territories.getOwnerBySystemId().remove(systemId);
        } else {
            territories.getOwnerBySystemId().put(systemId, newOwner);
        }
        if (oldOwner != null) {
            affectedFactionIds.add(oldOwner.factionId());
        }
        if (newOwner != null) {
            affectedFactionIds.add(newOwner.factionId());
        }
        cellsToReshape.add(systemId);
        cellsToReshape.addAll(neighbourSystemIdsOf(geometryCache, systemId));
    }

    // Indexes the sector's systems by id, so a stale system id resolves to its
    // StarSystemAPI directly rather than through SectorAPI.getStarSystem, which matches
    // by name and would miss an id that differs from the display name.
    private static Map<String, StarSystemAPI> indexSystemsById(SectorAPI sector) {
        var systemById = new HashMap<String, StarSystemAPI>();
        for (var system : sector.getStarSystems()) {
            systemById.put(system.getId(), system);
        }
        return systemById;
    }

    // The systems whose cell borders this one, read from the adjacency graph. When this
    // system's owner flips, each neighbour's shared edge flips between a same-faction
    // seam and a national border, so every neighbour re-shapes too.
    private static Set<String> neighbourSystemIdsOf(
            PoliticalMapGeometryCache geometryCache,
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

    // Re-shapes one cell against the now-updated owners and replaces its draw record, or
    // drops it when the cell contributes nothing (inset-collapsed, or factionless with
    // neither its fill nor its outline drawn).
    private static void reshapeCellInPlace(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache,
            String cellId) {

        var edges = geometryCache.getCellEdgesByCellId().get(cellId);
        if (edges == null) {
            territories.removeStyledCell(cellId);
            return;
        }
        // The system the cell draws as, whose owner colours and keys it. Every cell here is a
        // star's own ground, so it resolves to that star, but the resolve is explicit so an
        // absorbed cell would key by its owner rather than its own missing star.
        var drawnSystemId = geometryCache.getSystemIdByCellId().get(cellId);
        var owner = territories.getOwnerBySystemId().get(drawnSystemId);
        var ownerFactionId = owner == null ? null : owner.factionId();
        var shaped = CellShaper.shapeCell(
                edges,
                ownerFactionId,
                DominantOwner.mapFactionIdBySystemId(territories.getOwnerBySystemId()),
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
            PoliticalMapGeometryCache geometryCache,
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
            territories.getFactionTerritoryByFactionId().remove(factionId);
        } else {
            territories.getFactionTerritoryByFactionId().put(factionId, territory);
        }
    }
}
