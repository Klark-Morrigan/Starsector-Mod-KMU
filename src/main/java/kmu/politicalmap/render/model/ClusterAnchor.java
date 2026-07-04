package kmu.politicalmap.render.model;

import java.awt.Color;

/**
 * A debug marker for one system cluster's label anchor: where a faction name will sit
 * and which way it will run, drawn as a dot at the cluster's centre, the accepted line
 * a name will occupy, and - when their toggles ask for them - the two extra diagnostic
 * lines that show how the fit arrived there.
 *
 * <p>{@code (centroidX, centroidY)} is the anchor point in world (hyperspace)
 * coordinates - the site position of a single-system cluster, or the mean of a
 * multi-system cluster's site positions. The axis segment
 * {@code (axisStartX, axisStartY)}..{@code (axisEndX, axisEndY)} is the accepted label
 * line: the cluster's long axis - leaned horizontal, kept to the genuinely interior
 * pieces of the national border (never bridging a concavity or an enclave), trimmed
 * clear of every system icon, and pulled short of the border at both ends - so its
 * length shows how much room a label truly has and its slope the angle it will follow.
 * A cluster's direction never goes undefined merely for having one system: a
 * single-system cluster still has its own cell's shape to fall back on, so it is fitted
 * a real line the same way as any other cluster. The segment collapses to the anchor
 * point - only the dot shows - solely when nothing survives that whole attempt: no
 * traceable border, or no clear interval left after the icon and end-margin trims.
 *
 * <p>{@code rejectedAxis}, non-null only when the accepted line collapsed and the "show
 * rejected cluster axes" toggle is on, is the best candidate the fit found before that
 * collapse - the furthest-along interior or icon-clear span it had, before the border,
 * icon, or end-margin trim discarded it. {@code unbiasedAxis}, non-null only when the
 * "show unbiased cluster axes" toggle is on and the un-leaned direction actually differs
 * from the accepted one, is what the same fit would have produced with no horizontal
 * bias applied - so the effect of the bias setting is visible directly on the map. When
 * the bias is already a no-op (a setting of 1, or an axis already horizontal) the two
 * directions coincide and the accepted line already stands in for the unbiased one, so
 * no separate line is built.
 *
 * <p>{@code color} is the owning faction's bright palette shade and marks the centroid
 * dot; the three lines instead use a fixed diagnostic palette (accepted, rejected,
 * unbiased) so their role reads the same regardless of faction.
 *
 * <p>Scaffolding for the label work: it makes the otherwise invisible clustering and
 * axis fit verifiable on the map before any text is drawn. Only built when the "show
 * cluster anchors" dev toggle is on.
 */
public record ClusterAnchor(float centroidX, float centroidY, float axisStartX, float axisStartY,
        float axisEndX, float axisEndY, Color color, AxisSegment rejectedAxis,
        AxisSegment unbiasedAxis) {

    /**
     * One of a {@link ClusterAnchor}'s diagnostic lines, in world (hyperspace)
     * coordinates - the rejected or the unbiased candidate, kept out of the main
     * field list since both are optional and drawn in their own fixed color rather
     * than the anchor's faction shade.
     */
    public record AxisSegment(float startX, float startY, float endX, float endY) {
    }
}
