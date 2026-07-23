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
import java.util.HashMap;
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
    // The suffixes that split a bloc's one grouping key into a key per fill state, so the border
    // tracer traces the solid, hatched, and unfilled members as separate regions rather than the
    // one body their shared key makes them. Appended to the bloc's own key, which already carries a
    // sentinel prefix no real bloc id can hold, so no derived key can collide with a rival's.
    private static final String SOLID_SUB_REGION_SUFFIX = "#solid";
    private static final String HATCHED_SUB_REGION_SUFFIX = "#hatched";
    private static final String UNFILLED_SUB_REGION_SUFFIX = "#unfilled";

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

            // Snapshot every system's star-icon geometry once for the whole build, so the cursor's
            // icon gate reads a static value by id rather than resolving a system each frame. It is
            // captured here, off the sector, not paired into the per-cell writes below, because it
            // never changes as cells re-shape.
            captureStarIconGeometry(territories, sector);

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
    // cell draws nothing: an inset-collapsed cell, or a factionless cell whose outline
    // is "No color". An owned cell keeps only its interior seams (its fill and national
    // border are per-cluster, in factionTerritories); a factionless cell keeps its own
    // inset fill and outline, since factionless cells do not fuse. Shared by the full
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
        var style = systemId != null && territories.getDecivilisedSystemIds().contains(systemId)
                ? territories.getCategoryStyle(MapCategory.DECIVILISED)
                : territories.getCategoryStyle(MapCategory.UNINHABITED);
        if (!style.outer().isDrawn()) {
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
        var fillSplit = splitMembersByFillState(
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
        // A territory whose members do not all fill solid splits its fill per state inside its one
        // frontier - solid where the bloc holds, hatched where it is present but dominated, empty
        // where it is held-but-unfilled - so the states read apart without the border fracturing. A
        // spotlit bloc always splits (its fill is per-state even when it dominates everywhere it is
        // present); every other territory splits only when it has non-solid members. A territory
        // that fills solid throughout tessellates its whole region from the same smoothed loops the
        // border strokes, so fill and border match exactly.
        var fill = isSpotlit || fillSplit.hasNonSolidMembers()
                ? computeSplitFill(
                        territories,
                        geometryCache,
                        cellGrouping,
                        borderTrace,
                        borderLoops,
                        factionId,
                        fillSplit,
                        fillColor)
                : new TerritoryFill(
                        fillColor == null
                                ? GlVertexRuns.NO_VERTICES
                                : PolygonTessellator.tessellateToTriangles(borderLoops),
                        GlVertexRuns.NO_VERTICES);
        var borderRuns = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
                borderRuns.add(GlVertexRuns.flattenVertices(loop));
            }
        }
        return new FactionTerritory(
                fill.solidTriangles(),
                fill.hatchSegments(),
                new UiElementPaint(fillColor, adjustment.muteOpacity(style.fill().opacity())),
                borderRuns,
                new UiElementPaint(borderColor, adjustment.muteOpacity(style.outer().opacity())),
                (float) style.outerWidth());
    }

    // The fill state one member's system draws in - the pure rule the split turns on, free of
    // geometry so it can be exercised on plain id sets. Unfilled takes precedence over hatched,
    // since a system drawn empty is empty however dominance falls; a cell with no star of its own
    // has no per-system fill state, so it fills solid with the bloc's held ground.
    static FillState classifyFillState(
            String systemId, Set<String> contestedSystemIds, Set<String> unfilledSystemIds) {
        if (systemId == null) {
            return FillState.SOLID;
        }
        if (unfilledSystemIds.contains(systemId)) {
            return FillState.UNFILLED;
        }
        if (contestedSystemIds.contains(systemId)) {
            return FillState.HATCHED;
        }
        return FillState.SOLID;
    }

    // Captures each system's star-icon inputs - its hyperspace anchor and star radius - keyed by
    // system id. Every system is snapshotted, not only the drawn ones: a hovered cell is always a
    // drawn one, so a spare entry is harmless, and reading the whole sector once is cheaper and
    // simpler than filtering to the draw set. A system with no anchor is skipped, and a starless
    // one keeps a zero radius, which the hit test reads as the smallest icon.
    private static void captureStarIconGeometry(
            PoliticalMapTerritories territories, SectorAPI sector) {
        for (var system : sector.getStarSystems()) {
            var anchor = system.getHyperspaceAnchor();
            if (anchor == null || anchor.getLocation() == null) {
                continue;
            }
            var star = system.getStar();
            var starRadius = star == null ? 0f : star.getRadius();
            territories.putStarIconGeometry(
                    system.getId(),
                    new StarIconGeometry(
                            anchor.getLocation().x, anchor.getLocation().y, starRadius));
        }
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
                : resolveCellGrouping(territories, geometryCache).groupCellIdsByKey().entrySet()) {
            var territory = buildFactionTerritory(
                    territories,
                    geometryCache,
                    faction.getKey(),
                    faction.getValue());
            if (territory != null) {
                territories.getFactionTerritoryByFactionId().put(faction.getKey(), territory);
            }
        }
    }

    // Splits a bloc's footprint fill into its three states: the systems the bloc holds solid
    // tessellate into the solid triangle soup, the contested systems into their own soup the hatch
    // generator then clips diagonal lines to, and the unfilled systems into nothing - held inside
    // the one frontier but painting no fill. Each drawn state fills from its own traced rings rather
    // than from its members' individual cells, so no per-cell inset truncation can leave an unfilled
    // wedge where two members meet at a corner against a rival or empty space. Both drawn fills are
    // clipped to the smoothed national border loops, so neither keeps the mitered corner the
    // border's rounding cut and pokes out past the frontier the border strokes. A "No color" fill
    // draws no region.
    private static TerritoryFill computeSplitFill(
            PoliticalMapTerritories territories,
            PoliticalMapGeometryCache geometryCache,
            CellGrouping cellGrouping,
            PoliticalBorderTrace borderTrace,
            List<List<double[]>> borderLoops,
            String blocId,
            FillSplit split,
            Color fillColor) {
        if (fillColor == null) {
            return new TerritoryFill(GlVertexRuns.NO_VERTICES, GlVertexRuns.NO_VERTICES);
        }
        // The states are traced against each other, so each names the others' members as
        // coincident: their shared boundary lands on the raw cell edge from both sides and the
        // fills meet exactly along it - the same line the cells' own interior seams stroke. The
        // unfilled state paints nothing, but the drawn states still name its members coincident so
        // the fill stops flush against it rather than opening a channel over held-but-empty ground.
        var subRegionKeys = mapSubRegionKeyBySystemId(
                territories,
                blocId,
                split);
        var solidTriangles = tessellateSubRegion(
                borderTrace,
                geometryCache,
                borderLoops,
                cellGrouping.systemIdByCellId(),
                split.solid(),
                subRegionKeys,
                unionSystemIds(split.hatched().systemIds(), split.unfilled().systemIds()));
        var hatchedTriangles = tessellateSubRegion(
                borderTrace,
                geometryCache,
                borderLoops,
                cellGrouping.systemIdByCellId(),
                split.hatched(),
                subRegionKeys,
                unionSystemIds(split.solid().systemIds(), split.unfilled().systemIds()));
        var hatch = territories.getGlobalStyle().hatch();
        var hatchSegments = Hatching.computeHatchSegments(
                hatchedTriangles,
                hatch.angleRadians(),
                hatch.spacing());
        // The unfilled state is deliberately never tessellated: it holds ground for the bloc's
        // border and label but paints no fill of its own.
        return new TerritoryFill(solidTriangles, hatchSegments);
    }

    // Keys the footprint's three fill states apart, so the border tracer - which fuses cells sharing
    // a key - traces the solid, hatched, and unfilled members as separate regions rather than as the
    // one body their shared footprint key makes them. Every system outside the footprint keeps its
    // real key, so an edge from a member to a rival or to empty space classifies exactly as it does
    // when the whole footprint is traced, and the sub-regions' outer edge therefore lands where the
    // national border draws it. Suffixing the footprint's own key leaves the derived keys as
    // collision-free as it already is.
    private static Map<String, String> mapSubRegionKeyBySystemId(
            PoliticalMapTerritories territories,
            String blocId,
            FillSplit split) {
        var keys = new HashMap<String, String>(
                DominantOwner.mapFactionIdBySystemId(territories.getOwnerBySystemId()));
        for (var systemId : split.solid().systemIds()) {
            keys.put(systemId, blocId + SOLID_SUB_REGION_SUFFIX);
        }
        for (var systemId : split.hatched().systemIds()) {
            keys.put(systemId, blocId + HATCHED_SUB_REGION_SUFFIX);
        }
        for (var systemId : split.unfilled().systemIds()) {
            keys.put(systemId, blocId + UNFILLED_SUB_REGION_SUFFIX);
        }
        return keys;
    }

    // Splits the footprint's members into the three fill states, each held as both its cells and
    // its systems: the cells are what a region is traced from, while the keys and the coincident
    // set the trace is given are per system, since that is the level a fill state is decided at.
    private static FillSplit splitMembersByFillState(
            CellGrouping cellGrouping,
            List<String> memberCellIds,
            Set<String> contestedSystemIds,
            Set<String> unfilledSystemIds) {
        var solid = new FillMembers(new LinkedHashSet<>(), new LinkedHashSet<>());
        var hatched = new FillMembers(new LinkedHashSet<>(), new LinkedHashSet<>());
        var unfilled = new FillMembers(new LinkedHashSet<>(), new LinkedHashSet<>());
        for (var cellId : memberCellIds) {
            var systemId = cellGrouping.resolveDrawnSystemIdOf(cellId);
            var state = switch (classifyFillState(systemId, contestedSystemIds, unfilledSystemIds)) {
                case SOLID -> solid;
                case HATCHED -> hatched;
                case UNFILLED -> unfilled;
            };
            state.cellIds().add(cellId);
            if (systemId != null) {
                state.systemIds().add(systemId);
            }
        }
        return new FillSplit(solid, hatched, unfilled);
    }

    // Tessellates one of the footprint's states into a GL_TRIANGLES soup from the rings tracing
    // its cells as a single region, so a state fills as one continuous area with no per-cell
    // seam or truncation inside it. The other states' systems are the coincident neighbours, whose
    // shared edge insets by nothing so the states abut with no channel between them. The traced
    // rings are clipped to the smoothed national border loops rather than tessellated as traced:
    // their shared inter-state seam is interior to both operands and survives the clip untouched, so
    // the states still meet exactly along it, while their outer edge is clamped onto the exact
    // line the national border strokes instead of keeping the mitered corner the border's rounding
    // cut away. Empty when the state holds no members or the trace yields no drawable ring.
    private static float[] tessellateSubRegion(
            PoliticalBorderTrace borderTrace,
            PoliticalMapGeometryCache geometryCache,
            List<List<double[]>> borderLoops,
            Map<String, String> systemIdByCellId,
            FillMembers subRegion,
            Map<String, String> subRegionKeyBySystemId,
            Set<String> coincidentSystemIds) {
        if (subRegion.cellIds().isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        var rings = borderTrace.traceRings(
                subRegion.cellIds(),
                geometryCache.getCellEdgesByCellId(),
                new CellGrouping(systemIdByCellId, subRegionKeyBySystemId),
                coincidentSystemIds);
        if (rings.isEmpty()) {
            return GlVertexRuns.NO_VERTICES;
        }
        return PolygonTessellator.tessellateIntersectionToTriangles(rings, borderLoops);
    }

    // The systems two states hold together, the coincident set a third state's trace opts out of the
    // border channel so it abuts them flush. Order does not matter - the trace reads it as a
    // membership test - so a plain union suffices.
    private static Set<String> unionSystemIds(Set<String> first, Set<String> second) {
        var union = new LinkedHashSet<String>(first);
        union.addAll(second);
        return union;
    }

    // The three states a bloc's system can draw its fill in: solid where the bloc holds, hatched
    // where it is present but dominated, unfilled where it is held but painted empty. Package-private
    // so the pure classifier that decides it can be pinned on plain id sets.
    enum FillState {
        SOLID,
        HATCHED,
        UNFILLED
    }

    // A bloc footprint's fill split into its two painted regions: the solid triangle soup for the
    // held cells and the hatch GL_LINES for the contested ones. The unfilled state carries no
    // geometry here - it paints nothing - so a territory with no hatched members leaves the hatch
    // empty and one that fills solid throughout carries only its solid region.
    private record TerritoryFill(
            float[] solidTriangles,
            float[] hatchSegments) {
    }

    // One fill state's members at both levels the fill needs them: the cells a region is traced
    // from, and the systems its keys and coincident set are addressed by. The two differ wherever
    // the footprint holds a cell with no star of its own - it joins a region's outline but names no
    // system to key or to mark coincident.
    private record FillMembers(Set<String> cellIds, Set<String> systemIds) {
    }

    // The footprint's members split into the three states its fill paints apart - solid where the
    // bloc holds, hatched where it is present but dominated, unfilled where it is held but drawn
    // empty - each carried as its cells and systems.
    private record FillSplit(FillMembers solid, FillMembers hatched, FillMembers unfilled) {

        // Whether any member draws as something other than solid, so a territory that is not
        // spotlit still takes the per-state split when it holds hatched or unfilled ground.
        private boolean hasNonSolidMembers() {
            return !hatched.systemIds().isEmpty() || !unfilled.systemIds().isEmpty();
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
                        independentStyle.fill().color(), factionStyle.fill().opacity()),
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
                                ? MapPalettes.pickPaletteColor(style.fill().color(), primaryColor,
                                        secondaryColor)
                                : null,
                        adjustment.muteOpacity(style.fill().opacity())),
                new UiElementPaint(
                        perCellFillAndBorder
                                ? MapPalettes.pickPaletteColor(style.outer().color(), primaryColor,
                                        secondaryColor)
                                : null,
                        adjustment.muteOpacity(style.outer().opacity())),
                new UiElementPaint(
                        MapPalettes.pickPaletteColor(style.inner().color(), primaryColor,
                                secondaryColor),
                        adjustment.muteOpacity(style.inner().opacity())),
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
