package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.ContentInputs;
import kmu.maplayers.ownermap.OwnerPaintedView;
import kmu.maplayers.ownermap.holding.HolderGrouping;
import kmu.maplayers.ownermap.owners.SpotlitBlocs;

/**
 * Resolves the shared per-bloc style decision - whether a bloc recedes to the independent
 * style and the adjustment it draws under - that both the fills and the cluster-name labels
 * read, so a bloc's name never drifts from its fill. The one home for the "who recedes, and
 * how, this pass" rule; the concrete {@link CategoryStyle} it maps to is picked by the caller
 * that has the theme, keeping this decision view-agnostic and style-free.
 */
public final class OwnerStyleResolver {

    // Resolves only; never instantiated.
    private OwnerStyleResolver() {
    }

    /**
     * The style choice a bloc draws under this pass, as a view-agnostic (independent-style?,
     * adjustment) pair the fill path and the label path both resolve from - the single decision
     * that keeps a bloc's name in step with its fill and border. Off filter it is the active
     * view's own call: its independent-recede test and per-bloc adjustment.
     *
     * <p>Under filter only the ADJUSTMENT is filter-driven; the base-style decision stays the
     * view's, so independent-held space keeps its independent style rather than snapping to the
     * faction style the moment a filter turns on - independent styling reads one source of truth
     * in both modes. The spotlighted bloc is the sole exception: it draws untouched at full
     * faction strength (NONE, faction style), so its synthetic key never inherits the view's
     * independent test (which a view's own desaturate would otherwise trip). Every other bloc
     * unions the view's own recede with the pass's shared recede, so a bloc receded by both never
     * mutes twice.
     *
     * <p>The adjustment resolves first and is handed to the view's style test, so a view that keys
     * its bundle off desaturation sees the union rather than one contributing toggle - the palette
     * and the bundle then agree in every mode. The order is safe because no adjustment input depends
     * on the style decision.
     *
     * <p>Both halves - whether this pass filters at all, and what the sector recedes to when it
     * does - come off the bake's own {@code contentInputs} rather than being passed separately, so
     * the mode a bloc is styled in and the recede it takes in that mode are the one sampling. The
     * view is handed the same value, since a view that recedes blocs of its own reads its recede
     * out of it.
     */
    public static OwnerStyleDecision resolveBlocStyleDecision(
            String blocId,
            OwnerPaintedView view,
            HolderGrouping grouping,
            ContentInputs contentInputs) {

        if (contentInputs.isFiltering()) {
            var isSpotlit = SpotlitBlocs.isSpotlitBloc(blocId);
            var adjustment = resolveFilterAdjustment(
                isSpotlit,
                view.resolveBlocStyleAdjustment(blocId, grouping, contentInputs),
                contentInputs.filterRecedeAdjustment());

            return new OwnerStyleDecision(
                !isSpotlit && view.shouldUseIndependentStyle(blocId, grouping, adjustment),
                adjustment);
        }
        var adjustment = view.resolveBlocStyleAdjustment(blocId, grouping, contentInputs);
        return new OwnerStyleDecision(
            view.shouldUseIndependentStyle(blocId, grouping, adjustment),
            adjustment);
    }

    /**
     * The filter-mode adjustment a bloc takes: the spotlighted bloc draws untouched (NONE), every
     * other bloc unions its view-decided recede with the pass's shared recede so the sector fades
     * to a muted background the spotlight reads against. The union (strongest mute, either
     * desaturate) applies once, so a bloc the view already recedes of its own accord does not
     * mute a second time when the filter recedes it too. Pure over
     * its inputs, so the rule needs no geometry.
     */
    public static ElementStyleAdjustment resolveFilterAdjustment(
            boolean isSpotlit,
            ElementStyleAdjustment viewAdjustment,
            ElementStyleAdjustment recedeAdjustment) {
        return isSpotlit
            ? ElementStyleAdjustment.NONE
            : viewAdjustment.mergeRecede(recedeAdjustment);
    }
}
