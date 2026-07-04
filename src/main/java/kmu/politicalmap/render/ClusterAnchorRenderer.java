package kmu.politicalmap.render;

import kmlib.opengl.GlColor;

import kmu.diagnostics.KmuProfiling;
import kmu.politicalmap.render.model.ClusterAnchor;

import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Paints the debug cluster-anchor overlay on the sector (M) map: each cluster's anchor
 * dot plus its accepted, rejected, and unbiased label lines.
 *
 * <p>Its own renderer, apart from {@link PoliticalMapRenderer}, because the anchors are
 * an independent overlay: the terrain plugin draws them after whichever base view is
 * live - the normal political map or the debug border-tracing overlay - so turning
 * border tracing on never hides them. Pure GL emission over an already-built anchor
 * list, in the same below-UI map pass and coordinate convention the base renderers use.
 */
final class ClusterAnchorRenderer {
    // The anchor dot's diameter in screen pixels (GL_POINTS sizes in pixels, so it
    // stays a constant dot at any zoom) and its axis lines' width. Sized to read over
    // the fills and borders without swamping the systems they mark. The lines' colors
    // come from the shared {@link DiagnosticPalette}, so the anchor overlay and the
    // border-tracing overlay grade their layers identically.
    private static final float ANCHOR_DOT_SIZE = 10f;
    private static final float ANCHOR_AXIS_WIDTH = 2f;

    // Emits only; never instantiated.
    private ClusterAnchorRenderer() {
    }

    // Draws the whole anchor overlay for one map frame. The lines layer bottom to top by
    // verdict - rejected candidates in red, unbiased comparisons in yellow, accepted
    // label lines in green - one color pass across all anchors per layer, so where lines
    // overlap the accepted verdict always reads on top; the dots, in each owner's bright
    // faction shade, go over everything to keep the anchor marker visible even under a
    // pile of lines. Empty (nothing emitted) unless the dev toggle built the anchors, so
    // the normal map pays only an empty-list check; a state push/pop isolates the blend,
    // line, and point settings from the rest of the map render.
    static void renderOnMap(List<ClusterAnchor> anchors, float factor, float alphaMult) {
        // A fully faded-out overlay (alphaMult 0, at the ends of the map's fade) would
        // emit everything at zero effective alpha - all cost, nothing on screen.
        if (anchors.isEmpty() || alphaMult <= 0f) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_POINT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Profiled (not logged) like the base passes: this runs every frame the map is
        // open, so only the profiler's accumulated view is affordable here.
        KmuProfiling.getProfiler().measure("politicalMap.render.anchors",
                () -> drawClusterAnchors(anchors, factor, alphaMult));

        GL11.glPopAttrib();
    }

    // The three line layers and the dots, each layered pass one color. The dot is a
    // fixed-pixel GL_POINTS mark; each line is one GL_LINES segment.
    private static void drawClusterAnchors(List<ClusterAnchor> anchors, float factor,
            float alphaMult) {
        GL11.glPointSize(ANCHOR_DOT_SIZE);
        GL11.glLineWidth(ANCHOR_AXIS_WIDTH);
        GlColor.set(DiagnosticPalette.DISCARDED_COLOR, alphaMult);
        for (var anchor : anchors) {
            drawAxisSegment(anchor.rejectedAxis(), factor);
        }
        GlColor.set(DiagnosticPalette.INTERMEDIATE_COLOR, alphaMult);
        for (var anchor : anchors) {
            drawAxisSegment(anchor.unbiasedAxis(), factor);
        }
        GlColor.set(DiagnosticPalette.ACCEPTED_COLOR, alphaMult);
        for (var anchor : anchors) {
            drawAxisSegment(anchor.acceptedAxis(), factor);
        }
        for (var anchor : anchors) {
            GlColor.set(anchor.color(), alphaMult);
            GL11.glBegin(GL11.GL_POINTS);
            GL11.glVertex2f(anchor.anchorX() * factor, anchor.anchorY() * factor);
            GL11.glEnd();
        }
    }

    // Strokes one of an anchor's lines; an anchor without that line carries null and
    // emits nothing - a collapsed fit, or a diagnostic whose toggle is off.
    private static void drawAxisSegment(ClusterAnchor.AxisSegment segment, float factor) {
        if (segment == null) {
            return;
        }
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2f(segment.startX() * factor, segment.startY() * factor);
        GL11.glVertex2f(segment.endX() * factor, segment.endY() * factor);
        GL11.glEnd();
    }
}
