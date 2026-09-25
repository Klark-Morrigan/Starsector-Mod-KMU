package kmu.maplayers.ownermap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.CellShaper;
import kmu.maplayers.base.geometry.EdgeInset;
import kmu.maplayers.base.labels.LabelsBuilder;
import kmu.maplayers.base.labels.anchor.ClusterNameDisturbance;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolve;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolveSource;
import kmu.maplayers.ownermap.render.clusters.ClusterGroupBuilder;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.clusters.PaintedCellBuilder;
import kmu.maplayers.ownermap.render.labels.ClusterAnchorsBuilder;
import kmu.maplayers.ownermap.render.labels.ClusterLabelStylingSnapshot;
import kmu.maplayers.ownermap.render.ribbon.CellRibbonsBaker;

import org.apache.log4j.Logger;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Redraws what a colony event moved, over the standing map rather than by rebuilding it:
 * {@link MarkedSystemRederive} brings the marked systems back into step with the sector, and this
 * turns what that disturbed into fresh cells, clusters, names and bands.
 *
 * <p>What a change costs depends on which fact moved. A flip re-shapes the ring of cells around
 * the system (each neighbour's shared edge flips between a same-faction seam and a cluster
 * border) and rebuilds the two factions' clusters (the old holder's and the new one's); a
 * system that merely became settled, or that the spotlit bloc just colonised, redraws its own cell
 * and nothing else, neither fact moving a seam. Both go through the same {@link PaintedCellBuilder}
 * and {@link ClusterGroupBuilder} primitives a full rebuild uses, so the incremental result
 * matches a full rebuild.
 *
 * <p>The presence bands are baked last here for the reason they are baked last in a full
 * rebuild: they are laid around the cluster names, so they follow the re-fit rather than the
 * re-shape. What that costs differs between the two paths, and only because a re-fit does: a
 * batch that flipped nothing moved no name, so only the marked systems' bands are re-baked, while
 * a flip re-fits and so owes a band to every cell one of the moved names reaches as well.
 *
 * <p>One of these is made per batch and holds what the whole batch works over - the map being
 * edited and the reading of the sector it is edited against. Every stage below then names only
 * what varies between stages, which is what the batch found disturbed; threading the map through
 * each of them instead would leave every signature restating what none of them chooses.
 */
public final class IncrementalOwnerRefresh {
    private static final Logger LOG = Global.getLogger(IncrementalOwnerRefresh.class);

    private static final ProfileSection APPLY_UPDATES_SECTION =
        ProfileSection.registerSection("ownerMap.applyOwnerUpdates");

    // The map this batch edits, and the two halves of it every stage reaches for. Unpacked once
    // here rather than at each stage, since a stage that unpacked it for itself could be handed a
    // different standing map than the one the stage before it wrote to.
    private final StandingOwnerMap standingMap;
    private final OwnerMapClusters clusters;
    private final CellGeometryCache geometryCache;

    // The batch's one reading of the sector, behind the holder read that stands on it. Every
    // question asked about a marked system - its holder, the spotlit bloc's presence in it, and the
    // counts its band reports - is answered off this single walk of it; a reading opened per system
    // would pay a settings read and a colony walk for each.
    private final SystemHolderResolve holderResolve;

    private IncrementalOwnerRefresh(
            StandingOwnerMap standingMap,
            SystemHolderResolve holderResolve) {

        this.standingMap = standingMap;
        this.clusters = standingMap.clusters();
        this.geometryCache = standingMap.cellGeometry().cells();
        this.holderResolve = holderResolve;
    }

    // Folds the changes of the systems a colony resize marked stale into the standing clusters,
    // the placements, and the name labels: re-derive what was marked over one reading of the
    // sector, then redraw whatever that disturbed.
    //
    // Both the sector and the marked systems arrive from the caller rather than being resolved
    // here. This entry point is static and is handed the standing map whole, so the batch holds no
    // machinery to ask either of - and the caller that assembles that map is exactly the one
    // that does hold it, so what it drains and what it reads the colonies of are one sector's by
    // construction. It is also what answers for the frames with nothing marked, which is nearly all
    // of them: having drained the board itself, it knows there is nothing to fold before it
    // assembles anything for one.
    public static void applyStaleOwnerUpdates(
            SectorAPI sector,
            StandingOwnerMap standingMap,
            Set<SystemKey> staleSystemKeys,
            SystemHolderResolveSource holderResolveSource) {

        try (var refreshScope = ActiveProfiler.resolveProfiler().open(APPLY_UPDATES_SECTION)) {

            var holderResolve = holderResolveSource.openResolveOver(
                sector,
                standingMap.clusters().getBuildInputs().viewGrouping().grouping());

            new IncrementalOwnerRefresh(standingMap, holderResolve)
                .applyMarkedOwnerUpdates(staleSystemKeys);
        }
    }

    // The batch itself, inside the measurement the entry point opened.
    private void applyMarkedOwnerUpdates(Set<SystemKey> staleSystemKeys) {

        // A system with no cell seeds no drawing, so it is left out of the whole batch: a
        // resize changes what is in systems already on the map, never map membership.
        var markedSystemKeys = selectDrawnSystemKeys(staleSystemKeys);

        var disturbance = MarkedSystemRederive.rederiveMarkedSystems(
            clusters,
            geometryCache,
            holderResolve,
            markedSystemKeys);

        redrawDisturbedCells(markedSystemKeys, disturbance);
    }

    // The marked systems that draw a cell, in the order they were marked.
    //
    // The board marks a system by the key its cell is cut under, so a mark names one cell and
    // nothing has to widen it: a system sharing its ID with another is redrawn as itself, and the
    // twin is left standing on the reading it already has. Asked of the cut's own cells rather than
    // of a reading of the sector, which is what keeps a system the sector has since dropped in the
    // batch: its cell stands until the next cut, and a mark on it is exactly the change the redraw
    // has to show.
    private Set<SystemKey> selectDrawnSystemKeys(Set<SystemKey> staleSystemKeys) {

        var drawnSystemKeys = new LinkedHashSet<SystemKey>();
        var cellEdgesByCellKey = geometryCache.getCellEdgesByCellKey();

        for (var systemKey : staleSystemKeys) {
            if (cellEdgesByCellKey.containsKey(systemKey)) {
                drawnSystemKeys.add(systemKey);
            }
        }
        return drawnSystemKeys;
    }

    // Redraws what the batch disturbed: every cell it named re-shaped against the updated holders,
    // and - only where holding actually moved - the clustering, the two sides' clusters and the
    // names that follow from it. The bands are laid last, around wherever those names ended up.
    //
    // The cell redraw runs off the cells the batch named rather than off there having been a flip,
    // because the two are not the same question: a system whose last colony went, or one the
    // spotlit bloc just settled, draws differently with its holder exactly where it was. What the
    // flips still decide is everything below the cells, all of which is a consequence of holding
    // having moved.
    private void redrawDisturbedCells(
            Set<SystemKey> markedSystemKeys,
            StaleOwnerMapDisturbance disturbance) {

        for (var cellKey : disturbance.getCellKeysToRedraw()) {
            reshapeCellInPlace(cellKey);
        }
        var nameDisturbance = disturbance.hasFlips()
            ? rebuildFlippedHolding(disturbance)
            : ClusterNameDisturbance.NONE;

        // A marked system's band counts what is in it, and the events that mark a system are
        // exactly the ones that add or remove a colony - so its band is re-baked whether or not
        // anything about its cell moved.
        var cellKeysToBake = collectCellKeysToBake(
            markedSystemKeys,
            disturbance,
            nameDisturbance);

        bakeBandsOf(cellKeysToBake);

        LOG.debug("Owner map holding updated incrementally; marked="
            + markedSystemKeys.size()
            + " redrawnCells=" + disturbance.getCellKeysToRedraw().size()
            + " rebuiltFactions=" + disturbance.getAffectedFactionIds().size()
            + " rebakedBands=" + cellKeysToBake.size());
    }

    // What a flip owes beyond the cells themselves: the cluster index and the two sides'
    // clusters rebuilt off the updated holders, and the names re-fitted over them. Reports what
    // that re-fit moved, since the bands below have to be re-laid wherever a name did.
    private ClusterNameDisturbance rebuildFlippedHolding(StaleOwnerMapDisturbance disturbance) {

        // A flip changes which systems are contiguous - it can sever one cluster group in two or
        // bridge two into one - so the cursor read's cluster index is re-derived off the
        // updated holders here, in step with the cells that just re-shaped.
        clusters.reindexClusters(
            geometryCache.getCellEdgesByCellKey(),
            geometryCache.getSystemKeyByCellKey());

        rebuildAffectedClusterGroups(disturbance);

        // A flip can split or merge clusters (a lost system severs one, a gained
        // one bridges two), so the whole placement list is re-derived off the updated
        // holders rather than the touched factions' anchors being patched alone. What that
        // costs is settled by the matching inside the rebuild, not here: a cluster the flip
        // re-partitioned no longer matches any standing placement and is searched again,
        // while every cluster it left alone is carried over - which is why naming the
        // affected factions would buy nothing this does not already get. A no-op while both
        // consumers are off. The name labels then rebuild from the placements so a renamed or
        // relocated cluster's name follows.
        //
        // Fitted over the batch's own sector rather than the running game's, so every half of one
        // redraw is answered off the reading the re-derive already worked from.
        var nameDisturbance = ClusterAnchorsBuilder.rebuildClusterAnchors(
            standingMap.standingAnchors(),
            standingMap.cellGeometry(),
            holderResolve.readHolderPass().sectorIndex().getSector(),
            ClusterLabelStylingSnapshot.resolveFrom(clusters));

        // The name choice off the standing map rather than off the preference: this fold edits the
        // build already on screen, so whether its labels draw is what that build was baked under. A
        // live read here would mint or drop labels for a pick no cell of the standing map was
        // shaped for, without the rebuild that pick is owed.
        LabelsBuilder.rebuildLabels(
            standingMap.factionLabels(),
            standingMap.standingAnchors().getAnchors(),
            clusters.getBuildInputs().contentInputs().nameFormat().areNamesDrawn());

        return nameDisturbance;
    }

    // Rebuilds the cluster group of each faction the batch's flips moved. Only the old and new
    // holders' clusters can have changed shape; every other faction's rings trace unchanged
    // cells, so they are left as-is. A faction's members are the cells it draws, so they are
    // grouped from the cells here to match what buildClusterGroup traces.
    private void rebuildAffectedClusterGroups(StaleOwnerMapDisturbance disturbance) {

        var cellGrouping = clusters.resolveCellGroupingOver(
            geometryCache.getSystemKeyByCellKey());

        var cellsByFaction = cellGrouping.groupCellKeysByOwner();

        for (var factionId : disturbance.getAffectedFactionIds()) {
            rebuildClusterGroupInPlace(
                cellGrouping,
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
    // clusters' - the batch opened it from exactly that - which is the condition a shared
    // reading has to meet before bands are planned through it.
    private void bakeBandsOf(Collection<SystemKey> cellKeys) {

        CellRibbonsBaker
            .createForPass(
                clusters,
                geometryCache,
                holderResolve.readHolderPass(),
                standingMap.standingAnchors().getAnchors())
            .bakeCellRibbonsOf(cellKeys);
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
    // the alternative to naming those cells, which the fit reports it moved, is re-baking the
    // whole sector.
    private Set<SystemKey> collectCellKeysToBake(
            Set<SystemKey> markedSystemKeys,
            StaleOwnerMapDisturbance disturbance,
            ClusterNameDisturbance nameDisturbance) {

        var cellKeysToBake = new LinkedHashSet<>(markedSystemKeys);

        cellKeysToBake.addAll(disturbance.getCellKeysToRedraw());
        cellKeysToBake.addAll(
            nameDisturbance.selectDisturbedCellKeys(clusters.getFillPolygonByCellKey()));

        return cellKeysToBake;
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
    private void reshapeCellInPlace(SystemKey cellKey) {

        var edges = geometryCache.getCellEdgesByCellKey().get(cellKey);
        if (edges == null) {
            clusters.getPaintedCells().removePaintedCell(cellKey);
            return;
        }
        // The system the cell draws as, whose holder colours and keys it. Every cell here is a
        // star's own, so it resolves to that star, but the resolve is explicit so an
        // absorbed cell would key by its holder rather than its own missing star.
        var drawnSystemKey = geometryCache.getSystemKeyByCellKey().get(cellKey);
        var occupancy = clusters.getOccupancy();
        var holder = drawnSystemKey == null
            ? null
            : occupancy.readHolderOf(drawnSystemKey);

        // This cell's own holder narrowly, and the whole holding beside it: the shaper compares
        // every neighbour's holder against this one to tell a same-bloc seam from a border, so that
        // half is one of the few reads the map itself answers.
        var ownerFactionId = holder == null ? null : holder.factionId();
        var shaped = CellShaper.shapeCell(
            edges,
            ownerFactionId,
            SystemOwner.mapFactionIdBySystemKey(occupancy.getHolderBySystemKey()),
            EdgeInset.asTheMapDraws());

        var painted = PaintedCellBuilder.buildPaintedCellForSystem(
            clusters,
            drawnSystemKey,
            shaped);

        if (painted == null) {
            clusters.getPaintedCells().removePaintedCell(cellKey);
        } else {
            clusters.getPaintedCells().putPaintedCell(cellKey, painted);
        }
    }

    // Rebuilds (or drops) one faction's cluster group entry from its current members. A
    // faction that lost its last member, or whose cluster no longer yields drawable
    // geometry, is removed so its fill and border stop drawing.
    private void rebuildClusterGroupInPlace(
            CellGrouping cellGrouping,
            String factionId,
            List<SystemKey> memberCellKeys) {

        var clusterGroup = memberCellKeys == null || memberCellKeys.isEmpty()
            ? null
            : ClusterGroupBuilder.buildClusterGroup(
                clusters,
                geometryCache,
                cellGrouping,
                factionId,
                memberCellKeys);

        clusters.putStyledClusterGroup(factionId, clusterGroup);
    }
}
