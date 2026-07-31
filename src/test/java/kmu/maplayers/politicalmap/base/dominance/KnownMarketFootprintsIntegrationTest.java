package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.fleet.MutableMarketStatsAPI;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.DynamicStatsAPI;

import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints.DOMINANCE_WEIGHT_SCALE;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.faction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.hiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.market;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.marketAtStability;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.onlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.sectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.undiscoveredHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.visibleMarket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the economy read: {@link KnownMarketFootprints} folding a
 * stubbed system's markets into per-faction footprints through the known-market
 * filter and the dominance-weighting rule. Exercises the filter (condition-only
 * skipped, the discovery / un-hidden gate), the hidden-market scaling (its real size
 * under Normal, the fixed weight under Fixed), the colony-size weight scaling each base
 * rating, the three weight factors (colony size, the station presence bonus with its
 * hidden-market rate and opt-out, and the patrol strength read from the economy), the
 * per-factor low-stability penalties under the master stability toggle, and the
 * zero-factor short-circuits that skip the station scan, the patrol read, and the
 * stability read.
 */
class KnownMarketFootprintsIntegrationTest {

    private static final float FULL_STABILITY = 10.0f;
    private static final float HALF_STABILITY = 5.0f;
    private static final float NO_STABILITY = 0.0f;

    // A fluent builder over DominanceRules and its three weight factors, defaulting every
    // knob to its CSV default so each test states only the axis it pins. The defaults are:
    // colony-size weight 1 (raw size), hidden markets Fixed at token weight 1, the
    // stability master on with a full (1.0) colony penalty, stations off (weight 1,
    // hidden rate 0.5, penalty 0.5), and patrols off (0.25/0.5/1 tier weights, penalty
    // 0.5).
    private static final class RulesBuilder {
        private double colonySizeWeight = 1.0;
        private HiddenMarketScalingChoice hiddenMarketScaling = HiddenMarketScalingChoice.FIXED;
        private double hiddenMarketFixedWeight = 1.0;
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

        private RulesBuilder withColonyWeight(double value) {
            colonySizeWeight = value;
            return this;
        }

        private RulesBuilder withHiddenMarketScaling(HiddenMarketScalingChoice value) {
            hiddenMarketScaling = value;
            return this;
        }

        private RulesBuilder withHiddenMarketFixedWeight(double value) {
            hiddenMarketFixedWeight = value;
            return this;
        }

        private RulesBuilder withStabilityMaster(boolean value) {
            stabilityMaster = value;
            return this;
        }

        private RulesBuilder withNormalPenalty(double value) {
            normalPenalty = value;
            return this;
        }

        private RulesBuilder withStationWeighting() {
            stationOn = true;
            return this;
        }

        private RulesBuilder withStationWeight(double value) {
            stationWeight = value;
            return this;
        }

        private RulesBuilder withPatrolWeighting() {
            patrolOn = true;
            return this;
        }

        private DominanceRules build() {
            return new DominanceRules(stabilityMaster,
                    new BaseSizeWeighting(colonySizeWeight, hiddenMarketScaling,
                            hiddenMarketFixedWeight, normalPenalty),
                    new StationWeighting(stationOn, stationWeight, stationHiddenRate, stationPenalty),
                    new PatrolWeighting(patrolOn, patrolSmall, patrolMedium, patrolLarge,
                            patrolPenalty));
        }
    }

    private static RulesBuilder rules() {
        return new RulesBuilder();
    }

    @Nested
    class ReadByFaction {

        @Test
        void readByFactionWeighsAKnownMarketUnderItsFaction() {
            // At full stability a market is worth its whole size on the weight grid:
            // size 5 -> 5 grid units.
            var sector = sectorWith("owned-system", visibleMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().build());

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
                    sector, onlySystem(sector), rules().build());

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
                    sector, onlySystem(sector), rules().withStabilityMaster(false).build());

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
                    sector, onlySystem(sector), rules().build());

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
                    sector, onlySystem(sector), rules().withNormalPenalty(0.5).build());

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
                    sector, onlySystem(sector), rules().build());

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
                    sector, onlySystem(sector), rules().build())).isEmpty();
        }

        @Test
        void readByFactionCountsHiddenMarketAtItsFixedWeightByDefault() {
            // A hidden market folds in at its fixed token weight of 1 rather than its
            // real size 5, so it flags presence without skewing dominance.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheFixedHiddenMarketTokenByStability() {
            // The fixed token is a size rating like any other, so the colony penalty
            // scales it too: a destabilised hidden market marks less presence.
            var sector = sectorWith("hidden-shaky-system",
                    hiddenMarketAtStability(faction("hegemony"), 5, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionCountsHiddenMarketByRealSizeUnderNormalScaling() {
            // Under Normal scaling a hidden market counts by its real size like any
            // colony: size 5 -> 5 grid units.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    rules().withHiddenMarketScaling(HiddenMarketScalingChoice.NORMAL).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionUsesTheConfiguredFixedHiddenMarketWeight() {
            // The fixed hidden-market weight sets the token size a hidden market folds in
            // at: at weight 2 a hidden market of any real size -> 2 grid units.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    rules().withHiddenMarketFixedWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheFixedHiddenMarketTokenByTheColonyWeight() {
            // The colony-size weight scales a hidden market's fixed token of 1 the same
            // way as any base size: at weight 2, 2 grid units.
            var sector = sectorWith("weighted-hidden-system",
                    hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().withColonyWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionExcludesUndiscoveredStation() {
            // An undiscovered hidden market on a still-discoverable entity fails
            // the known-market gate, so it folds into no footprint.
            var sector = sectorWith("undiscovered-system",
                    undiscoveredHiddenMarket(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().build())).isEmpty();
        }

        @Test
        void readByFactionIncludesAnUndiscoveredStationWhenIncludingUndiscoveredMarkets() {
            // The show-all-factions dev reveal drops the known-to-player gate, so an
            // undiscovered hidden market folds in at its fixed token size of 1 rather
            // than being skipped as it is under the normal filter.
            var sector = sectorWith("undiscovered-system",
                    undiscoveredHiddenMarket(faction("knights_of_selkie"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().build(), true);

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
                    sector, onlySystem(sector), rules().withStationWeighting().build());

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
                    rules().withColonyWeight(0.0).withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionAddsTheStationBonusWithoutStabilityWeighting() {
            // With the master toggle off but station weighting on, the market folds in
            // at its full size plus the flat station weight: (4 + 1) grid units.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector),
                    rules().withStabilityMaster(false).withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionDropsTheStationBonusWhenStationWeightingIsOff() {
            // A stationed market read under a station-off weighting is worth exactly its
            // size, matching an unstationed colony - the bonus is gone.
            var sector = sectorWith("stationed-system", stationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionGivesAHiddenStationedMarketTheStationPointAtTheHiddenRate() {
            // A hidden stationed market gets the station weight times the hidden rate on
            // its token rating of 1, at full stability: (1 + 0.5) size points.
            var sector = sectorWith("hidden-fortress-system",
                    hiddenStationedMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().withStationWeighting().build());

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
                    sector, onlySystem(sector), rules().withStationWeighting().build());

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
                    rules().withStabilityMaster(false).withStationWeighting()
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
                    rules().withStabilityMaster(false).withStationWeighting()
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
                    sector, onlySystem(sector), rules().withColonyWeight(2.0).build());

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
                    rules().withStabilityMaster(false).withColonyWeight(2.0)
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
                    sector, onlySystem(sector), rules().withColonyWeight(0.0).build());

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
                    sector, onlySystem(sector), rules().withPatrolWeighting().build());

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
                    rules().withColonyWeight(0.0).withStabilityMaster(false)
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
                    rules().withColonyWeight(0.0).withPatrolWeighting().build());

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
                    rules().withColonyWeight(0.0).withStabilityMaster(false)
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
                    sector, onlySystem(sector), rules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
            verify(market, never()).getStats();
        }

        @Test
        void readByFactionSkipsPatrolStrengthForAMarketWithoutThePatrolFlag() {
            // The patrol-count stats are also written by hidden raider and pather bases
            // that set no $patrol flag; without a functional patrol HQ the garrison
            // contributes no dominance, so a size-3 colony folds in at its size alone.
            var sector = sectorWith("stat-only-garrison-system",
                    patrolStatOnlyMarket(faction("hegemony"), 3, 2, 1, 0));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), rules().withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
        }
    }

    // A hidden market at the given stability, for pinning that its token rating scales
    // with stability like any other size rating.
    private static MarketAPI hiddenMarketAtStability(FactionAPI faction, int size,
            float stability) {
        return market(faction, size, false, true, false, stability);
    }

    // A visible owned market that owns an attached defensive station - a "station"-tagged
    // connected entity, the holding link vanilla itself reads.
    private static MarketAPI stationedMarket(FactionAPI faction, int size) {
        return withConnectedEntities(visibleMarket(faction, size), stationEntity());
    }

    // A stationed market at the given stability, for pinning the station factor's own
    // low-stability penalty.
    private static MarketAPI stationedMarketAtStability(FactionAPI faction, int size,
            float stability) {
        return withConnectedEntities(marketAtStability(faction, size, stability), stationEntity());
    }

    // A hidden market that owns a station, for pinning that it earns the station point
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
    // stubbed onto its dynamic stats the way vanilla's military industries write them,
    // with the $patrol flag set so it reads as garrisoned by a functional patrol HQ.
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

    // A market carrying the patrol-count stats but no $patrol flag - a hidden raider or
    // pather base, which writes the tier counts without a functional patrol HQ. Pins that
    // the patrol contribution gates on the flag, not the raw counts.
    private static MarketAPI patrolStatOnlyMarket(FactionAPI faction, int size, int small,
            int medium, int large) {
        return withPatrolStats(visibleMarket(faction, size), small, medium, large);
    }

    // Stubs the market's connected entities - the holding link the station scan reads -
    // to the given entities.
    private static MarketAPI withConnectedEntities(MarketAPI market,
            SectorEntityToken... entities) {
        when(market.getConnectedEntities()).thenReturn(Set.of(entities));
        return market;
    }

    // A garrisoned market: the patrol-tier stats a patrol HQ writes plus the $patrol flag
    // it sets, the pair the gated patrol contribution now requires together.
    private static MarketAPI withPatrols(MarketAPI market, int small, int medium, int large) {
        return withPatrolFlag(withPatrolStats(market, small, medium, large));
    }

    // Stubs the market's dynamic stats to report the given patrol-tier counts, the seam
    // the patrol read walks (getStats -> getDynamic -> getMod(tier).computeEffective).
    // Stats only: a raider base writes these without the $patrol flag.
    private static MarketAPI withPatrolStats(MarketAPI market, int small, int medium, int large) {
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

    // Stubs the market's $patrol flag on, the signal a functional patrol HQ sets and the
    // gate the patrol contribution requires alongside the patrol-count stats.
    private static MarketAPI withPatrolFlag(MarketAPI market) {
        var memoryMock = mock(MemoryAPI.class);
        when(memoryMock.getBoolean(MemFlags.MARKET_PATROL)).thenReturn(true);
        when(market.getMemoryWithoutUpdate()).thenReturn(memoryMock);
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

}
