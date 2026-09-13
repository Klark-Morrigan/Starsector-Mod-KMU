package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Limits;
import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segment;
import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlLines;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlQuads;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;

import kmu.maplayers.base.render.MapFrame;
import kmu.settings.KmuMapLabelSettings;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;
import java.util.function.Function;

/**
 * Paints the debug cluster-anchor overlay on the sector (M) map: each cluster's anchor
 * dot plus its accepted, rejected, and unbiased label lines.
 *
 * <p>Its own renderer, apart from whatever draws the clusters themselves, because the
 * anchors are an independent overlay: they are drawn after whichever base view is live -
 * a layer's normal render or its debug overlay - so turning a debug view on never hides
 * them. Pure GL emission over an already-built anchor list, in the same below-UI map pass
 * and coordinate convention the base renderers use.
 */
public final class ClusterAnchorRenderer {

    // Held rather than named per frame: a section found by reference costs the pass nothing where
    // one found by name is a lookup a frame.
    private static final ProfileSection RENDER_SECTION =
        ProfileSection.registerSection("mapLayer.render.anchors");

    // The anchor dot's diameter in screen pixels (GL_POINTS sizes in pixels, so it
    // stays a constant dot at any zoom) and its axis lines' width. Sized to read over
    // the fills and borders without swamping the systems they mark. The lines' colours
    // come from the shared {@link DiagnosticPalette}, so the anchor overlay and the
    // border-tracing overlay grade their layers identically.
    private static final float ANCHOR_DOT_SIZE = 10f;
    private static final float ANCHOR_AXIS_WIDTH = 2f;

    // Below this world girth a band is drawn as its centreline alone: a collapsed fit
    // (thickness zero) and the sub-unit residue of the thickness bisection both have no
    // meaningful area to fill, so only the line shows. A rendering floor, distinct from
    // the geometric degenerate-length guard (kmlib's Limits.MIN_EDGE_LENGTH) a segment's
    // own direction is checked against below - the two happen to differ by orders of
    // magnitude, so conflating them would silently change one when tuning the other.
    private static final float MIN_BAND_THICKNESS = 1f;

    // The verdict layers in draw order, bottom to top. Both passes over them - the bands
    // and the centrelines - read this one list, so the order a verdict layers in and the
    // shade it grades to cannot drift apart between the two.
    private static final List<AnchorVerdictLayer> VERDICT_LAYERS = List.of(
        new AnchorVerdictLayer(ClusterAnchor::rejectedAxis, DiagnosticPalette.DISCARDED_COLOUR),
        new AnchorVerdictLayer(ClusterAnchor::unbiasedAxis, DiagnosticPalette.INTERMEDIATE_COLOUR),
        new AnchorVerdictLayer(ClusterAnchor::acceptedAxis, DiagnosticPalette.ACCEPTED_COLOUR));

    // Emits only; never instantiated.
    private ClusterAnchorRenderer() {
    }

    // Draws the whole anchor overlay for one map frame. The lines layer bottom to top by
    // verdict - rejected candidates in red, unbiased comparisons in yellow, accepted
    // label lines in green - one colour pass across all anchors per layer, so where lines
    // overlap the accepted verdict always reads on top; the dots, in each cluster's own
    // resolved shade, go over everything to keep the anchor marker visible even under a
    // pile of lines. Empty (nothing emitted) unless the dev toggle built the anchors, so
    // the normal map pays only an empty-list check; the pass's own saved state isolates its
    // blend, line, and point settings from the rest of the map render.
    public static void renderOnMap(List<ClusterAnchor> anchors, MapFrame mapFrame) {
        // A fully faded-out overlay (alphaMult 0, at the ends of the map's fade) would
        // emit everything at zero effective alpha - all cost, nothing on screen.
        if (anchors.isEmpty() || mapFrame.isFadedOut()) {
            return;
        }
        // Aliased: this is a diagnostic read for where a line lands, and smoothing spreads a
        // one-pixel rule across two - the opposite of what a measurement overlay wants.
        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            GlLineQuality.ALIASED,
            // Profiled (not logged) like the base passes: this runs every frame the map is
            // open, so only the profiler's accumulated view is affordable here.
            () -> {
                try (var renderScope = ActiveProfiler.resolveProfiler().open(RENDER_SECTION)) {
                    drawClusterAnchors(anchors, mapFrame);
                }
            });
    }

    // The band quads, the centrelines, and the dots, layered bottom to top so a verdict
    // reads consistently where they overlap: the fitted-box bands first (rejected, then
    // unbiased, then accepted), each a translucent fill so the border shows through and an
    // overflow is visible; then the thin centrelines in the same order; then the dots in
    // each owner's bright shade over everything. All three verdicts draw their band at the
    // accepted box's girth so their footprints compare like-for-like. The band fill's alpha
    // is the fill-opacity knob times the map fade; the outlines, rules, centrelines, and
    // dots take the separate line-opacity knob times the map fade, so the strokes can read
    // stronger than the wash they sit on.
    private static void drawClusterAnchors(
            List<ClusterAnchor> anchors,
            MapFrame mapFrame) {

        var bandAlpha = (float) KmuMapLabelSettings.getMapAnchorBandOpacity() * mapFrame.alphaMult();
        var lineAlpha = (float) KmuMapLabelSettings.getMapAnchorBandLineOpacity() * mapFrame.alphaMult();

        for (var layer : VERDICT_LAYERS) {
            drawBandLayer(anchors, layer, mapFrame.factor(), bandAlpha, lineAlpha);
        }

        GL11.glLineWidth(ANCHOR_AXIS_WIDTH);
        for (var layer : VERDICT_LAYERS) {
            drawCentrelineLayer(anchors, layer, mapFrame.factor(), lineAlpha);
        }

        GL11.glPointSize(ANCHOR_DOT_SIZE);
        for (var anchor : anchors) {
            GlColour.set(anchor.colour(), lineAlpha);
            GL11.glBegin(GL11.GL_POINTS);
            GL11.glVertex2f(anchor.anchorX() * mapFrame.factor(), anchor.anchorY() * mapFrame.factor());
            GL11.glEnd();
        }
    }

    // Draws one verdict's band across every anchor that carries that line and a band girth
    // to show: a translucent fill, a solid outline, and the line-count divider rules that
    // split the band into the lanes the name's lines would fill. A band thinner than the
    // floor (a collapsed fit or bisection residue) is left to the centreline layer.
    private static void drawBandLayer(
            List<ClusterAnchor> anchors,
            AnchorVerdictLayer layer,
            float factor,
            float bandAlpha,
            float lineAlpha) {

        for (var anchor : anchors) {
            var segment = layer.axisAccessor().apply(anchor);
            if (segment == null || anchor.thickness() < MIN_BAND_THICKNESS) {
                continue;
            }
            var band = bandCorners(segment, anchor.thickness(), factor);
            if (band == null) {
                continue;
            }
            GlColour.set(layer.layerColour(), bandAlpha);
            GlQuads.fillQuad(band);
            GlColour.set(layer.layerColour(), lineAlpha);
            GlLines.strokeLoop(band);

            drawLineRules(
                segment,
                anchor.thickness(),
                anchor.lineCount(),
                factor);
        }
    }

    // Strokes one verdict's centrelines across every anchor that carries that line - the
    // axis a name follows, drawn over the band fill so the line reads on top; an anchor
    // without that line carries null and emits nothing.
    private static void drawCentrelineLayer(
            List<ClusterAnchor> anchors,
            AnchorVerdictLayer layer,
            float factor,
            float lineAlpha) {

        GlColour.set(layer.layerColour(), lineAlpha);
        for (var anchor : anchors) {
            var segment = layer.axisAccessor().apply(anchor);
            if (segment == null) {
                continue;
            }
            GL11.glBegin(GL11.GL_LINES);
            GL11.glVertex2f(
                (float) (segment.startX() * factor),
                (float) (segment.startY() * factor));
            GL11.glVertex2f(
                (float) (segment.endX() * factor),
                (float) (segment.endY() * factor));
            GL11.glEnd();
        }
    }

    // The four corners of a band, in draw (factor-scaled) coordinates: the world box the
    // centreline occupies at that girth, scaled for drawing. Null when the segment has no
    // length to take a direction from, so the band collapses to its centreline.
    //
    // The box itself comes from the segment rather than being widened here, because the same
    // box is what a name is kept clear of elsewhere: an overlay drawing its own reading of
    // the footprint would be a diagnostic that agrees with the thing it is meant to check
    // only by coincidence.
    private static float[] bandCorners(
            Segment segment,
            float thickness,
            float factor) {

        var corners = segment.computeBandCorners(thickness);
        if (corners.isEmpty()) {
            return null;
        }
        var scaled = new float[corners.size() * 2];

        for (var corner = 0; corner < corners.size(); corner++) {
            scaled[corner * 2] = (float) corners.get(corner)[0] * factor;
            scaled[corner * 2 + 1] = (float) corners.get(corner)[1] * factor;
        }
        return scaled;
    }

    // The centreline's unit perpendicular, shared by the band's edges and its lane rules,
    // or null when the segment is too short to take a direction from. Reuses kmlib's
    // degenerate-vector guard (Points.computeUnitVector against Limits.MIN_EDGE_LENGTH)
    // rather than re-deriving the same hypot-and-divide check locally, then rotates the
    // unit direction a quarter turn to its perpendicular.
    private static float[] unitNormalOf(Segment segment) {

        var unit = Points.computeUnitVector(
            segment.endX() - segment.startX(),
            segment.endY() - segment.startY(),
            Limits.MIN_EDGE_LENGTH);

        return unit == null
            ? null
            : new float[] {
                (float) -unit[1],
                (float) unit[0]};
    }

    // Draws the divider rules between a multi-line band's lanes: lineCount minus one lines
    // parallel to the centreline, evenly spaced across the girth, so a two- or three-line
    // fit reads as stacked lines rather than one thick bar. Nothing for a single line.
    private static void drawLineRules(
            Segment segment,
            float thickness,
            int lineCount,
            float factor) {

        if (lineCount < 2) {
            return;
        }
        var normal = unitNormalOf(segment);
        if (normal == null) {
            return;
        }
        GL11.glBegin(GL11.GL_LINES);
        for (var rule = 1; rule < lineCount; rule++) {

            // Step from one edge (-half) across the girth in even lane widths.
            var offset = -thickness / 2f + thickness * rule / lineCount;

            GL11.glVertex2f(
                (float) (segment.startX() + normal[0] * offset) * factor,
                (float) (segment.startY() + normal[1] * offset) * factor);
            GL11.glVertex2f(
                (float) (segment.endX() + normal[0] * offset) * factor,
                (float) (segment.endY() + normal[1] * offset) * factor);
        }
        GL11.glEnd();
    }

    /**
     * One verdict's presentation: the accessor that pulls that verdict's axis off an anchor -
     * null on an anchor that carries no such line, which the passes skip - paired with the
     * palette shade the whole layer draws in. Pairing the two makes the band pass and the
     * centreline pass share one declaration of what a verdict looks like, instead of each
     * repeating the accessor and the shade per verdict.
     */
    private record AnchorVerdictLayer(
        Function<ClusterAnchor, Segment> axisAccessor,
        Color layerColour) {
    }
}
