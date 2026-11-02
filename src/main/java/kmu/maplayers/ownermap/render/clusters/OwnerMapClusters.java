package kmu.maplayers.ownermap.render.clusters;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.SystemClusterIndex;
import kmu.maplayers.base.geometry.SystemClusters;
import kmu.maplayers.base.hover.MapHoverTargets;
import kmu.maplayers.base.render.clusters.ClusterDrawLists;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledCell.FusedCell;
import kmu.maplayers.base.render.clusters.StyledCell.LoneCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built map a single full rebuild produces and the incremental refresh then edits in place:
 * the two draw lists the renderer paints, the clusters the cursor resolves a hovered cell's
 * cluster group through, and who is in each system - beside the inputs the build was baked under, so
 * a handful of cells can be rebuilt against the same holding and styles the full rebuild used.
 *
 * <p>Two things live here, and the type keeps them apart. The built map is live: the
 * {@link PaintedCellStore} and the styled-cluster-group map are written through by the refresh,
 * which replaces just the cells and owners an owner change touched; the {@link SystemOccupancy}
 * is folded by the same refresh as what stands in a system comes and goes, its four facts
 * mutable together behind one type's folds so no pass can bring one up to date and leave a cell
 * drawn from two readings of the sector; and the cluster index is re-derived whenever the
 * holding moves. The {@link OwnerMapBuildInputs} beside them are fixed once the build ends and
 * only read after, so an incremental pass re-shapes against exactly what the full build baked
 * in - and a reader takes that record and names the snapshot it reads, rather than reaching one
 * field of it through a getter here that says nothing about which sampling it came from.
 *
 * <p>A plain class rather than a record because the built map is state, not a value: the draw
 * lists start empty and the build fills them, so they are created here rather than passed in.
 *
 * <p>It satisfies {@link ClusterDrawLists} directly, and so is what the framework's cluster
 * emission paints: the two draw lists it hands over are the two it already holds, and the
 * global tier the pass binds once is the one its own builders resolved against. Nothing
 * layer-specific crosses that seam - the IDs in both maps stay opaque to the emission.
 *
 * <p>It satisfies {@link MapHoverTargets} directly rather than through an adapter, because the
 * two reads the cursor needs are already exactly the two it exposes - the shapes it painted and
 * the clusters it fused. Where an answer has to be worked out from these rather than handed over
 * verbatim, the adapter is the right shape and the layer supplies one; see
 * {@code OwnerMapHoverHighlightSource}.
 */
public final class OwnerMapClusters
        implements ClusterDrawLists, MapHoverTargets {

    // The two halves of the render output, both mutated in place by the incremental refresh and
    // created empty here since a fresh build fills them and no caller ever supplies them
    // pre-populated. What one cell draws is the store's; what a whole owner draws is the map's,
    // keyed by its ID - one the owner source resolved, or one of the filter's
    // synthetic spotlight keys - which the emission never interprets.
    private final PaintedCellStore paintedCells = new PaintedCellStore();

    private final Map<String, StyledClusterGroup> styledClusterGroupByOwnerId =
        new LinkedHashMap<>();

    // Who is in each system: the owner, what stands there, and where a spotlit pick lives in a
    // system nobody holds. The one live input, folded per marked system by the incremental
    // refresh.
    private final SystemOccupancy occupancy;

    // Everything else the build was baked under, set once at build and only read after.
    private final OwnerMapBuildInputs buildInputs;

    // Which contiguous cluster each system sits in, re-derived by reindexClusters whenever the
    // owner map changes. Seeded empty so a build that never indexes still answers a lookup rather
    // than tripping over a null.
    private SystemClusterIndex clusterIndex = SystemClusterIndex.indexClusters(List.of());

    public OwnerMapClusters(SystemOccupancy occupancy, OwnerMapBuildInputs buildInputs) {

        this.occupancy = occupancy;
        this.buildInputs = buildInputs;
    }

    /**
     * @return everything this build holds per drawn cell - the draw records, the rings they were
     *         painted on, and the bands laid inside those rings; live, and written through by the
     *         build and the incremental refresh alike
     */
    public PaintedCellStore getPaintedCells() {

        return paintedCells;
    }

    // The cell reads the framework seams oblige, answered off the store above. Delegated rather than
    // left to each caller to reach through getPaintedCells, because these are what ClusterDrawLists
    // and PaintedCellShapes ask for by name - a seam cannot be satisfied by a getter that hands back
    // something holding the answer.

    @Override
    public List<FusedCell> getFusedCells() {

        return paintedCells.getFusedCells();
    }

    @Override
    public List<LoneCell> getLoneCells() {

        return paintedCells.getLoneCells();
    }

    /**
     * @return each drawn cell's draw record, keyed by cell key, read-only
     */
    public Map<SystemKey, StyledCell> getStyledCellByCellKey() {

        return paintedCells.getStyledCellByCellKey();
    }

    @Override
    public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {

        return paintedCells.getFillPolygonByCellKey();
    }

    /**
     * @return which contiguous cluster each system belongs to, so a hovered cell resolves to the
     *         whole cluster around it - the same clusters that carry one name apiece
     */
    @Override
    public SystemClusterIndex getClusterIndex() {

        return clusterIndex;
    }

    /**
     * How the drawn cells group under this build's holding: which system each cell draws as,
     * paired with the owner holding that system.
     *
     * <p>The one composition of the cut's draws-as map with this build's holding. The stages of an
     * owner build that shape, trace or cluster its cells must agree about which cells fuse, and each
     * composing the grouping for itself is how two of them come to disagree - so they ask the build
     * that owns the owner half rather than each assembling one.
     *
     * <p>The substrate's name fit is not one of those stages. It sits below every layer and groups
     * by the keys it was handed, which the label snapshot reads off this same holding.
     *
     * @param systemKeyByCellKey the system each cell draws as, off the geometry cache
     * @return the cells grouped by the owner holding the system each draws as
     */
    public CellGrouping resolveCellGroupingOver(Map<SystemKey, SystemKey> systemKeyByCellKey) {

        return SystemOwner.mapCellGrouping(
            systemKeyByCellKey,
            occupancy.getOwnerBySystemKey());
    }

    /**
     * The cell builder this build's cells are baked by, over the paints it was baked under and the
     * occupancy it holds.
     *
     * <p>The one place the pipeline states the builder's inputs, so the full build and every batch
     * folding into it after hand the builder the same two values and bake a cell identically.
     *
     * @return a builder over this build's paints and live occupancy
     */
    public PaintedCellBuilder createPaintedCellBuilder() {
        return new PaintedCellBuilder(buildInputs.getPaints(), occupancy);
    }

    /**
     * The cluster-group builder this build's owners are traced by, over the paints it was baked
     * under, the fill states its occupancy now holds and the cells it groups.
     *
     * <p>The one place the pipeline states the builder's inputs, for the reason
     * {@link #createPaintedCellBuilder()} is. The grouping and edges arrive because a batch groups
     * the cells afresh once its flips have folded, and the fill states are read off the occupancy
     * for the same reason: a batch folds them per marked system. The paints stand for the life of
     * the build.
     *
     * @param cellGrouping       how the drawn cells group under the current owners, as
     *                           {@link #resolveCellGroupingOver} answers it
     * @param cellEdgesByCellKey the raw cells the borders are traced from
     * @return a builder over this build's paints, its current fill states and the given cells
     */
    public ClusterGroupBuilder createClusterGroupBuilder(
            CellGrouping cellGrouping,
            Map<SystemKey, List<CellEdge>> cellEdgesByCellKey) {

        return new ClusterGroupBuilder(
            buildInputs.getPaints(),
            cellGrouping,
            cellEdgesByCellKey,
            occupancy.snapshotFillStates());
    }

    /**
     * Re-derives the cluster index from the current owners.
     *
     * <p>Clusters are a function of ownership, so any pass that edits the owner map re-runs this:
     * a single system flipping can sever one cluster group in two or bridge two into one, which no
     * amount of patching the standing index would catch.
     *
     * <p>Takes the grouping rather than composing it, since every caller has just composed it for
     * the shaping beside this call, and composing it again copies every owner once more.
     *
     * @param cellEdgesByCellKey the adjacency the clusters are walked over
     * @param cellGrouping       how the drawn cells group under the current owners, as
     *                           {@link #resolveCellGroupingOver} answers it
     */
    public void reindexClusters(
            Map<SystemKey, List<CellEdge>> cellEdgesByCellKey,
            CellGrouping cellGrouping) {

        clusterIndex = SystemClusterIndex.indexClusters(SystemClusters.findClusters(
            cellEdgesByCellKey,
            cellGrouping));
    }

    // Each owner's bodies with the fill, hatch, and cluster border they share, keyed by its
    // ID. Named for the framework record it hands over rather than for a layer's word
    // for it: the same map answers the emission's cluster read and this layer's own lookups, so
    // it carries one name.
    @Override
    public Map<String, StyledClusterGroup> getStyledClusterGroupByOwnerId() {

        return styledClusterGroupByOwnerId;
    }

    /**
     * Traces one owner's cluster group from its members and records it, or drops the owner where it
     * has no members left.
     *
     * <p>The one statement of how an owner's group is rebuilt, for the full build tracing every
     * owner and a batch re-tracing the owners it touched, so the two cannot trace one owner
     * differently. An owner whose members are gone draws nothing and is not traced at all.
     *
     * @param groupBuilder   the builder this pass traces its owners by, as
     *                       {@link #createClusterGroupBuilder} answers it
     * @param cellGrouping   how the drawn cells group under the current owners, as
     *                       {@link #resolveCellGroupingOver} answers it
     * @param ownerId        the owner's ID, as the grouping keys it
     * @param memberCellKeys the cells the owner draws, or null or empty where it draws none
     */
    public void rebuildClusterGroupOf(
            ClusterGroupBuilder groupBuilder,
            CellGrouping cellGrouping,
            String ownerId,
            List<SystemKey> memberCellKeys) {

        var clusterGroup = memberCellKeys == null || memberCellKeys.isEmpty()
            ? null
            : groupBuilder.buildClusterGroup(
                occupancy.readOwnerOfMembers(cellGrouping, memberCellKeys),
                memberCellKeys);

        putStyledClusterGroup(ownerId, clusterGroup);
    }

    /**
     * Records one owner's cluster group, or drops the owner where it bakes none.
     *
     * <p>The write path for the map, rather than each caller choosing between a put and a remove: an
     * owner whose group came back empty-handed draws nothing, and an entry left standing for it
     * would go on painting the fill and border it lost.
     *
     * @param ownerId      the owner's ID - one the owner source resolved, or
     *                     one of the filter's synthetic spotlight keys
     * @param clusterGroup the owner's baked bodies, or null where it paints nothing or its cells
     *                     yield no borderable geometry
     */
    public void putStyledClusterGroup(String ownerId, StyledClusterGroup clusterGroup) {

        if (clusterGroup == null) {
            styledClusterGroupByOwnerId.remove(ownerId);

        } else {
            styledClusterGroupByOwnerId.put(ownerId, clusterGroup);
        }
    }

    /**
     * @return who is in each system, as the one live record: what a pass folding a marked system's
     *         owner, its inhabitation or the pick's presence in it writes through, and what every
     *         reader of any of the three reads
     */
    public SystemOccupancy getOccupancy() {

        return occupancy;
    }

    /**
     * @return what this build was baked under - the paint scheme, the view and its reading, and the
     *         sampled picks - as the one reading every stage of it sampled.
     *         What a pass that runs after the build reads rather than asking the source again: a
     *         label re-fit or a band re-bake taken off a second reading would spell the names one
     *         way and have been sized for another
     */
    public OwnerMapBuildInputs getBuildInputs() {

        return buildInputs;
    }

    // The sector-wide tier (hatch, border smoothing, desaturation profile), the one read the
    // framework's emission makes of the theme; answered off the retained scheme so a global knob
    // resolves once off the theme this build was styled from.
    @Override
    public GlobalStyle getGlobalStyle() {

        return buildInputs
            .getStyling()
            .renderStyle()
            .global();
    }

    // True when there is nothing to paint, so the renderer can skip the GL state push
    // entirely.
    @Override
    public boolean isEmpty() {

        return paintedCells.isEmpty()
            && styledClusterGroupByOwnerId.isEmpty();
    }
}
