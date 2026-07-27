package kmu.maplayers.politicalmap.base.render.territories;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.math.geometry.PolygonSmoothing;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.profiling.Timings;
import kmlib.starsector.factions.StarsectorFactionColors;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.geometry.CellGrouping;
import kmu.maplayers.politicalmap.base.geometry.CellShaper;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.maplayers.politicalmap.base.render.PoliticalBorderTrace;
import kmu.maplayers.politicalmap.base.render.style.BlocStyleResolver;
import kmu.maplayers.politicalmap.base.render.style.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.ElementStyle;
import kmu.maplayers.politicalmap.base.render.style.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.PoliticalMapStyle;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;

import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

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

    // Shapes the cached raw cells into merged clusters and partitions them into the
    // cluster-filled (owned) and per-cell (decivilised/uninhabited) draw lists, baking in each
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

            // The spotlighted bloc, read once so the whole pass keys off one snapshot - the
            // ownership provider (which keeps a spotlit bloc drawn wherever it is present), the
            // recede the rest of the sector takes, and the retained filter snapshot all resolve
            // from this one read, exactly like the grouping.
            var selectedBlocId = FilterSelection.getSelectedBlocId(view.getId());
            var isFiltering = selectedBlocId != null;

            // The politics scan walks the whole economy - the priciest content step -
            // so it is profiled and timed on its own, and the owner count logged
            // independent of the profiler's accumulated view. Under a filter it also reports
            // which spotlit systems are contested, since the whole spotlit footprint shares one
            // key and that set is the only record of the dominant/contested split.
            var politicsStart = System.nanoTime();
            var resolution = profiler.measure("politicalMap.resolvePolitics",
                    () -> view.resolveOwnershipProvider()
                            .resolveOwnership(sector, grouping, selectedBlocId));
            var ownerBySystemId = resolution.ownerBySystemId();
            var contestedSystemIds = resolution.contestedSystemIds();

            // The owned systems this resolution paints no fill for - held by their bloc but drawn
            // empty inside its one border. Empty for the faction/alliance and filter paths today;
            // the split reads it so a source that populates it needs no further wiring.
            var unfilledSystemIds = resolution.unfilledSystemIds();
            LOG.debug("Political map politics resolved; ownedSystems=" + ownerBySystemId.size()
                    + " filtering=" + isFiltering + " contested=" + contestedSystemIds.size()
                    + " unfilled=" + unfilledSystemIds.size()
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
                    unfilledSystemIds,
                    new MapStyling(renderStyle, neutralColor, desaturationPalette),
                    new ViewGrouping(view, grouping),
                    new FilterSnapshot(selectedBlocId, recedeAdjustment, contestedSystemIds));

            // Shape the raw cells into merged clusters once, ownership-aware. The agnostic
            // geometry clusters by grouping key, so hand it each system's faction id as the
            // key. Cells consumed by the inset (fewer than three vertices left) drop out.
            var shapeStart = System.nanoTime();
            var cellGrouping = resolveCellGrouping(territories, geometryCache);
            var shapedCells = profiler.measure("politicalMap.shapeCells",
                    () -> CellShaper.shapeCells(geometryCache.getCellEdgesByCellId(),
                            cellGrouping, PoliticalMapStyle.BORDER_INSET_DISTANCE));
            for (var entry : shapedCells.entrySet()) {
                var styled = buildStyledCellForSystem(
                        territories,
                        cellGrouping.resolveDrawnSystemIdOf(entry.getKey()),
                        entry.getValue());
                if (styled != null) {
                    territories.putStyledCell(
                            entry.getKey(),
                            styled,
                            entry.getValue().fillPolygon());
                }
            }
            // The clusters the cursor read resolves a hovered cell's whole territory through.
            // Derived here off the same keys the shaping just fused the cells by, so a highlighted
            // territory is exactly the one the map merged into a single region.
            territories.reindexClusters(
                    geometryCache.getCellEdgesByCellId(),
                    geometryCache.getSystemIdByCellId());

            // Each owned faction's territory: one region per cluster (traced across all
            // its cells so a multi-system cluster reads as one frontier), tessellated for
            // the fill and flattened for the border - the same shape for both. Built off
            // the same raw cells and owners the seams used, and profiled on its own since
            // chaining, smoothing, and tessellating every faction's outline is comparable
            // in cost to shaping the cells.
            profiler.measure("politicalMap.buildFactionTerritories",
                    () -> buildAllFactionTerritories(territories, geometryCache));

            LOG.debug("Political map cells shaped; shaped=" + shapedCells.size()
                    + " styledCells=" + territories.getStyledCellByCellId().size()
                    + " factionTerritories=" + territories.getFactionTerritoryByFactionId().size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - shapeStart));
            return territories;
        });
    }

    // Builds one cell's draw record from its shaped cell, or null when the
    // cell draws nothing: an inset-collapsed cell, or a factionless cell with neither its
    // fill nor its outline drawn. An owned cell keeps only its interior seams (its fill and
    // national border are per-cluster, in factionTerritories); a factionless cell keeps its
    // own inset fill and outline, since factionless cells do not fuse. Shared by the full
    // rebuild and the incremental re-shape so both classify a cell identically.
    //
    // Takes the system the cell draws as rather than the cell itself, since everything it
    // reads - the owner, the palette, whether the ground is decivilised - is known per
    // system and not per cell. A cell with no system of its own passes null and draws as
    // plain uninhabited ground: it has no owner to colour it and no market to have died.
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
        // A cell with no star of its own has no owner to look up, so the null id skips the owner
        // map rather than probing it for a key it does not hold - keeping the null-star path clear
        // of whether the owner map happens to tolerate a null-key get.
        var owner = systemId == null ? null : territories.getOwnerBySystemId().get(systemId);
        if (owner != null) {
            // Every owned cell - the spotlighted bloc included - contributes only its interior
            // seams here; its fill and national border come per cluster from the tessellated
            // region. A spotlit cell is no exception: keeping its seams is what lets the footprint
            // read as its constituent cells rather than one smooth blob, so wherever two of the
            // bloc's cells meet across a real cell border the seam between them still draws - the
            // solid<->hatched transition included, which is such a border like any other.
            // The base style and per-bloc adjustment come from the pass's styling resolver -
            // the active view off filter, the spotlight rules under one. The adjustment dims
            // and/or recolours the cell: desaturating swaps the owner's own palette for the
            // pass's shared desaturation palette, and the opacity multiplier scales every seam
            // alpha on top of the style's own opacities.
            var styling = resolveBlocStyling(territories, owner.factionId());
            var palette = MapPalettes.resolveEffectivePalette(
                    styling.adjustment(),
                    owner,
                    territories.getDesaturationPalette());
            return buildStyledCell(
                    shaped,
                    palette.primaryColor(),
                    palette.secondaryColor(),
                    styling.style(),
                    false,
                    styling.adjustment(),
                    territories.getGlobalStyle().borderSmoothing());
        }
        // Factionless: decivilised or (otherwise) uninhabited. Its style names no faction
        // palette, so both slots resolve to the shared neutral color, and it draws its own
        // per-cell fill and outline; drop it only when neither of those puts ink down, since
        // a cell is kept for its fill as readily as for its outline. Factionless cells are not
        // blocs the view classifies, so they never carry an adjustment.
        var style = systemId != null && territories.getDecivilisedSystemIds().contains(systemId)
                ? territories.getCategoryStyle(MapCategory.DECIVILISED)
                : territories.getCategoryStyle(MapCategory.UNINHABITED);

        if (!style.outer().isDrawn() && !style.fill().isDrawn()) {
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
            List<String> memberCellIds) {

        var cellGrouping = resolveCellGrouping(territories, geometryCache);

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

        // How each of this territory's members fills: solid, hatched (a spotlit bloc's contested
        // systems), or unfilled (systems held for border and label but drawn empty). Resolved once
        // so both the fast-path decision and the split read the same partition.
        var fillSplit = FillSplit.splitMembersByFillState(
                cellGrouping,
                memberCellIds,
                territories.getContestedSystemIds(),
                territories.getUnfilledSystemIds());

        // Every system of a faction shares its palette, so any member resolves the same
        // fill and border colors.
        var owner = territories.getOwnerBySystemId()
                .get(cellGrouping.resolveDrawnSystemIdOf(memberCellIds.get(0)));
        var palette = MapPalettes.resolveEffectivePalette(
                adjustment,
                owner,
                territories.getDesaturationPalette());
        var fillColor = MapPalettes.pickPaletteColor(
                style.fill().color(),
                palette.primaryColor(),
                palette.secondaryColor());
        var borderColor = MapPalettes.pickPaletteColor(
                style.outer().color(),
                palette.primaryColor(),
                palette.secondaryColor());
        if (fillColor == null && borderColor == null) {
            return null;
        }

        // One trace for the whole territory: the national border's rings, and - for a spotlit
        // footprint - the per-state sub-region rings its fill splits into, so border and fill
        // offset under identical parameters and cannot drift apart.
        var borderTrace = PoliticalBorderTrace.readFromLunaSettings();
        var insetRings = borderTrace.traceRings(
                memberCellIds,
                geometryCache.getCellEdgesByCellId(),
                cellGrouping);
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
        // The fill, built off the same smoothed loops the border below strokes so the two match
        // exactly. Whether it takes the per-state split or fills as one solid region is the fill
        // builder's own call, made from the footprint's partition and whether it is spotlit.
        var fill = new SplitFillBuilder(
                territories,
                geometryCache,
                cellGrouping,
                borderTrace,
                borderLoops)
                .buildFill(isSpotlit, fillSplit, factionId, fillColor);

        var borderRuns = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
                borderRuns.add(GlVertexRuns.flattenVertices(loop));
            }
        }
        return new FactionTerritory(
                fill.solidTriangles(),
                fill.hatchSegments(),
                new UiElementPaint(
                        fillColor,
                        adjustment.muteOpacity(style.fill().opacity())),
                borderRuns,
                new UiElementPaint(
                        borderColor,
                        adjustment.muteOpacity(style.outer().opacity())),
                (float) style.outerWidth());
    }

    // Builds every owned faction's territory into the territories, keyed by faction id.
    // Each faction is independent - its cluster(s) trace only its own cells - so the
    // incremental refresh rebuilds one faction's entry without touching the rest.
    //
    // Membership is the cells a faction draws, not the systems it holds: those differ
    // wherever a faction's ground includes a cell no star of its own sits in, and it is the
    // cells that carry the edges a border is traced from.
    private static void buildAllFactionTerritories(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache) {
        for (var faction
                : resolveCellGrouping(territories, geometryCache)
                        .groupCellIdsByKey()
                        .entrySet()) {
            var territory = buildFactionTerritory(
                    territories,
                    geometryCache,
                    faction.getKey(),
                    faction.getValue());
            if (territory != null) {
                territories
                        .getFactionTerritoryByFactionId()
                        .put(faction.getKey(), territory);
            }
        }
    }

    // The drawn cells' grouping this pass shapes and traces against: which system each cell draws
    // as, off the geometry cache, paired with each owned system's faction id. Resolved once per
    // pass so every stage groups the cells identically.
    private static CellGrouping resolveCellGrouping(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache) {
        return DominantOwner.mapCellGrouping(
                geometryCache.getSystemIdByCellId(),
                territories.getOwnerBySystemId());
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
        var factionStyle = territories.getCategoryStyle(MapCategory.FACTION);
        if (!decision.usesIndependentStyle()) {
            return new BlocStyling(factionStyle, decision.adjustment());
        }
        return new BlocStyling(
                applyDesaturatedFillOpacity(
                        territories.getCategoryStyle(MapCategory.INDEPENDENT),
                        factionStyle,
                        decision.adjustment()),
                decision.adjustment());
    }

    // Holds every desaturated bloc's ground at the one faction fill opacity, so a sector drawn
    // under desaturation reads as a single uniform surface separated by colour alone rather than
    // by two fill weights. Independent space carries a lighter fill of its own so it recedes
    // behind faction ground when the map paints in full colour, but once desaturation has already
    // sunk a bloc to the shared grey that second cue only fractures the background: neighbouring
    // greys at different weights read as two kinds of empty. The rest of the independent bundle -
    // both border opacities and both widths - still applies, since those distinguish independent
    // ground without breaking the fill's uniformity.
    private static CategoryStyle applyDesaturatedFillOpacity(
            CategoryStyle independentStyle,
            CategoryStyle factionStyle,
            BlocStyleAdjustment adjustment) {
        if (!adjustment.desaturate()) {
            return independentStyle;
        }
        return new CategoryStyle(
                new ElementStyle(
                        independentStyle.fill().color(),
                        factionStyle.fill().opacity()),
                independentStyle.outer(),
                independentStyle.outerWidth(),
                independentStyle.inner(),
                independentStyle.innerWidth());
    }

    // Builds one cell's per-cell draw record: its interior seams always, plus - only
    // when {@code perCellFillAndBorder} - a fill and an outer outline. An owned cell
    // passes false: its fill and national border come per cluster from the tessellated
    // region, so it contributes only its seams here. A factionless cell passes true and
    // the neutral color for both palette shades: it does not fuse into a cluster, so it
    // keeps its own inset fill and outline. That outline's corners are rounded - with the
    // same corner settings and gate the cluster borders use, so a lone dead system reads
    // as smoothly as a cluster when rounding is on and stays a sharp Voronoi cell when it
    // is off - and the fill is triangulated from that same rounded ring, so it cannot spill
    // past the line its own outline strokes. Each color resolves against the two palette
    // shades, null for a "No color"
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
        // Tessellate the fill only when it will actually be painted: uninhabited ground is
        // outline-only and covers most of the sector, so triangulating every one of its cells
        // for a fill no pass emits would be the map's largest wasted rebuild cost.
        var isFillDrawn = perCellFillAndBorder && style.fill().isDrawn();
        return new StyledCell(
                isFillDrawn
                        ? PolygonTessellator.tessellateToTriangles(List.of(outline))
                        : GlVertexRuns.NO_VERTICES,
                perCellFillAndBorder
                        ? GlVertexRuns.flattenClosedLoopAsSegments(outline)
                        : GlVertexRuns.NO_VERTICES,
                VertexRuns.flattenEdgesOfClass(shaped, false),
                new UiElementPaint(
                        perCellFillAndBorder
                                ? MapPalettes.pickPaletteColor(
                                        style.fill().color(),
                                        primaryColor,
                                        secondaryColor)
                                : null,
                        adjustment.muteOpacity(style.fill().opacity())),
                new UiElementPaint(
                        perCellFillAndBorder
                                ? MapPalettes.pickPaletteColor(
                                        style.outer().color(),
                                        primaryColor,
                                        secondaryColor)
                                : null,
                        adjustment.muteOpacity(style.outer().opacity())),
                new UiElementPaint(
                        MapPalettes.pickPaletteColor(
                                style.inner().color(),
                                primaryColor,
                                secondaryColor),
                        adjustment.muteOpacity(style.inner().opacity())),
                (float) style.outerWidth(),
                (float) style.innerWidth());
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
