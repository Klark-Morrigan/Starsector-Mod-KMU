package kmu.maplayers.politicalmap.base.render.labels.anchor.specifications;

/**
 * The two debug-line toggles the search reads: whether a cluster also carries the best
 * rejected candidate and the pure-longest accepted line, so the search only builds the
 * extra candidates while someone is looking at them.
 *
 * @param showRejectedAxis whether a cluster whose accepted line collapsed also carries the
 *                         best rejected candidate the search found, for the red diagnostic
 *                         line
 * @param showUnbiasedAxis whether each cluster also carries the pure-longest accepted line
 *                         (the winner with no vertical penalty), for the yellow diagnostic
 *                         line
 */
public record AnchorDiagnostics(
        boolean showRejectedAxis,
        boolean showUnbiasedAxis) {
}
