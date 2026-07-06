package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.MutableMarketStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.DynamicStatsAPI;

import kmu.settings.ConcealedBaseScalingChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static kmu.politicalmap.domain.politics.KnownMarketFootprints.DOMINANCE_WEIGHT_SCALE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the economy read: {@link KnownMarketFootprints} folding a
 * stubbed system's markets into per-faction footprints through the known-market
 * filter and the dominance-weighting rule. Exercises the filter (condition-only
 * skipped, the discovery / un-hidden gate), the concealed-base scaling (its real size
 * under Normal, the fixed weight under Fixed), the colony-size weight scaling each base
 * rating, the three weight factors (colony size, the station presence bonus with its
 * hidden-base rate and opt-out, and the patrol strength read from the economy), the
 * per-factor low-stability penalties under the master stability toggle, and the
 * zero-factor short-circuits that skip the station scan, the patrol read, and the
 * stability read.
 */
class KnownMarketFootprintsIntegrationTest {

    private static final float FULL_STABILITY = 10.0f;
    private static final float HALF_STABILITY = 5.0f;
    private static final float NO_STABILITY = 0.0f;

    // A fluent builder over the 14-factor DominanceWeighting, defaulting every field to
    // its CSV default so each test states only the axis it pins. The defaults are:
    // colony-size weight 1 (raw size), concealed bases Fixed at token weight 1, the
    // stability master on with a full (1.0) colony penalty, stations off (weight 1,
    // hidden rate 0.5, penalty 0.5), and patrols off (0.25/0.5/1 tier weights, penalty
    // 0.5).
    private static final class WeightingBuilder {
        private double colonySizeWeight = 1.0;
        private ConcealedBaseScalingChoice concealedScaling = ConcealedBaseScalingChoice.FIXED;
        private double concealedFixedWeight = 1.0;
        private boolean stabilityMaster = true;
        private double normalPenalty = 1.0;
        private boolean stationOn = false;
        private double stationWeight = 1.0;
        private double stationHiddenRate = 0.5;
        private double stationPenalty = 0.5;
        private boolean patrolOn = false;
        private double patrolSmall = 0.25;
        private double patrolMedium = 0.5;
        private double patrolLarge = 1.0;
        private double patrolPenalty = 0.5;

        private WeightingBuilder withColonyWeight(double value) {
            colonySizeWeight = value;
            return this;
        }

        private WeightingBuilder withConcealedScaling(ConcealedBaseScalingChoice value) {
            concealedScaling = value;
            return this;
        }

        private WeightingBuilder withConcealedFixedWeight(double value) {
            concealedFixedWeight = value;
            return this;
        }

        private WeightingBuilder withStabilityMaster(boolean value) {
            stabilityMaster = value;
            return this;
        }

        private WeightingBuilder withNormalPenalty(double value) {
            normalPenalty = value;
            return this;
        }

        private WeightingBuilder withStationWeighting() {
            stationOn = true;
            return this;
        }

        private WeightingBuilder withStationWeight(double value) {
            stationWeight = value;
            return this;
        }

        private WeightingBuilder withPatrolWeighting() {
            patrolOn = true;
            return this;
        }

        private DominanceWeighting build() {
            return new DominanceWeighting(colonySizeWeight, concealedScaling, concealedFixedWeight,
                    stabilityMaster, normalPenalty, stationOn, stationWeight, stationHiddenRate,
                    stationPenalty, patrolOn, patrolSmall, patrolMedium, patrolLarge,
                    patrolPenalty);
        }
    }

    private static WeightingBuilder weighting() {
        return new WeightingBuilder();
    }

    @Nested
    class ReadByFaction {

        @Test
        void readByFactionWeighsAKnownMarketUnderItsFaction() {
            // At full stability a market is worth its whole size on the weight grid:
            // size 5 -> 5 grid units.
            var sector = sectorWith("owned-system", visibleMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesColonySizeByStabilityAtFullPenalty() {
            // The colony penalty defaults to a full collapse, so a size-4 market at
            // stability 5 contributes half its size.
            var sector = sectorWith("shaky-system",
                    marketAtStability(faction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionIgnoresStabilityWhenTheMasterIsOff() {
            // With the master toggle off, a destabilised market folds in at its full
            // size regardless of any penalty - the raw-size dominance the toggle
            // opts back into.
            var sector = sectorWith("shaky-system",
                    marketAtStability(faction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withStabilityMaster(false).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(4 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionZeroStabilityAtFullPenaltyMarksPresenceAtNoWeight() {
            // At a full colony penalty a colony at 0 stability is worth nothing to
            // dominance, but it is still a known colony: the faction keeps its
            // footprint entry, so presence (and painting an unopposed system) holds.
            var sector = sectorWith("collapsed-system",
                    marketAtStability(faction("hegemony"), 5, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight()).isZero();
        }

        @Test
        void readByFactionKeepsPartOfColonyWeightAtZeroStabilityUnderAPartialPenalty() {
            // A half colony penalty leaves half the colony's weight at 0 stability: a
            // size-4 market -> 4 * (1 - 0.5) = 2 grid units.
            var sector = sectorWith("shaky-system",
                    marketAtStability(faction("hegemony"), 4, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withNormalPenalty(0.5).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionClampsStabilityIntoTheVanillaBand() {
            // A modded market can report stability above 10; the fraction clamps to
            // full so a market never outweighs its own size.
            var sector = sectorWith("overstable-system",
                    marketAtStability(faction("hegemony"), 3, 12.0f));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsConditionOnlyMarket() {
            // A bare rock's condition-only placeholder is no colony, so it folds into
            // no footprint.
            var sector = sectorWith("bare-system",
                    market(faction("hegemony"), 6, true, false, false, FULL_STABILITY));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build())).isEmpty();
        }

        @Test
        void readByFactionCountsConcealedBaseAtItsFixedWeightByDefault() {
            // A concealed base folds in at its fixed token weight of 1 rather than its
            // real size 5, so it flags presence without skewing dominance.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheFixedConcealedTokenByStability() {
            // The fixed token is a size rating like any other, so the colony penalty
            // scales it too: a destabilised concealed base marks less presence.
            var sector = sectorWith("hidden-shaky-system",
                    hiddenMarketAtStability(faction("hegemony"), 5, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionCountsConcealedBaseByRealSizeUnderNormalScaling() {
            // Under Normal scaling a concealed base counts by its real size like any
            // colony: size 5 -> 5 grid units.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withConcealedScaling(ConcealedBaseScalingChoice.NORMAL).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionUsesTheConfiguredFixedConcealedWeight() {
            // The fixed concealed weight sets the token size a concealed base folds in
            // at: at weight 2 a concealed base of any real size -> 2 grid units.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withConcealedFixedWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheFixedConcealedTokenByTheColonyWeight() {
            // The colony-size weight scales a concealed base's fixed token of 1 the same
            // way as any base size: at weight 2, 2 grid units.
            var sector = sectorWith("weighted-hidden-system",
                    hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withColonyWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionExcludesUndiscoveredStation() {
            // A concealed station (hidden market on a still-discoverable entity) fails
            // the known-market gate, so it folds into no footprint.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build())).isEmpty();
        }

        @Test
        void readByFactionIncludesAnUndiscoveredStationWhenIncludingUndiscoveredMarkets() {
            // The show-all-factions dev reveal drops the known-to-player gate, so a
            // concealed station folds in at its fixed token size of 1 rather than being
            // skipped as it is under the normal filter.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build(), true);

            assertThat(footprints).containsOnlyKeys("knights_of_selkie");
            assertThat(footprints.get("knights_of_selkie").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionAddsTheStationWeightForAnAttachedStation() {
            // A stationed size-5 market at full stability is worth the station weight
            // more than its size alone: (5 + 1) grid units.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(6 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionKeepsHalfTheStationBonusAtZeroStabilityByDefault() {
            // Isolating the station factor (colony weight zeroed), the station bonus
            // keeps half its worth at 0 stability under its default half penalty:
            // 1 * (1 - 0.5) -> half a grid unit.
            var sector = sectorWith("shaky-fortress-system",
                    stationedMarketAtStability(faction("hegemony"), 5, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withColonyWeight(0.0).withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionAddsTheStationBonusWithoutStabilityWeighting() {
            // With the master toggle off but station weighting on, the market folds in
            // at its full size plus the flat station weight: (4 + 1) grid units.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withStabilityMaster(false).withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionDropsTheStationBonusWhenStationWeightingIsOff() {
            // A stationed market read under a station-off weighting is worth exactly its
            // size, matching an unstationed colony - the bonus is gone.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionGivesAConcealedStationedBaseTheStationPointAtTheHiddenRate() {
            // A concealed stationed base gets the station weight times the hidden rate on
            // its token rating of 1, at full stability: (1 + 0.5) size points.
            var sector = sectorWith("hidden-fortress-system",
                    hiddenStationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionIgnoresAStationTaggedNoOrbitalStation() {
            // A "station"-tagged connected entity opted out via NO_ORBITAL_STATION is not
            // a market's orbital station, so it earns no bonus: size 5 alone.
            var sector = sectorWith("opted-out-system",
                    marketWithOptedOutStation(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsTheStationScanWhenTheStationWeightIsZero() {
            // A zero station weight can add nothing, so the connected-entity station scan
            // is skipped and the stationed market folds in at its size alone.
            var market = stationedMarket(faction("hegemony"), 5);
            var sector = sectorWith("zero-weight-station-system", market);

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withStabilityMaster(false).withStationWeighting()
                            .withStationWeight(0.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
            verify(market, never()).getConnectedEntities();
        }

        @Test
        void readByFactionScalesTheStationBonusByTheStationWeight() {
            // The station weight sets how many size points a station is worth: at weight
            // 2 a stationed size-4 market folds in at 4 + 2 -> 6 grid units.
            var sector = sectorWith("heavy-station-system",
                    stationedMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withStabilityMaster(false).withStationWeighting()
                            .withStationWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(6 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesVisibleMarketSizeByTheColonyWeight() {
            // The colony-size weight multiplies a visible market's raw size before the
            // station and patrol bonuses: size 4 at weight 2 -> 8 grid units.
            var sector = sectorWith("weighted-system", visibleMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withColonyWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(8 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionAppliesTheColonyWeightToSizeButNotTheStationPoint() {
            // The weight multiplies base size only; the station point is added after,
            // unweighted: (4 * 2) + 1 -> 9 grid units, not (4 + 1) * 2.
            var sector = sectorWith("weighted-fortress-system",
                    stationedMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withStabilityMaster(false).withColonyWeight(2.0)
                            .withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(9 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsTheStabilityReadWhenEveryFactorIsZero() {
            // A zero colony-size weight with no station or patrol bonus zeroes every
            // factor before stability, so the stability read is skipped; the market
            // still folds into the footprint (marking presence) at no dominance weight.
            var market = visibleMarket(faction("hegemony"), 5);
            var sector = sectorWith("weightless-system", market);

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withColonyWeight(0.0).build());

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight()).isZero();
            verify(market, never()).getStabilityValue();
        }

        @Test
        void readByFactionAddsPatrolStrengthWhenPatrolWeightingIsOn() {
            // With patrols on, a colony's small/medium/large counts each fold in at their
            // tier weight: 2 small * 0.25 + 1 medium * 0.5 -> 1 size point on top of the
            // size-3 colony -> 4 grid units at full stability.
            var sector = sectorWith("garrison-system",
                    patrolMarket(faction("hegemony"), 3, 2, 1, 0));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(4 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionWeighsPatrolTiersByTheirOwnWeights() {
            // Isolating the patrol factor (colony weight zeroed, master off), two large
            // patrols at the default large weight of 1 -> 2 size points.
            var sector = sectorWith("heavy-garrison-system",
                    patrolMarket(faction("hegemony"), 5, 0, 0, 2));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withColonyWeight(0.0).withStabilityMaster(false)
                            .withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionCutsPatrolStrengthByThePatrolPenaltyAtZeroStability() {
            // Isolating the patrol factor, a medium patrol worth 0.5 at 0 stability keeps
            // half its worth under the default half patrol penalty: 2 * 0.5 * 0.5 -> half
            // a grid unit.
            var sector = sectorWith("shaky-garrison-system",
                    patrolMarketAtStability(faction("hegemony"), 5, 0, 2, 0, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withColonyWeight(0.0).withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionKeepsPatrolStrengthFullWhenTheStabilityMasterIsOff() {
            // The same garrison with the master toggle off keeps its full patrol worth at
            // 0 stability: 2 * 0.5 -> 1 grid unit.
            var sector = sectorWith("shaky-garrison-system",
                    patrolMarketAtStability(faction("hegemony"), 5, 0, 2, 0, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    weighting().withColonyWeight(0.0).withStabilityMaster(false)
                            .withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionDoesNotReadPatrolStatsWhenPatrolWeightingIsOff() {
            // With patrols off the economy patrol read is skipped, so the market's stats
            // are never touched and it folds in at its size alone.
            var market = visibleMarket(faction("hegemony"), 5);
            var sector = sectorWith("no-patrol-system", market);

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), weighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
            verify(market, never()).getStats();
        }
    }

    private static StarSystemAPI onlySystem(SectorAPI sector) {
        return sector.getStarSystems().get(0);
    }

    private static MarketAPI visibleMarket(FactionAPI faction, int size) {
        return market(faction, size, false, false, false, FULL_STABILITY);
    }

    // A visible owned market at the given stability, the shape the penalty tests vary.
    private static MarketAPI marketAtStability(FactionAPI faction, int size, float stability) {
        return market(faction, size, false, false, false, stability);
    }

    // A concealed base (vanilla hidden market) on a discovered entity: it still marks
    // its system, but its base rating is chosen by the concealed-base scaling.
    private static MarketAPI hiddenMarket(FactionAPI faction, int size) {
        return market(faction, size, false, true, false, FULL_STABILITY);
    }

    // A concealed base at the given stability, for pinning that its token rating scales
    // with stability like any other size rating.
    private static MarketAPI hiddenMarketAtStability(FactionAPI faction, int size,
            float stability) {
        return market(faction, size, false, true, false, stability);
    }

    // A concealed station: a hidden market on a still-discoverable entity, the shape a
    // base wears before the player finds it. Fails both known-market arms, so it confers
    // no presence until discovery un-hides or reveals it.
    private static MarketAPI concealedStation(FactionAPI faction, int size) {
        return market(faction, size, false, true, true, FULL_STABILITY);
    }

    // A visible owned market that owns an attached defensive station - a "station"-tagged
    // connected entity, the ownership link vanilla itself reads.
    private static MarketAPI stationedMarket(FactionAPI faction, int size) {
        return withConnectedEntities(visibleMarket(faction, size), stationEntity());
    }

    // A stationed market at the given stability, for pinning the station factor's own
    // low-stability penalty.
    private static MarketAPI stationedMarketAtStability(FactionAPI faction, int size,
            float stability) {
        return withConnectedEntities(marketAtStability(faction, size, stability), stationEntity());
    }

    // A concealed base that owns a station, for pinning that it earns the station point
    // at the hidden rate on its token rating.
    private static MarketAPI hiddenStationedMarket(FactionAPI faction, int size) {
        return withConnectedEntities(hiddenMarket(faction, size), stationEntity());
    }

    // A market whose only connected "station"-tagged entity is opted out via
    // NO_ORBITAL_STATION, so it is not the market's orbital station and earns no bonus.
    private static MarketAPI marketWithOptedOutStation(FactionAPI faction, int size) {
        return withConnectedEntities(visibleMarket(faction, size), optedOutStationEntity());
    }

    // A visible owned market that fields the given small/medium/large patrol counts,
    // stubbed onto its dynamic stats the way vanilla's military industries write them.
    private static MarketAPI patrolMarket(FactionAPI faction, int size, int small, int medium,
            int large) {
        return withPatrols(visibleMarket(faction, size), small, medium, large);
    }

    // A patrolling market at the given stability, for pinning the patrol factor's own
    // low-stability penalty.
    private static MarketAPI patrolMarketAtStability(FactionAPI faction, int size, int small,
            int medium, int large, float stability) {
        return withPatrols(marketAtStability(faction, size, stability), small, medium, large);
    }

    // Stubs the market's connected entities - the ownership link the station scan reads -
    // to the given entities.
    private static MarketAPI withConnectedEntities(MarketAPI market,
            SectorEntityToken... entities) {
        when(market.getConnectedEntities()).thenReturn(Set.of(entities));
        return market;
    }

    // Stubs the market's dynamic stats to report the given patrol-tier counts, the seam
    // the patrol read walks (getStats -> getDynamic -> getMod(tier).computeEffective).
    private static MarketAPI withPatrols(MarketAPI market, int small, int medium, int large) {
        // Build each tier's mock before the getMod stubbing: patrolMod() stubs a mock
        // of its own, and Mockito rejects a nested when(...) inside a thenReturn(...).
        var smallMod = patrolMod(small);
        var mediumMod = patrolMod(medium);
        var largeMod = patrolMod(large);
        var dynamicMock = mock(DynamicStatsAPI.class);
        when(dynamicMock.getMod(Stats.PATROL_NUM_LIGHT_MOD)).thenReturn(smallMod);
        when(dynamicMock.getMod(Stats.PATROL_NUM_MEDIUM_MOD)).thenReturn(mediumMod);
        when(dynamicMock.getMod(Stats.PATROL_NUM_HEAVY_MOD)).thenReturn(largeMod);
        var statsMock = mock(MutableMarketStatsAPI.class);
        when(statsMock.getDynamic()).thenReturn(dynamicMock);
        when(market.getStats()).thenReturn(statsMock);
        return market;
    }

    // A patrol-count mod whose effective value at base 0 is the given count.
    private static StatBonus patrolMod(float effective) {
        var modMock = mock(StatBonus.class);
        when(modMock.computeEffective(0.0f)).thenReturn(effective);
        return modMock;
    }

    // A station entity: carries the "station" tag and no opt-out, so the scan counts it
    // as the market's orbital station.
    private static SectorEntityToken stationEntity() {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.hasTag(Tags.STATION)).thenReturn(true);
        return entityMock;
    }

    // A "station"-tagged entity flagged NO_ORBITAL_STATION, vanilla's own opt-out, so
    // the scan skips it.
    private static SectorEntityToken optedOutStationEntity() {
        var entityMock = stationEntity();
        when(entityMock.hasTag("NO_ORBITAL_STATION")).thenReturn(true);
        return entityMock;
    }

    private static MarketAPI market(FactionAPI faction, int size, boolean isConditionOnly,
            boolean isHidden, boolean isUndiscovered, float stability) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(isUndiscovered);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(faction);
        when(marketMock.getSize()).thenReturn(size);
        when(marketMock.getStabilityValue()).thenReturn(stability);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(isConditionOnly);
        when(marketMock.isHidden()).thenReturn(isHidden);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }

    private static FactionAPI faction(String id) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(id);
        return factionMock;
    }

    // Wires a sector with one system whose economy holds the given markets. No faction
    // palette or neutral faction is stubbed: the footprint read keys on each market's
    // own faction id and never resolves colors.
    private static SectorAPI sectorWith(String systemId, MarketAPI... markets) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(systemMock)).thenReturn(List.of(markets));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systemMock));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        return sectorMock;
    }
}
