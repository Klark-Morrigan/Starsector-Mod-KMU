package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;

import java.util.Map;

/**
 * The categories a layer divides its cells into, declared by that layer: which categories exist
 * and how each is styled, which one an owner at full strength draws in, and which one a cell nobody
 * owns falls to.
 *
 * <p>How a map divides is the vocabulary of whoever paints it - independent space set apart from
 * factions is one layer's division, relay coverage set apart from none is another's - so the tier
 * states the roles a category plays and never names a category itself. It indexes the theme by the
 * open {@link MapStyleCategory} the layer hands back. Which category an owner draws in is the
 * owner reading's answer rather than this declaration's, since it can depend on the snapshot the
 * reading was taken over.
 *
 * <p>Two roles the tier's own rules are stated over, and so the two this declaration has to name:
 * the <em>full-strength</em> category, whose fill opacity every desaturated owner holds so a
 * receded backdrop reads as one surface; and the split of unowned cells into <em>settled</em> -
 * something stands there the layer's holding does not account for - against <em>empty</em>, which
 * decides both the category and whether a spotlight recedes the cell.
 */
public interface OwnerCategories {

    /**
     * Reads every category this layer draws in, with the style the player set for each, once per
     * rebuild.
     *
     * @param contentInputs the preferences this rebuild sampled, for a category whose style rides on
     *                      a sidebar pick rather than on a settings field
     * @return one style per category this layer's owners and unowned cells can draw in
     */
    Map<MapStyleCategory, CategoryStyle> readCategoryStyles(ContentInputs contentInputs);

    /**
     * Reads how an owner's cluster name is coloured and faded in each category an owner can draw
     * in, once per rebuild: the colour choice the name inherits beside that category's name opacity.
     *
     * @return one name style per category an owner can draw in
     */
    Map<MapStyleCategory, ElementStyle> readNameStyles();

    /**
     * The category an owner draws in at full strength - the one a spotlighted owner is held in
     * whatever its own reading says, and whose fill opacity a desaturated owner keeps.
     *
     * @return the full-strength category
     */
    MapStyleCategory resolveFullStrengthCategory();

    /**
     * The category a cell nobody owns falls to.
     *
     * @param isSettled whether something stands in the cell's system that the layer's holding does
     *                  not account for; false for a cell drawn as no system at all
     * @return the category the unowned cell draws in
     */
    MapStyleCategory resolveUnownedCategory(boolean isSettled);
}
