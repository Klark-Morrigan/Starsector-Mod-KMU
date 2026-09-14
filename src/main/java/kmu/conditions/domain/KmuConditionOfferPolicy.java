package kmu.conditions.domain;

/**
 * Decides which market conditions the picker may offer a market for hand
 * placement. Vanilla marks the hand-placeable set as "planetary", so that is the
 * baseline; this seam lets KMU widen the set - for example opting in the
 * decivilisation conditions - without the condition service knowing which
 * conditions those are or how the choice is configured.
 */
public interface KmuConditionOfferPolicy {
    boolean isConditionOfferable(KmuConditionSpec spec);

    /**
     * @return the baseline policy that offers exactly the conditions vanilla flags
     *         planetary - the set the game itself treats as hand-placeable
     */
    static KmuConditionOfferPolicy planetaryOnly() {
        return spec -> spec != null && spec.isPlanetary();
    }
}
