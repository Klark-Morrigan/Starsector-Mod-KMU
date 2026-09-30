package kmu.maplayers.ownermap.render.labels;

import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;

import java.util.Map;

/**
 * How a cluster name is coloured and faded, per category an owner draws in: the colour choice the
 * name inherits beside that category's own name opacity.
 *
 * <p>Per category because an owner's name follows whichever category its owner was placed in, the
 * way its fill and border do - a name receded with its owner reads as quietly as the space it
 * labels. Which categories there are, and what each name style holds, is the painting layer's
 * declaration; this only looks the answer up. That lookup is what makes it an owner-map value: the
 * search that places a name knows nothing of categories, so the resolved shade reaches it as a plain
 * colour and the split stays on this side of the seam.
 *
 * @param nameStyleByCategory each category's name style, as the painting layer read it this
 *                            rebuild
 */
public record BlocNameStyles(Map<MapStyleCategory, ElementStyle> nameStyleByCategory) {

    /**
     * The name style of one category.
     *
     * @param category the category the named owner draws in
     * @return that category's name style, or {@link ElementStyle#NOT_DRAWN} for a category its layer
     *         gave no name style - one whose owners it never meant to label
     */
    public ElementStyle resolveNameStyleOf(MapStyleCategory category) {
        return nameStyleByCategory.getOrDefault(category, ElementStyle.NOT_DRAWN);
    }
}
