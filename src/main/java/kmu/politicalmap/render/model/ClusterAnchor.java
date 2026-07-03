package kmu.politicalmap.render.model;

import java.awt.Color;

/**
 * A debug marker for one system cluster's label anchor: where a faction name will sit
 * and which way it will run, drawn as a dot at the cluster's centre and the straight
 * line the name can actually occupy.
 *
 * <p>{@code (centroidX, centroidY)} is the anchor point in world (hyperspace)
 * coordinates - the mean of the cluster's system positions. The axis segment
 * {@code (axisStartX, axisStartY)}..{@code (axisEndX, axisEndY)} is the cluster's long
 * axis refit for a name: leaned horizontal, kept to the genuinely interior pieces of
 * the national border (never bridging a concavity or an enclave), trimmed clear of
 * every system icon, and pulled short of the border at both ends - so its length shows
 * how much room a label truly has and its slope the angle the label will follow. The
 * segment collapses to the anchor point - only the dot shows - when the cluster has no
 * spread (a single system), no traceable border, or no clear interval with room left
 * between the end margins. {@code color} is the owning faction's bright palette shade,
 * so the marker reads against that faction's fill.
 *
 * <p>Scaffolding for the label work: it makes the otherwise invisible clustering and
 * axis fit verifiable on the map before any text is drawn. Only built when the "show
 * cluster anchors" dev toggle is on.
 */
public record ClusterAnchor(float centroidX, float centroidY, float axisStartX, float axisStartY,
        float axisEndX, float axisEndY, Color color) {
}
