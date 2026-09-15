package kmu.maplayers.base.render.clusters;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.maplayers.base.render.MapFrame;
import kmu.maplayers.base.render.clusters.StyledCell.FusedCell;
import kmu.maplayers.base.render.clusters.StyledCell.LoneCell;
import kmu.maplayers.base.theme.GlLineHatchStroke;

import org.lwjgl.opengl.GL11;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Paints pre-built cluster draw lists on the sector (M) map, through two entry points a caller
 * invokes in order: the fills - every solid fill first, the clusters' and then the lone cells',
 * then the contested hatch over them - and the borders, which are the interior seams, the lone
 * cells' outlines, and the cluster boundaries. The debug cluster anchors are not drawn here: they
 * are an independent overlay ({@link kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer}) the
 * terrain plugin layers over whichever base view is live.
 *
 * <p>The two are separate entries rather than one because a caller may need to put its own drawing
 * between them - the sector map lays its nebulae over the overlay mid-frame, and which side of that
 * each half lands on is the caller's call. Nothing here orders them: emitted back to back they
 * produce the single pass they used to be, and the fills-under-borders order that reads correctly
 * is the caller's to keep.
 *
 * <p>This is pure GL emission over an already-baked {@link ClusterDrawLists} - it scales each
 * world coordinate into map space and strokes/fills the flattened vertex runs, with no knowledge
 * of settings, caches, or how the runs were shaped. The map widget has already applied the map's
 * pan and centering to the GL matrix, so only the scale is applied here.
 */
public final class ClusterRenderer {

    // Held rather than named per frame: both passes run every frame the map is open, and a section
    // found by reference costs a pass nothing where one found by name is a lookup a frame.
    private static final ProfileSection FILLS_SECTION =
        ProfileSection.registerSection("mapLayer.render.clusters.fills");

    private static final ProfileSection BORDERS_SECTION =
        ProfileSection.registerSection("mapLayer.render.clusters.borders");

    // Emits only; never instantiated.
    private ClusterRenderer() {
    }

    // Draws the overlay's fills - solid then hatched - for one map frame. Drawn in the below-UI map
    // pass so system and constellation names stay on top; a state push/pop isolates the blend and
    // line settings from the rest of the map render. An empty overlay skips the push entirely.
    //
    // Aliased is the baseline this pass leaves in force, so a run added here later lands hard-edged
    // instead of quietly picking up the smoothing of the pass that happened to run last. The hatch
    // inside opens its own pass regardless, since the quality a line wants is the pass's own
    // decision rather than something to inherit from whatever wraps it.
    public static void renderFillsOnMap(
            ClusterDrawLists drawLists,
            MapFrame mapFrame) {

        renderMeasuredPassOnMap(
            drawLists,
            mapFrame,
            GlLineQuality.ALIASED,
            FILLS_SECTION,
            ClusterRenderer::drawFills);
    }

    // Draws the overlay's border strokes for one map frame, in the same below-UI map pass the fills
    // go down in. Smoothed because borders are long continuous runs a player follows across the
    // sector - the case antialiasing is worth its blend per covered pixel, unlike the dense short
    // strokes of the hatch - and saying so as the pass's quality is what keeps the choice one the
    // library applies rather than a pair of GL calls restated here.
    public static void renderBordersOnMap(
            ClusterDrawLists drawLists,
            MapFrame mapFrame) {

        renderMeasuredPassOnMap(
            drawLists,
            mapFrame,
            GlLineQuality.SMOOTHED,
            BORDERS_SECTION,
            ClusterRenderer::strokeBorderRuns);
    }

    // The scaffold both entry points are: skip a frame with nothing on it, pair the draw lists with
    // the frame, and emit under a measured blended pass. Written once because the two halves differ
    // only in the three values named at the call sites - a second copy is where a guard gets
    // tightened on one entry and not the other, which shows up as an overlay half-drawn at the ends
    // of the map's fade and nothing in the frame to say why.
    //
    // A fully faded-out overlay (alphaMult 0, at the ends of the map's fade) would emit every run at
    // zero effective alpha - all cost, nothing on screen - so the whole GL pass is skipped rather
    // than left to blend away, and the frame is not built for it either.
    //
    // The scope times only the per-frame GL emission; the surrounding state push/pop is
    // negligible. It is per entry point so the profiler shows which half costs, but never logged -
    // this runs every frame the map is open, so only the profiler's accumulated view is affordable.
    private static void renderMeasuredPassOnMap(
            ClusterDrawLists drawLists,
            MapFrame mapFrame,
            GlLineQuality lineQuality,
            ProfileSection section,
            Consumer<ClusterMapFrame> emitRuns) {

        if (drawLists.isEmpty() || mapFrame.isFadedOut()) {
            return;
        }
        var frame = new ClusterMapFrame(drawLists, mapFrame);

        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            lineQuality,
            () -> {
                try (var renderScope = ActiveProfiler.resolveProfiler().open(section)) {
                    emitRuns.accept(frame);
                }
            });
    }

    // All the solid fills first, then the hatch over them. A cluster whose fill does
    // not all fill solid carries both runs at once - solid triangles where it fills and
    // pre-clipped diagonal hatch lines where the layer marked it hatched, in the same colour and
    // opacity - so a partly-filled body still reads as one inside its boundary.
    //
    // Hoisting every hatch run above every solid fill covers nothing that was visible before:
    // cells are disjoint, so no owner's solid triangles can land on another owner's hatched
    // fill, and typically only one owner (the filter's spotlit one) carries a non-empty hatch
    // run at all. What the split buys is that the line state the hatch strokes under is set once
    // for the whole map rather than pushed and popped around every solid fill.
    private static void drawFills(ClusterMapFrame frame) {
        drawSolidFills(frame);
        drawHatchedFills(frame);
    }

    // Fills every cluster an owner holds with that owner's resolved fill colour at its opacity,
    // then each lone cell that carries a fill of its own. Both are pre-tessellated triangle
    // soups, so a concave cluster (or one with an enclave) fills correctly and exactly matches
    // the stroked boundary.
    private static void drawSolidFills(ClusterMapFrame frame) {

        drawEachClusterRun(
            frame,
            GL11.GL_TRIANGLES,
            StyledCluster::fillTriangles);

        // Then the lone cells' own fills. They fill per cell rather than per cluster because
        // an unowned cell never fuses into one, and they cover no cluster, so drawing them after
        // the cluster fills is a matter of grouping the fill pass rather than of layering. A
        // fused cell is not reached at all: its fill is its cluster's, drawn above, so this pass
        // sees only the cells that have one.
        drawEachCellOfForm(
            frame,
            LoneCell.class,
            lone -> emitIfVisible(
                lone.fillPaint(),
                frame.getAlphaMult(),
                () -> GlRuns.drawScaled(
                    GL11.GL_TRIANGLES,
                    lone.fillTriangles(),
                    frame.getFactor())));
    }

    // Lays the hatch over the solid fill, in whichever way the theme's stroke says. The stroke
    // is the sector-wide choice of substrate, so it is resolved once for the whole pass rather
    // than per owner - the geometry underneath is the same runs either way, and only how they are
    // turned into pixels differs.
    private static void drawHatchedFills(ClusterMapFrame frame) {

        var hatch = frame.drawLists().getGlobalStyle().hatch();
        if (hatch.stroke() instanceof GlLineHatchStroke lineStroke) {
            drawHatchAsGlLines(frame, hatch.pattern().spacing(), lineStroke);
        }
    }

    // The hatch handed to the GL line rasteriser, in a blended pass of its own so the line quality
    // and width it strokes at are the hatch's own rather than whatever the surrounding fill pass
    // happened to leave in force. A cluster that fills solid carries an empty hatch run and so
    // costs this pass nothing beyond the colour bind its group already needs.
    //
    // The spacing comes down with the stroke because this substrate's width is a fraction of it:
    // the segments are world-space geometry the frame scales, so a width that did not scale with
    // them would cover a different share of the gap at every zoom - see GlLineHatchStroke.
    private static void drawHatchAsGlLines(
            ClusterMapFrame frame,
            double spacing,
            GlLineHatchStroke stroke) {

        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            stroke.quality(),
            () -> {
                GL11.glLineWidth(stroke.computeWidthPixelsAt(spacing, frame.getFactor()));
                drawEachClusterRun(
                    frame,
                    GL11.GL_LINES,
                    StyledCluster::hatchSegments);
            });
    }

    // Emits one run of every body every owner holds, under that owner's fill paint. The two fill
    // passes are the same walk over the same bodies and differ only in which of a body's runs they
    // read and what primitive it draws as, so the walk is written once and each pass names only
    // that difference - which is what stops one of them from later growing a narrowing or a paint
    // the other silently lacks.
    //
    // The colour binds once per owner and every body that owner holds emits under it, which is
    // what the paint-per-owner, geometry-per-cluster split buys: a holding scattered over the
    // sector costs one bind, not one per body. A hidden fill (UiElementPaint.isHidden) is skipped,
    // its geometry kept to shape its neighbours but never emitted.
    private static void drawEachClusterRun(
            ClusterMapFrame frame,
            int primitiveMode,
            Function<StyledCluster, float[]> selectRun) {

        for (var group : frame.drawLists().getStyledClusterGroupByOwnerId().values()) {
            emitIfVisible(
                group.fill(),
                frame.getAlphaMult(),
                () -> {
                    for (var cluster : group.clusters()) {
                        GlRuns.drawScaled(
                            primitiveMode,
                            selectRun.apply(cluster),
                            frame.getFactor());
                    }
            });
        }
    }

    // Draws one element of every cell of one form, skipping the cells of the other. Each pass over
    // the cells names the form it draws and what it draws for it, rather than restating the walk,
    // the narrowing, and which of the frame's lists the cells come out of - which is what keeps a
    // later pass from being written over every cell and reaching for a part the form it meant does
    // not have.
    private static <T extends StyledCell> void drawEachCellOfForm(
            ClusterMapFrame frame,
            Class<T> form,
            Consumer<T> drawCell) {

        for (var cell : frame.drawLists().getStyledCellByCellKey().values()) {
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

    // Strokes the interior seams first, then the lone cells' outlines, then the cluster boundaries
    // over them, so a cluster's boundary dominates the seams inside it where they meet. Colour,
    // opacity, and line width are all per element, and a hidden element (UiElementPaint.isHidden)
    // is skipped - its geometry stays baked to shape its neighbours, but nothing invisible is
    // emitted. Which cells each stroke reaches is the cell's own form rather than a test here: a
    // fused cell carries only seams and a lone cell only an outline, and a cluster's boundary is
    // its own loops in the cluster draw list.
    private static void strokeBorderRuns(ClusterMapFrame frame) {

        drawEachCellOfForm(
            frame,
            FusedCell.class,
            fused -> emitIfVisible(
                fused.seamPaint(),
                frame.getAlphaMult(),
                () -> {
                    GL11.glLineWidth(fused.seamWidth());
                    GlRuns.drawScaled(GL11.GL_LINES, fused.seamEdges(), frame.getFactor());
                }));

        drawEachCellOfForm(
            frame,
            LoneCell.class,
            lone -> emitIfVisible(
                lone.outlinePaint(),
                frame.getAlphaMult(),
                () -> {
                    GL11.glLineWidth(lone.outlineWidth());
                    GlRuns.drawScaled(GL11.GL_LINES, lone.outlineEdges(), frame.getFactor());
                }));

        // The cluster boundaries in the owner's own style, over the interior seams so a boundary
        // dominates where they meet. Each loop is closed and already rounded, so it strokes as
        // one continuous GL_LINE_LOOP rather than the disconnected GL_LINES the per-cell edges
        // use. Colour and width bind once per owner, as the fills do.
        for (var group : frame.drawLists().getStyledClusterGroupByOwnerId().values()) {
            emitIfVisible(
                group.border(),
                frame.getAlphaMult(),
                () -> {
                    GL11.glLineWidth(group.borderWidth());
                    for (var cluster : group.clusters()) {
                        // The two are walked apart rather than through the cluster's own loop
                        // list, which would allocate one per cluster per frame; the stroke
                        // itself does not distinguish them.
                        GlRuns.drawScaled(
                            GL11.GL_LINE_LOOP,
                            cluster.outerLoop(),
                            frame.getFactor());
                        for (var enclaveLoop : cluster.enclaveLoops()) {
                            GlRuns.drawScaled(GL11.GL_LINE_LOOP, enclaveLoop, frame.getFactor());
                        }
                    }
            });
        }
    }
}
