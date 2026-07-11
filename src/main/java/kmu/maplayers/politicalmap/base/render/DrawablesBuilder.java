package kmu.maplayers.politicalmap.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.math.geometry.Polygons;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.Hatching;
import kmlib.opengl.PolygonTessellator;
import kmlib.profiling.Timings;
import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.factions.StarsectorFactionColors;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.geometry.CellShaper;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.geometry.ShapedCell;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.refresh.FilterSelection;
import kmu.maplayers.politicalmap.base.render.model.FactionTerritory;
import kmu.maplayers.politicalmap.base.render.model.FillStyle;
import kmu.maplayers.politicalmap.base.render.model.MapStyle;
import kmu.maplayers.politicalmap.base.render.model.PoliticalMapDrawables;
import kmu.maplayers.politicalmap.base.render.model.StyledCell;
import kmu.settings.DesaturationProfileChoice;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import org.apache.log4j.Logger;

import java.awt.Color;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns the raw cached cells and the current sector ownership into the political
 * map's draw lists: it resolves who holds each system, shapes the cells into merged
 * clusters, and bakes each element's colors, opacities, and widths from the current
 * settings into GL-ready vertex runs.
 *
 * <p>The full {@link #buildDrawables} pass produces a fresh {@link PoliticalMapDrawables}
 * from scratch. The per-cell {@link #buildStyledCellForSystem} and per-faction
 * {@link #buildFactionTerritory} builders are the shared primitives the incremental
 * refresh reuses to rebuild just the cells and factions an ownership change touched,
 * so a full rebuild and an incremental re-shape classify and style a cell identically.
 */
final class DrawablesBuilder {
    private static final Logger LOG = Global.getLogger(DrawablesBuilder.class);

    // Builds only; never instantiated.
    private DrawablesBuilder() {
    }

    // Shapes the cached raw cells into merged clusters and partitions them into filled
    // (owned) and outline-only (decivilised/uninhabited) draw lists, baking in each
    // cell's colors, opacities, and widths resolved from the current settings, then
    // flattens each to GL-ready vertex runs. Reads the settings once per category, not
    // per cell. The active view supplies the ownership grouping the pass resolves under
    // and the style classifier each cell reads; both are retained on the drawables so an
    // incremental re-shape classifies against the same view and grouping snapshot.
    static PoliticalMapDrawables buildDrawables(PoliticalMapGeometryCache geometryCache,
            SectorAPI sector, PoliticalMapView view) {
        var profiler = KmuProfiling.getProfiler();
        return profiler.measure("politicalMap.rebuildDrawables", () -> {
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
            // independent of the profiler's accumulated view.
            var politicsStart = System.nanoTime();
            var ownerBySystemId = profiler.measure("politicalMap.resolvePolitics",
                    () -> isFiltering
                            ? FilteredPolitics.resolveOwnerBySystemId(sector, grouping, selectedBlocId)
                            : SectorPolitics.resolveDominantOwnerBySystemId(sector, grouping));
            LOG.debug("Political map politics resolved; ownedSystems=" + ownerBySystemId.size()
                    + " filtering=" + isFiltering
                    + " took=" + Timings.formatMillis(System.nanoTime() - politicsStart));

            var decivilisedStart = System.nanoTime();
            var decivilisedSystemIds = profiler.measure("politicalMap.findDecivilised",
                    () -> DecivilisedMarkets.findRevealedDecivilisedSystemIds(sector));
            LOG.debug("Political map decivilised scan; systems=" + decivilisedSystemIds.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - decivilisedStart));

            // One style bundle per category, the shared neutral color, and the desaturation
            // palette the profile setting resolves to, held on the drawables so the
            // incremental refresh re-shapes cells against the same inputs this pass used.
            var neutralColor = StarsectorFactionColors.resolveNeutralColor(sector);
            var desaturationPalette = resolveDesaturationPalette(
                    KmuLunaSettings.getPoliticalMapDesaturationProfile(), sector, neutralColor);
            // The styling every non-spotlighted bloc recedes to, resolved once from the shared
            // recede toggles; the identity adjustment off filter, so a normal pass touches no bloc.
            var recedeAdjustment = isFiltering
                    ? RecedePreferences.resolveRecedeAdjustment()
                    : BlocStyleAdjustment.NONE;
            var drawables = new PoliticalMapDrawables(
                    new LinkedHashMap<>(), new LinkedHashMap<>(),
                    ownerBySystemId, decivilisedSystemIds,
                    neutralColor, desaturationPalette,
                    MapStyleReader.readFactionStyle(), MapStyleReader.readIndependentStyle(),
                    MapStyleReader.readDecivilisedStyle(), MapStyleReader.readUninhabitedStyle(),
                    view, grouping, selectedBlocId, recedeAdjustment);

            // Shape the raw cells into merged clusters once, ownership-aware. The agnostic
            // geometry clusters by grouping key, so hand it each system's faction id as the
            // key. Cells consumed by the inset (fewer than three vertices left) drop out.
            var shapeStart = System.nanoTime();
            var groupKeyBySystemId = DominantOwner.factionIdBySystemId(ownerBySystemId);
            var shapedCells = profiler.measure("politicalMap.shapeCells",
                    () -> CellShaper.shapeCells(geometryCache.getCellEdgesBySystemId(),
                            groupKeyBySystemId, PoliticalMapStyle.BORDER_INSET_DISTANCE));
            for (var entry : shapedCells.entrySet()) {
                var styled = buildStyledCellForSystem(drawables, entry.getKey(), entry.getValue());
                if (styled != null) {
                    drawables.getStyledCellBySystemId().put(entry.getKey(), styled);
                }
            }

            // Each owned faction's territory: one region per cluster (traced across all
            // its cells so a multi-system cluster reads as one frontier), tessellated for
            // the fill and flattened for the border - the same shape for both. Built off
            // the same raw cells and owners the seams used, and profiled on its own since
            // chaining, smoothing, and tessellating every faction's outline is comparable
            // in cost to shaping the cells.
            profiler.measure("politicalMap.buildFactionTerritories",
                    () -> buildAllFactionTerritories(drawables, geometryCache));

            LOG.debug("Political map cells shaped; shaped=" + shapedCells.size()
                    + " styledCells=" + drawables.getStyledCellBySystemId().size()
                    + " factionTerritories=" + drawables.getFactionTerritoryByFactionId().size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - shapeStart));
            return drawables;
        });
    }

    // Builds one system's per-cell draw record from its shaped cell, or null when the
    // cell draws nothing: an inset-collapsed cell, or a factionless cell whose outline
    // is "No color". An owned cell keeps only its interior seams (its fill and national
    // border are per-cluster, in factionTerritories); a factionless cell keeps its own
    // inset fill and outline, since factionless cells do not fuse. Shared by the full
    // rebuild and the incremental re-shape so both classify a cell identically.
    static StyledCell buildStyledCellForSystem(PoliticalMapDrawables drawables, String systemId,
            ShapedCell shaped) {
        // A cell the border inset consumed or collapsed comes back with an empty fill
        // (CellShaper via Polygons.insetSelectedEdges guarantees empty-or-drawable), so
        // there is nothing to fill or stroke.
        if (shaped.fillPolygon().isEmpty()) {
            return null;
        }
        var owner = drawables.getOwnerBySystemId().get(systemId);
        if (owner != null) {
            // The base style and per-bloc adjustment come from the pass's styling resolver -
            // the active view off filter, the spotlight rules under one. The adjustment dims
            // and/or recolours the cell: desaturating swaps the owner's own palette for the
            // pass's shared desaturation palette, and the opacity multiplier scales every seam
            // alpha on top of the style's own opacities. The fill and national border are per
            // cluster from the tessellated region, so an owned cell contributes only its
            // interior seams here.
            var styling = resolveBlocStyling(drawables, owner.factionId());
            var palette = resolveEffectivePalette(styling.adjustment(), owner,
                    drawables.getDesaturationPalette());
            return buildStyledCell(shaped, palette.primaryColor(), palette.secondaryColor(),
                    styling.style(), false, styling.adjustment().opacityMultiplier());
        }
        // Factionless: decivilised or (otherwise) uninhabited. Its style fills neither
        // palette slot, so both resolve to the shared neutral color and only its
        // per-cell outline draws; drop it when that outline is "No color". Factionless
        // cells are not blocs the view classifies, so they never carry an adjustment.
        var style = drawables.getDecivilisedSystemIds().contains(systemId)
                ? drawables.getDecivilisedStyle()
                : drawables.getUninhabitedStyle();
        if (style.outerColor() == FactionPaletteChoice.NONE) {
            return null;
        }
        var neutralColor = drawables.getNeutralColor();
        return buildStyledCell(shaped, neutralColor, neutralColor, style, true,
                BlocStyleAdjustment.NONE.opacityMultiplier());
    }

    // Builds every owned faction's territory into the drawables, keyed by faction id.
    // Each faction is independent - its cluster(s) trace only its own cells - so the
    // incremental refresh rebuilds one faction's entry without touching the rest.
    private static void buildAllFactionTerritories(PoliticalMapDrawables drawables,
            PoliticalMapGeometryCache geometryCache) {
        for (var faction : groupOwnedSystemsByFaction(drawables.getOwnerBySystemId()).entrySet()) {
            var territory = buildFactionTerritory(drawables, geometryCache,
                    faction.getKey(), faction.getValue());
            if (territory != null) {
                drawables.getFactionTerritoryByFactionId().put(faction.getKey(), territory);
            }
        }
    }

    // Groups the currently owned systems by their faction id, so each faction's
    // cluster(s) are traced from its own members. Takes the owner map rather than the whole
    // drawables so the debug overlay - which resolves owners without building any draw
    // lists - can group the same way the production build does.
    static Map<String, List<String>> groupOwnedSystemsByFaction(
            Map<String, DominantOwner> ownerBySystemId) {
        var systemsByFaction = new LinkedHashMap<String, List<String>>();
        for (var entry : ownerBySystemId.entrySet()) {
            systemsByFaction
                    .computeIfAbsent(entry.getValue().factionId(), factionId -> new ArrayList<>())
                    .add(entry.getKey());
        }
        return systemsByFaction;
    }

    // Builds one faction's fill and national border from its border rings,
    // traced across all the systems it holds so a multi-system cluster reads as one
    // continuous frontier. The rings are tessellated into fill triangles and flattened
    // into border loops - the same geometry - so the fill exactly matches the stroked
    // border. Disjoint clusters and enclaves each come back as their own ring, so
    // rebuilding a faction from its current members alone re-splits or re-merges its
    // clusters when the incremental refresh gains or loses one. Returns null when the
    // faction has no fill and no border color, or no borderable geometry.
    static FactionTerritory buildFactionTerritory(PoliticalMapDrawables drawables,
            PoliticalMapGeometryCache geometryCache, String factionId,
            List<String> memberSystemIds) {
        // The grouping key here is a bloc id (a faction id under the faction view, or one of the
        // filter's synthetic spotlight keys), so its style and per-bloc adjustment come from the
        // same styling resolver a per-cell owner does. A desaturated bloc's fill and border swap
        // to the pass's shared desaturation palette instead of its own two shades.
        var styling = resolveBlocStyling(drawables, factionId);
        var style = styling.style();
        var adjustment = styling.adjustment();
        // The filter's contested cluster (present but dominated) hatches; every other territory
        // fills solid. A real owner is never contested, so an un-filtered pass is always solid.
        var fillStyle = resolveFillStyle(FilteredPolitics.isContestedBloc(factionId));
        // Every system of a faction shares its palette, so any member resolves the same
        // fill and border colors.
        var owner = drawables.getOwnerBySystemId().get(memberSystemIds.get(0));
        var palette = resolveEffectivePalette(adjustment, owner, drawables.getDesaturationPalette());
        var fillColor = pickPaletteColor(
                style.fillColor(), palette.primaryColor(), palette.secondaryColor());
        var borderColor = pickPaletteColor(
                style.outerColor(), palette.primaryColor(), palette.secondaryColor());
        if (fillColor == null && borderColor == null) {
            return null;
        }
        var insetRings = PoliticalBorderTrace.readFromLunaSettings().traceRings(memberSystemIds,
                geometryCache.getCellEdgesBySystemId(),
                DominantOwner.factionIdBySystemId(drawables.getOwnerBySystemId()));
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
        if (KmuLunaSettings.shouldSandBorderSpikes()) {
            borderLoops = BorderSmoothing.sandBorderSpikes(borderLoops);
        }
        if (KmuLunaSettings.shouldRoundBorderCorners()) {
            borderLoops = BorderSmoothing.roundBorderCorners(borderLoops);
        }
        var fillTriangles = fillColor == null
                ? GlVertexRuns.NO_VERTICES
                : PolygonTessellator.tessellateToTriangles(borderLoops);
        // The contested cluster paints as diagonal hatch instead of a solid fill, so its lines
        // are clipped to the same tessellated region here (once at build time, read off the live
        // Dev-tab knobs) rather than re-derived per frame. Every solid territory keeps none.
        var hatchSegments = fillStyle == FillStyle.HATCHED
                ? Hatching.computeHatchSegments(fillTriangles,
                        KmuLunaSettings.getPoliticalMapHatchAngleRadians(),
                        KmuLunaSettings.getPoliticalMapHatchSpacing())
                : GlVertexRuns.NO_VERTICES;
        var borderRuns = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
                borderRuns.add(GlVertexRuns.flattenVertices(loop));
            }
        }
        return new FactionTerritory(fillTriangles,
                new UiElementPaint(fillColor,
                        (float) (style.fillOpacity() * adjustment.opacityMultiplier())),
                fillStyle,
                hatchSegments,
                borderRuns,
                new UiElementPaint(borderColor,
                        (float) (style.outerOpacity() * adjustment.opacityMultiplier())),
                (float) style.outerWidth());
    }

    // The base style and per-bloc adjustment a bloc draws under this pass, shared by the per-cell
    // and per-faction builders so both style a bloc identically. Maps the shared style decision -
    // the same call the label path reads, so a bloc's name never drifts from its fill - onto this
    // pass's two concrete styles: the independent style when the decision recedes a bloc to it,
    // the faction style otherwise.
    private static BlocStyling resolveBlocStyling(PoliticalMapDrawables drawables, String blocId) {
        var decision = resolveBlocStyleDecision(drawables.isFiltering(), blocId,
                drawables.getView(), drawables.getGrouping(), drawables.getRecedeAdjustment());
        var style = decision.usesIndependentStyle()
                ? drawables.getIndependentStyle()
                : drawables.getFactionStyle();
        return new BlocStyling(style, decision.adjustment());
    }

    // The style choice a bloc draws under this pass, as a view-agnostic (independent-style?,
    // adjustment) pair the fill path and the label path both resolve from - the single decision
    // that keeps a bloc's name in step with its fill and border. Off filter it is the active
    // view's own call: its independent-recede test and per-bloc adjustment.
    //
    // Under filter only the ADJUSTMENT is filter-driven; the base-style decision stays the view's,
    // so independent-held space keeps its independent style rather than snapping to the faction
    // style the moment a filter turns on - independent styling reads one source of truth in both
    // modes. The spotlighted bloc is the sole exception: it draws untouched at full faction
    // strength (NONE, faction style), so its synthetic key never inherits the view's independent
    // test (which an alliances-view desaturate would otherwise trip). Every other bloc unions the
    // view's own recede with the pass's shared recede, so a bloc receded by both never mutes twice.
    static BlocStyleDecision resolveBlocStyleDecision(boolean isFiltering, String blocId,
            PoliticalMapView view, OwnershipGrouping grouping,
            BlocStyleAdjustment recedeAdjustment) {
        if (isFiltering) {
            var isSpotlit = FilteredPolitics.isSpotlitBloc(blocId);
            return new BlocStyleDecision(
                    !isSpotlit && view.shouldUseIndependentStyle(blocId, grouping),
                    resolveFilterAdjustment(isSpotlit,
                            view.resolveBlocStyleAdjustment(blocId, grouping), recedeAdjustment));
        }
        return new BlocStyleDecision(view.shouldUseIndependentStyle(blocId, grouping),
                view.resolveBlocStyleAdjustment(blocId, grouping));
    }

    // The filter-mode adjustment a bloc takes: the spotlighted bloc draws untouched (NONE), every
    // other bloc unions its view-decided recede with the pass's shared recede so the sector fades
    // to a muted background the spotlight reads against. The union (strongest mute, either
    // desaturate) applies once, so a bloc the view already recedes - a non-allied faction under the
    // alliances view - does not mute a second time when the filter recedes it too. Pure over its
    // inputs so the rule pins without geometry.
    static BlocStyleAdjustment resolveFilterAdjustment(boolean isSpotlit,
            BlocStyleAdjustment viewAdjustment, BlocStyleAdjustment recedeAdjustment) {
        return isSpotlit
                ? BlocStyleAdjustment.NONE
                : viewAdjustment.mergeRecede(recedeAdjustment);
    }

    // The fill primitive a territory paints in: the filter's contested cluster (present but
    // dominated) hatches, every other territory fills solid.
    static FillStyle resolveFillStyle(boolean isContested) {
        return isContested ? FillStyle.HATCHED : FillStyle.SOLID;
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
    // choice, so the draw pass skips it. Every seam's opacity is scaled by
    // {@code opacityMultiplier}, the caller's resolved per-bloc adjustment (1.0 - no
    // change - for a factionless cell, which carries no adjustment).
    private static StyledCell buildStyledCell(ShapedCell shaped, Color primaryColor,
            Color secondaryColor, MapStyle style, boolean perCellFillAndBorder,
            double opacityMultiplier) {
        // Gate the corner rounding outside the round call: on rounds this cell's outline
        // in step with the cluster borders, off leaves its raw inset outline.
        var outline = shaped.fillPolygon();
        if (perCellFillAndBorder && KmuLunaSettings.shouldRoundBorderCorners()) {
            outline = roundCellOutline(shaped);
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
                                ? pickPaletteColor(style.fillColor(), primaryColor, secondaryColor)
                                : null,
                        (float) (style.fillOpacity() * opacityMultiplier)),
                new UiElementPaint(
                        perCellFillAndBorder
                                ? pickPaletteColor(style.outerColor(), primaryColor, secondaryColor)
                                : null,
                        (float) (style.outerOpacity() * opacityMultiplier)),
                new UiElementPaint(
                        pickPaletteColor(style.innerColor(), primaryColor, secondaryColor),
                        (float) (style.innerOpacity() * opacityMultiplier)),
                (float) style.outerWidth(), (float) style.innerWidth());
    }

    // Rounds a factionless cell's inset outline with the same corner settings the
    // cluster borders use, so its border reads consistently. The cell is a single
    // convex inset polygon (all its edges are national border), so it needs no chaining
    // or envelope resolve - only the corner rounding. The caller gates this on the
    // corner-rounding switch.
    private static List<double[]> roundCellOutline(ShapedCell shaped) {
        return Polygons.roundCorners(shaped.fillPolygon(),
                KmuLunaSettings.getPoliticalMapBorderCornerRadius(),
                KmuLunaSettings.getPoliticalMapBorderCornerSegments(),
                KmuLunaSettings.getPoliticalMapBorderChamferAngleRadians());
    }

    // The two shades a bloc actually paints in under its style adjustment: its owner's own
    // bright and dark shades normally, or the pass's shared desaturation palette when the
    // adjustment desaturates the bloc. The single home for the "desaturate swaps the
    // palette" rule, so a cell's seams, a faction's fill and border, and the bloc's name
    // all recolour off one decision rather than three copies of it.
    static FactionPalette resolveEffectivePalette(BlocStyleAdjustment adjustment,
            DominantOwner owner, FactionPalette desaturationPalette) {
        return adjustment.desaturate()
                ? desaturationPalette
                : new FactionPalette(owner.primaryColor(), owner.secondaryColor());
    }

    // Picks the palette shade the player pointed an element at: the secondary (dark)
    // shade for a SECONDARY choice, the primary (bright) shade for a PRIMARY choice, or
    // null for NONE ("No color") so the caller skips that element.
    static Color pickPaletteColor(FactionPaletteChoice choice, Color primaryColor,
            Color secondaryColor) {
        return switch (choice) {
            case PRIMARY -> primaryColor;
            case SECONDARY -> secondaryColor;
            case NONE -> null;
        };
    }

    // Resolves the desaturation palette a desaturated bloc recolours to, from the given
    // profile: Independent forges the Independent faction's own two shades (the same pair
    // a real independent owner's DominantOwner carries), so a desaturated bloc reads
    // exactly as independent-held space; Neutral is the shared neutral color in both
    // slots, the flat gray unowned space draws in. Takes the profile and the neutral color
    // as parameters (rather than reading KmuLunaSettings itself) so the mapping is a pure,
    // unit-testable lookup; buildDrawables reads the live setting once per pass and hands
    // it in.
    static FactionPalette resolveDesaturationPalette(DesaturationProfileChoice profile,
            SectorAPI sector, Color neutralColor) {
        return switch (profile) {
            case INDEPENDENT -> StarsectorFactionColors.resolvePalette(sector, Factions.INDEPENDENT);
            case NEUTRAL -> new FactionPalette(neutralColor, neutralColor);
        };
    }

    // The base style plus per-bloc adjustment a bloc draws under, resolved once and read by both
    // the fill and border and the interior seams so they never diverge.
    private record BlocStyling(MapStyle style, BlocStyleAdjustment adjustment) {
    }

    // The view-agnostic style decision the fill and label paths share: whether a bloc recedes to
    // the independent style, and its per-bloc adjustment. Free of the concrete MapStyle so the
    // label path - which needs only the boolean, not a resolved style - reads the very same call.
    record BlocStyleDecision(boolean usesIndependentStyle, BlocStyleAdjustment adjustment) {
    }
}
