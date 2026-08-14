package kmu.maplayers.politicalmap.base.render.ribbon;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;

import kmu.diagnostics.KmuProfiling;

import org.lwjgl.opengl.GL11;

import java.util.Collection;

/**
 * Paints the cells' baked presence bands on the sector (M) map: each run of each band as its own
 * patch of coloured triangles, laid inside the cell's own ring.
 *
 * <p>Pure GL emission over already-baked {@link CellRibbon}s - it binds a colour and scales world
 * coordinates into map space, and knows nothing of who is present in a system, how a run's length
 * was settled, or which cells were asked for a band at all. That is what makes a band cost a draw
 * call rather than a walk: everything a band says was decided at rebuild.
 *
 * <p>Drawn from the band that clears the map's own nebula sprites, and before the cluster names in
 * it. A band is a readout of a system rather than decoration over it, so it must not be fogged by
 * the sprites the map lays over the sector - while a name, which is the coarser statement of the
 * two, still wins where the two would overlap.
 *
 * <p>Triangles rather than wide lines, and scaled by the map factor like the fills: the band is
 * world-sized throughout, so it holds the same share of its cell's outline at every zoom and the
 * matrix work here is the single uniform scale the map widget leaves to the caller.
 */
public final class CellPresenceRibbonRenderer {

    // Emits only; never instantiated.
    private CellPresenceRibbonRenderer() {
    }

    /**
     * Draws every cell's band for one map frame.
     *
     * <p>Most of the sector carries no band - one is drawn only where a bloc the cell was not
     * painted for is present - so the usual frame pays an empty-map check and nothing else. A
     * fully faded-out map emits nothing rather than every run at zero effective alpha, which
     * would cost the whole pass for pixels that cannot appear.
     *
     * <p>A map zoomed far enough out is the third skip, and the one the sizes answer rather than
     * the bands: below the player's floor a world-sized band is thinner than a pixel, where it
     * stops reading as a band and starts reading as a discolouring of the border it runs inside.
     * Dropping it there is what makes it fade out as a feature rather than as an artefact.
     *
     * @param ribbons   the frame's baked bands, one per cell that draws one; the cells that draw
     *                  none are not among them. Taken as the bands alone rather than as the map
     *                  they are held in, since which cell a band belongs to is settled where it
     *                  was baked and says nothing about how it is painted
     * @param style     the sizes the bands were baked at, read for the one question a baked band
     *                  cannot answer for itself: whether it is thick enough at this zoom to read
     * @param factor    the map's world-to-screen scale, applied to every coordinate
     * @param alphaMult the map's own fade, applied over each run's colour
     */
    public static void renderOnMap(
            Collection<CellRibbon> ribbons,
            RibbonStyle style,
            float factor,
            float alphaMult) {

        if (ribbons.isEmpty() || alphaMult <= 0f || !style.isVisibleAtScale(factor)) {
            return;
        }
        // Profiled like the other map passes, since this runs every frame the map is open; only
        // the profiler's accumulated view is affordable here, never a per-frame log line.
        KmuProfiling.getProfiler().measure(
            "mapLayer.render.ribbons",
            () -> GlPasses.runBlendedPass(
                GlBlendMode.ALPHA,
                GlLineQuality.ALIASED,
                () -> drawBands(ribbons, factor, alphaMult)));
    }

    // Every run of every band, in the order they were laid around their rings.
    //
    // The colour binds per run rather than being grouped across cells: a run's colour is the whole
    // of what it says, the runs of one cell are few, and only the cells carrying a band are walked
    // at all. Grouping them would trade that for a per-frame sort over a set that changes only at
    // rebuild.
    private static void drawBands(
            Collection<CellRibbon> ribbons,
            float factor,
            float alphaMult) {

        for (var ribbon : ribbons) {
            for (var band : ribbon.bands()) {
                GlColour.set(band.colour(), alphaMult);
                GlRuns.drawScaled(GL11.GL_TRIANGLES, band.triangles(), factor);
            }
        }
    }
}
