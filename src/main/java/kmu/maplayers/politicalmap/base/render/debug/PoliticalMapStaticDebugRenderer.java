package kmu.maplayers.politicalmap.base.render.debug;

import kmlib.opengl.GlColour;
import kmlib.opengl.GlRuns;

import kmu.maplayers.base.labels.anchor.DiagnosticPalette;

import org.lwjgl.opengl.GL11;

import java.awt.Color;
import java.util.List;

/**
 * Paints the debug border-tracing overlay in place of the normal political map: the
 * smoothing pipeline's stages stacked so each pass's effect reads against the one before
 * it. Bottom to top - the base traced border in red, the despiked border in yellow, and
 * the rounded border in green - each stage present only when its pass ran, so the layering
 * mirrors the two smoothing gates.
 *
 * <p>Static because, unlike the production
 * {@link kmu.maplayers.politicalmap.base.render.territories.TerritoryRenderer} (which pairs with
 * {@link kmu.maplayers.politicalmap.base.render.IncrementalPoliticsRefresh} to fold holding
 * changes into its draw lists in place), this overlay has no incremental path: a colony flip
 * rebuilds the whole overlay, so nothing here updates a subset of it between full rebuilds.
 *
 * <p>Pure GL emission over an already-built {@link PoliticalMapDebugTerritories}, the debug
 * analogue of {@link kmu.maplayers.politicalmap.base.render.territories.TerritoryRenderer}; the
 * terrain plugin swaps to this renderer while the debug toggle is on. Line widths taper from base
 * to rounded so an inner stage rings out from under the one drawn over it rather than being fully
 * hidden.
 */
public final class PoliticalMapStaticDebugRenderer {
    // Widths taper so each earlier stage's line haloes out from under the next: the base is
    // widest, the rounded thinnest and on top. The stage colours come from the shared
    // {@link DiagnosticPalette}: the base trace is superseded geometry (red), the despiked
    // border a mid-pipeline view (yellow), and the rounded border what ships (green).
    private static final float BASE_WIDTH = 5f;
    private static final float DESPIKED_WIDTH = 3f;
    private static final float ROUNDED_WIDTH = 1.5f;

    // Emits only; never instantiated.
    private PoliticalMapStaticDebugRenderer() {
    }

    // Draws the whole debug overlay for one map frame, in the same below-UI pass and
    // isolated GL state the normal render uses. An empty overlay skips the push entirely.
    public static void renderOnMap(PoliticalMapDebugTerritories debug, float factor,
            float alphaMult) {
        if (debug.isEmpty()) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LINE_BIT | GL11.GL_HINT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        // Drawn in list order, so the last stage lands on top and the earlier ones halo out
        // from under it; the view transform is the same for every stage.
        for (var stage : listStagesBottomToTop(debug)) {
            drawStage(stage, factor, alphaMult);
        }

        GL11.glPopAttrib();
    }

    // Strokes one stage's loops in its colour and width; each loop is a closed ring, so it
    // draws as a GL_LINE_LOOP. An empty stage - its pass was gated off - draws nothing.
    private static void drawStage(DebugStage stage, float factor, float alphaMult) {
        if (stage.loopRuns().isEmpty()) {
            return;
        }
        GL11.glLineWidth(stage.lineWidth());
        GlColour.set(stage.strokeColour(), alphaMult);

        for (var loopRun : stage.loopRuns()) {
            GlRuns.drawScaled(GL11.GL_LINE_LOOP, loopRun, factor);
        }
    }

    // The stack order as data - base under despiked under rounded - so the layering, the
    // colour ramp and the width taper are read off one list instead of three call sites.
    private static List<DebugStage> listStagesBottomToTop(PoliticalMapDebugTerritories debug) {
        return List.of(
            new DebugStage(
                debug.baseLoops(),
                DiagnosticPalette.DISCARDED_COLOUR,
                BASE_WIDTH),
            new DebugStage(
                debug.despikedLoops(),
                DiagnosticPalette.INTERMEDIATE_COLOUR,
                DESPIKED_WIDTH),
            new DebugStage(
                debug.roundedLoops(),
                DiagnosticPalette.ACCEPTED_COLOUR,
                ROUNDED_WIDTH));
    }

    // One smoothing stage's stroke: the loops to draw plus how this stage is distinguished
    // from the ones under and over it.
    private record DebugStage(
        List<float[]> loopRuns,
        Color strokeColour,
        float lineWidth) {
    }
}
