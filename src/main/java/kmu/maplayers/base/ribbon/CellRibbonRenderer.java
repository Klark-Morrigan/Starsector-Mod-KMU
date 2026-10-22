package kmu.maplayers.base.ribbon;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;
import kmlib.profiling.ActiveProfiler;
import kmlib.profiling.ProfileSection;

import kmu.maplayers.base.render.MapFrame;

import org.lwjgl.opengl.GL11;

import java.util.Collection;

/**
 * Paints the cells' baked bands on the sector (M) map: each run of each band as its own
 * patch of coloured triangles, laid inside the cell's own ring.
 *
 * <p>Pure GL emission over already-baked {@link CellRibbon}s - it binds a colour and scales world
 * coordinates into map space, and knows nothing of why a run is the colour it is, how its length
 * was settled, or which cells were asked for a band at all. That is what makes a band cost a draw
 * call rather than a walk: everything a band says was decided at rebuild.
 *
 * <p>Which render band it is called from, and what the layer draws after it, are the layer's: this
 * draws whenever it is called and orders itself against nothing.
 *
 * <p>Triangles rather than wide lines, and scaled by the map factor like the fills: the band is
 * world-sized throughout, so it holds the same share of its cell's outline at every zoom and the
 * matrix work here is the single uniform scale the map widget leaves to the caller.
 */
public final class CellRibbonRenderer {

    // Held rather than named per frame: a section found by reference costs the pass nothing where
    // one found by name is a lookup a frame.
    private static final ProfileSection RENDER_SECTION =
        ProfileSection.registerSection("mapLayer.render.ribbons");

    // Emits only; never instantiated.
    private CellRibbonRenderer() {
    }

    /**
     * Draws every cell's band for one map frame.
     *
     * <p>Most of the sector carries no band - one is drawn only where the layer planned runs for the
     * cell - so the walk covers the banded cells and nothing else. A fully faded-out map emits
     * nothing rather than every run at zero effective alpha, which would cost the whole pass for
     * pixels that cannot appear.
     *
     * @param ribbons   the frame's baked bands, one per cell that draws one; the cells that draw
     *                  none are not among them. Taken as the bands alone rather than as the map
     *                  they are held in, since which cell a band belongs to is settled where it
     *                  was baked and says nothing about how it is painted
     * @param mapFrame  the scale every coordinate is multiplied by and the fade over each run
     */
    public static void renderOnMap(
            Collection<CellRibbon> ribbons,
            MapFrame mapFrame) {

        if (ribbons.isEmpty() || mapFrame.isFadedOut()) {
            return;
        }
        // Profiled like the other map passes, since this runs every frame the map is open; only
        // the profiler's accumulated view is affordable here, never a per-frame log line. Opened
        // inside the GL state push, where every map pass opens its scope, so the rows compare the
        // draws alone rather than one of them carrying the push.
        GlPasses.runBlendedPass(
            GlBlendMode.ALPHA,
            GlLineQuality.ALIASED,
            () -> {
                try (var renderScope = ActiveProfiler.resolveProfiler().open(RENDER_SECTION)) {
                    drawBands(ribbons, mapFrame);
                }
            });
    }

    // Every run of every band, in the order they were laid around their rings.
    //
    // The colour binds per run rather than being grouped across cells: a run's colour is the whole
    // of what it says, the runs of one cell are few, and only the cells carrying a band are walked
    // at all. Grouping them would trade that for a per-frame sort over a set that changes only at
    // rebuild.
    private static void drawBands(
            Collection<CellRibbon> ribbons,
            MapFrame mapFrame) {

        for (var ribbon : ribbons) {
            for (var band : ribbon.bands()) {
                GlColour.set(band.colour(), mapFrame.alphaMult());
                GlRuns.drawScaled(GL11.GL_TRIANGLES, band.triangles(), mapFrame.factor());
            }
        }
    }
}
