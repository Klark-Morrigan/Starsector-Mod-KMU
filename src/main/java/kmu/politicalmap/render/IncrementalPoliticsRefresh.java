package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.domain.geometry.CellShaper;
import kmu.politicalmap.domain.geometry.PoliticalMapGeometryCache;
import kmu.politicalmap.domain.politics.SectorPolitics;
import kmu.politicalmap.refresh.PoliticalMapRefresh;

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
 * (the old owner's and the new one's) through the same {@link DrawablesBuilder}
 * primitives a full rebuild uses, so the incremental result matches a full rebuild.
 */
final class IncrementalPoliticsRefresh {
    private static final Logger LOG = Global.getLogger(IncrementalPoliticsRefresh.class);

    // Refreshes only; never instantiated.
    private IncrementalPoliticsRefresh() {
    }

    // Drains the systems a colony resize marked stale and folds their ownership changes
    // into the standing drawables. The stale drain runs first, so a frame with nothing
    // marked returns before touching the sector - the cheap per-frame path.
    static void applyStalePoliticsUpdates(PoliticalMapDrawables drawables,
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
                rederiveSystemOwner(drawables, geometryCache, sector, systemById, systemId,
                        cellsToReshape, affectedFactionIds);
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
                reshapeCellInPlace(drawables, geometryCache, cellId);
            }
            // Only the old and new owners' territories can have changed shape; every
            // other faction's rings trace unchanged cells, so they are left as-is.
            var systemsByFaction = DrawablesBuilder.groupOwnedSystemsByFaction(drawables);
            for (var factionId : affectedFactionIds) {
                rebuildFactionTerritoryInPlace(drawables, geometryCache, factionId,
                        systemsByFaction.get(factionId));
            }
            // A flip can split or merge clusters (a lost system severs one, a gained
            // one bridges two), so re-fit every anchor off the updated owners rather than
            // patching the touched factions' anchors alone. A no-op while the toggle is
            // off. Runs only on a real flip - the early return above already left.
            DrawablesBuilder.rebuildClusterAnchors(drawables, geometryCache);
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
    private static void rederiveSystemOwner(PoliticalMapDrawables drawables,
            PoliticalMapGeometryCache geometryCache, SectorAPI sector,
            Map<String, StarSystemAPI> systemById, String systemId, Set<String> cellsToReshape,
            Set<String> affectedFactionIds) {
        if (!geometryCache.getCellEdgesBySystemId().containsKey(systemId)) {
            return;
        }
        var newOwner = SectorPolitics.resolveDominantOwner(sector, systemById.get(systemId));
        var oldOwner = drawables.getOwnerBySystemId().get(systemId);
        // DominantOwner is a record, so equality covers the faction and its palette: a
        // resize that leaves the same winner leaves the drawing identical.
        if (Objects.equals(oldOwner, newOwner)) {
            return;
        }
        if (newOwner == null) {
            drawables.getOwnerBySystemId().remove(systemId);
        } else {
            drawables.getOwnerBySystemId().put(systemId, newOwner);
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
    private static Set<String> neighbourSystemIdsOf(PoliticalMapGeometryCache geometryCache,
            String systemId) {
        var neighbours = new LinkedHashSet<String>();
        var edges = geometryCache.getCellEdgesBySystemId().get(systemId);
        if (edges != null) {
            for (var edge : edges) {
                if (edge.neighbourSystemId() != null) {
                    neighbours.add(edge.neighbourSystemId());
                }
            }
        }
        return neighbours;
    }

    // Re-shapes one cell against the now-updated owners and replaces its draw record, or
    // drops it when the cell contributes nothing (inset-collapsed, or factionless with a
    // "No color" outline).
    private static void reshapeCellInPlace(PoliticalMapDrawables drawables,
            PoliticalMapGeometryCache geometryCache, String systemId) {
        var edges = geometryCache.getCellEdgesBySystemId().get(systemId);
        if (edges == null) {
            drawables.getStyledCellBySystemId().remove(systemId);
            return;
        }
        var owner = drawables.getOwnerBySystemId().get(systemId);
        var ownerFactionId = owner == null ? null : owner.factionId();
        var shaped = CellShaper.shapeCell(edges, ownerFactionId, drawables.getOwnerBySystemId(),
                PoliticalMapStyle.BORDER_INSET_DISTANCE);
        var styled = DrawablesBuilder.buildStyledCellForSystem(drawables, systemId, shaped);
        if (styled == null) {
            drawables.getStyledCellBySystemId().remove(systemId);
        } else {
            drawables.getStyledCellBySystemId().put(systemId, styled);
        }
    }

    // Rebuilds (or drops) one faction's territory entry from its current members. A
    // faction that lost its last member, or whose cluster no longer yields drawable
    // geometry, is removed so its fill and border stop drawing.
    private static void rebuildFactionTerritoryInPlace(PoliticalMapDrawables drawables,
            PoliticalMapGeometryCache geometryCache, String factionId,
            List<String> memberSystemIds) {
        var territory = memberSystemIds == null || memberSystemIds.isEmpty()
                ? null
                : DrawablesBuilder.buildFactionTerritory(drawables, geometryCache, factionId,
                        memberSystemIds);
        if (territory == null) {
            drawables.getFactionTerritoryByFactionId().remove(factionId);
        } else {
            drawables.getFactionTerritoryByFactionId().put(factionId, territory);
        }
    }
}
