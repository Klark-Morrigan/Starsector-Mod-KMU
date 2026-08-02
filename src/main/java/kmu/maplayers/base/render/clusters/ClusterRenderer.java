package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.render.clusters.StyledCell.FusedCell;
import kmu.maplayers.base.render.clusters.StyledCell.LoneCell;

import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Paints pre-built cluster draw lists on the sector (M) map: every fill first - the fused
 * footprints' and then the lone cells' - and over them the interior seams, the lone cells'
 * outlines, and the cluster borders. The debug cluster anchors are not drawn here: they are an
 * independent overlay ({@link kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer}) the
 * terrain plugin layers over whichever base view is live.
 *
 * <p>This is pure GL emission over an already-baked {@link ClusterDrawLists} - it scales each
 * world coordinate into map space and strokes/fills the flattened vertex runs, with no knowledge
 * of settings, caches, or how the runs were shaped. The map widget has already applied the map's
 * pan and centering to the GL matrix, so only the scale is applied here.
 */
public final class ClusterRenderer {
    
    // Emits only; never instantiated.
    private ClusterRenderer() {
    }

    // Draws the whole overlay for one map frame. Drawn in the below-UI map pass so
    // system and constellation names stay on top; a state push/pop isolates the blend
    // and line settings from the rest of the map render. An empty overlay skips the
    // push entirely.
    public static void renderOnMap(
            ClusterDrawLists drawLists,
            float factor,
            float alphaMult) {

        // A fully faded-out overlay (alphaMult 0, at the ends of the map's fade) would
        // emit every run at zero effective alpha - all cost, nothing on screen - so the
        // whole GL pass is skipped, not just left to blend away.
        if (drawLists.isEmpty() || alphaMult <= 0f) {
            return;
        }
        // Aliased: the fills and their borders are large filled shapes whose edges the map's own
        // scaling already softens, and smoothing every border run costs a blend per covered pixel
        // across the whole sector.
        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            GlLineQuality.ALIASED,
            () -> {
                // Time only the per-frame GL emission; the surrounding state push/pop is
                // negligible. Broken into the two passes so the profiler shows which one costs,
                // but not logged - this runs every frame the map is open, so only the profiler's
                // accumulated view is affordable here, never a per-frame log line.
                var profiler = KmuProfiling.getProfiler();
                profiler.measure(
                    "mapLayer.render.clusters",
                    () -> {
                        profiler.measure(
                            "mapLayer.render.clusters.fills",
                            () -> drawFills(drawLists, factor, alphaMult));
                        profiler.measure(
                            "mapLayer.render.clusters.borders",
                            () -> drawBorders(drawLists, factor, alphaMult));
                    });
            });
    }

    // Fills each fused footprint with its resolved fill colour at its opacity, then each lone
    // cell that carries a fill of its own. Both are pre-tessellated triangle soups, so a concave
    // footprint (or one with an enclave) fills correctly and exactly matches the stroked border.
    // A hidden fill (UiElementPaint.isHidden) is skipped, its geometry kept to shape its
    // neighbours but never emitted. A footprint whose ground does not all fill solid splits into
    // both runs at once - solid triangles where it fills and pre-clipped diagonal hatch lines
    // where the layer marked it hatched, in the same colour and opacity - so a partly-filled body
    // still reads as one inside a single border; every other footprint carries an empty hatch run
    // and paints only its triangles.
    private static void drawFills(ClusterDrawLists drawLists, float factor, float alphaMult) {

        // The hatch fills its sub-cluster in the fill colour but strokes as GL_LINES, so its own
        // pixel width tunes the hatched texture apart from the solid fill. The width is
        // sector-wide, so set it once here off the theme's global tier rather than per footprint;
        // the triangle soups below are width-agnostic, and typically only one footprint carries a
        // non-empty hatch run.
        GL11.glLineWidth((float) drawLists.getGlobalStyle().hatch().width());
        for (var cluster : drawLists.getStyledClusterById().values()) {
            emitIfVisible(
                cluster.fill(),
                alphaMult,
                () -> {
                    GlRuns.drawScaled(
                        GL11.GL_TRIANGLES,
                        cluster.fillTriangles(),
                        factor);
                    GlRuns.drawScaled(
                        GL11.GL_LINES,
                        cluster.hatchSegments(),
                        factor);
            });
        }
        // Then the lone cells' own fills. They fill per cell rather than per footprint because
        // unowned ground never fuses into one, and they cover no footprint, so drawing them after
        // the footprint fills is a matter of grouping the fill pass rather than of layering. A
        // fused cell is not reached at all: its fill is its footprint's, drawn above, so this pass
        // sees only the cells that have one.
        drawEachCellOfForm(
            drawLists.getStyledCellByCellId().values(),
            LoneCell.class,
            lone -> emitIfVisible(
                lone.fillPaint(),
                alphaMult,
                () -> GlRuns.drawScaled(
                    GL11.GL_TRIANGLES,
                    lone.fillTriangles(),
                    factor)));
    }

    // Draws one element of every cell of one form, skipping the cells of the other. Each pass over
    // the cells names the form it draws and what it draws for it, rather than restating the walk and
    // the narrowing - which is what keeps a later pass from being written over every cell and
    // reaching for a part the form it meant does not have.
    private static <T extends StyledCell> void drawEachCellOfForm(
            Collection<StyledCell> cells,
            Class<T> form,
            Consumer<T> drawCell) {

        for (var cell : cells) {
            if (form.isInstance(cell)) {
                drawCell.accept(form.cast(cell));
            }
        }
    }

    // Binds one element's colour and emits its runs, or skips both when the element puts no ink
    // on the map. Every pass here does exactly this, so binding and the hidden test travel
    // together rather than being restated per loop - which is what keeps a new run from being
    // added with the colour bound but the hidden test forgotten, drawing an invisible element in
    // the previous one's colour.
    private static void emitIfVisible(UiElementPaint paint, float alphaMult, Runnable emitRuns) {
        if (paint.isHidden()) {
            return;
        }
        GlColour.set(paint.colour(), alphaMult * paint.alpha());
        emitRuns.run();
    }

    // Strokes the interior seams first, then the lone cells' outlines, then the smoothed cluster
    // borders over them, so a footprint's border dominates the seams inside it where they meet.
    // Colour, opacity, and line width are all per element, and a hidden element
    // (UiElementPaint.isHidden) is skipped - its geometry stays baked to shape its neighbours, but
    // nothing invisible is emitted. Which cells each stroke reaches is the cell's own form rather
    // than a test here: a fused cell carries only seams and a lone cell only an outline, and a
    // footprint's border is its border ring in the cluster draw list.
    private static void drawBorders(
            ClusterDrawLists drawLists,
            float factor,
            float alphaMult) {

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        drawEachCellOfForm(
            drawLists.getStyledCellByCellId().values(),
            FusedCell.class,
            fused -> emitIfVisible(
                fused.seamPaint(),
                alphaMult,
                () -> {
                    GL11.glLineWidth(fused.seamWidth());
                    GlRuns.drawScaled(GL11.GL_LINES, fused.seamEdges(), factor);
                }));

        drawEachCellOfForm(
            drawLists.getStyledCellByCellId().values(),
            LoneCell.class,
            lone -> emitIfVisible(
                lone.outlinePaint(),
                alphaMult,
                () -> {
                    GL11.glLineWidth(lone.outlineWidth());
                    GlRuns.drawScaled(GL11.GL_LINES, lone.outlineEdges(), factor);
                }));

        // The cluster border in its own style, over the interior seams so it dominates where they
        // meet. Each border ring is a closed rounded loop, so it strokes as one continuous
        // GL_LINE_LOOP rather than the disconnected GL_LINES the per-cell edges use.
        for (var cluster : drawLists.getStyledClusterById().values()) {
            emitIfVisible(
                cluster.border(),
                alphaMult,
                () -> {
                    GL11.glLineWidth(cluster.borderWidth());
                    for (var loop : cluster.borderLoops()) {
                        GlRuns.drawScaled(GL11.GL_LINE_LOOP, loop, factor);
                    }
            });
        }
    }
}
