package kmu.maplayers.politicalmap.base.render.labels.anchor.specifications;

/**
 * How a cluster's name is sized into its fitted box: the per-line font clamp, the most
 * lines it may wrap into, and the spacing between them.
 *
 * @param minFontSize the smallest per-line font height a fit will accept, world units - the
 *                    readability floor; a chord that cannot hold even one line this tall
 *                    collapses to the dot
 * @param maxFontSize the largest per-line font height a fit will grow to, world units, so a
 *                    roomy cluster does not mint an oversized label
 * @param maxLines    the most lines a name may wrap into, spending girth to shorten the
 *                    length its widest line needs
 * @param lineSpacing the line-height multiple between stacked lines, at least 1
 */
public record NameFit(
        double minFontSize,
        double maxFontSize,
        int maxLines,
        double lineSpacing) {
}
