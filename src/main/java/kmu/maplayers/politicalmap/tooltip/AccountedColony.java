package kmu.maplayers.politicalmap.tooltip;

/**
 * The colony an account's line names, with the two facts about it that only the account holds.
 *
 * <p>A reading of the system answers what kind of place a colony is and whether the player has found
 * it, but not whether the colony conceals itself or whether the economy lists it: those travel on the
 * account's own carrier. Both are flags about being out of view, so passed side by side a swapped pair
 * still compiles and simply calls out the wrong word. A factory named for the economy's listing leaves
 * one flag to pass, which cannot be swapped with anything.
 */
public final class AccountedColony {

    private final String marketId;
    private final boolean isHiddenMarket;
    private final boolean isListedByEconomy;

    private AccountedColony(String marketId, boolean isHiddenMarket, boolean isListedByEconomy) {
        this.marketId = marketId;
        this.isHiddenMarket = isHiddenMarket;
        this.isListedByEconomy = isListedByEconomy;
    }

    /**
     * A colony the economy's own set holds.
     *
     * @param marketId       the colony's market ID, as the account listing it carries
     * @param isHiddenMarket whether the colony conceals itself rather than being held in the open
     * @return the colony as a line names it
     */
    public static AccountedColony createEconomyListedColony(String marketId, boolean isHiddenMarket) {
        return new AccountedColony(marketId, isHiddenMarket, true);
    }

    /**
     * A colony the economy's own set does not hold, which only the walk of the system's entities finds.
     *
     * @param marketId       the colony's market ID, as the account listing it carries
     * @param isHiddenMarket whether the colony conceals itself rather than being held in the open
     * @return the colony as a line names it
     */
    public static AccountedColony createOffEconomyColony(String marketId, boolean isHiddenMarket) {
        return new AccountedColony(marketId, isHiddenMarket, false);
    }

    boolean isHiddenMarket() {
        return isHiddenMarket;
    }

    boolean isListedByEconomy() {
        return isListedByEconomy;
    }

    String marketId() {
        return marketId;
    }
}
