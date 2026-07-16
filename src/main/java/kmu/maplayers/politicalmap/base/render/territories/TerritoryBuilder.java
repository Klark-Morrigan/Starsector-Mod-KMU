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
import java.util.LinkedHashMap;
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
    public static PoliticalMapTerritories buildTerritories(PoliticalMapGeometryCache geometryCache,
            SectorAPI sector, PoliticalMapView view) {
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
            // Spotlighting Independent under the Independent desaturation profile would collapse
            // the spotlit fill and the receded background to one colour (both resolve to the
            // Independent palette), so the profile falls back to Neutral in that one case before
            // the palette is resolved; every other spotlight and the Neutral profile are untouched.
            var desaturationProfile = MapPalettes.resolveSpotlightSafeDesaturationProfile(
                    renderStyle.global().desaturationProfile(), selectedBlocId);
            var desaturationPalette = MapPalettes.resolveDesaturationPalette(
                    desaturationProfile, sector, neutralColor);
            // The styling every non-spotlighted bloc recedes to, resolved once from the filter recede
            // toggles - the "rest of the sector" set, shared across both views under a filter; the
            // identity adjustment off filter, so a normal pass touches no bloc.
            var recedeAdjustment = isFiltering
                    ? RecedePreferences.FILTER.resolveRecedeAdjustment()
                    : BlocStyleAdjustment.NONE;
            var territories = new PoliticalMapTerritories(
                    new LinkedHashMap<>(), new LinkedHashMap<>(),
                    ownerBySystemId, decivilisedSystemIds,
                    neutralColor, desaturationPalette, renderStyle,
                    view, grouping, selectedBlocId, recedeAdjustment, contestedSystemIds);

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
                    () -> buildAllFactionTerritories(territories, geometryCache, shapedCells));

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
    public static StyledCell buildStyledCellForSystem(PoliticalMapTerritories territories,
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
            // The spotlighted bloc paints its whole footprint from one territory: the solid and
            // hatched fills, the single frontier, and the solid<->hatched transition seam all live
            // on the FactionTerritory. Its cells' same-style internal seams fuse away, so a spotlit
            // cell contributes nothing here (its only per-cell element would be those vanished
            // seams). Every other bloc - real owners receding under the filter, or all of them off
            // filter - keeps its normal interior seams.
            if (FilteredPolitics.isSpotlitBloc(owner.factionId())) {
                return null;
            }
            // The base style and per-bloc adjustment come from the pass's styling resolver -
            // the active view off filter, the spotlight rules under one. The adjustment dims
            // and/or recolours the cell: desaturating swaps the owner's own palette for the
            // pass's shared desaturation palette, and the opacity multiplier scales every seam
            // alpha on top of the style's own opacities. The fill and national border are per
            // cluster from the tessellated region, so an owned cell contributes only its
            // interior seams here.
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
        return buildStyledCell(shaped, neutralColor, neutralColor, style, true,
                BlocStyleAdjustment.NONE,
                territories.getGlobalStyle().borderSmoothing());
    }

    // Builds every owned faction's territory into the territories, keyed by faction id.
    // Each faction is independent - its cluster(s) trace only its own cells - so the
    // incremental refresh rebuilds one faction's entry without touching the rest. The
    // already-shaped cells are handed along so the spotlit fill split reuses them rather
    // than re-shaping (the incremental refresh, never a spotlit build, passes none).
    private static void buildAllFactionTerritories(PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache, Map<String, ShapedCell> shapedCellBySystemId) {
        for (var faction
                : DominantOwner.groupSystemIdsByFactionId(territories.getOwnerBySystemId()).entrySet()) {
            var territory = buildFactionTerritory(territories, geometryCache,
                    faction.getKey(), faction.getValue(), shapedCellBySystemId);
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
    public static FactionTerritory buildFactionTerritory(PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache, String factionId,
            List<String> memberSystemIds, Map<String, ShapedCell> shapedCellBySystemId) {
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
        var palette = MapPalettes.resolveEffectivePalette(adjustment, owner,
                territories.getDesaturationPalette());
        var fillColor = MapPalettes.pickPaletteColor(
                style.fillColor(), palette.primaryColor(), palette.secondaryColor());
        var borderColor = MapPalettes.pickPaletteColor(
                style.outerColor(), palette.primaryColor(), palette.secondaryColor());
        if (fillColor == null && borderColor == null) {
            return null;
        }
        var insetRings = PoliticalBorderTrace.readFromLunaSettings().traceRings(memberSystemIds,
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
        // The spotlit footprint splits its fill per cell inside its one frontier - solid where the
        // bloc dominates, hatched where it is merely present - so the two states read apart without
        // the border fracturing. Every other territory fills its whole region solid, tessellated
        // from the same smoothed loops the border strokes so fill and border match exactly.
        var fill = isSpotlit
                ? computeSpotlitFill(territories, geometryCache, memberSystemIds,
                        shapedCellBySystemId, fillColor)
                : new TerritoryFill(fillColor == null
                        ? GlVertexRuns.NO_VERTICES
                        : PolygonTessellator.tessellateToTriangles(borderLoops),
                        GlVertexRuns.NO_VERTICES, GlVertexRuns.NO_VERTICES);
        var borderRuns = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
                borderRuns.add(GlVertexRuns.flattenVertices(loop));
            }
        }
        // The transition seam strokes the solid<->hatched boundary in the bloc's secondary shade at
        // the inner-seam width, reusing the inner-seam element so the contested pocket reads as a
        // bounded region; it is null-coloured (hidden) for every non-spotlit territory.
        return new FactionTerritory(fill.solidTriangles(), fill.hatchSegments(),
                new UiElementPaint(fillColor, adjustment.muteOpacity(style.fillOpacity())),
                fill.transitionSeams(),
                new UiElementPaint(isSpotlit ? palette.secondaryColor() : null,
                        adjustment.muteOpacity(style.innerOpacity())),
                (float) style.innerWidth(),
                borderRuns,
                new UiElementPaint(borderColor, adjustment.muteOpacity(style.outerOpacity())),
                (float) style.outerWidth());
    }

    // Splits the spotlit footprint's fill per cell: the systems the bloc dominates tessellate into
    // the solid triangle soup, the contested systems into their own soup the hatch generator then
    // clips diagonal lines to, and the edges between the two become the transition seam. Each cell
    // is tessellated on its own inset shape - the very shape the pass already built, reused here
    // rather than re-shaped. Same-key neighbours leave their shared edge on the true cell border,
    // so adjacent same-state cells' fills meet exactly and read as fused, and a dominated cell
    // meets a contested one along the raw edge the seam then strokes. A "No color" fill draws
    // neither region, but the seam still bounds the (invisible) pocket.
    private static TerritoryFill computeSpotlitFill(PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache, List<String> memberSystemIds,
            Map<String, ShapedCell> shapedCellBySystemId, Color fillColor) {
        var contestedSystemIds = territories.getContestedSystemIds();
        var transitionSeams = computeTransitionSeams(memberSystemIds, contestedSystemIds,
                geometryCache.getCellEdgesBySystemId());
        if (fillColor == null) {
            return new TerritoryFill(GlVertexRuns.NO_VERTICES, GlVertexRuns.NO_VERTICES,
                    transitionSeams);
        }
        var solidRuns = new ArrayList<float[]>();
        var contestedRuns = new ArrayList<float[]>();
        for (var systemId : memberSystemIds) {
            var cellTriangles = tessellateMemberCell(shapedCellBySystemId.get(systemId));
            (contestedSystemIds.contains(systemId) ? contestedRuns : solidRuns).add(cellTriangles);
        }
        var hatch = territories.getGlobalStyle().hatch();
        var hatchSegments = Hatching.computeHatchSegments(concatenateRuns(contestedRuns),
                hatch.angleRadians(), hatch.spacing());
        return new TerritoryFill(concatenateRuns(solidRuns), hatchSegments, transitionSeams);
    }

    // Tessellates one spotlit member's already-shaped inset cell into a GL_TRIANGLES soup, so the
    // footprint's fill is the union of its cells rather than one region cut from the traced rings -
    // which lets the dominant and contested cells fill in separate soups. Empty when the cell was
    // not shaped (absent) or the inset consumed it (an empty fill polygon).
    private static float[] tessellateMemberCell(ShapedCell shaped) {
        if (shaped == null || shaped.fillPolygon().isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        return PolygonTessellator.tessellateToTriangles(List.of(shaped.fillPolygon()));
    }

    // The edges between a dominant and a contested spotlit cell, as a GL_LINES run. Walks each
    // dominant member (so every transition edge is collected once, from the dominant side) and
    // keeps the edges whose neighbour is a contested spotlit system. These same-key edges are not
    // inset, so they sit on the true cell border where the solid and hatched fills meet - exactly
    // where the seam should stroke.
    static float[] computeTransitionSeams(List<String> memberSystemIds,
            Set<String> contestedSystemIds, Map<String, List<CellEdge>> edgesBySystemId) {
        var coordinates = new ArrayList<Float>();
        for (var systemId : memberSystemIds) {
            if (contestedSystemIds.contains(systemId)) {
                continue;
            }
            var edges = edgesBySystemId.get(systemId);
            if (edges == null) {
                continue;
            }
            for (var edge : edges) {
                if (edge.neighbourSystemId() != null
                        && contestedSystemIds.contains(edge.neighbourSystemId())) {
                    coordinates.add((float) edge.x1());
                    coordinates.add((float) edge.y1());
                    coordinates.add((float) edge.x2());
                    coordinates.add((float) edge.y2());
                }
            }
        }
        return GlVertexRuns.packFloats(coordinates);
    }

    // Concatenates a list of GL vertex runs into one run, since a triangle soup is order-agnostic:
    // separate cells tessellate independently and their soups append into the footprint's fill.
    private static float[] concatenateRuns(List<float[]> runs) {
        var total = 0;
        for (var run : runs) {
            total += run.length;
        }
        var concatenated = new float[total];
        var offset = 0;
        for (var run : runs) {
            System.arraycopy(run, 0, concatenated, offset, run.length);
            offset += run.length;
        }
        return concatenated;
    }

    // The spotlit footprint's fill split into its two painted regions plus the seam between them:
    // the solid triangle soup for the dominated cells, the hatch GL_LINES for the contested cells,
    // and the transition GL_LINES where they meet. A non-spotlit territory carries only its solid
    // region, the other two empty.
    private record TerritoryFill(float[] solidTriangles, float[] hatchSegments,
            float[] transitionSeams) {
    }

    // The base style and per-bloc adjustment a bloc draws under this pass, shared by the per-cell
    // and per-faction builders so both style a bloc identically. Maps the shared style decision -
    // the same call the label path reads, so a bloc's name never drifts from its fill - onto this
    // pass's two concrete styles: the independent style when the decision recedes a bloc to it,
    // the faction style otherwise.
    private static BlocStyling resolveBlocStyling(PoliticalMapTerritories territories, String blocId) {
        var decision = BlocStyleResolver.resolveBlocStyleDecision(territories.isFiltering(), blocId,
                territories.getView(), territories.getGrouping(), territories.getRecedeAdjustment());
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
    private static StyledCell buildStyledCell(ShapedCell shaped, Color primaryColor,
            Color secondaryColor, CategoryStyle style, boolean perCellFillAndBorder,
            BlocStyleAdjustment adjustment, BorderSmoothingStyle borderSmoothing) {
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
    private static List<double[]> roundCellOutline(ShapedCell shaped,
            BorderSmoothingStyle borderSmoothing) {
        return PolygonSmoothing.roundCorners(shaped.fillPolygon(),
                borderSmoothing.cornerRadius(),
                borderSmoothing.cornerSegments(),
                borderSmoothing.chamferAngleRadians());
    }

    // The base style plus per-bloc adjustment a bloc draws under, resolved once and read by both
    // the fill and border and the interior seams so they never diverge.
    private record BlocStyling(CategoryStyle style, BlocStyleAdjustment adjustment) {
    }
}
