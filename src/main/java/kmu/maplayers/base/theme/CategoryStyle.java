package kmu.maplayers.base.theme;

/**
 * One category's style, read once per rebuild and applied to every cluster
 * of that category: the fill, outer-border, and inner-seam elements, plus the width each
 * border strokes at. A factionless category always leaves its inner seam not drawn - its
 * cells never fuse into clusters, so there are no province divisions to stroke - and draws
 * its fill only where the ground was once settled.
 *
 * <p>The per-category tier of the {@link RenderStyle} theme, keyed by
 * {@link MapStyleCategory}.
 */
public record CategoryStyle(
        ElementStyle fill,
        ElementStyle outer,
        double outerWidth,
        ElementStyle inner,
        double innerWidth) {
}
