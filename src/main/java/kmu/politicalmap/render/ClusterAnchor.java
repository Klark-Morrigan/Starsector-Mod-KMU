package kmu.politicalmap.render;

import java.awt.Color;

/**
 * A debug marker for one system cluster's label anchor: where a faction name will sit
 * and which way it will run, drawn as a dot at the cluster's centre and a line down its
 * long axis.
 *
 * <p>{@code (centroidX, centroidY)} is the anchor point in world (hyperspace)
 * coordinates - the mean of the cluster's system positions. The axis segment
 * {@code (axisStartX, axisStartY)}..{@code (axisEndX, axisEndY)} spans the cluster's
 * long dimension, centred on the anchor, so its length shows how much room a label has
 * and its slope shows the angle the label will follow. A single-system cluster has no
 * spread, so its segment collapses to the anchor point and only the dot shows.
 * {@code color} is the owning faction's bright palette shade, so the marker reads
 * against that faction's fill.
 *
 * <p>Scaffolding for the label work: it makes the otherwise invisible clustering and
 * axis fit verifiable on the map before any text is drawn. Only built when the "show
 * cluster anchors" dev toggle is on.
 */
record ClusterAnchor(float centroidX, float centroidY, float axisStartX, float axisStartY,
        float axisEndX, float axisEndY, Color color) {
}
