package kmu.maplayers.base.render.clusters.debug;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;

import kmu.maplayers.base.labels.anchor.DiagnosticPalette;
import kmu.maplayers.base.render.MapFrame;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

/**
 * Paints the cluster-border smoothing pipeline in place of a layer's normal fills: the stages
 * stacked so each pass's effect reads against the one before it. Bottom to top - the base traced
 * border in red, the despiked border in yellow, and the rounded border in green - each stage
 * present only when its pass ran, so the layering mirrors the two smoothing gates.
 *
 * <p>Static because the overlay has no incremental path: any change to the cells it traces
 * rebuilds the whole of it, so nothing here updates a subset of one between rebuilds.
 *
 * <p>Pure GL emission over an already-built {@link ClusterBorderStageOverlay}, the debug analogue
 * of {@link kmu.maplayers.base.render.clusters.ClusterRenderer}. Line widths
 * taper from base to rounded so an inner stage rings out from under the one drawn over it rather
 * than being fully hidden.
 */
public final class ClusterBorderStageRenderer {

    // Widths taper so each earlier stage's line haloes out from under the next: the base is
    // widest, the rounded thinnest and on top. The stage colours come from the shared
    // {@link DiagnosticPalette}: the base trace is superseded geometry (red), the despiked
    // border a mid-pipeline view (yellow), and the rounded border what ships (green).
    private static final float BASE_WIDTH = 5f;
    private static final float DESPIKED_WIDTH = 3f;
    private static final float ROUNDED_WIDTH = 1.5f;

    // Emits only; never instantiated.
    private ClusterBorderStageRenderer() {
    }

    // Draws the whole stage overlay for one map frame, in the same below-UI pass and isolated
    // GL state the normal render uses. An empty overlay skips the push entirely.
    public static void renderOnMap(
            ClusterBorderStageOverlay stageOverlay,
            MapFrame mapFrame) {

        if (stageOverlay.isEmpty()) {
            return;
        }
        // Smoothed, because the stages are compared against each other by eye: three hard-edged
        // rings a pixel or two apart alias into one another at map zoom.
        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            GlLineQuality.SMOOTHED,
            () -> {
                // Drawn in list order, so the last stage lands on top and the earlier ones halo
                // out from under it; the view transform is the same for every stage.
                for (var stageStroke : listStageStrokesBottomToTop(stageOverlay)) {
                    drawStageStroke(stageStroke, mapFrame);
                }
            });
    }

    // The stack order as data - base under despiked under rounded - so the layering, the
    // colour ramp and the width taper are read off one list instead of three call sites. The
    // whole of what distinguishes the three stages resolves here, with no GL in reach, which
    // is why it is the list and not the emission that carries the ordering decision.
    static List<StageStroke> listStageStrokesBottomToTop(
            ClusterBorderStageOverlay stageOverlay) {

        return List.of(
            new StageStroke(
                stageOverlay.baseLoops(),
                DiagnosticPalette.DISCARDED_COLOUR,
                BASE_WIDTH),
            new StageStroke(
                stageOverlay.despikedLoops(),
                DiagnosticPalette.INTERMEDIATE_COLOUR,
                DESPIKED_WIDTH),
            new StageStroke(
                stageOverlay.roundedLoops(),
                DiagnosticPalette.ACCEPTED_COLOUR,
                ROUNDED_WIDTH));
    }

    // Strokes one stage's loops in its colour and width; each loop is a closed ring, so it
    // draws as a GL_LINE_LOOP. An empty stage - its pass was gated off - draws nothing.
    private static void drawStageStroke(StageStroke stageStroke, MapFrame mapFrame) {
        if (stageStroke.loopRuns().isEmpty()) {
            return;
        }
        GL11.glLineWidth(stageStroke.lineWidth());
        GlColour.set(stageStroke.strokeColour(), mapFrame.alphaMult());

        for (var loopRun : stageStroke.loopRuns()) {
            GlRuns.drawScaled(GL11.GL_LINE_LOOP, loopRun, mapFrame.factor());
        }
    }

    // One smoothing stage's stroke: the loops to draw plus how this stage is distinguished
    // from the ones under and over it.
    record StageStroke(
        List<float[]> loopRuns,
        Color strokeColour,
        float lineWidth) {
    }
}
