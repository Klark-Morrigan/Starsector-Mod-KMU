package kmu.maplayers.politicalmap.base.render.style;

import java.util.Map;

/**
 * The whole political-map theme, read once per rebuild: the {@link GlobalStyle} global tier
 * and the per-{@link MapCategory} {@link CategoryStyle} tier. The builders resolve each
 * element's concrete draw attributes by cascading these two tiers (global + the element's
 * category) at build time, so the renderer stays a flat draw loop that never re-reads
 * settings. Held as one value on the drawables in place of five separate style fields, so
 * an incremental re-shape styles a cell against the exact theme the full build used.
 */
public record RenderStyle(GlobalStyle global, Map<MapCategory, CategoryStyle> categories) {

    /**
     * @return the style for {@code category}, the per-category tier the cascade folds over
     *         the global tier
     */
    public CategoryStyle categoryStyle(MapCategory category) {
        return categories.get(category);
    }
}
