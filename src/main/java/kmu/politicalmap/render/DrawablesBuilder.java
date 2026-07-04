package kmu.politicalmap.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Polygons;
import kmlib.math.geometry.PrincipalAxis;
import kmlib.math.geometry.Spans;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.profiling.Timings;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.domain.geometry.CellEdge;
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
import java.util.Collection;
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
    // Two unit directions whose dot product clears this count as the same line for
    // display purposes - the tolerance only absorbs floating-point drift in the
    // renormalisation, so any real lean the bias applies lands far below it.
    private static final double SAME_DIRECTION_MIN_DOT_PRODUCT = 1.0 - 1e-9;

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
    // the toggle here (not at the call sites) so both paths gate identically; the fit's
    // tuning is read here too, so a settings change re-fits on the rebuild it triggers.
    static void rebuildClusterAnchors(PoliticalMapDrawables drawables,
            PoliticalMapGeometryCache geometryCache) {
        var anchors = drawables.getClusterAnchors();
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        var clusters = SystemClusters.findClusters(
                geometryCache.getCellEdgesBySystemId(), drawables.getOwnerBySystemId());
        anchors.addAll(computeClusterAnchors(clusters, geometryCache.getCellEdgesBySystemId(),
                geometryCache.getSiteBySystemId(), drawables.getOwnerBySystemId(),
                AnchorTuning.readFromSettings()));
    }

    // Fits one label anchor to each contiguous cluster: resolveClusterAxis gives the
    // centre to hang the label on and the raw direction (falling back to the member
    // cells' own shape when the site cloud alone has no spread, so a single-system
    // cluster still gets a real direction to try); leaning that horizontal by the
    // tuning's bias and refitting it against the border, the icons, and the end inset
    // (fitAlongDirection) gives the accepted line. When that collapses, the same fit's
    // best rejected candidate becomes the red diagnostic line (if its toggle is on); a
    // second fit with no bias applied becomes the yellow one (if its toggle is on and
    // the bias actually changed the direction). The owning faction's bright shade
    // colours the centroid dot so it reads against the fill; the lines use a fixed
    // diagnostic palette instead, painted by the renderer.
    static List<ClusterAnchor> computeClusterAnchors(List<List<String>> clusters,
            Map<String, List<CellEdge>> edgesBySystemId, Map<String, double[]> siteBySystemId,
            Map<String, DominantOwner> ownerBySystemId, AnchorTuning tuning) {
        var anchors = new ArrayList<ClusterAnchor>(clusters.size());
        for (var memberSystemIds : clusters) {
            var sites = collectClusterSites(memberSystemIds, siteBySystemId);
            if (sites.isEmpty()) {
                continue;
            }
            var color = ownerBySystemId.get(memberSystemIds.get(0)).primaryColor();
            var axis = resolveClusterAxis(memberSystemIds, edgesBySystemId, sites);
            var centroidX = (float) axis.centroidX();
            var centroidY = (float) axis.centroidY();
            if (axis.length() < Limits.MIN_EDGE_LENGTH) {
                // Nothing survives even the cell-shape fallback (missing or fully
                // degenerate geometry) - the true dead end where only the dot can show.
                anchors.add(new ClusterAnchor(centroidX, centroidY, color, null, null, null));
                continue;
            }

            var rings = tuning.borderTrace().traceRings(memberSystemIds, edgesBySystemId,
                    ownerBySystemId);
            var biasedDirection = leanTowardHorizontal(axis, tuning.horizontalBias());
            var biasedFit = fitAlongDirection(rings, siteBySystemId, axis.centroidX(),
                    axis.centroidY(), biasedDirection, tuning);

            ClusterAnchor.AxisSegment unbiasedAxis = null;
            if (tuning.showUnbiasedAxis()) {
                var unbiasedDirection = leanTowardHorizontal(axis, 1.0);
                if (!isSameDirection(biasedDirection, unbiasedDirection)) {
                    var unbiasedFit = fitAlongDirection(rings, siteBySystemId, axis.centroidX(),
                            axis.centroidY(), unbiasedDirection, tuning);
                    if (unbiasedFit.acceptedSpan() != null) {
                        unbiasedAxis = toSegment(axis, unbiasedDirection,
                                unbiasedFit.acceptedSpan());
                    }
                }
            }

            // The fit yields at most one of the two spans (a rejected candidate exists
            // only when nothing was accepted), so the anchor carries an accepted line,
            // or a rejected line when its toggle asks for it, or - both null - the dot.
            var acceptedAxis = biasedFit.acceptedSpan() != null
                    ? toSegment(axis, biasedDirection, biasedFit.acceptedSpan())
                    : null;
            var rejectedAxis = tuning.showRejectedAxis() && biasedFit.rejectedSpan() != null
                    ? toSegment(axis, biasedDirection, biasedFit.rejectedSpan())
                    : null;
            anchors.add(new ClusterAnchor(centroidX, centroidY, color,
                    acceptedAxis, rejectedAxis, unbiasedAxis));
        }
        return anchors;
    }

    // The direction and length to fit a cluster's label line along: the principal axis
    // of its member system positions when that cloud has real spread (the usual case
    // for a multi-system cluster), or - when it does not, a single-system cluster or
    // coincident sites - the principal axis of the member cells' own raw Voronoi
    // vertices instead, so even a lone system's cell shape gives it a real direction
    // rather than defaulting straight to the dot. The site centroid is always kept as
    // the anchor point regardless of which cloud supplied the direction, so the anchor
    // still marks the system's own position, not the cell's vertex-cloud mean.
    private static PrincipalAxis resolveClusterAxis(List<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId, List<double[]> sites) {
        var siteAxis = PrincipalAxis.fitTo(sites);
        if (siteAxis.length() >= Limits.MIN_EDGE_LENGTH) {
            return siteAxis;
        }
        var cellVertices = collectClusterCellVertices(memberSystemIds, edgesBySystemId);
        if (cellVertices.size() < 2) {
            return siteAxis;
        }
        var vertexAxis = PrincipalAxis.fitTo(cellVertices);
        if (vertexAxis.length() < Limits.MIN_EDGE_LENGTH) {
            return siteAxis;
        }
        return new PrincipalAxis(siteAxis.centroidX(), siteAxis.centroidY(),
                vertexAxis.axisX(), vertexAxis.axisY(), vertexAxis.length());
    }

    // Gathers every member cell's raw Voronoi edge endpoints as a point cloud - the
    // cluster's own footprint, fitted for a direction when its systems' site positions
    // alone have no spread to fit one to.
    private static List<double[]> collectClusterCellVertices(List<String> memberSystemIds,
            Map<String, List<CellEdge>> edgesBySystemId) {
        var vertices = new ArrayList<double[]>();
        for (var systemId : memberSystemIds) {
            var edges = edgesBySystemId.get(systemId);
            if (edges == null) {
                continue;
            }
            for (var edge : edges) {
                vertices.add(new double[] {edge.x1(), edge.y1()});
                vertices.add(new double[] {edge.x2(), edge.y2()});
            }
        }
        return vertices;
    }

    // Leans the fitted unit axis toward horizontal: its vertical component is scaled by
    // the bias (1 no bias, 0 flat) and the result renormalised, so an ambiguous cluster
    // tips horizontal while a strongly vertical one keeps its slope - a soft lean, not a
    // hard clamp. A fully vertical axis under a flat (zero) bias has no direction left,
    // so it falls back to horizontal outright - which is what a flat bias asks for.
    private static double[] leanTowardHorizontal(PrincipalAxis axis, double horizontalBias) {
        var biasedX = axis.axisX();
        var biasedY = axis.axisY() * horizontalBias;
        var length = Points.computeVectorLength(biasedX, biasedY);
        if (length < Limits.MIN_EDGE_LENGTH) {
            return new double[] {1.0, 0.0};
        }
        return new double[] {biasedX / length, biasedY / length};
    }

    // Whether two unit directions are, for display purposes, the same line: their dot
    // product sits at (effectively) 1. Catches both a no-op bias setting and an axis
    // that was already horizontal (where leaning changes nothing regardless of the
    // bias value) - either way there is nothing distinct for the unbiased line to add
    // over the accepted one.
    private static boolean isSameDirection(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] > SAME_DIRECTION_MIN_DOT_PRODUCT;
    }

    // One directional fit's outcome: the accepted {tStart, tEnd} interval the name may
    // occupy, or - only when nothing was accepted - the best rejected candidate found
    // along the way, for the red diagnostic line. Exactly one of the two is non-null;
    // both are null only when the direction found no interior geometry at all to work
    // with (no border ring, or the line misses the region entirely).
    private record DirectionalFit(double[] acceptedSpan, double[] rejectedSpan) {
    }

    // Fits one direction through a cluster's centroid against its border rings and the
    // system icons: the rings - the same ones the national border strokes, so the
    // anchor clips against what the player sees - bound the line to its genuinely
    // interior pieces (a chord across a concavity or an enclave is never kept); every
    // system icon then carves its keep-out interval from what remains; the longest
    // survivor is pulled in from both ends by the end inset so the name stops short of
    // the border. All sites act as icon blockers, not just the cluster's own: a
    // neighbour faction's icon just across the border can still overhang the inset
    // interior. Falls through three ways a direction can fail to produce an accepted
    // line, keeping the furthest-along candidate as the rejected span: no border ring
    // to clip against (nothing to report), the line missing the interior entirely
    // (nothing to report), the icons consuming every interior span (the longest
    // interior span reported), or the end inset consuming what the icons left (the
    // pre-inset clear span reported).
    private static DirectionalFit fitAlongDirection(List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId, double centroidX, double centroidY,
            double[] direction, AnchorTuning tuning) {
        if (rings.isEmpty()) {
            return new DirectionalFit(null, null);
        }
        var interiorSpans = Polygons.findLineInteriorSpans(rings, centroidX, centroidY,
                direction[0], direction[1]);
        if (interiorSpans.isEmpty()) {
            return new DirectionalFit(null, null);
        }
        var clear = Spans.findLongestClearSubsegment(interiorSpans, centroidX, centroidY,
                direction[0], direction[1], siteBySystemId.values(), tuning.iconClearance());
        if (clear == null) {
            return new DirectionalFit(null, Spans.findLongestSpan(interiorSpans));
        }
        var start = clear[0] + tuning.endInsetDistance();
        var end = clear[1] - tuning.endInsetDistance();
        // An interval shorter than twice the end inset leaves no room for a name
        // between the margins; the pre-inset clear span is the best rejected candidate.
        return start < end ? new DirectionalFit(new double[] {start, end}, null)
                : new DirectionalFit(null, clear);
    }

    // Maps a {tStart, tEnd} parameter interval back to world-coordinate endpoints along
    // the given direction from the centroid - the last step shared by the accepted,
    // rejected, and unbiased lines alike.
    private static ClusterAnchor.AxisSegment toSegment(PrincipalAxis axis, double[] direction,
            double[] span) {
        return new ClusterAnchor.AxisSegment(
                (float) (axis.centroidX() + direction[0] * span[0]),
                (float) (axis.centroidY() + direction[1] * span[0]),
                (float) (axis.centroidX() + direction[0] * span[1]),
                (float) (axis.centroidY() + direction[1] * span[1]));
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
        var insetRings = BorderTrace.readFromSettings().traceRings(memberSystemIds,
                geometryCache.getCellEdgesBySystemId(), drawables.getOwnerBySystemId());
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
        var borderRuns = new ArrayList<float[]>();
        if (borderColor != null) {
            for (var loop : PolygonTessellator.tessellateToBoundaryLoops(borderLoops)) {
                borderRuns.add(GlVertexRuns.flattenVertices(loop));
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
                        ? GlVertexRuns.flattenVertices(shaped.fillPolygon())
                        : GlVertexRuns.NO_VERTICES,
                perCellFillAndBorder
                        ? GlVertexRuns.flattenClosedLoopAsSegments(outline)
                        : GlVertexRuns.NO_VERTICES,
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

    /**
     * The modifiers the cluster-anchor fit reads, gathered into one value so the fit
     * takes its whole tuning surface as data rather than reaching into the settings
     * mid-computation.
     *
     * @param borderTrace      the national-border trace the anchor clips against -
     *                         shared with the territory build, so the anchor sees
     *                         the same rings the player does by construction
     * @param horizontalBias   the scale applied to the anchor axis's vertical
     *                         component before the fit; 1 no bias, 0 flat
     * @param endInsetDistance how far each end of the clear interval pulls inward,
     *                         in world units - the border-inset multiple already
     *                         resolved to a distance
     * @param iconClearance    the keep-out radius around each system icon, world
     *                         units
     * @param showRejectedAxis whether a cluster whose accepted line collapsed also
     *                         carries the best rejected candidate the fit found,
     *                         for the red diagnostic line
     * @param showUnbiasedAxis whether each cluster also carries the line the fit
     *                         would produce with no horizontal bias, for the
     *                         yellow diagnostic line
     */
    record AnchorTuning(BorderTrace borderTrace, double horizontalBias,
            double endInsetDistance, double iconClearance,
            boolean showRejectedAxis, boolean showUnbiasedAxis) {

        // Reads the live tuning: the anchor knobs from the Dev "Label anchors" section
        // plus the same border trace the national border renders with. The end-inset
        // multiple is resolved against the fixed border channel here, so the fit works
        // in plain distances. The two diagnostic-line toggles ride along so the fit
        // only computes the extra candidates while someone is looking at them.
        static AnchorTuning readFromSettings() {
            return new AnchorTuning(
                    BorderTrace.readFromSettings(),
                    KmuLunaSettings.getPoliticalMapAnchorHorizontalBias(),
                    KmuLunaSettings.getPoliticalMapAnchorEndInsetMultiple()
                            * PoliticalMapStyle.BORDER_INSET_DISTANCE,
                    KmuLunaSettings.getPoliticalMapAnchorIconClearance(),
                    KmuLunaSettings.getPoliticalMapShowRejectedAxes(),
                    KmuLunaSettings.getPoliticalMapShowUnbiasedAxes());
        }
    }

    /**
     * The parameters of one national-border ring trace, and the trace itself - the
     * single path both the territory build and the anchor fit go through, so "the
     * anchor clips against the rings the player sees" holds by construction: a new
     * trace parameter lands here once and both consumers pick it up together.
     *
     * @param weldTolerance   largest gap between two reports of a shared corner still
     *                        welded into one when chaining the boundary
     * @param miterSpikeLimit the multiple of the border inset past which a sharp
     *                        corner's inset miter is bevelled instead of pointed
     */
    record BorderTrace(double weldTolerance, double miterSpikeLimit) {

        // Reads the live trace parameters from the Dev "Border tracing" section.
        static BorderTrace readFromSettings() {
            return new BorderTrace(
                    KmuLunaSettings.getPoliticalMapBorderWeldTolerance(),
                    KmuLunaSettings.getPoliticalMapBorderMiterLimit());
        }

        // Traces one cluster's inset border rings with these parameters and the fixed
        // border channel every trace shares.
        List<List<double[]>> traceRings(Collection<String> memberSystemIds,
                Map<String, List<CellEdge>> edgesBySystemId,
                Map<String, DominantOwner> ownerBySystemId) {
            return SystemClusterBorders.traceBorderRings(memberSystemIds, edgesBySystemId,
                    ownerBySystemId, PoliticalMapStyle.BORDER_INSET_DISTANCE,
                    weldTolerance, miterSpikeLimit);
        }
    }
}
