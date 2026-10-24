package kmu.maplayers.base.labels.anchor;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;

import kmu.maplayers.base.render.MapFrame;

import org.lwjgl.opengl.GL11;

/**
 * Paints the debug cluster-anchor overlay on the sector (M) map: each cluster's anchor
 * dot plus its accepted, rejected, and unbiased label lines.
 *
 * <p>Its own renderer, apart from whatever draws the clusters themselves, because the
 * anchors are an independent overlay: they are drawn after whichever base view is live -
 * a layer's normal render or its debug overlay - so turning a debug view on never hides
 * them. Pure GL emission over the {@link ClusterAnchorRuns} a pass was packed into, in the same
 * below-UI map pass and coordinate convention the base renderers use.
 */
public final class ClusterAnchorRenderer {

    // Held rather than named per frame: a section found by reference costs the pass nothing where
    // one found by name is a lookup a frame.
    private static final ProfileSection RENDER_SECTION =
        ProfileSection.registerSection("mapLayer.render.anchors");

    // The anchor dot's diameter in screen pixels (GL_POINTS sizes in pixels, so it
    // stays a constant dot at any zoom) and its axis lines' width. Sized to read over
    // the fills and borders without swamping the systems they mark. The lines' colours
    // come from the shared palette the runs were packed in, so the anchor overlay and the
    // border-tracing overlay grade their layers identically.
    private static final float ANCHOR_DOT_SIZE = 10f;
    private static final float ANCHOR_AXIS_WIDTH = 2f;

    // Emits only; never instantiated.
    private ClusterAnchorRenderer() {
    }

    /**
     * Draws the whole anchor overlay for one map frame.
     *
     * <p>Nothing is emitted unless the dev toggle built the anchors, so the normal map pays only an
     * emptiness check. The pass's own saved state isolates its blend, line, and point settings from
     * the rest of the map render.
     *
     * @param anchorRuns the standing placements, packed into the runs they draw as
     * @param mapFrame   the scale every coordinate is multiplied by and the fade over every run
     */
    public static void renderOnMap(ClusterAnchorRuns anchorRuns, MapFrame mapFrame) {

        // A fully faded-out overlay (alphaMult 0, at the ends of the map's fade) would
        // emit everything at zero effective alpha - all cost, nothing on screen.
        if (anchorRuns.isEmpty() || mapFrame.isFadedOut()) {
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
                    drawAnchorRuns(anchorRuns, mapFrame);
                }
            });
    }

    // The band fills and strokes, the centrelines, and the dots, layered bottom to top so a verdict
    // reads consistently where they overlap: every verdict's bands first (rejected, then unbiased,
    // then accepted), each a translucent fill under its outline and lane rules so the border shows
    // through and an overflow is visible; then the thin centrelines in the same order; then the dots
    // in each owner's bright shade over everything, to keep the anchor marker visible even under a
    // pile of lines. The fill's alpha is the fill-opacity knob times the map fade; the strokes,
    // centrelines and dots take the separate line-opacity knob times the map fade, so they can read
    // stronger than the wash they sit on.
    private static void drawAnchorRuns(
            ClusterAnchorRuns anchorRuns,
            MapFrame mapFrame) {

        var factor = mapFrame.factor();
        var bandAlpha = anchorRuns.bandOpacity() * mapFrame.alphaMult();
        var lineAlpha = anchorRuns.lineOpacity() * mapFrame.alphaMult();

        for (var verdict : anchorRuns.verdictRuns()) {

            GlColour.set(verdict.verdictColour(), bandAlpha);
            drawRun(GL11.GL_QUADS, verdict.bandFills(), factor);

            GlColour.set(verdict.verdictColour(), lineAlpha);
            drawRun(GL11.GL_LINES, verdict.bandStrokes(), factor);
        }

        GL11.glLineWidth(ANCHOR_AXIS_WIDTH);

        for (var verdict : anchorRuns.verdictRuns()) {

            GlColour.set(verdict.verdictColour(), lineAlpha);
            drawRun(GL11.GL_LINES, verdict.centrelines(), factor);
        }

        GL11.glPointSize(ANCHOR_DOT_SIZE);

        for (var dotRun : anchorRuns.dotRuns()) {

            GlColour.set(dotRun.dotColour(), lineAlpha);
            drawRun(GL11.GL_POINTS, dotRun.dots(), factor);
        }
    }

    // One run, or nothing where the run is empty: a verdict the toggles leave off packs empty runs,
    // and an empty primitive is a begin and an end for nothing.
    private static void drawRun(int mode, float[] run, float factor) {

        if (run.length > 0) {
            GlRuns.drawScaled(mode, run, factor);
        }
    }
}
