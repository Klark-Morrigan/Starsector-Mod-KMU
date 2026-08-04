package kmu.maplayers.base.labels.anchor;

import kmlib.math.geometry.Segment;

import java.awt.Color;
import java.util.List;

/**
 * One system cluster's resolved label placement: where the owner's name sits, which way
 * it runs, and the wrapped lines and font size that fill it. It is both the production
 * input the cluster-name renderer draws from - {@code nameLines}, {@code fontHeight},
 * {@code (anchorX, anchorY)}, {@code colour}, and {@code acceptedAxis}'s slant - and the
 * debug marker the "show cluster anchors" overlay draws as a dot plus up to three
 * lines. The placement search runs whenever either the names or the anchor overlay is
 * on, so the two consumers share one computation; each then draws only under its own
 * toggle.
 *
 * <p>{@code identity} names the cluster the fit was made for - the owner its name and shade
 * were resolved from, and the exact members whose cells the search ran inside. It rides on
 * the placement rather than in a list beside it because the anchors are what a rebuild
 * inherits, so a second list would have to be kept in step by hand. Nothing that draws
 * reads it; it is there so a placement can be recognised as still belonging to the cluster
 * it was made for.
 *
 * <p>{@code (anchorX, anchorY)} is the point the name block centres on, in world
 * (hyperspace) coordinates. When a label line was accepted it is that line's own
 * midpoint - the accepted line has no tie to the cluster's centre, since the search is
 * free to place it wherever the cluster is roomiest, so the anchor point follows the
 * line rather than the sites. Only when nothing was accepted does it fall back to the
 * site centroid, the one point that always exists. {@code acceptedAxis} is the label
 * line the search accepted: the highest-scoring of many candidate lines swept across
 * the cluster - each kept to the genuinely interior pieces of the cluster's border
 * (never bridging a concavity or an enclave), trimmed clear of every system icon, and
 * pulled short of the border at both ends - with shallower (more horizontal) lines
 * favoured over steep ones by a font-height-versus-slope score. Its length shows how
 * much room a label truly has and its slope the angle it will follow.
 * {@code acceptedAxis} is null - only the dot shows - solely when no candidate survives
 * anywhere: no traceable border, or no clear interval left after the icon and
 * end-margin trims for any direction or offset the search tried.
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
 * <p>{@code colour} is the shade the cluster's own border resolves to - the name
 * inherits the outer border's colour rather than a fixed bright pick - and it marks the
 * anchor dot too; a hidden border falls back to the owner's bright shade so the name is
 * still legible. The three debug lines instead use the renderer's fixed diagnostic
 * palette (accepted, rejected, unbiased) so their verdict reads the same whoever holds
 * the cluster.
 *
 * <p>{@code nameLines} is the owner's display name wrapped into the lines the fit sized
 * - the exact wrap the box's required length was measured against, so the drawn block
 * matches the fitted box by construction. Empty when there is nothing to draw: a
 * collapsed fit, or a cluster whose fit ran on the aspect stand-in because the font or
 * the cluster's name would not resolve. {@code fontHeight} is the per-line height the fit
 * achieved, in world units - the size the name renders at.
 *
 * <p>{@code thickness} is the girth of the fitted label band in world units - the
 * height of the box the name fills along the accepted line, not just its centreline -
 * and {@code lineCount} how many lines the fit stacked the name into to spend that
 * girth (one for a single line). Together they turn the accepted line into the oriented
 * box the name occupies, so the search can keep the whole band inside the border and
 * the debug overlay can draw the true footprint rather than a thin line that hides an
 * overflow. Thickness, line count, and font height are all zero on a collapsed fit
 * (dot only), where there is no band.
 */
public record ClusterAnchor(
    ClusterIdentity identity,
    float anchorX,
    float anchorY,
    Color colour,
    List<String> nameLines,
    float fontHeight,
    Segment acceptedAxis,
    Segment rejectedAxis,
    Segment unbiasedAxis,
    float thickness,
    int lineCount) {

    /**
     * This placement in a different shade, every fitted component left exactly as it was.
     *
     * <p>The shade is resolved afresh each pass and is no input to the fit - the search
     * only carries it - so a placement that still describes its cluster can take a new one
     * without its box being searched again. That is what a bloc which only changed
     * appearance costs: one component replaced, and the geometry it already has.
     *
     * @param restyledColour the shade the name and its debug dot should draw in now
     * @return a copy carrying that shade in place of this one's
     */
    public ClusterAnchor copyWithColour(Color restyledColour) {
        return new ClusterAnchor(
            identity,
            anchorX,
            anchorY,
            restyledColour,
            nameLines,
            fontHeight,
            acceptedAxis,
            rejectedAxis,
            unbiasedAxis,
            thickness,
            lineCount);
    }
}
