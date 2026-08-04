package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeTarget;
import kmu.maplayers.base.labels.Label;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.AnchorFitFingerprint;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.refresh.MapLayerRefresh;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterAnchorsBuilder;
import kmu.maplayers.politicalmap.base.render.labels.anchor.ClusterLabelStylingSnapshot;
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
 * Folds the per-system holder changes a colony resize marked into the standing
 * draw lists, re-deriving and re-shaping only the affected systems and their
 * neighbours rather than rebuilding the whole map.
 *
 * <p>A resize that does not flip a system's holder costs just the re-derivation; a flip
 * re-shapes the ring of cells around it (each neighbour's shared edge flips between a
 * same-faction seam and a national border) and rebuilds the two factions' territories
 * (the old holder's and the new one's) through the same {@link StyledCellBuilder} and
 * {@link FactionTerritoryBuilder} primitives a full rebuild uses, so the incremental result
 * matches a full rebuild.
 */
final class IncrementalPoliticsRefresh {
    private static final Logger LOG = Global.getLogger(IncrementalPoliticsRefresh.class);

    // Refreshes only; never instantiated.
    private IncrementalPoliticsRefresh() {
    }

    // Drains the systems a colony resize marked stale and folds their holder changes
    // into the standing territories, the placements, and the name labels. The stale drain
    // runs first, so a frame with nothing marked returns before touching the sector - the
    // cheap per-frame path. The placement and label lists ride along because they are the
    // plugin's own overlays, not part of the territories, yet must track the same holding
    // the cells do.
    //
    // Answers what the placements it re-fitted were made under, or null on the frames where
    // it re-fitted none - the standing placements and whatever the caller already recorded
    // for them still describe each other, so there is nothing for it to overwrite. That same
    // record goes in with them, since a re-fit here is partial the same way a full rebuild's
    // is: the placements of every cluster a flip left alone are carried rather than searched
    // again. The geometry revision is handed in because this path only ever re-shapes cells
    // within a partition it did not recut, so the fit runs against the geometry the caller
    // already holds a revision for.
    static AnchorFitFingerprint applyStalePoliticsUpdates(
            PoliticalMapTerritories territories,
            List<ClusterAnchor> clusterAnchors,
            AnchorFitFingerprint lastAnchorFitFingerprint,
            List<Label> factionLabels,
            CellGeometryCache geometryCache,
            int geometryRevision) {

        var staleSystemIds = MapLayerRefresh.drainStaleGroupingSystemIds();
        if (staleSystemIds.isEmpty()) {
            return null;
        }
        return KmuProfiling.getProfiler().measure("politicalMap.applyPoliticsUpdates", () -> {
            var sector = Global.getSector();
            var systemById = indexSystemsById(sector);
            var cellsToReshape = new LinkedHashSet<String>();
            var affectedFactionIds = new LinkedHashSet<String>();

            // Re-derive every marked system first, so re-shaping below reads a fully
            // updated holder map even when two adjacent systems flipped in one batch.
            for (var systemId : staleSystemIds) {
                rederiveSystemHolder(
                    territories,
                    geometryCache,
                    sector,
                    systemById,
                    systemId,
                    cellsToReshape,
                    affectedFactionIds);
            }
            if (affectedFactionIds.isEmpty()) {
                // Every marked system resized without flipping its holder - nothing to
                // redraw. Logged so an un-updated colour can be confirmed a no-op flip
                // rather than a missed event.
                LOG.debug("Political map politics update: no holder changed; stale="
                    + staleSystemIds.size());
                return null;
            }
            for (var cellId : cellsToReshape) {
                reshapeCellInPlace(territories, geometryCache, cellId);
            }

            // A flip changes which systems are contiguous - it can sever one territory in two or
            // bridge two into one - so the cursor read's cluster index is re-derived off the
            // updated holders here, in step with the cells that just re-shaped.
            territories.reindexClusters(
                geometryCache.getCellEdgesByCellId(),
                geometryCache.getSystemIdByCellId());

            // Only the old and new holders' territories can have changed shape; every
            // other faction's rings trace unchanged cells, so they are left as-is. A faction's
            // members are the cells it draws, so they are grouped from the cells here to match
            // what buildFactionTerritory traces.
            var cellsByFaction = DominantHolder.mapCellGrouping(
                    geometryCache.getSystemIdByCellId(),
                    territories.getHolderBySystemId())
                .groupCellIdsByOwner();

            for (var factionId : affectedFactionIds) {
                rebuildFactionTerritoryInPlace(
                    territories,
                    geometryCache,
                    factionId,
                    cellsByFaction.get(factionId));
            }

            // A flip can split or merge clusters (a lost system severs one, a gained
            // one bridges two), so the whole placement list is re-derived off the updated
            // holders rather than the touched factions' anchors being patched alone. What that
            // costs is settled by the matching inside the rebuild, not here: a cluster the flip
            // re-partitioned no longer matches any standing placement and is searched again,
            // while every cluster it left alone is carried over - which is why naming the
            // affected factions would buy nothing this does not already get. A no-op while both
            // consumers are off. Runs only on a real flip - the early return above already left.
            // The name labels then rebuild from the placements so a renamed or relocated
            // cluster's name follows.
            var refittedUnder = ClusterAnchorsBuilder.rebuildClusterAnchors(
                clusterAnchors,
                lastAnchorFitFingerprint,
                geometryCache,
                sector,
                ClusterLabelStylingSnapshot.resolveFrom(territories),
                geometryRevision);

            LabelsBuilder.rebuildLabels(
                factionLabels,
                clusterAnchors,
                NameFormatPreference.getSelectedNameFormat().areNamesDrawn());

            LOG.debug("Political map politics updated incrementally; stale="
                + staleSystemIds.size()
                + " reshapedCells=" + cellsToReshape.size()
                + " rebuiltFactions=" + affectedFactionIds.size());

            return refittedUnder;
        });
    }

    // Re-derives one system's holder and, when it actually changed, records the cells to
    // re-shape (the system and its neighbours, whose edge against it flips between a
    // same-faction seam and a national border) and the factions whose territory must
    // rebuild (the old and new holder). A system with no cell seeds no drawing, so it is
    // skipped: a resize changes holding over existing cells, never map membership.
    private static void rederiveSystemHolder(
            PoliticalMapTerritories territories,
            CellGeometryCache geometryCache,
            SectorAPI sector,
            Map<String, StarSystemAPI> systemById,
            String systemId,
            Set<String> cellsToReshape,
            Set<String> affectedFactionIds) {

        if (!geometryCache.getCellEdgesByCellId().containsKey(systemId)) {
            return;
        }

        // Re-derive under the grouping the full build resolved this system's holder with,
        // so a single-system refresh lands the same winning bloc the bulk pass would.
        var newHolder = SectorPolitics.resolveDominantHolder(
            sector,
            systemById.get(systemId),
            territories.getGrouping());

        var oldHolder = territories.getHolderBySystemId().get(systemId);

        // DominantHolder is a record, so equality covers the faction and its palette: a
        // resize that leaves the same winner leaves the drawing identical.
        if (Objects.equals(oldHolder, newHolder)) {
            return;
        }
        if (newHolder == null) {
            territories.getHolderBySystemId().remove(systemId);
        } else {
            territories.getHolderBySystemId().put(systemId, newHolder);
        }
        if (oldHolder != null) {
            affectedFactionIds.add(oldHolder.factionId());
        }
        if (newHolder != null) {
            affectedFactionIds.add(newHolder.factionId());
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
    // neither its fill nor its outline drawn).
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
        // star's own ground, so it resolves to that star, but the resolve is explicit so an
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
