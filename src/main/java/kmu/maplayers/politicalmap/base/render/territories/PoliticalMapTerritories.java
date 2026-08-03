package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.SystemClusterIndex;
import kmu.maplayers.base.geometry.SystemClusters;
import kmu.maplayers.base.hover.MapHoverTargets;
import kmu.maplayers.base.render.clusters.ClusterDrawLists;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.base.theme.RenderStyle;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.style.BlocStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.BlocStyling;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapCategory;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The built map state a single full rebuild produces and the incremental refresh then
 * edits in place: the two draw lists the renderer paints, plus the derivation inputs an
 * incremental re-shape needs to rebuild a handful of cells against the same holding
 * and styles the full rebuild used.
 *
 * <p>The styled-cell and styled-cluster-group maps are the render output - the per-cell
 * seam/outline records, and each faction's bodies with the fill and national border they
 * share. They start empty and the build fills them, so they are created here rather than
 * passed in. The rest are
 * retained inputs, grouped into three cohesive snapshots: {@link MapStyling} (the theme,
 * the neutral colour, and the desaturation palette - how each category draws and what a
 * desaturated bloc recolours to), {@link ViewGrouping} (the view and its once-sampled
 * grouping - how holding is grouped and which blocs recede to the independent style),
 * and {@link FilterSnapshot} (the spotlight state). The holder-by-system, decivilised-system,
 * and unfilled-system sets ride alongside as who holds each system and how its fill is drawn.
 * An incremental re-shape reads them all back so it classifies a cell exactly as the full
 * build did.
 *
 * <p>A plain class rather than a record because three of its fields are mutable state,
 * not values: the styled-cell, styled-cluster-group, and holder-by-system maps are mutated
 * in place by the incremental refresh, which replaces just the cells and factions an
 * holder change touched. The styling, decivilised and unfilled sets, view grouping, and
 * filter snapshot are set once at build and only read after, so an incremental pass re-shapes
 * against the exact inputs the full build baked in.
 *
 * <p>It satisfies {@link ClusterDrawLists} directly, and so is what the framework's cluster
 * emission paints: the two draw lists it hands over are the two it already holds, and the
 * global tier the pass binds once is the one its own builders resolved against. Nothing
 * political crosses that seam - the ids in both maps stay opaque to the emission.
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

    // Render output, mutated in place by the incremental refresh. Created empty here since a
    // fresh build fills them and no caller ever supplies them pre-populated. A bloc's bodies are
    // keyed by its holder - a faction id under the factions view, or one of the filter's
    // synthetic spotlight keys - which the emission never interprets.
    private final Map<String, StyledCell> styledCellByCellId = new LinkedHashMap<>();
    private final Map<String, StyledClusterGroup> styledClusterGroupByOwnerId =
        new LinkedHashMap<>();

    // Each drawn cell's shaped fill polygon, the shape the cursor is tested against. Written only
    // through putStyledCell/removeStyledCell alongside the styled cell above, so what answers a
    // hover is exactly what the frame painted.
    private final Map<String, List<double[]>> fillPolygonByCellId = new LinkedHashMap<>();

    // Retained derivation inputs. The holder map is mutated in place as systems flip; the
    // rest are set once at build and only read after.
    private final Map<String, DominantHolder> ownerBySystemId;
    private final Set<String> decivilisedSystemIds;

    // The owned systems drawn with no fill: held by their bloc for border and label but painting
    // nothing inside its one frontier, so a held/claimed boundary reads as a seam where the fill
    // stops. Set once at build alongside the holder map, read by the per-faction fill split.
    private final Set<String> unfilledSystemIds;

    // The three cohesive input snapshots: the resolved paint scheme, the view and its once-sampled
    // grouping, and the spotlight state. The flat getters below unwrap them so every reader keeps
    // its original accessor.
    private final MapStyling styling;
    private final ViewGrouping viewGrouping;
    private final FilterSnapshot filter;

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

    public PoliticalMapTerritories(
            Map<String, DominantHolder> ownerBySystemId,
            Set<String> decivilisedSystemIds,
            Set<String> unfilledSystemIds,
            MapStyling styling,
            ViewGrouping viewGrouping,
            FilterSnapshot filter) {
        this.ownerBySystemId = ownerBySystemId;
        this.decivilisedSystemIds = decivilisedSystemIds;
        this.unfilledSystemIds = unfilledSystemIds;
        this.styling = styling;
        this.viewGrouping = viewGrouping;
        this.filter = filter;
    }

    // An empty placeholder for the render path to fall back on after a failed first
    // build: the two draw lists are empty so the render is a harmless no-op, and the
    // next frame's retry replaces it with a real build before any incremental pass -
    // which needs the theme - can run, so the null theme here is never read (the render
    // path skips an empty overlay before it would read the global tier). It carries
    // the active view (the one being drawn when the build failed) rather than naming a
    // concrete view, keeping this model view-agnostic; the identity grouping and the
    // gray-paired desaturation palette are inert defaults, never read for the same reason.
    public static PoliticalMapTerritories createEmpty(PoliticalMapView view) {
        return new PoliticalMapTerritories(
            new LinkedHashMap<>(),
            new LinkedHashSet<>(),
            new LinkedHashSet<>(),
            new MapStyling(
                null,
                Color.GRAY,
                new FactionPalette(Color.GRAY, Color.GRAY)),
            new ViewGrouping(
                view,
                HolderGrouping.identity()),
            FilterSnapshot.unfiltered());
    }

    @Override
    public Map<String, StyledCell> getStyledCellByCellId() {
        return styledCellByCellId;
    }

    /**
     * Records one cell's draw record together with the shape it was built from, the pair the
     * cursor read depends on staying aligned.
     *
     * <p>The write path for both maps, rather than each caller putting into them separately: a
     * cell that draws and a cell that answers a hover must be the same set, and pairing the two
     * writes here is what makes that true by construction instead of by two call sites
     * remembering to agree.
     *
     * @param cellId      the cell this record is for
     * @param styledCell  its draw record
     * @param fillPolygon the shaped fill it was built from - the cell's painted extent, with the
     *                    border inset, frontier setback, and keep-out clipping already applied
     */
    public void putStyledCell(String cellId, StyledCell styledCell, List<double[]> fillPolygon) {
        styledCellByCellId.put(cellId, styledCell);
        fillPolygonByCellId.put(cellId, fillPolygon);
    }

    /**
     * Drops one cell entirely - it draws nothing, so it can be hovered over no more than
     * it can be seen.
     *
     * @param cellId the cell that no longer draws
     */
    public void removeStyledCell(String cellId) {
        styledCellByCellId.remove(cellId);
        fillPolygonByCellId.remove(cellId);
    }

    /**
     * @return each drawn cell's painted extent as {x, y} vertex pairs in world coordinates, the
     *         geometry a cursor position is resolved against
     */
    @Override
    public Map<String, List<double[]>> getFillPolygonByCellId() {
        return fillPolygonByCellId;
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
     * Re-derives the cluster index from the current holders.
     *
     * <p>Clusters are a function of holding, so any pass that edits the holder map re-runs this:
     * a single system flipping can sever one territory in two or bridge two into one, which no
     * amount of patching the old index would catch.
     *
     * @param cellEdgesByCellId the adjacency the clusters are walked over
     * @param systemIdByCellId  the system each cell draws as, since a cluster is walked over
     *                          cells but indexed by the systems in it
     */
    public void reindexClusters(
            Map<String, List<CellEdge>> cellEdgesByCellId,
            Map<String, String> systemIdByCellId) {
        clusterIndex = SystemClusterIndex.indexClusters(SystemClusters.findClusters(
            cellEdgesByCellId,
            DominantHolder.mapCellGrouping(systemIdByCellId, ownerBySystemId)));
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

    public Map<String, DominantHolder> getHolderBySystemId() {
        return ownerBySystemId;
    }

    public Set<String> getDecivilisedSystemIds() {
        return decivilisedSystemIds;
    }

    // The owned systems the per-faction fill split leaves empty, drawn inside their bloc's one
    // border but painting nothing; empty when every owned system fills solid.
    public Set<String> getUnfilledSystemIds() {
        return unfilledSystemIds;
    }

    public Color getNeutralColour() {
        return styling.neutralColour();
    }

    public FactionPalette getDesaturationPalette() {
        return styling.desaturationPalette();
    }

    public RenderStyle getRenderStyle() {
        return styling.renderStyle();
    }

    // The sector-wide tier (hatch, border smoothing, desaturation profile), read by the
    // renderer and the builders so a global knob resolves once off the theme.
    @Override
    public GlobalStyle getGlobalStyle() {
        return styling.renderStyle().global();
    }

    // The style for one category, the per-category tier the cascade folds over the global
    // tier when a cell or territory of that category is built.
    public CategoryStyle getCategoryStyle(PoliticalMapCategory category) {
        return styling.renderStyle().categoryStyle(category);
    }

    /**
     * The concrete style and adjustment one bloc draws under this pass, cascading the retained
     * view, grouping, filter state, and theme in one step.
     *
     * <p>Asked of the territories rather than assembled by each builder from six separate
     * getters: every input is this build's own retained snapshot, so a bloc's fill, its national
     * border, and its cells' interior seams all resolve from the same read and cannot diverge.
     *
     * @param blocId the bloc to style - a faction id, or one of the filter's synthetic keys
     * @return the category bundle and the adjustment applied over it
     */
    public BlocStyling resolveBlocStyling(String blocId) {
        return BlocStyling.resolveFrom(
            getRenderStyle(),
            BlocStyleResolver.resolveBlocStyleDecision(
                isFiltering(),
                blocId,
                getView(),
                getGrouping(),
                getRecedeAdjustment()));
    }

    // The view and grouping as the one retained pair, for a consumer that carries both onward
    // rather than reading one of them; the two single getters below unpack it for the many
    // consumers that want only one.
    public ViewGrouping getViewGrouping() {
        return viewGrouping;
    }

    // The spotlight state as the one retained record, for the same reason: a consumer passing the
    // filter along keeps it whole rather than splitting it into three values that could be
    // recombined from different passes.
    public FilterSnapshot getFilterSnapshot() {
        return filter;
    }

    public PoliticalMapView getView() {
        return viewGrouping.view();
    }

    public HolderGrouping getGrouping() {
        return viewGrouping.grouping();
    }

    // Whether this build spotlights a bloc - it does exactly when a bloc id was selected, so the
    // shared cell and faction builders bypass the view's per-bloc styling seams for the filter's.
    public boolean isFiltering() {
        return filter.isFiltering();
    }

    // The spotlighted bloc's id this build recedes the rest of the sector around, or null off
    // filter; the label rebuild resolves the filter's synthetic spotlight keys back to its name.
    public String getSelectedBlocId() {
        return filter.selectedBlocId();
    }

    // The styling every non-spotlighted bloc recedes to this pass - and, through
    // FactionlessStyleResolver, decivilised ground with it; ElementStyleAdjustment.NONE off filter,
    // so anything no filter recedes draws untouched.
    public ElementStyleAdjustment getRecedeAdjustment() {
        return filter.recedeAdjustment();
    }

    // The spotlit systems the bloc is present in but does not dominate, so the faction builder
    // hatches their cells inside the one spotlit frontier while the dominated cells fill solid.
    // Empty off filter.
    public Set<String> getContestedSystemIds() {
        return filter.contestedSystemIds();
    }

    // True when there is nothing to paint, so the renderer can skip the GL state push
    // entirely.
    @Override
    public boolean isEmpty() {
        return styledCellByCellId.isEmpty() && styledClusterGroupByOwnerId.isEmpty();
    }
}
