package kmu.maplayers.politicalmap.dominance.ribbon;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.MarketFootprint;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlan;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.PERSEAN;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.PERSEAN_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.PERSEAN_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildInputsOver;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.buildSectorHolding;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the order a held cell's band comes out in, which is the whole of what the held side still
 * decides: how many colonies each bloc draws is one shared rule's answer, stated in its own suite.
 *
 * <p>The band has to open on the bloc the cell is painted for whatever gave that bloc the system,
 * and rank the rest exactly as the standings box ranks them, or the band and the fill it sits
 * inside disagree about who leads.
 *
 * <p>The cases are posed on the ways that can go wrong: a painter that does not lead on weight,
 * which the dominance rule's tie-breaks below the weights genuinely produce; two rivals level on
 * weight, where only the id separates them; and a bloc the weights never reached at all, which the
 * widened count now puts in a band that has no rank for it. Each poses its footprints in an order
 * the assertion does not expect back, since an ordering rule is invisible against inputs already in
 * the order it would produce.
 */
final class HeldCellRibbonsTest {

    private static final String SYSTEM_ID = "corvus";

    // The two weights below the combined one. Held constant across every case: the ordering reads
    // the combined weight alone, so a case varying either would vary nothing the rule can see.
    private static final int ANY_LARGEST_MARKET_WEIGHT = 4000;
    private static final int ANY_PLANET_WEIGHT = 4000;

    // The weights the cases rank by, stated as a lead, a middle and a level pair.
    private static final int LEADING_WEIGHT = 9000;
    private static final int MIDDLE_WEIGHT = 5000;
    private static final int TRAILING_WEIGHT = 2000;

    // How many colonies each bloc's footprint banked. The band no longer reads this - it counts the
    // system - so it is held at one throughout, the footprints being consulted for their order
    // alone.
    private static final int ONE_COLONY = 1;

    // The size the off-economy station is posed at, that being the one colony these cases build
    // themselves rather than taking from the shared sector helper.
    private static final int COLONY_SIZE = 5;

    @Nested
    class PlanHeldCellRibbon {

        @Test
        void leadsWithThePainterEvenWhereARivalOutweighsIt() {
            // The dominance rule can hand a system to a bloc that leads on none of the weights -
            // a dead heat settled by the market nearest the system centre, or by id - and the band
            // has to open on the bloc the cell is actually painted for regardless.
            var sector = buildSectorHolding(SYSTEM_ID, TRITACHYON, HEGEMONY);

            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(TRITACHYON, buildFootprint(LEADING_WEIGHT));
            footprints.put(HEGEMONY, buildFootprint(TRAILING_WEIGHT));

            assertThat(planFor(HEGEMONY, footprints, sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void ranksTheRivalsBehindThePainterByDescendingWeight() {
            // Behind the leader the band reads as the standings box does, so the heavier rival is
            // the nearer one.
            var sector = buildSectorHolding(SYSTEM_ID, PERSEAN, HEGEMONY, TRITACHYON);

            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(PERSEAN, buildFootprint(TRAILING_WEIGHT));
            footprints.put(HEGEMONY, buildFootprint(LEADING_WEIGHT));
            footprints.put(TRITACHYON, buildFootprint(MIDDLE_WEIGHT));

            assertThat(planFor(HEGEMONY, footprints, sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3),
                    new RibbonSegment(TRITACHYON_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3));
        }

        @Test
        void breaksAWeightTieBetweenRivalsByBlocId() {
            // Two rivals level on weight are separated by id and nothing else, so the order never
            // depends on which of them the economy walk reached first.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, TRITACHYON, PERSEAN);

            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(HEGEMONY, buildFootprint(LEADING_WEIGHT));
            footprints.put(TRITACHYON, buildFootprint(MIDDLE_WEIGHT));
            footprints.put(PERSEAN, buildFootprint(MIDDLE_WEIGHT));

            assertThat(planFor(HEGEMONY, footprints, sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3),
                    new RibbonSegment(PERSEAN_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }

        @Test
        void drawsABlocTheWeightsNeverReachedBehindTheOnesTheyDid() {
            // Tri-Tachyon holds nothing here but a station the economy does not list, which has no
            // industries or stability for a weight to be computed from - so it has no footprint and
            // no rank. It is present in the system all the same, and draws behind the blocs the
            // weights did reach rather than being given a place among them.
            var sector = buildSectorHolding(SYSTEM_ID, HEGEMONY, PERSEAN);

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                buildVisibleMarket(buildFaction(TRITACHYON), COLONY_SIZE));

            var footprints = new LinkedHashMap<String, MarketFootprint>();
            footprints.put(HEGEMONY, buildFootprint(LEADING_WEIGHT));
            footprints.put(PERSEAN, buildFootprint(TRAILING_WEIGHT));

            assertThat(planFor(HEGEMONY, footprints, sector).segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, 3),
                    new RibbonSegment(HEGEMONY_DARK, 1),
                    new RibbonSegment(PERSEAN_BRIGHT, 3),
                    new RibbonSegment(PERSEAN_DARK, 1),
                    new RibbonSegment(TRITACHYON_BRIGHT, 3));
        }
    }

    // The one call every case makes: the faction view, the shared palettes, and the design's own
    // segment lengths, leaving a case to state the footprints and the system.
    private static RibbonPlan planFor(
            String paintingBlocId,
            Map<String, MarketFootprint> footprintByBlocId,
            SectorAPI sector) {

        return HeldCellRibbons.planHeldCellRibbon(
            paintingBlocId,
            buildOnlySystem(sector),
            footprintByBlocId,
            buildInputsOver(sector, HolderGrouping.identity(), BASE_FOG));
    }

    // One bloc's footprint as the dominance pass banked it, stated by the combined weight the
    // ordering reads. Its colony count is carried because a footprint has one, and the band no
    // longer reads it.
    private static MarketFootprint buildFootprint(int totalWeight) {
        return new MarketFootprint(
            ONE_COLONY,
            totalWeight,
            ANY_LARGEST_MARKET_WEIGHT,
            ANY_PLANET_WEIGHT);
    }
}
