package kmu.maplayers.politicalmap.factions.render;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.PrincipalAxis;
import kmlib.math.geometry.RegionChord;
import kmlib.math.geometry.Segment;
import kmlib.math.solving.Picks;
import kmlib.starsector.ui.font.LazyFontMeasurer;
import kmlib.starsector.ui.label.AspectLabelLengthEstimator;
import kmlib.starsector.ui.label.FontLabelLengthEstimator;
import kmlib.starsector.ui.label.LabelBoxFitter;
import kmlib.starsector.ui.label.LabelLengthEstimator;

import kmu.maplayers.politicalmap.base.geometry.CellEdge;
import kmu.maplayers.politicalmap.base.geometry.PoliticalMapGeometryCache;
import kmu.maplayers.politicalmap.base.geometry.SystemClusters;
import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;
import kmu.maplayers.politicalmap.base.render.LabelFonts;
import kmu.maplayers.politicalmap.base.render.LabelSlantPreference;
import kmu.maplayers.politicalmap.base.render.PoliticalBorderTrace;
import kmu.maplayers.politicalmap.base.render.PoliticalMapStyle;
import kmu.maplayers.politicalmap.base.render.model.ClusterAnchor;
import kmu.settings.FactionNameFormatChoice;
import kmu.settings.FactionPaletteChoice;
import kmu.settings.KmuLunaSettings;

import org.lazywizard.lazylib.ui.LazyFont;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Fits the label placements: one {@link ClusterAnchor} per contiguous same-faction
 * cluster, its label box the highest-scoring of many candidate lines swept across the
 * cluster. This class generates the candidates (a direction fan crossed at parallel
 * offsets) and selects among them; {@link LabelBoxFitter} sizes each into the largest
 * box holding the owner's name - clipped inside the national border, trimmed clear of
 * system icons, pulled short of the border at both ends, and stacked into extra lines
 * where that buys a bigger font. The name is measured with the label font's own metrics
 * ({@link FontLabelLengthEstimator}), so the accepted box is sized for the glyphs that
 * will actually fill it; where the font or the owner's name will not resolve, an aspect
 * stand-in keeps the debug band meaningful and no name draws. Boxes that lean along the
 * cluster's own axis are favoured over ones that stray from it by a
 * font-height-versus-slope score ({@link LabelSlantPreference}) rather than by bending
 * any direction before the fit; the preferred lean is capped short of vertical and
 * fades to level for round clusters whose axis carries no real direction.
 *
 * <p>Its own builder, apart from {@link DrawablesBuilder}, because the anchors are an
 * independent overlay, not part of the production draw lists: they draw over the normal
 * render and the debug border-tracing overlay alike, so they cannot live inside either
 * view's build. The overlay list is owned by the terrain plugin and rebuilt in place
 * here, whichever base view a rebuild produced.
 */
final class ClusterAnchorsBuilder {

    // The stand-in name shape (length as a multiple of line height) sized against when
    // no real measurement exists - the label font failed to load, or a cluster's owner
    // resolves no display name. A plausible faction-name proportion, so the debug band
    // still shows a realistic footprint; no name is drawn from a stand-in fit.
    private static final double FALLBACK_NAME_ASPECT = 6.0;

    // Builds only; never instantiated.
    private ClusterAnchorsBuilder() {
    }

    // Rebuilds the cluster-label placements in place: clears the standing list, then -
    // only when the placements are needed - splits the owned systems into contiguous
    // clusters and fits one anchor to each. The placements feed two consumers: the
    // faction-name labels and the debug anchor overlay. Building whenever either is on
    // keeps them a single computation (an SSOT the labels and the overlay share), so the
    // search never runs twice; each consumer then draws only under its own toggle. Shared
    // by the full rebuild and the incremental refresh so an ownership change keeps the
    // placements in step with the fills and borders. Reads the toggles here (not at the
    // call sites) so all paths gate identically; the search's tuning is read here too, so
    // a settings change re-fits on the rebuild it triggers. The sector supplies each
    // owner's display name, which the fit sizes the boxes for.
    static void rebuildClusterAnchors(List<ClusterAnchor> anchors,
            PoliticalMapGeometryCache geometryCache,
            Map<String, DominantOwner> ownerBySystemId, SectorAPI sector) {
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowNames()
                && !KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        // The agnostic clustering and border trace key by grouping id, so hand them each
        // system's faction id; the owner map is still carried for the per-owner name and colour.
        var groupKeyBySystemId = DominantOwner.factionIdBySystemId(ownerBySystemId);
        var clusters = SystemClusters.findClusters(
                geometryCache.getCellEdgesBySystemId(), groupKeyBySystemId);
        anchors.addAll(computeClusterAnchors(clusters, geometryCache.getCellEdgesBySystemId(),
                geometryCache.getSiteBySystemId(), ownerBySystemId, groupKeyBySystemId,
                LabelAnchorSpecification.readFromLunaSettings(), newNameEstimatorResolver(sector)));
    }

    // The rebuild for a path with no owner map at hand - the debug border-tracing view,
    // which builds no production draw lists to borrow one from. Resolves ownership from
    // the sector itself, gated behind the toggle so the economy scan only runs while
    // someone is actually looking at the anchors.
    static void rebuildClusterAnchorsFromSector(List<ClusterAnchor> anchors,
            PoliticalMapGeometryCache geometryCache, SectorAPI sector) {
        anchors.clear();
        if (!KmuLunaSettings.getPoliticalMapShowClusterAnchors()) {
            return;
        }
        rebuildClusterAnchors(anchors, geometryCache,
                SectorPolitics.resolveDominantOwnerBySystemId(sector), sector);
    }

    // Fits one label anchor to each contiguous cluster by searching over candidate lines
    // (searchClusterAnchor). The site centroid supplies the cluster's own principal axis
    // - one candidate direction among the fan, and the dot's fallback position - but has
    // no privileged pull on the accepted line, which is free to sit off-centre wherever
    // the cluster is roomiest. Each cluster's fit sizes against its owner's name via the
    // injected resolver, and the name (and its debug dot) draws in the shade the owner's
    // national border resolves to (resolveLabelColor), so the name reads as that border's
    // own colour; the candidate lines use a fixed diagnostic palette instead, painted by
    // the renderer.
    static List<ClusterAnchor> computeClusterAnchors(List<List<String>> clusters,
            Map<String, List<CellEdge>> edgesBySystemId, Map<String, double[]> siteBySystemId,
            Map<String, DominantOwner> ownerBySystemId, Map<String, String> groupKeyBySystemId,
            LabelAnchorSpecification spec, Function<String, LabelLengthEstimator> nameEstimatorByFactionId) {
        var anchors = new ArrayList<ClusterAnchor>(clusters.size());
        for (var memberSystemIds : clusters) {
            var sites = collectClusterSites(memberSystemIds, siteBySystemId);
            if (sites.isEmpty()) {
                continue;
            }
            var owner = ownerBySystemId.get(memberSystemIds.get(0));
            var axis = resolveClusterAxis(memberSystemIds, edgesBySystemId, sites);
            var rings = spec.borderTrace().traceRings(memberSystemIds, edgesBySystemId,
                    groupKeyBySystemId);
            anchors.add(searchClusterAnchor(rings, siteBySystemId, axis,
                    resolveLabelColor(owner, spec), spec,
                    nameEstimatorByFactionId.apply(owner.factionId())));
        }
        return anchors;
    }

    // The colour a cluster's name (and its debug dot) draws in: the shade the owner's
    // national border resolves to, so the name inherits the border's own colour rather
    // than a fixed bright pick. Independent space and core factions each carry their own
    // outer-border palette choice, resolved against this owner's two shades by the same
    // DrawablesBuilder mapping the border itself uses. A hidden border ("No color") still
    // needs a legible name, so it falls back to the owner's bright primary shade.
    private static Color resolveLabelColor(DominantOwner owner, LabelAnchorSpecification spec) {
        var choice = Factions.INDEPENDENT.equals(owner.factionId())
                ? spec.independentOuterColor() : spec.factionOuterColor();
        var color = DrawablesBuilder.pickPaletteColor(choice, owner.primaryColor(),
                owner.secondaryColor());
        return color != null ? color : owner.primaryColor();
    }

    // The per-faction name estimators one rebuild fits against: each faction's display
    // name measured with the label font, or the aspect stand-in when the font
    // or the name will not resolve. Cached per faction id because every cluster of a
    // faction shares one name, so its wrap is measured once per rebuild, not per cluster.
    private static Function<String, LabelLengthEstimator> newNameEstimatorResolver(SectorAPI sector) {
        var font = LabelFonts.loadMapLabelFont();
        // Read once per rebuild, like the font: every cluster of a faction spells its
        // name the same way, so the full/short choice is resolved here rather than per
        // faction.
        var nameFormat = KmuLunaSettings.getPoliticalMapFactionNameFormat();
        var estimatorByFactionId = new HashMap<String, LabelLengthEstimator>();
        return factionId -> estimatorByFactionId.computeIfAbsent(factionId,
                id -> resolveNameEstimator(sector, font, nameFormat, id));
    }

    // One faction's name estimator: font-measured when both the font and a non-blank
    // display name resolved, the aspect stand-in otherwise (a stand-in fit still sizes
    // the debug band; it wraps no lines, so no label is minted from it). The name format
    // picks the owner's long-form name (Full) or its abbreviated name (Short).
    private static LabelLengthEstimator resolveNameEstimator(SectorAPI sector, LazyFont font,
            FactionNameFormatChoice nameFormat, String factionId) {
        if (font == null) {
            return new AspectLabelLengthEstimator(FALLBACK_NAME_ASPECT);
        }
        var faction = sector.getFaction(factionId);
        var name = faction == null ? null : resolveFactionName(faction, nameFormat);
        if (name == null || name.isBlank()) {
            return new AspectLabelLengthEstimator(FALLBACK_NAME_ASPECT);
        }
        // LazyFontMeasurer (KMLib) reads the concrete font's calcWidth behind the
        // LineWidthMeasurer port, so the name-measuring estimator stays independent of
        // the font itself.
        return new FontLabelLengthEstimator(new LazyFontMeasurer(font), name);
    }

    // The owner's name in the player's chosen format: the abbreviated display name for
    // Short, the long-form name for Full (the default). getDisplayName is a faction's
    // short name and getDisplayNameLong its full title; both may be blank, which the
    // caller then treats as an unresolved name and falls back to the aspect stand-in.
    private static String resolveFactionName(FactionAPI faction, FactionNameFormatChoice nameFormat) {
        return nameFormat == FactionNameFormatChoice.SHORT
                ? faction.getDisplayName()
                : faction.getDisplayNameLong();
    }

    // Searches one cluster's candidate lines and assembles its anchor as a fitted label
    // box. Every candidate is a direction (a fan over the half-circle, plus pure
    // horizontal, the cluster's own axis, and the preferred slant) crossed at a parallel
    // offset (a sweep across the cluster's perpendicular extent). At each, the box solver
    // sizes the largest name that fits - growing the font against the border, spending
    // spare girth on extra lines only where that buys a bigger font - and the candidate
    // is scored by that fitted font height docked for straying from the cluster's
    // preferred lean. The best-scoring box wins; its clear span is the accepted line, its
    // girth and line count the band the name fills, the span's midpoint the point the
    // name block centres on, and the name estimator's wrap at the winning line count the
    // lines the label draws. When no box fits anywhere the anchor collapses to the site
    // centroid dot, optionally carrying the best near-miss span for the red diagnostic.
    // With the unbiased toggle on, the box that wins on raw font height (no slope
    // penalty) rides along as the yellow diagnostic whenever the penalty moved the pick.
    private static ClusterAnchor searchClusterAnchor(List<List<double[]>> rings,
            Map<String, double[]> siteBySystemId, PrincipalAxis axis, Color color,
            LabelAnchorSpecification spec, LabelLengthEstimator nameEstimator) {
        var centroidX = (float) axis.centroidX();
        var centroidY = (float) axis.centroidY();
        if (rings.isEmpty()) {
            // No traceable border leaves nothing to prove a candidate interior - the
            // one dead end the search cannot work around, so only the dot can show.
            return new ClusterAnchor(centroidX, centroidY, color, List.of(), 0f,
                    null, null, null, 0f, 0);
        }

        var fitter = newBoxFitter(spec, nameEstimator);
        var icons = siteBySystemId.values();
        var slant = LabelSlantPreference.resolveFrom(axis, spec.maxSlantDegrees());
        var directions = buildCandidateDirections(axis, slant, spec.directionCount());
        LabelBoxFitter.BoxFit bestAccepted = null;
        var bestScore = 0.0;
        LabelBoxFitter.BoxFit longestAccepted = null;
        RejectedSpan bestRejected = null;
        for (var direction : directions) {
            var extent = Points.projectCombinedExtentOnto(rings, -direction[1], direction[0]);
            for (var offsetIndex = 1; offsetIndex <= spec.offsetCount(); offsetIndex++) {
                var through = offsetThroughPoint(axis, direction, extent, offsetIndex,
                        spec.offsetCount());
                var chord = new RegionChord(rings, icons, through[0], through[1], direction);
                var box = fitter.fitLargestBox(chord);
                if (box != null) {
                    // Selection docks the fitted font height for lines that stray from the
                    // cluster's preferred slant - a sizing-blind choice, so it lives here,
                    // not in the fitter; the unbiased pick keeps the raw-height winner for
                    // the yellow diagnostic.
                    var score = box.fontHeight() * slant.computePenaltyMultiplier(direction,
                            spec.verticalPenaltyStrength(), spec.verticalPenaltyExponent());
                    if (bestAccepted == null || score > bestScore) {
                        bestAccepted = box;
                        bestScore = score;
                    }
                    longestAccepted = Picks.pickHigher(longestAccepted, box,
                            LabelBoxFitter.BoxFit::fontHeight);
                } else if (spec.showRejectedAxis()) {
                    bestRejected = Picks.pickHigher(bestRejected,
                            findRejectedSpan(fitter, chord, spec.nameMinFontSize()),
                            RejectedSpan::length);
                }
            }
        }

        if (bestAccepted != null) {
            var accepted = bestAccepted.segment();
            // The accepted interval has no tie to the centroid any more, so the dot and
            // the label's hang-point are the line's own midpoint.
            var midX = (float) ((accepted.startX() + accepted.endX()) / 2.0);
            var midY = (float) ((accepted.startY() + accepted.endY()) / 2.0);
            var unbiased = spec.showUnbiasedAxis() && longestAccepted != null
                    && !longestAccepted.segment().equals(accepted)
                    ? longestAccepted.segment() : null;
            return new ClusterAnchor(midX, midY, color,
                    nameEstimator.wrapIntoLines(bestAccepted.lineCount()),
                    (float) bestAccepted.fontHeight(), accepted, null, unbiased,
                    (float) bestAccepted.thickness(), bestAccepted.lineCount());
        }
        // Collapse: no box fit anywhere, so the dot marks the site centroid; the best
        // near-miss span rides along only when the rejected toggle asked for it.
        var rejected = bestRejected != null ? bestRejected.segment() : null;
        return new ClusterAnchor(centroidX, centroidY, color, List.of(), 0f,
                null, rejected, null, 0f, 0);
    }

    // Builds the box fitter from the tuning and the cluster's name estimator: the
    // font-size clamp, line count, and spacing that shape the growth, the icon clearance
    // and end inset every band trim reads, and the name the fit sizes against.
    private static LabelBoxFitter newBoxFitter(LabelAnchorSpecification spec, LabelLengthEstimator nameEstimator) {
        return new LabelBoxFitter(spec.nameMinFontSize(), spec.nameMaxFontSize(),
                spec.nameMaxLines(), spec.nameLineSpacing(), spec.iconClearance(),
                spec.endInsetDistance(), nameEstimator);
    }

    // The candidate directions for one cluster: an even fan of unit directions over the
    // half-circle (index 0 is exactly horizontal, so horizontal is always searched),
    // plus the cluster's own principal axis so an elongated cluster can fit along its long
    // dimension between two fan spokes, plus the preferred slant so the winner can land
    // exactly on the cluster's capped lean rather than the nearest spoke. Directions are
    // lines, not arrows - the half-circle covers every slope, and the search treats a
    // direction and its opposite as one line.
    private static List<double[]> buildCandidateDirections(PrincipalAxis axis,
            LabelSlantPreference slant, int directionCount) {
        var directions = new ArrayList<double[]>(directionCount + 2);
        for (var i = 0; i < directionCount; i++) {
            var angle = Math.PI * i / directionCount;
            directions.add(new double[] {Math.cos(angle), Math.sin(angle)});
        }
        directions.add(new double[] {axis.axisX(), axis.axisY()});
        directions.add(slant.toDirection());
        return directions;
    }

    // The through-point for one parallel-offset line: the cluster's perpendicular extent
    // (its span projected onto the direction's normal) is divided into evenly spaced
    // interior offsets, and this returns the foot of the perpendicular from the centroid
    // onto the chosen offset line, so the through-point sits near the cluster for stable
    // parameters. Offsets step strictly inside the extent (never on the grazing edges);
    // a single offset lands at the centre line.
    private static double[] offsetThroughPoint(PrincipalAxis axis, double[] direction,
            double[] extent, int offsetIndex, int offsetCount) {
        var normalX = -direction[1];
        var normalY = direction[0];
        var offset = extent[0] + (extent[1] - extent[0]) * offsetIndex / (offsetCount + 1.0);
        var centroidOffset = axis.centroidX() * normalX + axis.centroidY() * normalY;
        var shift = offset - centroidOffset;
        return new double[] {axis.centroidX() + shift * normalX, axis.centroidY() + shift * normalY};
    }

    // The best near-miss line for the red diagnostic: a candidate's clear span before the
    // end-margin trim, kept with its length so the longest across candidates wins.
    private record RejectedSpan(Segment segment, double length) {
    }

    // A candidate's near-miss span for the red diagnostic: the pre-margin clear span of a
    // band at the minimum font's single-line girth - the furthest a name-holding line got
    // before the border, icon, or end-margin trim discarded it. Null when even the thin
    // band finds no clear interior at all.
    private static RejectedSpan findRejectedSpan(LabelBoxFitter fitter, RegionChord chord,
            double minFontSize) {
        var band = fitter.fitBand(chord, minFontSize / 2.0);
        if (band.clearSpan() == null) {
            return null;
        }
        return new RejectedSpan(chord.toSegment(band.clearSpan()),
                band.clearSpan()[1] - band.clearSpan()[0]);
    }

    // The direction and length to fit a cluster's label line along: the principal axis
    // of its member system positions when that cloud has real spread (the usual case
    // for a multi-system cluster), or - when it does not, a single-system cluster or
    // coincident sites - the principal axis of the member cells' own raw Voronoi
    // vertices instead, so even a lone system's cell shape gives it a real direction to
    // add to the fan. The site centroid is always kept as the fallback dot position and
    // the sweep's origin regardless of which cloud supplied the direction, so the anchor
    // still falls back to the system's own position, not the cell's vertex-cloud mean.
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
        // The vertex cloud supplies the direction, so it also supplies the minor extent -
        // the slant gate reads the elongation of whichever cloud gave the axis, not the
        // site cloud's (which had no usable spread here).
        return new PrincipalAxis(siteAxis.centroidX(), siteAxis.centroidY(),
                vertexAxis.axisX(), vertexAxis.axisY(), vertexAxis.length(),
                vertexAxis.minorLength());
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

    /**
     * The modifiers the cluster-anchor search reads, gathered into one value so the
     * search takes its whole tuning surface as data rather than reaching into the
     * settings mid-computation.
     *
     * @param borderTrace            the national-border trace the anchor clips against -
     *                               shared with the territory build, so the anchor sees
     *                               the same rings the player does by construction
     * @param endInsetDistance       how far each end of the clear interval pulls inward,
     *                               in world units - the border-inset multiple already
     *                               resolved to a distance
     * @param iconClearance          the keep-out radius around each system icon, world
     *                               units
     * @param directionCount         the number of directions the candidate fan spans
     *                               over the half-circle
     * @param offsetCount            the number of parallel lines swept per direction
     * @param verticalPenaltyStrength how much font height a line straying from the
     *                               cluster's preferred lean may give up and still win,
     *                               0 (pure longest) to 1 (a perpendicular line scores
     *                               zero)
     * @param verticalPenaltyExponent the exponent on the line's deviation from the lean
     *                               in the score, concentrating the penalty toward the
     *                               perpendicular
     * @param maxSlantDegrees        the ceiling on the cluster-axis lean the score
     *                               prefers, in degrees; 0 forces level labels, 90 lets
     *                               the lean follow a tall cluster's axis to vertical
     * @param showRejectedAxis       whether a cluster whose accepted line collapsed also
     *                               carries the best rejected candidate the search found,
     *                               for the red diagnostic line
     * @param showUnbiasedAxis       whether each cluster also carries the pure-longest
     *                               accepted line (the winner with no vertical penalty),
     *                               for the yellow diagnostic line
     * @param nameMinFontSize        the smallest per-line font height a fit will accept,
     *                               world units - the readability floor; a chord that
     *                               cannot hold even one line this tall collapses to the
     *                               dot
     * @param nameMaxFontSize        the largest per-line font height a fit will grow to,
     *                               world units, so a roomy cluster does not mint an
     *                               oversized label
     * @param nameMaxLines           the most lines a name may wrap into, spending girth
     *                               to shorten the length its widest line needs
     * @param nameLineSpacing        the line-height multiple between stacked lines,
     *                               at least 1
     * @param factionOuterColor      the outer-border palette choice a core faction's
     *                               name inherits its colour from, resolved against the
     *                               owner's palette
     * @param independentOuterColor  the outer-border palette choice independent space's
     *                               name inherits its colour from
     */
    record LabelAnchorSpecification(PoliticalBorderTrace borderTrace, double endInsetDistance, double iconClearance,
            int directionCount, int offsetCount, double verticalPenaltyStrength,
            double verticalPenaltyExponent, double maxSlantDegrees, boolean showRejectedAxis,
            boolean showUnbiasedAxis, double nameMinFontSize, double nameMaxFontSize,
            int nameMaxLines, double nameLineSpacing, FactionPaletteChoice factionOuterColor,
            FactionPaletteChoice independentOuterColor) {

        // Reads the live tuning: the anchor knobs from the Dev "Label anchors" section,
        // the name-fit knobs from the visuals "Faction names" section, the same two
        // outer-border colour choices the national border reads (so the name inherits the
        // border's colour), plus the same border trace the national border renders with.
        // The end-inset multiple is resolved against the fixed border channel here, so the
        // search works in plain distances. The two diagnostic-line toggles ride along so
        // the search only builds the extra candidates while someone is looking at them.
        static LabelAnchorSpecification readFromLunaSettings() {
            return new LabelAnchorSpecification(
                    PoliticalBorderTrace.readFromLunaSettings(),
                    KmuLunaSettings.getPoliticalMapAnchorEndInsetMultiple()
                            * PoliticalMapStyle.BORDER_INSET_DISTANCE,
                    KmuLunaSettings.getPoliticalMapAnchorIconClearance(),
                    KmuLunaSettings.getPoliticalMapAnchorDirectionCount(),
                    KmuLunaSettings.getPoliticalMapAnchorOffsetCount(),
                    KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyStrength(),
                    KmuLunaSettings.getPoliticalMapAnchorVerticalPenaltyExponent(),
                    KmuLunaSettings.getPoliticalMapAnchorMaxSlantDegrees(),
                    KmuLunaSettings.getPoliticalMapShowRejectedAxes(),
                    KmuLunaSettings.getPoliticalMapShowUnbiasedAxes(),
                    KmuLunaSettings.getPoliticalMapNameMinFontSize(),
                    KmuLunaSettings.getPoliticalMapNameMaxFontSize(),
                    KmuLunaSettings.getPoliticalMapNameMaxLines(),
                    KmuLunaSettings.getPoliticalMapNameLineSpacing(),
                    KmuLunaSettings.getFactionOuterBorderColor(),
                    KmuLunaSettings.getIndependentOuterBorderColor());
        }
    }
}
