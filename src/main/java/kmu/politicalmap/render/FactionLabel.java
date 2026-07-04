package kmu.politicalmap.render;

import org.lazywizard.lazylib.ui.LazyFont.DrawableString;

import java.awt.Color;

/**
 * One drawn faction name: a cached {@link DrawableString} plus where and how it hangs on
 * the map. Built from a {@link kmu.politicalmap.render.model.ClusterAnchor}'s accepted
 * placement - {@code (hangX, hangY)} is the anchor's accepted-line midpoint and
 * {@code slantDegrees} the line's slope - so the name sits inside the border, clear of
 * icons, leaning along the cluster's axis, in the owner's bright colour.
 *
 * <p>{@code baseColor} is that owner's bright shade at full opacity, kept alongside the
 * string because the renderer refades it to the map's alpha each frame: the string bakes
 * only one colour, so the un-faded original has to live here to be re-derived. Setting the
 * string's colour costs no buffer rebuild (there is no per-substring colour data), so the
 * per-frame refade stays cheap.
 *
 * <p>Lives in the render package rather than {@code render.model} because it is not pure
 * data: the {@link DrawableString} owns a GL vertex buffer that must be
 * {@link DrawableString#dispose() disposed} when the label is rebuilt. Caching it (rather
 * than rebuilding a string per frame) is what keeps the name draw a buffered blit instead
 * of per-frame VBO churn; the builder disposes the previous strings whenever it rebuilds
 * the list.
 */
record FactionLabel(DrawableString text, Color baseColor, float hangX, float hangY,
        float slantDegrees) {
}
