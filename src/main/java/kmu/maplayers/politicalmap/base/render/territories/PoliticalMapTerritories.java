package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.geometry.CellEdge;
import kmu.maplayers.politicalmap.base.geometry.SystemClusterIndex;
import kmu.maplayers.politicalmap.base.geometry.SystemClusters;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.render.style.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.style.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.RenderStyle;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The built map state a single full rebuild produces and the incremental refresh then
 * edits in place: the two draw lists the renderer paints, plus the derivation inputs an
 * incremental re-shape needs to rebuild a handful of cells against the same ownership
 * and styles the full rebuild used.
 *
 * <p>The styled-cell and faction-territory maps are the render output - the per-cell
 * seam/outline records and each faction's fill and national border. They start empty and
 * the build fills them, so they are created here rather than passed in. The rest are
 * retained inputs, grouped into three cohesive snapshots: {@link MapStyling} (the theme,
 * the neutral color, and the desaturation palette - how each category draws and what a
 * desaturated bloc recolours to), {@link ViewGrouping} (the view and its once-sampled
 * grouping - how ownership is grouped and which blocs recede to the independent style),
 * and {@link FilterSnapshot} (the spotlight state). The owner-by-system, decivilised-system,
 * and unfilled-system sets ride alongside as who holds each system and how its fill is drawn.
 * An incremental re-shape reads them all back so it classifies a cell exactly as the full
 * build did.
 *
 * <p>A plain class rather than a record because three of its fields are mutable state,
 * not values: the styled-cell, faction-territory, and owner-by-system maps are mutated
 * in place by the incremental refresh, which replaces just the cells and factions an
 * ownership change touched. The styling, decivilised and unfilled sets, view grouping, and
 * filter snapshot are set once at build and only read after, so an incremental pass re-shapes
 * against the exact inputs the full build baked in.
 */
public final class PoliticalMapTerritories {
    // Render output, mutated in place by the incremental refresh. Created empty here since a
    // fresh build fills them and no caller ever supplies them pre-populated.
    private final Map<String, StyledCell> styledCellByCellId = new LinkedHashMap<>();
    private final Map<String, FactionTerritory> factionTerritoryByFactionId = new LinkedHashMap<>();
    // Each drawn cell's shaped fill polygon, the shape the cursor is tested against. Written only
    // through putStyledCell/removeStyledCell alongside the styled cell above, so what answers a
    // hover is exactly what the frame painted.
    private final Map<String, List<double[]>> fillPolygonByCellId = new LinkedHashMap<>();
    // Retained derivation inputs. The owner map is mutated in place as systems flip; the
    // rest are set once at build and only read after.
    private final Map<String, DominantOwner> ownerBySystemId;
    private final Set<String> decivilisedSystemIds;
    // The owned systems drawn with no fill: held by their bloc for border and label but painting
    // nothing inside its one frontier, so a held/claimed boundary reads as a seam where the fill
    // stops. Set once at build alongside the owner map, read by the per-faction fill split.
    private final Set<String> unfilledSystemIds;
    // The three cohesive input snapshots: the resolved paint scheme, the view and its once-sampled
    // grouping, and the spotlight state. The flat getters below unwrap them so every reader keeps
    // its original accessor.
    private final MapStyling styling;
    private final ViewGrouping viewGrouping;
    private final FilterSnapshot filter;
    // Which contiguous territory each system sits in, re-derived by reindexClusters whenever the
    // owner map changes. Seeded empty so a build that never indexes (and the empty placeholder)
    // still answers a lookup rather than tripping over a null.
    private SystemClusterIndex clusterIndex = SystemClusterIndex.indexClusters(List.of());

    public PoliticalMapTerritories(
            Map<String, DominantOwner> ownerBySystemId,
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
                    OwnershipGrouping.identity()),
                new FilterSnapshot(
                    null,
                    BlocStyleAdjustment.NONE,
                    new LinkedHashSet<>()));
    }

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
    public Map<String, List<double[]>> getFillPolygonByCellId() {
        return fillPolygonByCellId;
    }

    /**
     * @return which contiguous territory each system belongs to, so a hovered cell resolves to the
     *         whole cluster around it - the same clusters that carry one name apiece
     */
    public SystemClusterIndex getClusterIndex() {
        return clusterIndex;
    }

    /**
     * Re-derives the cluster index from the current owners.
     *
     * <p>Clusters are a function of ownership, so any pass that edits the owner map re-runs this:
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
                DominantOwner.mapCellGrouping(systemIdByCellId, ownerBySystemId)));
    }

    public Map<String, FactionTerritory> getFactionTerritoryByFactionId() {
        return factionTerritoryByFactionId;
    }

    public Map<String, DominantOwner> getOwnerBySystemId() {
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

    public Color getNeutralColor() {
        return styling.neutralColor();
    }

    public FactionPalette getDesaturationPalette() {
        return styling.desaturationPalette();
    }

    public RenderStyle getRenderStyle() {
        return styling.renderStyle();
    }

    // The sector-wide tier (hatch, border smoothing, desaturation profile), read by the
    // renderer and the builders so a global knob resolves once off the theme.
    public GlobalStyle getGlobalStyle() {
        return styling.renderStyle().global();
    }

    // The style for one category, the per-category tier the cascade folds over the global
    // tier when a cell or territory of that category is built.
    public CategoryStyle getCategoryStyle(MapCategory category) {
        return styling.renderStyle().categoryStyle(category);
    }

    public PoliticalMapView getView() {
        return viewGrouping.view();
    }

    public OwnershipGrouping getGrouping() {
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

    // The styling every non-spotlighted bloc recedes to this pass; BlocStyleAdjustment.NONE off
    // filter, so a bloc no filter recedes draws untouched.
    public BlocStyleAdjustment getRecedeAdjustment() {
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
    public boolean isEmpty() {
        return styledCellByCellId.isEmpty() && factionTerritoryByFactionId.isEmpty();
    }
}
