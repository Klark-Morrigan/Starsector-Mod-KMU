package kmu.maplayers.politicalmap.dominance.ribbon;

import kmu.maplayers.politicalmap.dominance.weighting.MarketFootprint;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;

import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.ribbon.OwnerRibbonRunsFixtures.TRITACHYON;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the order a held cell's band comes out in, which is the whole of what the held side
 * decides: how many colonies each bloc draws is one shared rule's answer, stated in its own suite.
 *
 * <p>The band has to open on the bloc the cell is painted for whatever gave that bloc the system,
 * and rank the rest exactly as the standings box ranks them, or the band and the fill it sits
 * inside disagree about who leads.
 *
 * <p>The cases are posed on the ways that can go wrong: a painter that does not lead on weight,
 * which the dominance rule's tie-breaks below the weights genuinely produce, and two rivals level on
 * weight, where only the ID separates them. Each poses its footprints in an order the assertion
 * does not expect back, since an ordering rule is invisible against inputs already in the order it
 * would produce.
 */
final class HeldBlocRankerTests {

    // The two weights below the combined one. Held constant across every case: the ordering reads
    // the combined weight alone, so a case varying either would vary nothing the rule can see.
    private static final int ANY_LARGEST_MARKET_WEIGHT = 4000;
    private static final int ANY_PLANET_WEIGHT = 4000;

    // How many colonies each bloc's footprint banked. The ranking does not read it, so it is held
    // at one throughout.
    private static final int ONE_COLONY = 1;

    // The weights the cases rank by, stated as a lead, a middle and a level pair.
    private static final int LEADING_WEIGHT = 9000;
    private static final int MIDDLE_WEIGHT = 5000;
    private static final int TRAILING_WEIGHT = 2000;

    @Nested
    class RankBlocsByWeight {

        // The dominance rule can hand a system to a bloc that leads on none of the weights - a dead
        // heat settled by the market nearest the system centre, or by ID - and the band has to open
        // on the bloc the cell is actually painted for regardless.
        @Test
        void leadsWithThePainterEvenWhereARivalOutweighsIt() {

            // ARRANGE
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(TRITACHYON, buildFootprint(LEADING_WEIGHT));
            footprints.put(HEGEMONY, buildFootprint(TRAILING_WEIGHT));

            // ACT
            var actualBlocIds = HeldBlocRanker.rankBlocsByWeight(HEGEMONY, footprints);

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactly(HEGEMONY, TRITACHYON);
        }

        // Behind the leader the band reads as the standings box does, so the heavier rival is the
        // nearer one.
        @Test
        void ranksTheRivalsBehindThePainterByDescendingWeight() {

            // ARRANGE
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(PERSEAN, buildFootprint(TRAILING_WEIGHT));
            footprints.put(HEGEMONY, buildFootprint(LEADING_WEIGHT));
            footprints.put(TRITACHYON, buildFootprint(MIDDLE_WEIGHT));

            // ACT
            var actualBlocIds = HeldBlocRanker.rankBlocsByWeight(HEGEMONY, footprints);

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactly(HEGEMONY, TRITACHYON, PERSEAN);
        }

        // Two rivals level on weight are separated by ID and nothing else, so the order never
        // depends on which of them the economy walk reached first.
        @Test
        void breaksAWeightTieBetweenRivalsByBlocId() {

            // ARRANGE
            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(HEGEMONY, buildFootprint(LEADING_WEIGHT));
            footprints.put(TRITACHYON, buildFootprint(MIDDLE_WEIGHT));
            footprints.put(PERSEAN, buildFootprint(MIDDLE_WEIGHT));

            // ACT
            var actualBlocIds = HeldBlocRanker.rankBlocsByWeight(HEGEMONY, footprints);

            // ASSERT
            assertThat(actualBlocIds)
                .containsExactly(HEGEMONY, PERSEAN, TRITACHYON);
        }
    }

    // One bloc's footprint as the dominance pass banked it, stated by the combined weight the
    // ordering reads.
    private static MarketFootprint buildFootprint(int totalWeight) {
        return new MarketFootprint(
            ONE_COLONY,
            totalWeight,
            ANY_LARGEST_MARKET_WEIGHT,
            ANY_PLANET_WEIGHT);
    }
}
