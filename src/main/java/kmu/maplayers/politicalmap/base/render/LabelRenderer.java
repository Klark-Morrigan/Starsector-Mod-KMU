package kmu.maplayers.politicalmap.base.render;

import kmlib.color.Colors;

import kmu.diagnostics.KmuProfiling;

import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * Paints the cached cluster-name labels on the sector (M) map: each cluster's name as a
 * block of one or more lines centred on its anchor, slanted to its axis, in the owner's
 * bright colour, at the font size its fitted box carries. Each cached label is one line
 * with its own hang point - the builder laid the stack out - so this pass just draws them
 * all alike.
 *
 * <p>Drawn in the same below-UI {@code renderOnMap} pass as the fills and the debug anchor
 * overlay, and last of the three, so a name reads on top of its territory and over the
 * debug band that (when the anchor toggle is on) shows the box it will occupy - yet still
 * beneath the vanilla star and constellation names, which the map draws after every
 * terrain {@code renderOnMap}. ({@code renderOnMapAbove} would put the names over the star
 * labels instead.)
 *
 * <p>The names live in world space: the pass scales the GL matrix by the map factor once,
 * then hands each label its raw world coordinates, so a name grows and shrinks with the
 * territory it labels rather than staying a fixed pixel size. LazyLib's
 * {@code DrawableString.drawAtAngle} saves and restores its own GL state per draw, so this
 * renderer adds only the shared world-scale matrix around the batch.
 */
public final class LabelRenderer {
    // Emits only; never instantiated.
    private LabelRenderer() {
    }

    // Draws every cached label for one map frame, scaled into world space. Empty (nothing
    // emitted) unless the names toggle built labels, so the normal map pays only an
    // empty-list check; a fully faded-out map (alphaMult 0) emits nothing rather than
    // drawing invisible text. Profiled like the other map passes since it runs every frame
    // the map is open.
    public static void renderOnMap(List<Label> labels, float factor, float alphaMult) {
        if (labels.isEmpty() || alphaMult <= 0f) {
            return;
        }
        KmuProfiling.getProfiler().measure("politicalMap.render.labels",
                () -> drawLabels(labels, factor, alphaMult));
    }

    // Scales the modelview by the map factor once, then draws each label at its raw world
    // coordinates so the glyphs scale with the territory. Each label's colour is refaded to
    // the map's alpha first (no buffer rebuild, since there is no per-substring colour), so
    // the names fade with the fills at the edges of the map's zoom range.
    private static void drawLabels(List<Label> labels, float factor, float alphaMult) {
        GL11.glPushMatrix();
        GL11.glScalef(factor, factor, 1f);
        for (var label : labels) {
            // Refade the label to the frame's alpha so it dims in step with the territory
            // it sits on; scaleAlpha keeps a copy, so the cached base colour is untouched.
            label.text().setBaseColor(Colors.scaleAlpha(label.baseColor(), alphaMult));
            label.text().drawAtAngle(label.hangX(), label.hangY(), label.slantDegrees());
        }
        GL11.glPopMatrix();
    }
}
