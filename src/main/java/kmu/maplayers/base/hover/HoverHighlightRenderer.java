package kmu.maplayers.base.hover;

import kmlib.opengl.GlBlendMode;
import kmlib.opengl.GlColour;
import kmlib.opengl.GlLineQuality;
import kmlib.opengl.GlPasses;
import kmlib.opengl.GlRuns;
import kmlib.profiling.Timings;

import kmu.maplayers.base.theme.HoverGlowStyle;
import kmu.maplayers.base.theme.HoverHighlightStyle;
import kmu.maplayers.base.theme.HoverWashStyle;

import org.lwjgl.opengl.GL11;

import java.awt.Color;

/**
 * Draws the map's answer to the cursor: a halo blooming off the hovered cluster's frontier
 * and a wash lifting the one cell the cursor is in.
 *
 * <p>Both burn additively rather than blending over the map. A halo is layers of the same
 * loop stacked on each other - only additive blending lets those layers accumulate into a
 * soft outward falloff instead of the topmost one simply replacing the rest - and the wash
 * brightens what is already painted, so adding light to the fill under it reads as the cell
 * lighting up rather than as a second, flatter fill laid over it.
 *
 * <p>Pure GL emission over the geometry {@link HoverHighlightGeometry} resolved and the style
 * the theme baked; nothing here decides what is hovered or reads a setting.
 */
public final class HoverHighlightRenderer {
    private final HoverHighlightGeometry geometry = new HoverHighlightGeometry();

    /**
     * Paints the hover highlight for one map frame, or nothing when the cursor is over no
     * cell.
     *
     * @param source    the active layer's answers about the frame it painted - the hovered
     *                  extent, the loops around it, and the shade its fill draws in
     * @param style     the theme's highlight tier, which owns the shape of the halo and the
     *                  weight of the wash
     * @param hover     what the cursor is over this frame
     * @param factor    the per-vertex scale the map applies to world coordinates
     * @param alphaMult the map's own fade, applied on top of every element's opacity
     */
    public void renderOnMap(
            HoverHighlightSource source,
            HoverHighlightStyle style,
            MapHover hover,
            float factor,
            float alphaMult) {

        var colour = resolveHighlightColour(source, hover, style);
        // Nothing to paint when the cursor is over no cell (which is also how a disabled highlight
        // reads, its hover parked upstream) or the map has fully faded at the ends of its zoom
        // fade - both would emit every run for nothing, so both skip the GL state push rather than
        // being left to blend away.
        if (colour == null || alphaMult <= 0f) {
            return;
        }
        var highlight = geometry.resolveHighlightFor(source, hover);
        if (highlight.isEmpty()) {
            return;
        }
        // Additive and smoothed: the halo is layers of one loop stacked on each other, which only
        // additive blending accumulates into a bloom, and only smoothing keeps the outer layers
        // from reading as concentric hard rings. The pass restores the map's own blend function
        // on the way out - every pass after this one (the anchors, the cluster names) expects to
        // draw over the map, not into it.
        GlPasses.runBlendedPass(
            GlBlendMode.ADDITIVE,
            GlLineQuality.SMOOTHED,
            () -> {
                drawGlow(highlight, style.glow(), colour, factor, alphaMult);
                drawWash(highlight, style.wash(), colour, factor, alphaMult);
            });
    }

    // The colour the whole highlight paints in: the shade of the cell under the cursor, which
    // only the layer that owns that cell can name. Null when the cursor is over nothing (a
    // parked hover, including when the highlight is disabled) or the selection paints nothing,
    // so the caller skips the pass.
    private static Color resolveHighlightColour(
            HoverHighlightSource source,
            MapHover hover,
            HoverHighlightStyle style) {

        if (!hover.isHovering()) {
            return null;
        }
        return source.resolveHighlightColourOf(
            hover.hoveredSystemId(),
            style.colour());
    }

    // Strokes the hovered frontier once per layer, so the additive layers pile into a halo;
    // each layer's width and alpha come off the style, which owns the shape of the stack.
    private static void drawGlow(
            HoverHighlight highlight,
            HoverGlowStyle style,
            Color colour,
            float factor,
            float alphaMult) {

        if (style.opacity() <= 0 || style.layers() < 1) {
            return;
        }
        // The pulse rides a wall clock rather than the campaign's own: the sector map is open
        // on a paused game, where advance() does not tick, and a halo frozen mid-breath while
        // the player studies the map would read as the overlay having hung. Read once for the
        // whole stack, so every layer of one frame is phased alike.
        var timeSeconds = Timings.convertNanosToSeconds(System.nanoTime());

        for (var layer = 0; layer < style.layers(); layer++) {
            GL11.glLineWidth((float) style.computeLayerWidth(layer));
            GlColour.set(
                colour,
                (float) (alphaMult * style.computeLayerAlpha(layer, timeSeconds)));

            for (var loop : highlight.glowLoops()) {
                GlRuns.drawScaled(
                    GL11.GL_LINE_LOOP,
                    loop,
                    factor);
            }
        }
    }

    // Lifts the hovered cell: its whole painted extent brightened, then its boundary traced all
    // the way round. The trace is what names an interior cell - one walled in by its own cluster
    // draws no border of its own, so without it a wash inside a same-coloured cluster would
    // have no edge to read.
    private static void drawWash(
            HoverHighlight highlight,
            HoverWashStyle style,
            Color colour,
            float factor,
            float alphaMult) {

        if (style.fillOpacity() > 0) {
            GlColour.set(colour, (float) (alphaMult * style.fillOpacity()));
            GlRuns.drawScaled(
                GL11.GL_TRIANGLES,
                highlight.washTriangles(),
                factor);
        }
        if (style.outlineOpacity() > 0) {
            GL11.glLineWidth((float) style.outlineWidth());
            GlColour.set(
                colour,
                (float) (alphaMult * style.outlineOpacity()));

            for (var loop : highlight.washOutline()) {
                GlRuns.drawScaled(
                    GL11.GL_LINE_LOOP,
                    loop,
                    factor);
            }
        }
    }
}
