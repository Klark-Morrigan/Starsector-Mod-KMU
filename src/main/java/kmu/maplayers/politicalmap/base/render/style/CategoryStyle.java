package kmu.maplayers.politicalmap.base.render.style;

/**
 * One category's political-map style, read once per rebuild and applied to every cluster
 * of that category: the fill, outer-border, and inner-seam elements, plus the width each
 * border strokes at. A factionless category leaves its fill and inner seam not drawn, so
 * only its outline paints.
 *
 * <p>The per-category tier of the {@link RenderStyle} theme, keyed by {@link MapCategory}.
 */
public record CategoryStyle(
        ElementStyle fill,
        ElementStyle outer,
        double outerWidth,
        ElementStyle inner,
        double innerWidth) {
}
