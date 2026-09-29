package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.settings.KmuOwnerMapStyleSettings;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The categories a layer painting holders divides its cells into - the four of
 * {@link OwnerMapCategory} - with their styles read off the settings rows those four have always
 * been read from.
 *
 * <p>A holder at full strength draws as a faction; one receded to the backdrop draws as independent
 * space; a system nobody holds draws decivilised where somebody still stands there and uninhabited
 * where nobody does. Every layer painting holders shares this one declaration, so the settings
 * behind it stay one set of knobs across them.
 */
public final class HolderCategories
        implements OwnerCategories {

    /** The one shared instance; stateless, so every rebuild reuses it. */
    public static final HolderCategories INSTANCE = new HolderCategories();

    private HolderCategories() {
    }

    // The four styles in one map, keyed on the open category type rather than this set's enum so
    // the theme can serve any layer's categories - four inserts once per rebuild. The uninhabited
    // outline's on/off comes off the rebuild's sampling rather than being read here, because it is
    // a sidebar preference and a second reading of it could disagree with the one the rebuild was
    // owed by.
    @Override
    public Map<MapStyleCategory, CategoryStyle> readCategoryStyles(ContentInputs contentInputs) {

        Map<MapStyleCategory, CategoryStyle> categories = new LinkedHashMap<>();

        categories.put(
            OwnerMapCategory.FACTION,
            RenderStyleReader.readFactionStyle());

        categories.put(
            OwnerMapCategory.INDEPENDENT,
            RenderStyleReader.readIndependentStyle());

        categories.put(
            OwnerMapCategory.DECIVILISED,
            RenderStyleReader.readDecivilisedStyle());

        categories.put(
            OwnerMapCategory.UNINHABITED,
            RenderStyleReader.readUninhabitedStyle(contentInputs.isUninhabitedOutlineDrawn()));

        return categories;
    }

    // Each owned category's outer-border colour choice - the one its cluster border reads, so a
    // name never drifts from the border it labels - beside that category's own name opacity, from
    // the visuals tab's "Faction systems" and "Independent systems" sections. The two unowned
    // categories carry no name: their cells fuse into no cluster to label.
    @Override
    public Map<MapStyleCategory, ElementStyle> readNameStyles() {

        return Map.of(
            OwnerMapCategory.FACTION,
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(
                    KmuOwnerMapStyleSettings.getFactionOuterBorderColour()),
                KmuOwnerMapStyleSettings.getFactionNameOpacity()),
            OwnerMapCategory.INDEPENDENT,
            new ElementStyle(
                FactionPaletteSlot.resolvePaintSelectionOf(
                    KmuOwnerMapStyleSettings.getIndependentOuterBorderColour()),
                KmuOwnerMapStyleSettings.getIndependentNameOpacity()));
    }

    @Override
    public MapStyleCategory resolveFullStrengthCategory() {

        return OwnerMapCategory.FACTION;
    }

    // A live colony and a decivilised one share the one bundle. The distinction the two unowned
    // styles draw is presence against absence - is anything here, or is this the backdrop - and on
    // that question an unheld colony and a revealed decivilised world answer alike. Which kind of
    // settlement it is would be a third bundle's worth of theme and settings to say, and the map says
    // it in the hover box instead.
    @Override
    public MapStyleCategory resolveUnownedCategory(boolean isSettled) {

        return isSettled
            ? OwnerMapCategory.DECIVILISED
            : OwnerMapCategory.UNINHABITED;
    }
}
