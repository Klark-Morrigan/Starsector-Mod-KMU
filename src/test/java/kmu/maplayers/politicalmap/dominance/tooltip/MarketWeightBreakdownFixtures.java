package kmu.maplayers.politicalmap.dominance.tooltip;

import kmlib.starsector.entities.EntityNameplate;

import kmu.maplayers.politicalmap.dominance.weighting.BaseSizeFactor;
import kmu.maplayers.politicalmap.dominance.weighting.MarketWeightBreakdown;

import java.util.Optional;

import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.FULL_STABILITY;

/**
 * The colony a domination-box suite weighs when its cases are about which faction a colony is listed
 * under or what the box ranks it at, rather than about any one factor: a plain planet colony in the
 * open, worth what its base size alone gives it.
 *
 * <p>Shared because the box and the account beneath it explain one set of weights, and a suite for each
 * stating its own colony would be two chances to pose that colony differently.
 */
final class MarketWeightBreakdownFixtures {

    // Where a colony sits and whether it is concealed, neither of which a case using this turns on.
    // Named so the pair of booleans a colony's parts open with can be read rather than counted off
    // against the record's own order.
    private static final boolean VISIBLE_COLONY = false;
    private static final boolean PLANET_COLONY = false;

    // The size every colony here is rated at, which no case reads: what it is worth is the
    // contribution the case states.
    private static final int ANY_MARKET_SIZE = 4;
    private static final double ANY_SIZE_RATING = 4.0;
    private static final double NO_STABILITY_PENALTY = 0.0;

    private MarketWeightBreakdownFixtures() {
    }

    /**
     * One colony worth the given size points on its base size alone, so a case states a colony by the
     * one number it is ranked against its siblings by.
     *
     * @param marketName   the colony's name, which is also its ID
     * @param contribution what its base size adds, in size points
     * @return the colony's breakdown
     */
    static MarketWeightBreakdown buildBaseSizeBreakdown(String marketName, double contribution) {

        return new MarketWeightBreakdown(
            marketName,
            EntityNameplate.createUnmarkedNameplate(marketName),
            VISIBLE_COLONY,
            PLANET_COLONY,
            FULL_STABILITY,
            new BaseSizeFactor(ANY_MARKET_SIZE, ANY_SIZE_RATING, contribution, NO_STABILITY_PENALTY),
            Optional.empty(),
            Optional.empty());
    }
}
