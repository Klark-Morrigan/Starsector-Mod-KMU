package kmu.maplayers.base.labels;

import kmu.maplayers.base.labels.anchor.ClusterAnchor;

import org.lazywizard.lazylib.ui.LazyFont.DrawableString;

import java.awt.Color;

/**
 * One drawn line of a cluster name: a cached {@link DrawableString} plus where and how it
 * hangs on the map. Built from a
 * {@link ClusterAnchor}'s accepted placement - a
 * multi-line name mints one of these per wrapped line, each hung at its own point (stacked
 * perpendicular to the accepted line, centred as a block on the anchor) with the shared
 * slant - so the block sits inside the border, clear of icons, leaning along the cluster's
 * axis, in the owner's bright colour, at the font size the fit achieved. Per-line strings
 * rather than one embedded-newline string because each line centres on the block's axis
 * individually and the stack honours the line-spacing knob, which a single
 * {@link DrawableString} (fixed one-font-size line advance, left-aligned block) cannot do.
 *
 * <p>{@code baseColour} is that owner's bright shade at full opacity, kept alongside the
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
public record Label(
    DrawableString text,
    Color baseColour,
    float hangX,
    float hangY,
    float slantDegrees) {
}
