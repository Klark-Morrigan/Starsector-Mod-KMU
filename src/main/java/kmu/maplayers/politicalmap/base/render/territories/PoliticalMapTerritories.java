package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.SystemClusterIndex;
import kmu.maplayers.base.geometry.SystemClusters;
import kmu.maplayers.base.hover.MapHoverTargets;
import kmu.maplayers.base.render.clusters.ClusterDrawLists;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The built map a single full rebuild produces and the incremental refresh then edits in place:
 * the two draw lists the renderer paints, the clusters the cursor resolves a hovered cell's
 * territory through, and who is in each system - beside the inputs the build was baked under, so
 * a handful of cells can be rebuilt against the same holding and styles the full rebuild used.
 *
 * <p>Two things live here, and the type keeps them apart. The built map is live: the
 * {@link PaintedCellStore} and the styled-cluster-group map are written through by the refresh,
 * which replaces just the cells and factions a holder change touched; the {@link SystemOccupancy}
 * is folded by the same refresh as colonies come and go, its three facts mutable together behind
 * one type's folds so no pass can bring one up to date and leave a cell drawn from two readings
 * of the sector; and the cluster index is re-derived whenever the holding moves. The
 * {@link TerritoryBuildInputs} beside them are fixed once the build ends and only read after, so
 * an incremental pass re-shapes against exactly what the full build baked in - and a reader takes
 * that record and names the snapshot it reads, rather than reaching one field of it through a
 * getter here that says nothing about which sampling it came from.
 *
 * <p>A plain class rather than a record because the built map is state, not a value: the draw
 * lists start empty and the build fills them, so they are created here rather than passed in.
 *
 * <p>It satisfies {@link ClusterDrawLists} directly, and so is what the framework's cluster
 * emission paints: the two draw lists it hands over are the two it already holds, and the
 * global tier the pass binds once is the one its own builders resolved against. Nothing
 * political crosses that seam - the IDs in both maps stay opaque to the emission.
 *
 * <p>It satisfies {@link MapHoverTargets} directly rather than through an adapter, because the
 * two reads the cursor needs are already exactly the two it exposes - the shapes it painted and
 * the clusters it fused. Where an answer has to be worked out from these rather than handed over
 * verbatim, the adapter is the right shape and the layer supplies one; see
 * {@code PoliticalMapHoverHighlightSource}.
 */
public final class PoliticalMapTerritories implements
    ClusterDrawLists,
    MapHoverTargets {

    // The two halves of the render output, both mutated in place by the incremental refresh and
    // created empty here since a fresh build fills them and no caller ever supplies them
    // pre-populated. What one cell draws is the store's; what a whole bloc draws is the map's,
    // keyed by its holder - a faction ID under the factions view, or one of the filter's synthetic
    // spotlight keys - which the emission never interprets.
    private final PaintedCellStore paintedCells = new PaintedCellStore();
    private final Map<String, StyledClusterGroup> styledClusterGroupByOwnerId =
        new LinkedHashMap<>();

    // Who is in each system: the holder, what stands there, and where a spotlit pick lives in a
    // system nobody holds. The one live input, folded per marked system by the incremental
    // refresh.
    private final SystemOccupancy occupancy;

    // Everything else the build was baked under, set once at build and only read after.
    private final TerritoryBuildInputs buildInputs;

    // Which contiguous territory each system sits in, re-derived by reindexClusters whenever the
    // holder map changes. Seeded empty so a build that never indexes (and the empty placeholder)
    // still answers a lookup rather than tripping over a null.
    private SystemClusterIndex clusterIndex = SystemClusterIndex.indexClusters(List.of());

    // The last bloc whose loops were flattened for a cursor read, and what came out. One entry
    // because the cursor is over one cell at a time; see listCandidateBorderLoopsOf for why the
    // result has to be retained at all rather than rebuilt per ask.
    private String candidateLoopsBlocId;
    private StyledClusterGroup candidateLoopsClusterGroup;
    private List<float[]> candidateLoops = List.of();

    public PoliticalMapTerritories(SystemOccupancy occupancy, TerritoryBuildInputs buildInputs) {
        this.occupancy = occupancy;
        this.buildInputs = buildInputs;
    }

    // An empty placeholder for the render path to fall back on after a failed first build: the
    // two draw lists are empty so the render is a harmless no-op, and the next frame's retry
    // replaces it with a real build before any incremental pass - which needs the theme - can
    // run, so the placeholder inputs are never read. It carries the active view (the one being
    // drawn when the build failed) rather than naming a concrete view, keeping this model
    // view-agnostic.
    public static PoliticalMapTerritories createEmpty(PoliticalMapView view) {
        return new PoliticalMapTerritories(
            SystemOccupancy.createEmpty(),
            TerritoryBuildInputs.createEmpty(view));
    }

    /**
     * @return everything this build holds per drawn cell - the draw records, the rings they were
     *         painted on, and the bands laid inside those rings; live, and written through by the
     *         build and the incremental refresh alike
     */
    public PaintedCellStore getPaintedCells() {
        return paintedCells;
    }

    // The two cell reads the framework seams oblige, answered off the store above. Delegated rather
    // than left to each caller to reach through getPaintedCells, because these two are what
    // ClusterDrawLists and PaintedCellShapes ask for by name - a seam cannot be satisfied by a
    // getter that hands back something holding the answer.

    @Override
    public Map<SystemKey, StyledCell> getStyledCellByCellKey() {
        return paintedCells.getStyledCellByCellKey();
    }

    @Override
    public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {
        return paintedCells.getFillPolygonByCellKey();
    }

    /**
     * @return which contiguous territory each system belongs to, so a hovered cell resolves to the
     *         whole cluster around it - the same clusters that carry one name apiece
     */
    @Override
    public SystemClusterIndex getClusterIndex() {
        return clusterIndex;
    }

    /**
     * How the drawn cells group under this build's holding: which system each cell draws as,
     * paired with the bloc holding that system.
     *
     * <p>The one composition of the cut's draws-as map with this build's holding. Every stage that
     * shapes, traces, clusters or names a cell groups by it, and each composing it for itself is
     * how two stages of one pass come to disagree about which cells fuse - so it is asked of the
     * build that owns the holding half rather than assembled at each of them.
     *
     * @param systemKeyByCellKey the system each cell draws as, off the geometry cache
     * @return the cells grouped by the bloc holding the system each draws as
     */
    public CellGrouping resolveCellGroupingOver(Map<SystemKey, SystemKey> systemKeyByCellKey) {
        return DominantHolder.mapCellGrouping(
            systemKeyByCellKey,
            occupancy.getHolderBySystemKey());
    }

    /**
     * Re-derives the cluster index from the current holders.
     *
     * <p>Clusters are a function of holding, so any pass that edits the holder map re-runs this:
     * a single system flipping can sever one territory in two or bridge two into one, which no
     * amount of patching the old index would catch.
     *
     * @param cellEdgesByCellKey the adjacency the clusters are walked over
     * @param systemKeyByCellKey  the system each cell draws as, since a cluster is walked over
     *                           cells but indexed by the systems in it
     */
    public void reindexClusters(
            Map<SystemKey, List<CellEdge>> cellEdgesByCellKey,
            Map<SystemKey, SystemKey> systemKeyByCellKey) {
        clusterIndex = SystemClusterIndex.indexClusters(SystemClusters.findClusters(
            cellEdgesByCellKey,
            resolveCellGroupingOver(systemKeyByCellKey)));
    }

    // Each bloc's bodies with the fill, hatch, and national border they share, keyed by its
    // holder. Named for the framework record it hands over rather than for the political word
    // for it: the same map answers the emission's cluster read and this layer's own lookups, so
    // it carries one name.
    @Override
    public Map<String, StyledClusterGroup> getStyledClusterGroupByOwnerId() {
        return styledClusterGroupByOwnerId;
    }

    /**
     * Every loop one bloc strokes, across all of its bodies, as the flat candidate list a cursor
     * read is answered from.
     *
     * <p>Retained rather than rebuilt per ask, and that is the whole reason this is a method here
     * instead of a loop at the call site: the highlight memoises its resolved halo against the
     * identity of the list it was handed, so a fresh list each frame would miss that memo and
     * re-clip and re-tessellate the wash sixty times a second while the cursor sits still. One
     * entry is enough, because the cursor is over one cell at a time.
     *
     * <p>The retained answer is keyed on the bloc's own cluster group by identity: a rebuild or
     * an incremental refresh replaces that group wholesale rather than editing it, so a group
     * that still matches is geometry that has not moved.
     *
     * @param blocId the bloc to answer for
     * @return its loops, outer ring and enclaves alike, in cluster order; empty when the bloc
     *         drew none or is not on the map
     */
    public List<float[]> listCandidateBorderLoopsOf(String blocId) {
        var clusterGroup = styledClusterGroupByOwnerId.get(blocId);
        if (clusterGroup == null) {
            return List.of();
        }
        if (!blocId.equals(candidateLoopsBlocId) || clusterGroup != candidateLoopsClusterGroup) {
            var loops = new ArrayList<float[]>();
            for (var cluster : clusterGroup.clusters()) {
                loops.addAll(cluster.listLoops());
            }
            candidateLoopsBlocId = blocId;
            candidateLoopsClusterGroup = clusterGroup;
            candidateLoops = loops;
        }
        return candidateLoops;
    }

    /**
     * @return who is in each system, as the one live record: what a pass folding a marked system's
     *         holder, its inhabitation or the pick's presence in it writes through, and what every
     *         reader of any of the three reads
     */
    public SystemOccupancy getOccupancy() {
        return occupancy;
    }

    /**
     * @return what this build was baked under - the paint scheme, the view and grouping, the
     *         sampled picks and the two fill sets - as the one reading every stage of it sampled.
     *         What a pass that runs after the build reads rather than asking the holders again: a
     *         label re-fit or a band re-bake taken off a second reading would spell the names one
     *         way and have been sized for another
     */
    public TerritoryBuildInputs getBuildInputs() {
        return buildInputs;
    }

    // The sector-wide tier (hatch, border smoothing, desaturation profile), the one read the
    // framework's emission makes of the theme; answered off the retained scheme so a global knob
    // resolves once off the theme this build was styled from.
    @Override
    public GlobalStyle getGlobalStyle() {
        return buildInputs.styling().renderStyle().global();
    }

    // True when there is nothing to paint, so the renderer can skip the GL state push
    // entirely.
    @Override
    public boolean isEmpty() {
        return paintedCells.isEmpty() && styledClusterGroupByOwnerId.isEmpty();
    }
}
