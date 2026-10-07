package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.MapStyleCategory;
import kmu.maplayers.ownermap.ContentInputs;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A fixture layer's categories, over {@link StandInCategory}: an owner at full strength draws as
 * {@link StandInCategory#OWNED}, and a cell nobody owns falls to {@link StandInCategory#SETTLED} or
 * {@link StandInCategory#EMPTY} by whether something stands there.
 *
 * <p>Every category reads one bundle - the primary shade in every slot at full weight - so a suite
 * over a stage sees differences only from what it staged. A suite pinning a style a category reads
 * builds its own theme instead.
 */
public final class OwnerCategoriesFake implements OwnerCategories {

    /** The one shared instance; stateless, so every suite reuses it. */
    public static final OwnerCategoriesFake SHARED_FAKE = new OwnerCategoriesFake();

    private static final ElementStyle PRIMARY_AT_FULL_WEIGHT =
        new ElementStyle(OwnerPaletteSlot.PRIMARY, 1.0);

    private static final double BORDER_WIDTH = 1.0;

    private OwnerCategoriesFake() {
    }

    @Override
    public Map<MapStyleCategory, CategoryStyle> readCategoryStyles(ContentInputs contentInputs) {

        var styles = new LinkedHashMap<MapStyleCategory, CategoryStyle>();

        for (var category : StandInCategory.values()) {
            styles.put(category, new CategoryStyle(
                PRIMARY_AT_FULL_WEIGHT,
                PRIMARY_AT_FULL_WEIGHT,
                BORDER_WIDTH,
                PRIMARY_AT_FULL_WEIGHT,
                BORDER_WIDTH));
        }
        return styles;
    }

    // The two owned categories carry a name; the two unowned ones fuse into no cluster to label.
    @Override
    public Map<MapStyleCategory, ElementStyle> readNameStyles() {

        return Map.of(
            StandInCategory.OWNED, PRIMARY_AT_FULL_WEIGHT,
            StandInCategory.RECEDED, PRIMARY_AT_FULL_WEIGHT);
    }

    @Override
    public MapStyleCategory resolveFullStrengthCategory() {
        return StandInCategory.OWNED;
    }

    @Override
    public MapStyleCategory resolveUnownedCategory(boolean isSettled) {

        return isSettled
            ? StandInCategory.SETTLED
            : StandInCategory.EMPTY;
    }
}
