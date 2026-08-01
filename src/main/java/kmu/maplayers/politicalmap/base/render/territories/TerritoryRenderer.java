package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.opengl.GlColour;
import kmlib.opengl.GlRuns;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.diagnostics.KmuProfiling;
import kmu.maplayers.base.render.clusters.StyledCell;
import kmu.maplayers.base.render.clusters.StyledCell.FusedCell;
import kmu.maplayers.base.render.clusters.StyledCell.LoneCell;

import org.lwjgl.opengl.GL11;

import java.util.Collection;
import java.util.function.Consumer;

/**
 * Paints the political map's pre-built draw lists on the sector (M) map: every fill first -
 * the faction clusters' and then the factionless cells' - and over them the interior seams,
 * factionless outlines, and national borders. The debug cluster anchors are not drawn here:
 * they are an independent
 * overlay ({@link kmu.maplayers.base.labels.anchor.ClusterAnchorRenderer}) the terrain
 * plugin layers over whichever
 * base view is live.
 *
 * <p>This is pure GL emission over an already-baked {@link PoliticalMapTerritories} - it
 * scales each world coordinate into map space and strokes/fills the flattened vertex
 * runs, with no knowledge of settings, caches, or how the runs were shaped. The map
 * widget has already applied the map's pan and centering to the GL matrix, so only the
 * scale is applied here.
 */
public final class TerritoryRenderer {
    // Emits only; never instantiated.
    private TerritoryRenderer() {
    }

    // Draws the whole overlay for one map frame. Drawn in the below-UI map pass so
    // system and constellation names stay on top; a state push/pop isolates the blend
    // and line settings from the rest of the map render. An empty overlay skips the
    // push entirely.
    public static void renderOnMap(
            PoliticalMapTerritories territories,
            float factor,
            float alphaMult) {

        // A fully faded-out overlay (alphaMult 0, at the ends of the map's fade) would
        // emit every run at zero effective alpha - all cost, nothing on screen - so the
        // whole GL pass is skipped, not just left to blend away.
        if (territories.isEmpty() || alphaMult <= 0f) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT
                | GL11.GL_CURRENT_BIT
                | GL11.GL_COLOR_BUFFER_BIT
                | GL11.GL_LINE_BIT
                | GL11.GL_HINT_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Time only the per-frame GL emission; the surrounding state push/pop is
        // negligible. Broken into the two passes so the profiler shows which one costs,
        // but not logged - this runs every frame the map is open, so only the profiler's
        // accumulated view is affordable here, never a per-frame log line.
        var profiler = KmuProfiling.getProfiler();
        profiler.measure("politicalMap.render", () -> {
            profiler.measure(
                    "politicalMap.render.fills",
                    () -> drawFills(territories, factor, alphaMult));
            profiler.measure(
                    "politicalMap.render.borders",
                    () -> drawBorders(territories, factor, alphaMult));
        });

        GL11.glPopAttrib();
    }

    // Fills each owned faction's cluster(s) with the faction's resolved fill colour at
    // its opacity, then each factionless cell that carries a fill of its own. Both are
    // pre-tessellated triangle soups, so a concave cluster (or one with an enclave) fills
    // correctly and exactly matches the stroked border. A hidden fill (UiElementPaint.isHidden) is
    // skipped, its geometry kept to shape its neighbours but never emitted. The spotlighted
    // bloc splits its one footprint into both runs at once - solid triangles where it
    // dominates and pre-clipped diagonal hatch lines where it is contested, in the same colour
    // and opacity - so its contested pocket reads as "mine but contested" within one frontier;
    // every other territory carries an empty hatch run and paints only its triangles.
    private static void drawFills(PoliticalMapTerritories territories, float factor, float alphaMult) {

        // The hatch fills the contested pocket in the fill colour but strokes as GL_LINES, so its
        // own pixel width tunes the contested texture apart from the solid fill. The width is
        // sector-wide, so set it once here off the theme's global tier rather than per territory;
        // the triangle soups below are width-agnostic, and only the one spotlit territory carries a
        // non-empty hatch run.
        GL11.glLineWidth((float) territories.getGlobalStyle().hatch().width());
        for (var territory : territories.getFactionTerritoryByFactionId().values()) {
            emitIfVisible(
                    territory.fill(),
                    alphaMult,
                    () -> {
                            GlRuns.drawScaled(
                                    GL11.GL_TRIANGLES,
                                    territory.fillTriangles(),
                                    factor);
                            GlRuns.drawScaled(
                                    GL11.GL_LINES,
                                    territory.hatchSegments(),
                                    factor);
            });
        }
        // Then the lone cells' own fills - dead colonies washed in the neutral colour. They fill
        // per cell rather than per cluster because factionless ground never fuses into one, and
        // they cover no faction's cluster, so drawing them after the cluster fills is a matter of
        // grouping the fill pass rather than of layering. A fused cell is not reached at all: its
        // fill is its cluster's, drawn above, so this pass sees only the cells that have one.
        drawEachCellOfForm(
                territories.getStyledCellByCellId().values(),
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

    // Strokes the interior province seams first, then the lone cells' outlines, then the
    // smoothed national borders over them, so a cluster's frontier dominates its internal
    // province lines where they meet. Color, opacity, and line width are all per element, and a
    // hidden element (UiElementPaint.isHidden) is skipped - its geometry stays baked to shape its
    // neighbours, but nothing invisible is emitted. Which cells each stroke reaches is the cell's
    // own form rather than a test here: a fused cell carries only seams and a lone cell only an
    // outline, and a cluster's national border is its border ring in factionTerritories.
    private static void drawBorders(
            PoliticalMapTerritories territories,
            float factor,
            float alphaMult) {

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        drawEachCellOfForm(
                territories.getStyledCellByCellId().values(),
                FusedCell.class,
                fused -> emitIfVisible(
                        fused.seamPaint(),
                        alphaMult,
                        () -> {
                                GL11.glLineWidth(fused.seamWidth());
                                GlRuns.drawScaled(GL11.GL_LINES, fused.seamEdges(), factor);
                        }));
        drawEachCellOfForm(
                territories.getStyledCellByCellId().values(),
                LoneCell.class,
                lone -> emitIfVisible(
                        lone.outlinePaint(),
                        alphaMult,
                        () -> {
                                GL11.glLineWidth(lone.outlineWidth());
                                GlRuns.drawScaled(GL11.GL_LINES, lone.outlineEdges(), factor);
                        }));

        // The national border in its own style, over the interior seams so the frontier dominates
        // where they meet. Each border ring is a closed rounded loop, so it strokes as one
        // continuous GL_LINE_LOOP rather than the disconnected GL_LINES the per-cell edges use.
        for (var territory : territories.getFactionTerritoryByFactionId().values()) {
            emitIfVisible(
                    territory.border(),
                    alphaMult,
                    () -> {
                            GL11.glLineWidth(territory.borderWidth());
                            for (var loop : territory.borderLoops()) {
                                GlRuns.drawScaled(GL11.GL_LINE_LOOP, loop, factor);
                            }
            });
        }
    }

}
