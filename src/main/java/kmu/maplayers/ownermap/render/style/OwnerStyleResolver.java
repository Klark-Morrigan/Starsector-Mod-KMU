package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;

/**
 * Resolves the shared per-owner style decision - which category an owner draws in and the
 * adjustment it draws under - that both the fills and the cluster-name labels read, so an owner's
 * name never drifts from its fill. The one home for the "who recedes, and how, this pass" rule; the
 * concrete {@link CategoryStyle} it maps to is picked by the caller that has the theme, keeping this
 * decision layer-agnostic and style-free.
 */
public final class OwnerStyleResolver {

    // Resolves only; never instantiated.
    private OwnerStyleResolver() {
    }

    /**
     * The style choice an owner draws under this pass, as a theme-free (category, adjustment) pair
     * the fill path and the label path both resolve from - the single decision that keeps an
     * owner's name in step with its fill and border. Off filter it is the layer's own call: the
     * owner reading's category and per-owner adjustment.
     *
     * <p>Under filter only the ADJUSTMENT is filter-driven; the category stays the reading's, so an
     * owner the layer already recedes keeps its quieter category rather than snapping to full
     * strength the moment a filter turns on - the category reads one source of truth in both modes.
     * The spotlighted owner is the sole exception: it draws untouched in the full-strength category,
     * so its synthetic key never inherits the reading's category (which the reading's own
     * desaturate would otherwise trip). Every other owner unions the reading's own recede with the
     * pass's shared recede, so an owner receded by both never mutes twice.
     *
     * <p>The adjustment resolves first and is handed to the reading's category, so a layer that keys
     * its category off desaturation sees the union rather than one contributing toggle - the palette
     * and the bundle then agree in every mode. The order is safe because no adjustment input depends
     * on the category.
     *
     * <p>Both halves - whether this pass filters at all, and what the sector recedes to when it
     * does - come off the bake's own {@code contentInputs} rather than being passed separately, so
     * the mode an owner is styled in and the recede it takes in that mode are the one sampling. The
     * reading is handed the same value, since a layer that recedes owners of its own reads its
     * recede out of it.
     *
     * @param ownerId       the owner to style - an owner ID, or one of the filter's synthetic keys
     * @param reading       the painting layer's answers about its owners, resolved once this rebuild
     * @param categories    the painting layer's categories, whose full-strength one a spotlit
     *                      owner draws in
     * @param contentInputs the preferences this rebuild sampled
     * @return the category and adjustment the owner draws under
     */
    public static OwnerStyleDecision resolveBlocStyleDecision(
            String ownerId,
            OwnerReading reading,
            OwnerCategories categories,
            ContentInputs contentInputs) {

        if (contentInputs.isFiltering()) {
            var isSpotlit = SpotlitBlocs.isSpotlitBloc(ownerId);
            var adjustment = resolveFilterAdjustment(
                isSpotlit,
                reading.resolveStyleAdjustment(ownerId, contentInputs),
                contentInputs.filterRecedeAdjustment());

            return new OwnerStyleDecision(
                isSpotlit
                    ? categories.resolveFullStrengthCategory()
                    : reading.resolveCategory(ownerId, adjustment),
                adjustment);
        }
        var adjustment = reading.resolveStyleAdjustment(ownerId, contentInputs);
        return new OwnerStyleDecision(
            reading.resolveCategory(ownerId, adjustment),
            adjustment);
    }

    /**
     * The filter-mode adjustment an owner takes: the spotlighted owner draws untouched (NONE),
     * every other owner unions its layer-decided recede with the pass's shared recede so the sector
     * fades to a muted background the spotlight reads against. The union (strongest mute, either
     * desaturate) applies once, so an owner the layer already recedes of its own accord does not
     * mute a second time when the filter recedes it too. Pure over its inputs, so the rule needs no
     * geometry.
     */
    public static ElementStyleAdjustment resolveFilterAdjustment(
            boolean isSpotlit,
            ElementStyleAdjustment ownerAdjustment,
            ElementStyleAdjustment recedeAdjustment) {
        return isSpotlit
            ? ElementStyleAdjustment.NONE
            : ownerAdjustment.mergeRecede(recedeAdjustment);
    }
}
