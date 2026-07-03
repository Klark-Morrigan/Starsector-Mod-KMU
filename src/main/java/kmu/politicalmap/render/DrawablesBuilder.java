package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.math.geometry.Polygons;
import kmlib.math.geometry.PrincipalAxis;
import kmlib.opengl.PolygonTessellator;
import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.domain.geometry.CellShaper;
import kmu.politicalmap.domain.geometry.PoliticalMapGeometryCache;
import kmu.politicalmap.domain.geometry.ShapedCell;
import kmu.politicalmap.domain.geometry.SystemClusterBorders;
import kmu.politicalmap.domain.geometry.SystemClusters;
import kmu.politicalmap.domain.politics.DominantOwner;
import kmu.politicalmap.domain.politics.SectorPolitics;
import kmu.politicalmap.domain.visibility.DecivilisedPresence;
import kmu.politicalmap.render.model.ClusterAnchor;
import kmu.politicalmap.render.model.ElementPaint;
import kmu.politicalmap.render.model.FactionTerritory;
import kmu.politicalmap.render.model.MapStyle;
import kmu.politicalmap.render.model.PoliticalMapDrawables;
import kmu.politicalmap.render.model.StyledCell;
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
    // per cell.
    static PoliticalMapDrawables buildDrawables(PoliticalMapGeometryCache geometryCache,
            SectorAPI sector) {
        var profiler = KmuProfiling.getProfiler();
        return profiler.measure("politicalMap.rebuildDrawables", () -> {
            // The politics scan walks the whole economy - the priciest content step -
            // so it is profiled and timed on its own, and the owner count logged
            // independent of the profiler's accumulated view.
            var politicsStart = System.nanoTime();
            var ownerBySystemId = profiler.measure("politicalMap.resolvePolitics",
                    () -> SectorPolitics.resolveDominantOwnerBySystemId(sector));
            LOG.debug("Political map politics resolved; ownedSystems=" + ownerBySystemId.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - politicsStart));

            var decivilisedStart = System.nanoTime();
            var decivilisedSystemIds = profiler.measure("politicalMap.findDecivilised",
                    () -> DecivilisedPresence.findRevealedDecivilisedSystemIds(sector));
            LOG.debug("Political map decivilised scan; systems=" + decivilisedSystemIds.size()
                    + " took=" + Timings.formatMillis(System.nanoTime() - decivilisedStart));

            // One style bundle per category and the shared neutral color, held on the
            // drawables so the incremental refresh re-shapes cells against the same
            // inputs this pass used.
            var drawables = new PoliticalMapDrawables(
                    new LinkedHashMap<>(), new LinkedHashMap<>(), new ArrayList<>(),
                    ownerBySystemId, decivilisedSystemIds,
                    SectorPolitics.resolveNeutralColor(sector),
                    MapStyleReader.readFactionStyle(), MapStyleReader.readIndependentStyle(),
                    MapStyleReader.readDecivilisedStyle(), MapStyleReader.readUninhabitedStyle());

            // Shape the raw cells into merged clusters once, ownership-aware. Cells
            // consumed by the inset (fewer than three vertices left) drop out - nothing
            // to fill or stroke.
            var shapeStart = System.nanoTime();
            var shapedCells = profiler.measure("politicalMap.shapeCells",
                    () -> CellShaper.shapeCells(geometryCache.getCellEdgesBySystemId(),
                            ownerBySystemId, PoliticalMapStyle.BORDER_INSET_DISTANCE));
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

            // The debug label anchors, when the dev toggle asks for them. Cheap next to
            // the shaping above, so it shares the same rebuild rather than a pass of its
            // own; a no-op (leaving the list empty) when the toggle is off.
            rebuildClusterAnchors(drawables, geometryCache);

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
            // Independent space styles from its own bundle; every other owner is a core
            // faction. The fill and national border are per cluster from the tessellated
            // region, so an owned cell contributes only its interior seams here, in its
            // inner-seam color resolved against the owner's palette.
            var style = Factions.INDEPENDENT.equals(owner.factionId())
                    ? drawables.getIndependentStyle()
                    : drawables.getFactionStyle();
            return buildStyledCell(shaped, owner.primaryColor(), owner.secondaryColor(),
                    style, false);
        }
        // Factionless: decivilised or (otherwise) uninhabited. Its style fills neither
        // palette slot, so both resolve to the shared neutral color and only its
        // per-cell outline draws; drop it when that outline is "No color".
        var style = drawables.getDecivilisedSystemIds().contains(systemId)
                ? drawables.getDecivilisedStyle()
                : drawables.getUninhabitedStyle();
        if (style.outerColor() == FactionPaletteChoice.NONE) {
            return null;
        }
        var neutralColor = drawables.getNeutralColor();
        return buildStyledCell(shaped, neutralColor, neutralColor, style, true);
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

    // Rebuilds the debug label anchors in place: clears the standing list, then - only
    // when the dev toggle is on - splits the owned systems into contiguous clusters and
    // fits one anchor to each. Shared by the full rebuild and the incremental refresh so
    // an ownership change keeps the anchors in step with the fills and borders. Reads
    // the toggle here (not at the call sites) so both paths gate identically.
    static void rebuildClusterAnchors(PoliticalMapDrawables drawables,
            PoliticalMapGeometryCache geometryCache) {
        var anchors = drawables.getClusterAnchors();
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        var clusters = SystemClusters.findClusters(
                geometryCache.getCellEdgesBySystemId(), drawables.getOwnerBySystemId());
        anchors.addAll(computeClusterAnchors(
                clusters, geometryCache.getSiteBySystemId(), drawables.getOwnerBySystemId()));
    }

    // Fits one label anchor to each contiguous cluster: the principal axis of its member
    // system positions gives the centre to hang the label on, the direction it runs, and
    // the span it can fill. The axis segment is centred on the anchor, so it reaches half
    // the span each way; a single-system cluster has zero span and collapses to the dot.
    // The owning faction's bright shade colours the marker so it reads against the fill.
    static List<ClusterAnchor> computeClusterAnchors(List<List<String>> clusters,
            Map<String, double[]> siteBySystemId, Map<String, DominantOwner> ownerBySystemId) {
        var anchors = new ArrayList<ClusterAnchor>(clusters.size());
        for (var memberSystemIds : clusters) {
            var sites = collectClusterSites(memberSystemIds, siteBySystemId);
            if (sites.isEmpty()) {
                continue;
            }
            var axis = PrincipalAxis.fitTo(sites);
            var halfSpan = axis.length() / 2.0;
            var color = ownerBySystemId.get(memberSystemIds.get(0)).primaryColor();
            anchors.add(new ClusterAnchor(
                    (float) axis.centroidX(), (float) axis.centroidY(),
                    (float) (axis.centroidX() - axis.axisX() * halfSpan),
                    (float) (axis.centroidY() - axis.axisY() * halfSpan),
                    (float) (axis.centroidX() + axis.axisX() * halfSpan),
                    (float) (axis.centroidY() + axis.axisY() * halfSpan),
                    color));
        }
        return anchors;
    }

    // Gathers the {x, y} sites of a cluster's members, skipping any whose site is missing
    // - the point cloud the anchor's axis is fitted to.
    private static List<double[]> collectClusterSites(List<String> memberSystemIds,
            Map<String, double[]> siteBySystemId) {
        var sites = new ArrayList<double[]>(memberSystemIds.size());
        for (var systemId : memberSystemIds) {
            var site = siteBySystemId.get(systemId);
            if (site != null) {
                sites.add(site);
            }
        }
        return sites;
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
        var style = Factions.INDEPENDENT.equals(factionId)
                ? drawables.getIndependentStyle()
                : drawables.getFactionStyle();
        // Every system of a faction shares its palette, so any member resolves the same
        // fill and border colors.
        var palette = drawables.getOwnerBySystemId().get(memberSystemIds.get(0));
        var fillColor = pickPaletteColor(
                style.fillColor(), palette.primaryColor(), palette.secondaryColor());
        var borderColor = pickPaletteColor(
                style.outerColor(), palette.primaryColor(), palette.secondaryColor());
        if (fillColor == null && borderColor == null) {
            return null;
        }
        var insetRings = SystemClusterBorders.traceBorderRings(memberSystemIds,
                geometryCache.getCellEdgesBySystemId(), drawables.getOwnerBySystemId(),
                PoliticalMapStyle.BORDER_INSET_DISTANCE,
                KmuLunaSettings.getPoliticalMapBorderWeldTolerance(),
                KmuLunaSettings.getPoliticalMapBorderMiterLimit());
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
                ? VertexRuns.NO_VERTICES
                : PolygonTessellator.tessellateToTriangles(borderLoops);
        var borderRuns = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
                borderRuns.add(VertexRuns.flattenVertices(loop));
            }
        }
        return new FactionTerritory(fillTriangles,
                new ElementPaint(fillColor, (float) style.fillOpacity()), borderRuns,
                new ElementPaint(borderColor, (float) style.outerOpacity()),
                (float) style.outerWidth());
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
    // choice, so the draw pass skips it.
    private static StyledCell buildStyledCell(ShapedCell shaped, Color primaryColor,
            Color secondaryColor, MapStyle style, boolean perCellFillAndBorder) {
        // Gate the corner rounding outside the round call: on rounds this cell's outline
        // in step with the cluster borders, off leaves its raw inset outline.
        var outline = shaped.fillPolygon();
        if (perCellFillAndBorder && KmuLunaSettings.shouldRoundBorderCorners()) {
            outline = roundCellOutline(shaped);
        }
        return new StyledCell(
                perCellFillAndBorder
                        ? VertexRuns.flattenVertices(shaped.fillPolygon())
                        : VertexRuns.NO_VERTICES,
                perCellFillAndBorder
                        ? VertexRuns.flattenClosedLoopAsSegments(outline)
                        : VertexRuns.NO_VERTICES,
                VertexRuns.flattenEdgesOfClass(shaped, false),
                new ElementPaint(
                        perCellFillAndBorder
                                ? pickPaletteColor(style.fillColor(), primaryColor, secondaryColor)
                                : null,
                        (float) style.fillOpacity()),
                new ElementPaint(
                        perCellFillAndBorder
                                ? pickPaletteColor(style.outerColor(), primaryColor, secondaryColor)
                                : null,
                        (float) style.outerOpacity()),
                new ElementPaint(
                        pickPaletteColor(style.innerColor(), primaryColor, secondaryColor),
                        (float) style.innerOpacity()),
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
}
