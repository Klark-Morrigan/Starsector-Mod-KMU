package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.opengl.GlColor;
import kmlib.opengl.GlRuns;
import kmlib.starsector.ui.render.gl.UiElementPaint;

import kmu.diagnostics.KmuProfiling;

import org.lwjgl.opengl.GL11;

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

    // Fills each owned faction's cluster(s) with the faction's resolved fill color at
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
            emitIfVisible(territory.fill(), alphaMult, () -> {
                GlRuns.drawScaled(GL11.GL_TRIANGLES, territory.fillTriangles(), factor);
                GlRuns.drawScaled(GL11.GL_LINES, territory.hatchSegments(), factor);
            });
        }
        // Then the factionless cells' own fills - dead colonies washed in the neutral colour.
        // They fill per cell rather than per cluster because factionless ground never fuses
        // into one, and they cover no faction's region, so drawing them after the cluster
        // fills is a matter of grouping the fill pass rather than of layering.
        for (var cell : territories.getStyledCellByCellId().values()) {
            emitIfVisible(cell.fillPaint(), alphaMult,
                    () -> GlRuns.drawScaled(GL11.GL_TRIANGLES, cell.fillTriangles(), factor));
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
        GlColor.set(paint.color(), alphaMult * paint.alpha());
        emitRuns.run();
    }

    // Strokes the interior province seams first, then the factionless outlines, then the
    // smoothed national borders over them, so a cluster's frontier dominates its
    // internal province lines where they meet. Color, opacity, and line width are all
    // per element, and a hidden element (UiElementPaint.isHidden) is skipped - its geometry
    // stays baked to shape its neighbours, but nothing invisible is emitted. An owned
    // cluster's national border is its border ring (in factionTerritories), so the
    // per-cell outline only carries factionless cells; of the runs this pass strokes an
    // owned cell contributes only its seams and a factionless cell only its outline.
    private static void drawBorders(
            PoliticalMapTerritories territories,
            float factor,
            float alphaMult) {

        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glHint(GL11.GL_LINE_SMOOTH_HINT, GL11.GL_NICEST);

        for (var cell : territories.getStyledCellByCellId().values()) {
            emitIfVisible(cell.inner(), alphaMult, () -> {
                GL11.glLineWidth(cell.innerWidth());
                GlRuns.drawScaled(GL11.GL_LINES, cell.interiorEdges(), factor);
            });
        }
        for (var cell : territories.getStyledCellByCellId().values()) {
            emitIfVisible(cell.outer(), alphaMult, () -> {
                GL11.glLineWidth(cell.outerWidth());
                GlRuns.drawScaled(GL11.GL_LINES, cell.boundaryEdges(), factor);
            });
        }
        // The national border in its own style, over the interior seams so the frontier dominates
        // where they meet. Each border ring is a closed rounded loop, so it strokes as one
        // continuous GL_LINE_LOOP rather than the disconnected GL_LINES the per-cell edges use.
        for (var territory : territories.getFactionTerritoryByFactionId().values()) {
            emitIfVisible(territory.border(), alphaMult, () -> {
                GL11.glLineWidth(territory.borderWidth());
                for (var loop : territory.borderLoops()) {
                    GlRuns.drawScaled(GL11.GL_LINE_LOOP, loop, factor);
                }
            });
        }
    }

}
