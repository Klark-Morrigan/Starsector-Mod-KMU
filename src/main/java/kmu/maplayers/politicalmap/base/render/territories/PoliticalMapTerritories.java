package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.geometry.CellEdge;
import kmu.maplayers.base.geometry.CellGrouping;
import kmu.maplayers.base.geometry.SystemClusterIndex;
import kmu.maplayers.base.geometry.SystemClusters;
import kmu.maplayers.base.hover.MapHoverTargets;
import kmu.maplayers.base.render.clusters.ClusterDrawLists;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.base.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.ViewGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.render.ContentInputs;
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
 * <p>The {@link PaintedCellStore} and the styled-cluster-group map are the render output - what
 * each cell puts on the map, and each faction's bodies with the fill and national border they
 * share. They start empty and the build fills them, so they are created here rather than
 * passed in. The rest are
 * retained inputs, three of them cohesive snapshots: {@link SystemOccupancy} (who is in each
 * system - the holder, what stands there, and where a spotlit pick lives unheld),
 * {@link MapStyling} (the theme,
 * the neutral colour, and the desaturation palette - how each category draws and what a
 * desaturated bloc recolours to), and {@link ViewGrouping} (the view and its once-sampled
 * grouping - how holding is grouped and which blocs recede to the independent style). The
 * {@link ContentInputs} the rebuild sampled ride beside them, and two derived sets alongside
 * those: the unfilled systems, as how an owned system's fill is drawn, and the contested ones, as
 * how a spotlit system it does not dominate is. An incremental re-shape reads them all back so it
 * classifies a cell exactly as the full build did.
 *
 * <p>A plain class rather than a record because three of its fields are mutable state,
 * not values: the cell store and the styled-cluster-group map are written through
 * by the incremental refresh, which replaces just the cells and factions a
 * holder change touched, and the occupancy is folded by the same refresh as colonies come
 * and go. Its three facts are mutable together and behind that one type's folds, so no pass
 * can bring one of them up to date and leave a cell drawn from two readings of the sector.
 * The styling, view grouping, sampled picks and two derived sets are set
 * once at build and only read after, so an incremental pass re-shapes against the exact inputs
 * the full build baked in.
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
    // refresh; every other retained input below is set once at build and only read after.
    private final SystemOccupancy occupancy;

    // The owned systems drawn with no fill: held by their bloc for border and label but painting
    // nothing inside its one frontier, so a held/claimed boundary reads as a seam where the fill
    // stops. Set once at build alongside the occupancy, read by the per-faction fill split.
    private final Set<SystemKey> unfilledSystemKeys;

    // The two cohesive input snapshots: the resolved paint scheme, and the view with its
    // once-sampled grouping. The flat getters below unwrap them so every reader keeps its original
    // accessor.
    private final MapStyling styling;
    private final ViewGrouping viewGrouping;

    // The sidebar picks this build was baked under, as the rebuild's one sampling of them. Held
    // whole rather than unpacked, so a pass reading the spotlight and a pass reading the name
    // format are reading one moment.
    private final ContentInputs contentInputs;

    // The one thing this build derived about that spotlight: the spotlit systems the bloc is
    // present in but does not dominate, so the faction builder hatches their cells inside the one
    // spotlit frontier while the dominated cells fill solid. Empty off filter, and fixed at build -
    // unlike the presence set beside the holder map, which moves as colonies come and go.
    private final Set<SystemKey> contestedSystemKeys;

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
            SystemOccupancy occupancy,
            Set<SystemKey> unfilledSystemKeys,
            MapStyling styling,
            ViewGrouping viewGrouping,
            ContentInputs contentInputs,
            Set<SystemKey> contestedSystemKeys) {

        this.occupancy = occupancy;
        this.unfilledSystemKeys = unfilledSystemKeys;
        this.styling = styling;
        this.viewGrouping = viewGrouping;
        this.contentInputs = contentInputs;
        this.contestedSystemKeys = contestedSystemKeys;
    }

    // An empty placeholder for the render path to fall back on after a failed first
    // build: the two draw lists are empty so the render is a harmless no-op, and the
    // next frame's retry replaces it with a real build before any incremental pass -
    // which needs the theme - can run, so the null theme here is never read (the render
    // path skips an empty overlay before it would read the global tier). It carries
    // the active view (the one being drawn when the build failed) rather than naming a
    // concrete view, keeping this model view-agnostic; the identity grouping is an inert default,
    // never read for the same reason. The scheme's own stand-ins are named by MapStyling.
    public static PoliticalMapTerritories createEmpty(PoliticalMapView view) {
        return new PoliticalMapTerritories(
            SystemOccupancy.createEmpty(),
            new LinkedHashSet<>(),
            MapStyling.createEmpty(),
            new ViewGrouping(
                view,
                HolderGrouping.identity()),
            ContentInputs.createEmpty(),
            Set.of());
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

    // Who is in each system, as the one live record; what a pass folding a marked system's holder,
    // its inhabitation or the pick's presence in it writes through. The three flat getters below
    // unwrap it for the readers that only look, as the other snapshots' getters do.
    public SystemOccupancy getOccupancy() {
        return occupancy;
    }

    public Map<SystemKey, DominantHolder> getHolderBySystemKey() {
        return occupancy.getHolderBySystemKey();
    }

    // Every system something is standing in, whoever holds it and whether or not this layer's
    // holding accounts for them; what the factionless classifier reads to tell a settled cell from
    // the empty backdrop.
    public Set<SystemKey> getInhabitedSystemKeys() {
        return occupancy.getInhabitedSystemKeys();
    }

    // The owned systems the per-faction fill split leaves empty, drawn inside their bloc's one
    // border but painting nothing; empty when every owned system fills solid.
    public Set<SystemKey> getUnfilledSystemKeys() {
        return unfilledSystemKeys;
    }

    public Color getNeutralColour() {
        return styling.readNeutralColour();
    }

    // The shades a factionless cell paints in when nothing this pass does recolours it - the plain
    // neutral in both slots, resolved once for the build rather than rebuilt per cell.
    public FactionPalette getNeutralPalette() {
        return styling.neutralPalette();
    }

    public FactionPalette getDesaturationPalette() {
        return styling.desaturationPalette();
    }

    // The shades a factionless cell the spotlight spares paints in - the neutral lifted toward
    // white - so it reads clear of the receded greys instead of sitting at the value they sank
    // from.
    public FactionPalette getPresencePalette() {
        return styling.presencePalette();
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
     * @param blocId the bloc to style - a faction ID, or one of the filter's synthetic keys
     * @return the category bundle and the adjustment applied over it
     */
    public BlocStyling resolveBlocStyling(String blocId) {
        return BlocStyling.resolveFrom(
            styling.renderStyle(),
            BlocStyleResolver.resolveBlocStyleDecision(
                blocId,
                getView(),
                getGrouping(),
                getContentInputs()));
    }

    // The view and grouping as the one retained pair, for a consumer that carries both onward
    // rather than reading one of them; the two single getters below unpack it for the many
    // consumers that want only one.
    public ViewGrouping getViewGrouping() {
        return viewGrouping;
    }

    // The preferences this build was baked under, as the one reading every stage of it sampled -
    // what a pass that runs after the build reads rather than asking the holders again. A label
    // re-fit or a band re-bake taken off a second reading would spell the names one way and have
    // been sized for another.
    public ContentInputs getContentInputs() {
        return contentInputs;
    }

    public PoliticalMapView getView() {
        return viewGrouping.view();
    }

    public HolderGrouping getGrouping() {
        return viewGrouping.grouping();
    }

    // Whether this build spotlights a bloc - it does exactly when a bloc ID was selected, so the
    // shared cell and faction builders bypass the view's per-bloc styling seams for the filter's.
    public boolean isFiltering() {
        return contentInputs.isFiltering();
    }

    // The spotlighted bloc's ID this build recedes the rest of the sector around, or null off
    // filter; the label rebuild resolves the filter's synthetic spotlight keys back to its name.
    public String getSelectedBlocId() {
        return contentInputs.selectedBlocId();
    }

    // The styling every non-spotlighted bloc recedes to this pass - and, through
    // FactionlessStyleResolver, a decivilised cell with it; ElementStyleAdjustment.NONE off filter,
    // so anything no filter recedes draws untouched.
    public ElementStyleAdjustment getRecedeAdjustment() {
        return contentInputs.filterRecedeAdjustment();
    }

    // The spotlit systems the bloc is present in but does not dominate, so the faction builder
    // hatches their cells inside the one spotlit frontier while the dominated cells fill solid.
    // Empty off filter.
    public Set<SystemKey> getContestedSystemKeys() {
        return contestedSystemKeys;
    }

    // The settled systems the spotlit bloc lives in that no holder was resolved for, so the
    // factionless cell builder spares them the recede that sinks the rest of the sector. Empty off
    // filter, and empty on any view whose holding accounts for every inhabited system.
    public Set<SystemKey> getSpotlitPresenceSystemKeys() {
        return occupancy.getSpotlitPresenceSystemKeys();
    }

    // True when there is nothing to paint, so the renderer can skip the GL state push
    // entirely.
    @Override
    public boolean isEmpty() {
        return paintedCells.isEmpty() && styledClusterGroupByOwnerId.isEmpty();
    }
}
