package kmu.conditions.domain;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * The offer policy KMU ships. When the player has the "show all conditions" toggle
 * on - the default - every condition is offerable, so the picker can place the
 * non-planetary conditions (such as decivilisation) the game does not normally
 * expose. With the toggle off it falls back to vanilla's planetary set, the
 * conditions the game itself treats as hand-placeable.
 */
public final class StarsectorConditionOfferPolicy implements KmuConditionOfferPolicy {
    private final BooleanSupplier shouldOfferAllConditions;

    public StarsectorConditionOfferPolicy(BooleanSupplier shouldOfferAllConditions) {
        this.shouldOfferAllConditions = Objects.requireNonNull(
            shouldOfferAllConditions,
            "shouldOfferAllConditions");
    }

    @Override
    public boolean isConditionOfferable(KmuConditionSpec spec) {
        if (spec == null) {
            return false;
        }
        // Planetary conditions are always offerable, so short-circuit before the
        // live settings read; the toggle only decides the non-planetary ones.
        return spec.isPlanetary()
            || shouldOfferAllConditions.getAsBoolean();
    }
}
