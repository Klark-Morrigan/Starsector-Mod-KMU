package kmu.maplayers.politicalmap.base.render.territories;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.PolygonSmoothing;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.Hatching;
import kmlib.opengl.PolygonTessellator;
import kmlib.profiling.Timings;
import kmlib.starsector.factions.StarsectorFactionColors;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.geometry.CellEdge;
import kmu.maplayers.politicalmap.base.geometry.CellShaper;
import kmu.maplayers.politicalmap.base.geometry.FrontierSettings;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.maplayers.politicalmap.base.render.PoliticalBorderTrace;
import kmu.maplayers.politicalmap.base.render.style.BlocStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapStyle;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;
import kmu.settings.FactionPaletteChoice;

import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns the raw cached cells and the current sector ownership into the political
 * map's draw lists: it resolves who holds each system, shapes the cells into merged
 * clusters, and bakes each element's colors, opacities, and widths from the current
 * settings into GL-ready vertex runs.
 *
 * <p>The full {@link #buildTerritories} pass produces a fresh {@link PoliticalMapTerritories}
 * from scratch. The per-cell {@link #buildStyledCellForSystem} and per-faction
 * {@link #buildFactionTerritory} builders are the shared primitives the incremental
 * refresh reuses to rebuild just the cells and factions an ownership change touched,
 * so a full rebuild and an incremental re-shape classify and style a cell identically.
 */
public final class TerritoryBuilder {
    // The suffixes that split the spotlit footprint's one grouping key into a key per state, so
    // the border tracer traces the dominant and the contested members as two regions. Appended to
    // the footprint's own key, which already carries a sentinel prefix no real bloc id can hold,
    // so neither derived key can collide with a rival's.
    private static final String CONTESTED_SUB_REGION_SUFFIX = "#contested";
    private static final String DOMINANT_SUB_REGION_SUFFIX = "#dominant";

    private static final Logger LOG = Global.getLogger(TerritoryBuilder.class);

    // Builds only; never instantiated.
    private TerritoryBuilder() {
    }

    // Shapes the cached raw cells into merged clusters and partitions them into filled
    // (owned) and outline-only (decivilised/uninhabited) draw lists, baking in each
    // cell's colors, opacities, and widths resolved from the current settings, then
    // flattens each to GL-ready vertex runs. Reads the settings once per category, not
    // per cell. The active view supplies the ownership grouping the pass resolves under
    // and the style classifier each cell reads; both are retained on the territories so an
    // incremental re-shape classifies against the same view and grouping snapshot.
    public static PoliticalMapTerritories buildTerritories(
            PoliticalMapGeometryCache geometryCache,
            SectorAPI sector,
            PoliticalMapView view) {
        var profiler = KmuProfiling.getProfiler();
        return profiler.measure("politicalMap.rebuildTerritories", () -> {
            // Sample the view's grouping once for the whole pass (the alliances view reads
            // Nexerelin), so every stage keys off one snapshot and the retained copy the
            // incremental re-shape reads matches the ownership this build resolved.
            var grouping = view.resolveGrouping();
            // A spotlighted bloc switches the pass to the presence-aware resolver: it keeps the
            // selected bloc visible everywhere it owns a market (solid where it wins, contested
            // elsewhere) instead of collapsing every system to its lone winner. Read once so the
            // whole pass keys off one snapshot, exactly like the grouping.
            var selectedBlocId = FilterSelection.getSelectedBlocId();
            var isFiltering = selectedBlocId != null;
            // The politics scan walks the whole economy - the priciest content step -
            // so it is profiled and timed on its own, and the owner count logged
            // independent of the profiler's accumulated view. Under a filter it also reports
            // which spotlit systems are contested, since the whole spotlit footprint shares one
            // key and that set is the only record of the dominant/contested split.
            var politicsStart = System.nanoTime();
            var filtered = profiler.measure("politicalMap.resolvePolitics",
                    () -> isFiltering
                            ? FilteredPolitics.resolveFilteredOwnership(sector, grouping, selectedBlocId)
                            : new FilteredPolitics.FilteredOwnership(
                                    SectorPolitics.resolveDominantOwnerBySystemId(sector, grouping),
                                    Set.of()));
            var ownerBySystemId = filtered.ownerBySystemId();
            var contestedSystemIds = filtered.contestedSystemIds();
            LOG.debug("Political map politics resolved; ownedSystems=" + ownerBySystemId.size()
                    + " filtering=" + isFiltering + " contested=" + contestedSystemIds.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - politicsStart));

            var decivilisedStart = System.nanoTime();
            var decivilisedSystemIds = profiler.measure("politicalMap.findDecivilised",
                    () -> DecivilisedMarkets.findRevealedDecivilisedSystemIds(sector));
            LOG.debug("Political map decivilised scan; systems=" + decivilisedSystemIds.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - decivilisedStart));

            // The whole theme - the global tier plus one style per category - read once here
            // through the single reader seam, plus the shared neutral color and the desaturation
            // palette the profile resolves to. Held on the territories so the incremental refresh
            // re-shapes cells against the same snapshot this pass used.
            var renderStyle = RenderStyleReader.readRenderStyle();
            var neutralColor = StarsectorFactionColors.resolveNeutralColor(sector);
            // The receded background desaturates to a uniform Independent-based grey, darkened by the
            // live setting so it sits below genuine independent-held space - a spotlit bloc, even
            // Independent at full strength, therefore reads distinctly against it.
            var desaturationPalette = MapPalettes.resolveDesaturationPalette(
                    sector, renderStyle.global().desaturationDarkening());
            // The styling every non-spotlighted bloc recedes to, resolved once from the filter recede
            // toggles - the "rest of the sector" set, shared across both views under a filter; the
            // identity adjustment off filter, so a normal pass touches no bloc.
            var recedeAdjustment = isFiltering
                    ? RecedePreferences.FILTER.resolveRecedeAdjustment()
                    : BlocStyleAdjustment.NONE;
            var territories = new PoliticalMapTerritories(
                    ownerBySystemId,
                    decivilisedSystemIds,
                    new MapStyling(renderStyle, neutralColor, desaturationPalette),
                    new ViewGrouping(view, grouping),
                    new FilterSnapshot(selectedBlocId, recedeAdjustment, contestedSystemIds));

            // Shape the raw cells into merged clusters once, ownership-aware. The agnostic
            // geometry clusters by grouping key, so hand it each system's faction id as the
            // key. Cells consumed by the inset (fewer than three vertices left) drop out.
            var shapeStart = System.nanoTime();
            var groupKeyBySystemId = DominantOwner.mapFactionIdBySystemId(ownerBySystemId);
            // The frontier snapshot the whole shape pass reads: a factionless cell's edge
            // facing an owned neighbour recedes to its keep-out pocket while the toggle is on.
            var frontier = FrontierSettings.readFromLunaSettings(geometryCache.getSiteBySystemId());
            var shapedCells = profiler.measure("politicalMap.shapeCells",
                    () -> CellShaper.shapeCells(geometryCache.getCellEdgesBySystemId(),
                            groupKeyBySystemId, PoliticalMapStyle.BORDER_INSET_DISTANCE, frontier));
            for (var entry : shapedCells.entrySet()) {
                var styled = buildStyledCellForSystem(territories, entry.getKey(), entry.getValue());
                if (styled != null) {
                    territories.getStyledCellBySystemId().put(entry.getKey(), styled);
                }
            }

            // Each owned faction's territory: one region per cluster (traced across all
            // its cells so a multi-system cluster reads as one frontier), tessellated for
            // the fill and flattened for the border - the same shape for both. Built off
            // the same raw cells and owners the seams used, and profiled on its own since
            // chaining, smoothing, and tessellating every faction's outline is comparable
            // in cost to shaping the cells.
            profiler.measure("politicalMap.buildFactionTerritories",
                    () -> buildAllFactionTerritories(territories, geometryCache, frontier));

            LOG.debug("Political map cells shaped; shaped=" + shapedCells.size()
                    + " styledCells=" + territories.getStyledCellBySystemId().size()
                    + " factionTerritories=" + territories.getFactionTerritoryByFactionId().size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - shapeStart));
            return territories;
        });
    }

    // Builds one system's per-cell draw record from its shaped cell, or null when the
    // cell draws nothing: an inset-collapsed cell, or a factionless cell whose outline
    // is "No color". An owned cell keeps only its interior seams (its fill and national
    // border are per-cluster, in factionTerritories); a factionless cell keeps its own
    // inset fill and outline, since factionless cells do not fuse. Shared by the full
    // rebuild and the incremental re-shape so both classify a cell identically.
    public static StyledCell buildStyledCellForSystem(
            PoliticalMapTerritories territories,
            String systemId,
            ShapedCell shaped) {
        // A cell the border inset consumed or collapsed comes back with an empty fill
        // (CellShaper via PolygonOffsets.insetSelectedEdges guarantees empty-or-drawable), so
        // there is nothing to fill or stroke.
        if (shaped.fillPolygon().isEmpty()) {
            return null;
        }
        var owner = territories.getOwnerBySystemId().get(systemId);
        if (owner != null) {
            // Every owned cell - the spotlighted bloc included - contributes only its interior
            // seams here; its fill and national border come per cluster from the tessellated
            // region. A spotlit cell is no exception: keeping its seams is what lets the footprint
            // read as its constituent cells rather than one smooth blob, so wherever two of the
            // bloc's cells meet across a real cell border the seam between them still draws. The
            // per-territory solid<->hatched transition seam then emphasises that one boundary on
            // top of these uniform seams.
            // The base style and per-bloc adjustment come from the pass's styling resolver -
            // the active view off filter, the spotlight rules under one. The adjustment dims
            // and/or recolours the cell: desaturating swaps the owner's own palette for the
            // pass's shared desaturation palette, and the opacity multiplier scales every seam
            // alpha on top of the style's own opacities.
            var styling = resolveBlocStyling(territories, owner.factionId());
            var palette = MapPalettes.resolveEffectivePalette(styling.adjustment(), owner,
                    territories.getDesaturationPalette());
            return buildStyledCell(shaped, palette.primaryColor(), palette.secondaryColor(),
                    styling.style(), false, styling.adjustment(),
                    territories.getGlobalStyle().borderSmoothing());
        }
        // Factionless: decivilised or (otherwise) uninhabited. Its style fills neither
        // palette slot, so both resolve to the shared neutral color and only its
        // per-cell outline draws; drop it when that outline is "No color". Factionless
        // cells are not blocs the view classifies, so they never carry an adjustment.
        var style = territories.getDecivilisedSystemIds().contains(systemId)
                ? territories.getCategoryStyle(MapCategory.DECIVILISED)
                : territories.getCategoryStyle(MapCategory.UNINHABITED);
        if (style.outerColor() == FactionPaletteChoice.NONE) {
            return null;
        }
        var neutralColor = territories.getNeutralColor();
        return buildStyledCell(
                shaped,
                neutralColor,
                neutralColor,
                style,
                true,
                BlocStyleAdjustment.NONE,
                territories.getGlobalStyle().borderSmoothing());
    }

    // Builds every owned faction's territory into the territories, keyed by faction id.
    // Each faction is independent - its cluster(s) trace only its own cells - so the
    // incremental refresh rebuilds one faction's entry without touching the rest.
    private static void buildAllFactionTerritories(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache,
            FrontierSettings frontier) {
        for (var faction
                : DominantOwner.groupSystemIdsByFactionId(territories.getOwnerBySystemId()).entrySet()) {
            var territory = buildFactionTerritory(
                    territories,
                    geometryCache,
                    faction.getKey(),
                    faction.getValue(),
                    frontier);
            if (territory != null) {
                territories.getFactionTerritoryByFactionId().put(faction.getKey(), territory);
            }
        }
    }

    // Builds one faction's fill and national border from its border rings,
    // traced across all the systems it holds so a multi-system cluster reads as one
    // continuous frontier. The rings are tessellated into fill triangles and flattened
    // into border loops - the same geometry - so the fill exactly matches the stroked
    // border. Disjoint clusters and enclaves each come back as their own ring, so
    // rebuilding a faction from its current members alone re-splits or re-merges its
    // clusters when the incremental refresh gains or loses one. Returns null when the
    // faction has no fill and no border color, or no borderable geometry.
    public static FactionTerritory buildFactionTerritory(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache,
            String factionId,
            List<String> memberSystemIds,
            FrontierSettings frontier) {
        // The grouping key here is a bloc id (a faction id under the faction view, or one of the
        // filter's synthetic spotlight keys), so its style and per-bloc adjustment come from the
        // same styling resolver a per-cell owner does. A desaturated bloc's fill and border swap
        // to the pass's shared desaturation palette instead of its own two shades.
        var styling = resolveBlocStyling(territories, factionId);
        var style = styling.style();
        var adjustment = styling.adjustment();
        // The spotlighted bloc's whole footprint - dominated and contested systems alike - shares
        // one key, so it clusters into this single territory outlined by one frontier, then splits
        // its fill per cell below. Every other territory fills solid as one region.
        var isSpotlit = FilteredPolitics.isSpotlitBloc(factionId);
        // Every system of a faction shares its palette, so any member resolves the same
        // fill and border colors.
        var owner = territories.getOwnerBySystemId().get(memberSystemIds.get(0));
        var palette = MapPalettes.resolveEffectivePalette(
                adjustment,
                owner,
                territories.getDesaturationPalette());
        var fillColor = MapPalettes.pickPaletteColor(
                style.fillColor(),
                palette.primaryColor(),
                palette.secondaryColor());
        var borderColor = MapPalettes.pickPaletteColor(
                style.outerColor(),
                palette.primaryColor(),
                palette.secondaryColor());
        if (fillColor == null && borderColor == null) {
            return null;
        }
        // One trace for the whole territory: the national border's rings, and - for a spotlit
        // footprint - the per-state sub-region rings its fill splits into, so border and fill
        // offset under identical parameters and cannot drift apart.
        var borderTrace = PoliticalBorderTrace.readFromLunaSettings(frontier);
        var insetRings = borderTrace.traceRings(
                memberSystemIds,
                geometryCache.getCellEdgesBySystemId(),
                DominantOwner.mapFactionIdBySystemId(territories.getOwnerBySystemId()));
        if (insetRings.isEmpty()) {
            return null;
        }
        // Resolve the inset rings to their clean outer envelope first (positive winding
        // drops any neck self-crossing), then run each smoothing pass only when its
        // Dev-tab gate is on: sand the spikes, then round the corners. Smoothing before
        // the resolve would have any arc clipped off at the crossing and left a sharp
        // corner, and sanding must precede rounding so the arc meets clean geometry.
        // Fill and border are the same loops (triangulated vs its boundary loops), so
        // they match exactly whichever passes ran.
        var borderLoops = PolygonTessellator.tessellateToBoundaryLoops(insetRings);
        var borderSmoothing = territories.getGlobalStyle().borderSmoothing();
        if (borderSmoothing.shouldSandSpikes()) {
            borderLoops = BorderSmoothing.sandBorderSpikes(borderLoops);
        }
        if (borderSmoothing.shouldRoundCorners()) {
            borderLoops = BorderSmoothing.roundBorderCorners(borderLoops);
        }
        // The spotlit footprint splits its fill in two inside its one frontier - solid where the
        // bloc dominates, hatched where it is merely present - so the two states read apart without
        // the border fracturing. Every other territory fills its whole region solid, tessellated
        // from the same smoothed loops the border strokes so fill and border match exactly.
        var fill = isSpotlit
                ? computeSpotlitFill(
                        territories,
                        geometryCache,
                        borderTrace,
                        factionId,
                        memberSystemIds,
                        fillColor)
                : new TerritoryFill(
                        fillColor == null
                                ? GlVertexRuns.NO_VERTICES
                                : PolygonTessellator.tessellateToTriangles(borderLoops),
                        GlVertexRuns.NO_VERTICES,
                        GlVertexRuns.NO_VERTICES);
        var borderRuns = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
                borderRuns.add(GlVertexRuns.flattenVertices(loop));
            }
        }
        // The contested borders stroke every interior footprint edge that touches a hatched
        // (contested) cell - the solid<->hatched transitions and the hatched<->hatched divisions - in
        // the bloc's own national-border style (colour, width, opacity), so a contested cell reads as
        // a bounded territory rather than dissolving into the hatch. Two dominated cells fuse with
        // only the faint per-cell interior seam between them, so the solid region stays one nation.
        // The run is empty for every non-spotlit territory.
        return new FactionTerritory(
                fill.solidTriangles(),
                fill.hatchSegments(),
                new UiElementPaint(fillColor, adjustment.muteOpacity(style.fillOpacity())),
                borderRuns,
                fill.contestedBorders(),
                new UiElementPaint(borderColor, adjustment.muteOpacity(style.outerOpacity())),
                (float) style.outerWidth());
    }

    // Splits the spotlit footprint's fill into its two states: the systems the bloc dominates
    // tessellate into the solid triangle soup, the contested systems into their own soup the hatch
    // generator then clips diagonal lines to, and every interior edge touching a contested cell
    // becomes a contested border. Each state fills from its own traced rings rather than from its
    // members' individual cells, so no per-cell inset truncation can leave an unfilled wedge where
    // two members meet at a corner against a rival or empty space. A "No color" fill draws neither
    // region, but the borders still bound the (invisible) pockets.
    private static TerritoryFill computeSpotlitFill(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache,
            PoliticalBorderTrace borderTrace,
            String blocId,
            List<String> memberSystemIds,
            Color fillColor) {
        var contestedMembers = new LinkedHashSet<String>();
        var dominantMembers = new LinkedHashSet<String>();
        for (var systemId : memberSystemIds) {
            (territories.getContestedSystemIds().contains(systemId)
                    ? contestedMembers : dominantMembers).add(systemId);
        }
        var contestedBorders = computeContestedBorders(
                memberSystemIds,
                territories.getContestedSystemIds(),
                geometryCache.getCellEdgesBySystemId());
        if (fillColor == null) {
            return new TerritoryFill(
                    GlVertexRuns.NO_VERTICES,
                    GlVertexRuns.NO_VERTICES,
                    contestedBorders);
        }
        // The two states are traced against each other, so each names the other's members as
        // coincident: their shared boundary lands on the raw cell edge from both sides and the
        // solid and hatched fills meet exactly along the line the contested border strokes.
        var subRegionKeys = mapSubRegionKeyBySystemId(
                territories,
                blocId,
                memberSystemIds,
                contestedMembers);
        var solidTriangles = tessellateSubRegion(
                borderTrace,
                geometryCache,
                dominantMembers,
                subRegionKeys,
                contestedMembers);
        var contestedTriangles = tessellateSubRegion(
                borderTrace,
                geometryCache,
                contestedMembers,
                subRegionKeys, dominantMembers);
        var hatch = territories.getGlobalStyle().hatch();
        var hatchSegments = Hatching.computeHatchSegments(
                contestedTriangles,
                hatch.angleRadians(),
                hatch.spacing());
        return new TerritoryFill(solidTriangles, hatchSegments, contestedBorders);
    }

    // Keys the footprint's two states apart, so the border tracer - which fuses cells sharing a
    // key - traces the dominant and the contested members as separate regions rather than as the
    // one body their shared footprint key makes them. Every system outside the footprint keeps its
    // real key, so an edge from a member to a rival or to empty space classifies exactly as it does
    // when the whole footprint is traced, and the sub-regions' outer edge therefore lands where the
    // national border draws it. Suffixing the footprint's own key leaves the derived keys as
    // collision-free as it already is.
    private static Map<String, String> mapSubRegionKeyBySystemId(
            PoliticalMapTerritories territories,
            String blocId,
            List<String> memberSystemIds,
            Set<String> contestedMembers) {
        var keys = new HashMap<String, String>(
                DominantOwner.mapFactionIdBySystemId(territories.getOwnerBySystemId()));
        for (var systemId : memberSystemIds) {
            keys.put(systemId, blocId + (contestedMembers.contains(systemId)
                    ? CONTESTED_SUB_REGION_SUFFIX
                    : DOMINANT_SUB_REGION_SUFFIX));
        }
        return keys;
    }

    // Tessellates one of the footprint's states into a GL_TRIANGLES soup from the rings tracing
    // its members as a single region, so a state fills as one continuous area with no per-cell
    // seam or truncation inside it. The other state's members are the coincident neighbours, whose
    // shared edge insets by nothing so the two states abut with no channel between them. The rings
    // are tessellated exactly as traced - unsmoothed - since the smoothing the national border runs
    // would pull the fill off the border it is drawn under. Empty when the state holds no members
    // or the trace yields no drawable ring.
    private static float[] tessellateSubRegion(
            PoliticalBorderTrace borderTrace,
            PoliticalMapGeometryCache geometryCache,
            Set<String> subRegionSystemIds,
            Map<String, String> subRegionKeyBySystemId,
            Set<String> coincidentSystemIds) {
        if (subRegionSystemIds.isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        var rings = borderTrace.traceRings(
                subRegionSystemIds,
                geometryCache.getCellEdgesBySystemId(),
                subRegionKeyBySystemId,
                coincidentSystemIds);
        if (rings.isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        return PolygonTessellator.tessellateToTriangles(rings);
    }

    // Every interior footprint edge that touches a contested cell, as a GL_LINES run: the
    // dominant<->contested transitions (where the solid fill meets the hatched one) and the
    // contested<->contested divisions (between two joined hatched cells). These are stroked in the
    // national-border style so a hatched cell reads as a bounded territory; two dominated cells,
    // whose shared edge touches no contested cell, are omitted and fuse with only the faint per-cell
    // interior seam between them. Only an edge between two footprint members is interior; an edge to
    // an outside system (or to empty space) is the footprint's own national border, traced
    // elsewhere. Voronoi adjacency is symmetric, so each shared edge is seen from both of its cells -
    // it is emitted once, from the lexicographically smaller system id, so a contested<->contested
    // division is not stroked twice. These same-key edges are not inset, so they sit on the true
    // cell border where the cells meet - exactly where the border should stroke.
    static float[] computeContestedBorders(
            List<String> memberSystemIds,
            Set<String> contestedSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId) {
        var memberSystemIdSet = new HashSet<>(memberSystemIds);
        var coordinates = new ArrayList<Float>();
        for (var systemId : memberSystemIds) {
            var edges = edgesBySystemId.get(systemId);
            if (edges == null) {
                continue;
            }
            var isThisContested = contestedSystemIds.contains(systemId);
            for (var edge : edges) {
                var neighbourSystemId = edge.neighbourSystemId();
                // An edge to empty space or to a system outside the footprint is the national
                // border, not an interior division.
                if (neighbourSystemId == null || !memberSystemIdSet.contains(neighbourSystemId)) {
                    continue;
                }
                // Walk each shared edge from its smaller-id cell only, so a division seen from both
                // cells is stroked once.
                if (systemId.compareTo(neighbourSystemId) >= 0) {
                    continue;
                }
                // A border reads only where a contested cell is involved; two dominant cells fuse
                // into the solid region with only their faint per-cell interior seam.
                if (!isThisContested && !contestedSystemIds.contains(neighbourSystemId)) {
                    continue;
                }
                coordinates.add((float) edge.x1());
                coordinates.add((float) edge.y1());
                coordinates.add((float) edge.x2());
                coordinates.add((float) edge.y2());
            }
        }
        return GlVertexRuns.packFloats(coordinates);
    }

    // The spotlit footprint's fill split into its two painted regions plus the contested borders
    // dividing them: the solid triangle soup for the dominated cells, the hatch GL_LINES for the
    // contested cells, and the contested-border GL_LINES on every interior edge touching a contested
    // cell. A non-spotlit territory carries only its solid region, the other two empty.
    private record TerritoryFill(
            float[] solidTriangles,
            float[] hatchSegments,
            float[] contestedBorders) {
    }

    // The base style and per-bloc adjustment a bloc draws under this pass, shared by the per-cell
    // and per-faction builders so both style a bloc identically. Maps the shared style decision -
    // the same call the label path reads, so a bloc's name never drifts from its fill - onto this
    // pass's two concrete styles: the independent style when the decision recedes a bloc to it,
    // the faction style otherwise.
    private static BlocStyling resolveBlocStyling(
            PoliticalMapTerritories territories,
            String blocId) {
        var decision = BlocStyleResolver.resolveBlocStyleDecision(
                territories.isFiltering(),
                blocId,
                territories.getView(),
                territories.getGrouping(),
                territories.getRecedeAdjustment());
        var style = decision.usesIndependentStyle()
                ? territories.getCategoryStyle(MapCategory.INDEPENDENT)
                : territories.getCategoryStyle(MapCategory.FACTION);
        return new BlocStyling(style, decision.adjustment());
    }

    // Builds one cell's per-cell draw record: its interior seams always, plus - only
    // when {@code perCellFillAndBorder} - a fill and an outer outline. An owned cell
    // passes false: its fill and national border come per cluster from the tessellated
    // region, so it contributes only its seams here. A factionless cell passes true and
    // the neutral color for both palette shades: it does not fuse into a cluster, so it
    // keeps its own inset fill and outline. That outline's corners are rounded - with the
    // same corner settings and gate the cluster borders use, so a lone dead system reads
    // as smoothly as a cluster when rounding is on and stays a sharp Voronoi cell when it
    // is off. Each color resolves against the two palette shades, null for a "No color"
    // choice, so the draw pass skips it. Every seam's opacity is muted by the caller's
    // resolved per-bloc {@code adjustment} through its one muteOpacity rule (BlocStyleAdjustment.NONE
    // for a factionless cell, which carries no adjustment, so its opacity passes unchanged).
    private static StyledCell buildStyledCell(
            ShapedCell shaped,
            Color primaryColor,
            Color secondaryColor,
            CategoryStyle style,
            boolean perCellFillAndBorder,
            BlocStyleAdjustment adjustment,
            BorderSmoothingStyle borderSmoothing) {
        // Gate the corner rounding outside the round call: on rounds this cell's outline
        // in step with the cluster borders, off leaves its raw inset outline.
        var outline = shaped.fillPolygon();
        if (perCellFillAndBorder && borderSmoothing.shouldRoundCorners()) {
            outline = roundCellOutline(shaped, borderSmoothing);
        }
        return new StyledCell(
                perCellFillAndBorder
                        ? GlVertexRuns.flattenVertices(shaped.fillPolygon())
                        : GlVertexRuns.NO_VERTICES,
                perCellFillAndBorder
                        ? GlVertexRuns.flattenClosedLoopAsSegments(outline)
                        : GlVertexRuns.NO_VERTICES,
                VertexRuns.flattenEdgesOfClass(shaped, false),
                new UiElementPaint(
                        perCellFillAndBorder
                                ? MapPalettes.pickPaletteColor(style.fillColor(), primaryColor,
                                        secondaryColor)
                                : null,
                        adjustment.muteOpacity(style.fillOpacity())),
                new UiElementPaint(
                        perCellFillAndBorder
                                ? MapPalettes.pickPaletteColor(style.outerColor(), primaryColor,
                                        secondaryColor)
                                : null,
                        adjustment.muteOpacity(style.outerOpacity())),
                new UiElementPaint(
                        MapPalettes.pickPaletteColor(style.innerColor(), primaryColor,
                                secondaryColor),
                        adjustment.muteOpacity(style.innerOpacity())),
                (float) style.outerWidth(), (float) style.innerWidth());
    }

    // Rounds a factionless cell's inset outline with the same corner settings the
    // cluster borders use, so its border reads consistently. The cell is a single
    // convex inset polygon (all its edges are national border), so it needs no chaining
    // or envelope resolve - only the corner rounding. The caller gates this on the
    // corner-rounding switch.
    private static List<double[]> roundCellOutline(
            ShapedCell shaped,
            BorderSmoothingStyle borderSmoothing) {
        return PolygonSmoothing.roundCorners(
                shaped.fillPolygon(),
                borderSmoothing.cornerRadius(),
                borderSmoothing.cornerSegments(),
                borderSmoothing.chamferAngleRadians());
    }

    // The base style plus per-bloc adjustment a bloc draws under, resolved once and read by both
    // the fill and border and the interior seams so they never diverge.
    private record BlocStyling(CategoryStyle style, BlocStyleAdjustment adjustment) {
    }
}
