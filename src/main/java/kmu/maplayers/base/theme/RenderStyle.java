package kmu.maplayers.base.theme;

import java.util.Map;

/**
 * The whole theme a map layer paints under, read once per rebuild: the {@link GlobalStyle} tier
 * and the per-{@link MapStyleCategory} {@link CategoryStyle} tier. The builders resolve each
 * element's concrete draw attributes by cascading these two tiers (global + the element's
 * category) at build time, so the renderer stays a flat draw loop that never re-reads
 * settings. Held as one value on the drawables in place of five separate style fields, so
 * an incremental re-shape styles a cell against the exact theme the full build used.
 *
 * <p>The category tier is keyed on the open {@link MapStyleCategory} rather than on any one
 * layer's set of names, so a layer populates it with whatever divisions it paints. The map is
 * built once per rebuild and read per element, so a plain hash map costs nothing worth the
 * theme knowing which layer's categories are in it.
 */
public record RenderStyle(GlobalStyle global, Map<MapStyleCategory, CategoryStyle> categories) {

    /**
     * @return the style for {@code category}, the per-category tier the cascade folds over
     *         the global tier
     */
    public CategoryStyle categoryStyle(MapStyleCategory category) {
        return categories.get(category);
    }
}
