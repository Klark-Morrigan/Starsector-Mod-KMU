package kmu.politicalmap.render.model;

import java.awt.Color;

/**
 * A debug marker for one system cluster's label anchor: where a faction name will sit
 * and which way it will run, drawn as a dot at the anchor point plus up to three lines -
 * the accepted label line and two optional diagnostics that show how the search arrived
 * at it.
 *
 * <p>{@code (anchorX, anchorY)} is the point a name will hang on, in world (hyperspace)
 * coordinates. When a label line was accepted it is that line's own midpoint - the
 * accepted line has no tie to the cluster's centre, since the search is free to place it
 * wherever the cluster is roomiest, so the anchor point follows the line rather than the
 * sites. Only when nothing was accepted does it fall back to the site centroid, the one
 * point that always exists. {@code acceptedAxis} is the label line the search accepted:
 * the highest-scoring of many candidate lines swept across the cluster - each kept to the
 * genuinely interior pieces of the national border (never bridging a concavity or an
 * enclave), trimmed clear of every system icon, and pulled short of the border at both
 * ends - with shallower (more horizontal) lines favoured over steep ones by a
 * length-versus-slope score. Its length shows how much room a label truly has and its
 * slope the angle it will follow. {@code acceptedAxis} is null - only the dot shows -
 * solely when no candidate survives anywhere: no traceable border, or no clear interval
 * left after the icon and end-margin trims for any direction or offset the search tried.
 *
 * <p>{@code rejectedAxis}, non-null only when the accepted line collapsed and the "show
 * rejected cluster axes" toggle is on, is the best-scoring candidate the search found
 * before that collapse - the furthest-along interior or icon-clear span it had, before
 * the border, icon, or end-margin trim discarded it. {@code unbiasedAxis}, non-null only
 * when the "show unbiased cluster axes" toggle is on and it differs from the accepted
 * line, is the pure longest accepted line - the candidate that wins with no vertical
 * penalty applied - so the effect of the penalty knobs is visible directly on the map.
 * When the penalty does not move the pick (its strength at zero, or the longest line
 * already the shallowest) the accepted line already stands in for it, so no separate
 * line is built.
 *
 * <p>{@code color} is the owning faction's bright palette shade and marks the anchor
 * dot; the three lines instead use the renderer's fixed diagnostic palette (accepted,
 * rejected, unbiased) so their verdict reads the same regardless of faction.
 *
 * <p>Scaffolding for the label work: it makes the otherwise invisible clustering and
 * placement search verifiable on the map before any text is drawn. Only built when the
 * "show cluster anchors" dev toggle is on.
 */
public record ClusterAnchor(float anchorX, float anchorY, Color color,
        AxisSegment acceptedAxis, AxisSegment rejectedAxis, AxisSegment unbiasedAxis) {

    /**
     * One of a {@link ClusterAnchor}'s lines, in world (hyperspace) coordinates - the
     * accepted label line or one of the two diagnostic candidates. A value of its own
     * so each line is present or null as a whole, rather than a collapsed line being
     * encoded as a zero-length coordinate sentinel a consumer must know to test for.
     */
    public record AxisSegment(float startX, float startY, float endX, float endY) {
    }
}
