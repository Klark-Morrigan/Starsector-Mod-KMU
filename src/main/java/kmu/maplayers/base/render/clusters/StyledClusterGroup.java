package kmu.maplayers.base.render.clusters;

import kmlib.starsector.ui.render.gl.UiElementPaint;

import java.util.List;

/**
 * Everything one owner paints: the clusters its cells fused into, and the paint all of them
 * share.
 *
 * <p>Owners are not connected. A holding split across the sector fuses into one cluster per
 * body, and every one of them fills and strokes in the owner's own colours - so the geometry
 * is per cluster and the paint is per owner, and this is the type that says so. Spelled as one
 * record with the paint repeated per cluster, two bodies of one owner could carry different
 * colours, which is a state no build can produce and every reader would have to
 * distrust anyway.
 *
 * <p>It is also what the emission wants: one colour bind and one line width per owner,
 * however many bodies that owner holds, rather than a rebind per body.
 *
 * <p>Each {@link UiElementPaint} carries its element's colour and opacity and reports whether
 * it is hidden, so a pass skips what shows nothing while the geometry stays baked to shape the
 * owners around it.
 *
 * @param clusters    the connected bodies this owner holds, each with its own geometry
 * @param fill        the colour and opacity every cluster's fill and hatch paints at
 * @param border      the colour and opacity every cluster's loops stroke at
 * @param borderWidth the line width those loops stroke at, in pixels
 */
public record StyledClusterGroup(
    List<StyledCluster> clusters,
    UiElementPaint fill,
    UiElementPaint border,
    float borderWidth) {
}
