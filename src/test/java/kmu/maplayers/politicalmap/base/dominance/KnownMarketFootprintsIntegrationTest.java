package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
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
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HALF_STABILITY;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.NO_STABILITY;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildMarketAtStability;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
 *
 * <p>Both halves of that read are covered here, over one economy: the weights the map
 * paints by, and the {@link MarketWeightBreakdown} they are summed over - each factor's
 * rating, cut and contribution, the station it names, the tiers of the garrison behind it,
 * and the absences that say a factor never ran. The two are asserted against the same
 * figures where they meet, since a total that disagreed with its own parts is the failure
 * the one-arithmetic read exists to make impossible. The arithmetic the economy cannot
 * reach - rounding a fractional sum onto the grid - is pinned on hand-built parts in
 * {@link MarketWeightBreakdownTest}.
 */
class KnownMarketFootprintsIntegrationTest {

    // The name every stubbed station answers to, so a breakdown asserting that it named the
    // right entity has something to name.
    private static final String STATION_NAME = "Fort Ludd";

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
            return new DominanceRules(
                stabilityMaster,
                new BaseSizeWeighting(
                    colonySizeWeight,
                    hiddenMarketScaling,
                    hiddenMarketFixedWeight,
                    normalPenalty),
                new StationWeighting(
                    stationOn,
                    stationWeight,
                    stationHiddenRate,
                    stationPenalty),
                new PatrolWeighting(
                    patrolOn,
                    patrolSmall,
                    patrolMedium,
                    patrolLarge,
                    patrolPenalty));
        }
    }

    private static RulesBuilder buildRules() {
        return new RulesBuilder();
    }

    @Nested
    class ReadByFaction {

        @Test
        void readByFactionWeighsAKnownMarketUnderItsFaction() {
            // At full stability a market is worth its whole size on the weight grid:
            // size 5 -> 5 grid units.
            var sector = buildSectorWith("owned-system", buildVisibleMarket(buildFaction("hegemony"), 5));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints)
                .containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionBanksOneColonyOnceWhenTwoMarketsShareItsEntity() {
            // A mod that supersedes a market by adding its own beside vanilla's leaves two
            // markets on one station. Summing both would read the owner as holding 3 + 5;
            // only the larger stands for the place, so the weight is size 5 -> 5 grid units.
            var independent = buildFaction("independent");
            var supersededMarket = buildVisibleMarket(independent, 3);
            var supersedingMarket = buildVisibleMarket(independent, 5);

            placeOnOneEntity(supersededMarket, supersedingMarket);

            var sector = buildSectorWith("academy-system", supersededMarket, supersedingMarket);
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints.get("independent").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionKeepsBothOwnersWhenTheirMarketsShareOneEntity() {
            // Resolving per place alone would drop whichever owner lost the size contest,
            // erasing a faction's only foothold in the system rather than deduplicating it.
            var hegemonyMarket = buildVisibleMarket(buildFaction("hegemony"), 3);
            var pirateMarket = buildVisibleMarket(buildFaction("pirates"), 5);

            placeOnOneEntity(hegemonyMarket, pirateMarket);

            var sector = buildSectorWith("contested-station-system", hegemonyMarket, pirateMarket);
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints)
                .containsOnlyKeys("hegemony", "pirates");
            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
            assertThat(footprints.get("pirates").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesColonySizeByStabilityAtFullPenalty() {
            // The colony penalty defaults to a full collapse, so a size-4 market at
            // stability 5 contributes half its size.
            var sector = buildSectorWith(
                "shaky-system",
                buildMarketAtStability(buildFaction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionIgnoresStabilityWhenTheMasterIsOff() {
            // With the master toggle off, a destabilised market folds in at its full
            // size regardless of any penalty - the raw-size dominance the toggle
            // opts back into.
            var sector = buildSectorWith(
                "shaky-system",
                buildMarketAtStability(buildFaction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withStabilityMaster(false).build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(4 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionZeroStabilityAtFullPenaltyMarksPresenceAtNoWeight() {
            // At a full colony penalty a colony at 0 stability is worth nothing to
            // dominance, but it is still a known colony: the faction keeps its
            // footprint entry, so presence (and painting an unopposed system) holds.
            var sector = buildSectorWith(
                "collapsed-system",
                buildMarketAtStability(buildFaction("hegemony"), 5, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints)
                .containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                .isZero();
        }

        @Test
        void readByFactionKeepsPartOfColonyWeightAtZeroStabilityUnderAPartialPenalty() {
            // A half colony penalty leaves half the colony's weight at 0 stability: a
            // size-4 market -> 4 * (1 - 0.5) = 2 grid units.
            var sector = buildSectorWith(
                "shaky-system",
                buildMarketAtStability(buildFaction("hegemony"), 4, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withNormalPenalty(0.5).build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionClampsStabilityIntoTheVanillaBand() {
            // A modded market can report stability above 10; the fraction clamps to
            // full so a market never outweighs its own size.
            var sector = buildSectorWith(
                "overstable-system",
                buildMarketAtStability(buildFaction("hegemony"), 3, 12.0f));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsConditionOnlyMarket() {
            // A bare rock's condition-only placeholder is no colony, so it folds into
            // no footprint.
            var sector = buildSectorWith(
                "bare-system",
                buildMarket(buildFaction("hegemony"), 6, true, false, false, FULL_STABILITY));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector,
                    buildOnlySystem(sector),
                    buildRules().build()))
                .isEmpty();
        }

        @Test
        void readByFactionCountsHiddenMarketAtItsFixedWeightByDefault() {
            // A hidden market folds in at its fixed token weight of 1 rather than its
            // real size 5, so it flags presence without skewing dominance.
            var sector = buildSectorWith("hidden-system", buildHiddenMarket(buildFaction("hegemony"), 5));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheFixedHiddenMarketTokenByStability() {
            // The fixed token is a size rating like any other, so the colony penalty
            // scales it too: a destabilised hidden market marks less presence.
            var sector = buildSectorWith(
                "hidden-shaky-system",
                buildHiddenMarketAtStability(buildFaction("hegemony"), 5, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionCountsHiddenMarketByRealSizeUnderNormalScaling() {
            // Under Normal scaling a hidden market counts by its real size like any
            // colony: size 5 -> 5 grid units.
            var sector = buildSectorWith("hidden-system", buildHiddenMarket(buildFaction("hegemony"), 5));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withHiddenMarketScaling(HiddenMarketScalingChoice.NORMAL).build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionUsesTheConfiguredFixedHiddenMarketWeight() {
            // The fixed hidden-market weight sets the token size a hidden market folds in
            // at: at weight 2 a hidden market of any real size -> 2 grid units.
            var sector = buildSectorWith("hidden-system", buildHiddenMarket(buildFaction("hegemony"), 5));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withHiddenMarketFixedWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesTheFixedHiddenMarketTokenByTheColonyWeight() {
            // The colony-size weight scales a hidden market's fixed token of 1 the same
            // way as any base size: at weight 2, 2 grid units.
            var sector = buildSectorWith(
                "weighted-hidden-system",
                buildHiddenMarket(buildFaction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withColonyWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionExcludesUndiscoveredStation() {
            // An undiscovered hidden market on a still-discoverable entity fails
            // the known-market gate, so it folds into no footprint.
            var sector = buildSectorWith(
                "undiscovered-system",
                buildUndiscoveredHiddenMarket(buildFaction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector,
                    buildOnlySystem(sector),
                    buildRules().build()))
                .isEmpty();
        }

        @Test
        void readByFactionIncludesAnUndiscoveredStationWhenIncludingUndiscoveredMarkets() {
            // The show-all-factions dev reveal drops the known-to-player gate, so an
            // undiscovered hidden market folds in at its fixed token size of 1 rather
            // than being skipped as it is under the normal filter.
            var sector = buildSectorWith(
                "undiscovered-system",
                buildUndiscoveredHiddenMarket(buildFaction("knights_of_selkie"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build(),
                true);

            assertThat(footprints)
                .containsOnlyKeys("knights_of_selkie");
            assertThat(footprints.get("knights_of_selkie").totalWeight())
                .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionAddsTheStationWeightForAnAttachedStation() {
            // A stationed size-5 market at full stability is worth the station weight
            // more than its size alone: (5 + 1) grid units.
            var sector = buildSectorWith("stationed-system", buildStationedMarket(buildFaction("hegemony"), 5));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(6 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionKeepsHalfTheStationBonusAtZeroStabilityByDefault() {
            // Isolating the station factor (colony weight zeroed), the station bonus
            // keeps half its worth at 0 stability under its default half penalty:
            // 1 * (1 - 0.5) -> half a grid unit.
            var sector = buildSectorWith(
                "shaky-fortress-system",
                buildStationedMarketAtStability(buildFaction("hegemony"), 5, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withColonyWeight(0.0).withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionAddsTheStationBonusWithoutStabilityWeighting() {
            // With the master toggle off but station weighting on, the market folds in
            // at its full size plus the flat station weight: (4 + 1) grid units.
            var sector = buildSectorWith("stationed-system", buildStationedMarket(buildFaction("hegemony"), 4));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withStabilityMaster(false).withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionDropsTheStationBonusWhenStationWeightingIsOff() {
            // A stationed market read under a station-off weighting is worth exactly its
            // size, matching an unstationed colony - the bonus is gone.
            var sector = buildSectorWith("stationed-system", buildStationedMarket(buildFaction("hegemony"), 5));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionGivesAHiddenStationedMarketTheStationPointAtTheHiddenRate() {
            // A hidden stationed market gets the station weight times the hidden rate on
            // its token rating of 1, at full stability: (1 + 0.5) size points.
            var sector = buildSectorWith(
                "hidden-fortress-system",
                buildHiddenStationedMarket(buildFaction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionIgnoresAStationTaggedNoOrbitalStation() {
            // A "station"-tagged connected entity opted out via NO_ORBITAL_STATION is not
            // a market's orbital station, so it earns no bonus: size 5 alone.
            var sector = buildSectorWith(
                "opted-out-system",
                buildMarketWithOptedOutStation(buildFaction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withStationWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsTheStationScanWhenTheStationWeightIsZero() {
            // A zero station weight can add nothing, so the connected-entity station scan
            // is skipped and the stationed market folds in at its size alone.
            var market = buildStationedMarket(buildFaction("hegemony"), 5);
            var sector = buildSectorWith("zero-weight-station-system", market);
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules()
                .withStabilityMaster(false)
                    .withStationWeighting()
                    .withStationWeight(0.0)
                    .build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);

            verify(market, never())
                .getConnectedEntities();
        }

        @Test
        void readByFactionScalesTheStationBonusByTheStationWeight() {
            // The station weight sets how many size points a station is worth: at weight
            // 2 a stationed size-4 market folds in at 4 + 2 -> 6 grid units.
            var sector = buildSectorWith(
                "heavy-station-system",
                buildStationedMarket(buildFaction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules()
                    .withStabilityMaster(false)
                    .withStationWeighting()
                    .withStationWeight(2.0)
                    .build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(6 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesVisibleMarketSizeByTheColonyWeight() {
            // The colony-size weight multiplies a visible market's raw size before the
            // station and patrol bonuses: size 4 at weight 2 -> 8 grid units.
            var sector = buildSectorWith("weighted-system", buildVisibleMarket(buildFaction("hegemony"), 4));
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withColonyWeight(2.0).build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(8 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionAppliesTheColonyWeightToSizeButNotTheStationPoint() {
            // The weight multiplies base size only; the station point is added after,
            // unweighted: (4 * 2) + 1 -> 9 grid units, not (4 + 1) * 2.
            var sector = buildSectorWith(
                "weighted-fortress-system",
                buildStationedMarket(buildFaction("hegemony"), 4));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules()
                    .withStabilityMaster(false)
                    .withColonyWeight(2.0)
                    .withStationWeighting()
                    .build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(9 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsTheStabilityScalingWhenEveryFactorIsZero() {
            // A zero colony-size weight with no station or patrol bonus zeroes every
            // factor before stability, so the scaling is skipped and the one stability
            // read is the one the breakdown records; the market still folds into the
            // footprint (marking presence) at no dominance weight.
            var market = buildVisibleMarket(buildFaction("hegemony"), 5);
            var sector = buildSectorWith("weightless-system", market);
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withColonyWeight(0.0).build());

            assertThat(footprints)
                .containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                .isZero();

            verify(market, times(1))
                .getStabilityValue();
        }

        @Test
        void readByFactionAddsPatrolStrengthWhenPatrolWeightingIsOn() {
            // With patrols on, a colony's small/medium/large counts each fold in at their
            // tier weight: 2 small * 0.25 + 1 medium * 0.5 -> 1 size point on top of the
            // size-3 colony -> 4 grid units at full stability.
            var sector = buildSectorWith(
                "garrison-system",
                buildPatrolMarket(buildFaction("hegemony"), 3, 2, 1, 0));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(4 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionWeighsPatrolTiersByTheirOwnWeights() {
            // Isolating the patrol factor (colony weight zeroed, master off), two large
            // patrols at the default large weight of 1 -> 2 size points.
            var sector = buildSectorWith(
                "heavy-garrison-system",
                buildPatrolMarket(buildFaction("hegemony"), 5, 0, 0, 2));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules()
                    .withColonyWeight(0.0)
                    .withStabilityMaster(false)
                    .withPatrolWeighting()
                    .build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionCutsPatrolStrengthByThePatrolPenaltyAtZeroStability() {
            // Isolating the patrol factor, a medium patrol worth 0.5 at 0 stability keeps
            // half its worth under the default half patrol penalty: 2 * 0.5 * 0.5 -> half
            // a grid unit.
            var sector = buildSectorWith(
                "shaky-garrison-system",
                buildPatrolMarketAtStability(buildFaction("hegemony"), 5, 0, 2, 0, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withColonyWeight(0.0).withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionKeepsPatrolStrengthFullWhenTheStabilityMasterIsOff() {
            // The same garrison with the master toggle off keeps its full patrol worth at
            // 0 stability: 2 * 0.5 -> 1 grid unit.
            var sector = buildSectorWith(
                "shaky-garrison-system",
                buildPatrolMarketAtStability(buildFaction("hegemony"), 5, 0, 2, 0, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules()
                    .withColonyWeight(0.0)
                    .withStabilityMaster(false)
                    .withPatrolWeighting()
                    .build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionDoesNotReadPatrolStatsWhenPatrolWeightingIsOff() {
            // With patrols off the economy patrol read is skipped, so the market's stats
            // are never touched and it folds in at its size alone.
            var market = buildVisibleMarket(buildFaction("hegemony"), 5);
            var sector = buildSectorWith("no-patrol-system", market);
            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);

            verify(market, never())
                .getStats();
        }

        @Test
        void readByFactionSkipsPatrolStrengthForAMarketWithoutThePatrolFlag() {
            // The patrol-count stats are also written by hidden raider and pather bases
            // that set no $patrol flag; without a functional patrol HQ the garrison
            // contributes no dominance, so a size-3 colony folds in at its size alone.
            var sector = buildSectorWith(
                "stat-only-garrison-system",
                buildPatrolStatOnlyMarket(buildFaction("hegemony"), 3, 2, 1, 0));

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().withPatrolWeighting().build());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
        }
    }

    @Nested
    class ReadBreakdownByFaction {

        // The one colony the breakdown assertions read: a size-4 fortress at stability 5
        // fielding two light and one medium patrol. Under the station-and-patrol rule the
        // suite builds, its parts come out as
        //   base    4 * (1 - 1.0 * 0.5)        = 2
        //   station 1 * (1 - 0.5 * 0.5)        = 0.75
        //   patrols (2 * 0.25 + 1 * 0.5) * 0.75 = 0.75
        // for 3.5 size points, or 3500 weight units on the dominance grid.
        private static final int FORTRESS_WEIGHT = 7 * DOMINANCE_WEIGHT_SCALE / 2;

        @Test
        void readBreakdownByFactionSumsAMarketsPartsIntoTheWeightTheFootprintFolds() {
            // The scalar weight is the sum over the breakdown, so the parts the tooltip
            // explains and the total the map paints by are one arithmetic, not two.
            var sector = buildFortifiedGarrisonSector();
            var rules = buildFortifiedGarrisonRules();

            var breakdowns = KnownMarketFootprints.readBreakdownByFaction(
                sector,
                buildOnlySystem(sector),
                rules,
                false);

            var footprints = KnownMarketFootprints.readByFaction(
                sector,
                buildOnlySystem(sector),
                rules);

            assertThat(breakdowns)
                .containsOnlyKeys("hegemony");
            assertThat(breakdowns.get("hegemony"))
                .singleElement()
                .extracting(MarketWeightBreakdown::computeTotalWeight)
                .isEqualTo(FORTRESS_WEIGHT);
            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(FORTRESS_WEIGHT);
        }

        @Test
        void readBreakdownByFactionNamesTheMarketEachSetOfPartsBelongsTo() {
            // The tooltip lists a bloc's markets by name, so the breakdown carries the
            // colony's own name rather than leaving the caller to re-read the economy.
            var sector = buildFortifiedGarrisonSector();
            var breakdown = readOnlyBreakdown(sector, buildFortifiedGarrisonRules());

            assertThat(breakdown.marketName())
                .isEqualTo("Chicomoztoc");
            assertThat(breakdown.isHiddenMarket())
                .isFalse();
        }

        @Test
        void readBreakdownByFactionCarriesTheBaseSizePartOfAMarket() {
            // The base-size part states the rating that entered the weight, what it was
            // worth after the stability cut, and how much of it that cut took.
            var sector = buildFortifiedGarrisonSector();
            var baseSize = readOnlyBreakdown(sector, buildFortifiedGarrisonRules()).baseSize();

            assertThat(baseSize.rawMarketSize())
                .isEqualTo(4);
            assertThat(baseSize.sizeRating())
                .isEqualTo(4.0);
            assertThat(baseSize.contribution())
                .isEqualTo(2.0);
            assertThat(baseSize.stabilityPenaltyFraction())
                .isEqualTo(0.5);
        }

        @Test
        void readBreakdownByFactionCarriesTheStationPartOfAMarket() {
            // The station part names the station that earned it, so the line the tooltip
            // draws points at something the player can find on the map.
            var sector = buildFortifiedGarrisonSector();
            var station = readOnlyBreakdown(sector, buildFortifiedGarrisonRules()).station();

            assertThat(station)
                .isPresent();
            assertThat(station.get().stationName())
                .isEqualTo(STATION_NAME);
            assertThat(station.get().weight())
                .isEqualTo(1.0);
            assertThat(station.get().hiddenMarketPenaltyFraction())
                .isEqualTo(0.0);
            assertThat(station.get().stabilityPenaltyFraction())
                .isEqualTo(0.25);
            assertThat(station.get().contribution())
                .isEqualTo(0.75);
        }

        @Test
        void readBreakdownByFactionCarriesEachPatrolTierOfAMarket() {
            // Every tier is stated on its own - its headcount, what one patrol of it is
            // worth, and what it folded in at - so the garrison's worth is explicable
            // rather than a single opaque number.
            var sector = buildFortifiedGarrisonSector();
            var patrols = readOnlyBreakdown(sector, buildFortifiedGarrisonRules()).patrols();

            assertThat(patrols)
                .isPresent();
            assertThat(patrols.get().small())
                .isEqualTo(new PatrolTierFactor(2, 0.25, 0.375));
            assertThat(patrols.get().medium())
                .isEqualTo(new PatrolTierFactor(1, 0.5, 0.375));
            assertThat(patrols.get().large())
                .isEqualTo(new PatrolTierFactor(0, 1.0, 0.0));
            assertThat(patrols.get().stabilityPenaltyFraction())
                .isEqualTo(0.25);
            assertThat(patrols.get().computeContribution())
                .isEqualTo(0.75);
        }

        @Test
        void readBreakdownByFactionRatesAHiddenMarketAtItsFixedToken() {
            // A hidden market's real size and the token it counts as part company under
            // Fixed scaling, so the breakdown carries both - the pair that explains why a
            // large secret base weighs almost nothing.
            var sector = buildSectorWith(
                "hidden-system",
                withName(buildHiddenMarket(buildFaction("hegemony"), 5), "Kanta's Den"));

            var breakdown = readOnlyBreakdown(sector, buildRules().build());

            assertThat(breakdown.isHiddenMarket())
                .isTrue();
            assertThat(breakdown.baseSize().rawMarketSize())
                .isEqualTo(5);
            assertThat(breakdown.baseSize().sizeRating())
                .isEqualTo(1.0);
            assertThat(breakdown.computeTotalWeight())
                .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readBreakdownByFactionCutsAHiddenMarketsStationByTheHiddenRate() {
            // The hidden-market rate is stated as the share it removes, matching the way
            // the stability cut reads, so both of the station's cuts read one way round.
            var sector = buildSectorWith(
                "hidden-fortress-system",
                withName(buildHiddenStationedMarket(buildFaction("hegemony"), 5), "Kanta's Den"));

            var station = readOnlyBreakdown(
                    sector,
                    buildRules().withStationWeighting().build())
                .station();

            assertThat(station)
                .isPresent();
            assertThat(station.get().hiddenMarketPenaltyFraction())
                .isEqualTo(0.5);
            assertThat(station.get().contribution())
                .isEqualTo(0.5);
        }

        @Test
        void readBreakdownByFactionCarriesNoStationPartForAMarketWithoutAStation() {
            // An absent part means the factor never ran, which is a different answer from
            // a factor that ran and contributed nothing.
            var sector = buildSectorWith(
                "plain-system",
                withName(buildVisibleMarket(buildFaction("hegemony"), 4), "Jangala"));

            var breakdown = readOnlyBreakdown(sector, buildRules().withStationWeighting().build());

            assertThat(breakdown.station())
                .isEmpty();
        }

        @Test
        void readBreakdownByFactionCarriesNoStationPartWhenStationWeightingIsOff() {
            // The player has the factor switched off, so no station moved this market's
            // number even though it owns one.
            var sector = buildSectorWith(
                "stationed-system",
                withName(buildStationedMarket(buildFaction("hegemony"), 4), "Jangala"));

            var breakdown = readOnlyBreakdown(sector, buildRules().build());

            assertThat(breakdown.station())
                .isEmpty();
        }

        @Test
        void readBreakdownByFactionCarriesNoPatrolPartForAMarketWithoutThePatrolFlag() {
            // A raider base writes the patrol-tier stats without a functional patrol HQ, so
            // the factor never runs for it and the breakdown says so.
            var sector = buildSectorWith(
                "stat-only-garrison-system",
                withName(
                    buildPatrolStatOnlyMarket(buildFaction("hegemony"), 3, 2, 1, 0),
                    "Kanta's Den"));

            var breakdown = readOnlyBreakdown(sector, buildRules().withPatrolWeighting().build());

            assertThat(breakdown.patrols())
                .isEmpty();
        }

        @Test
        void readBreakdownByFactionGroupsEachFactionsMarketsUnderItsOwnId() {
            // The tooltip lists a bloc's markets beneath it, so the read hands back each
            // faction's markets rather than one flat list.
            var sector = buildSectorWith(
                "contested-system",
                withName(buildVisibleMarket(buildFaction("hegemony"), 4), "Jangala"),
                withName(buildVisibleMarket(buildFaction("hegemony"), 3), "Culann"),
                withName(buildVisibleMarket(buildFaction("tritachyon"), 5), "Eventide"));

            var breakdowns = KnownMarketFootprints.readBreakdownByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build(),
                false);

            assertThat(breakdowns)
                .containsOnlyKeys("hegemony", "tritachyon");
            assertThat(breakdowns.get("hegemony"))
                .extracting(MarketWeightBreakdown::marketName)
                .containsExactly("Jangala", "Culann");
            assertThat(breakdowns.get("tritachyon"))
                .extracting(MarketWeightBreakdown::marketName)
                .containsExactly("Eventide");
        }

        @Test
        void readBreakdownByFactionSkipsAMarketTheColonyFilterExcludes() {
            // The parts are read over exactly the markets the totals are folded from, so a
            // bare rock's placeholder is absent from both.
            var sector = buildSectorWith(
                "bare-system",
                buildMarket(buildFaction("hegemony"), 6, true, false, false, FULL_STABILITY));

            assertThat(KnownMarketFootprints.readBreakdownByFaction(
                    sector,
                    buildOnlySystem(sector),
                    buildRules().build(),
                    false))
                .isEmpty();
        }

        @Test
        void readBreakdownByFactionExplainsAnUndiscoveredMarketUnderTheDevReveal() {
            // The reveal drops the known-to-player gate for the parts exactly as it does for
            // the totals, so a revealed colony the box paints is a colony the box can explain.
            var sector = buildSectorWith(
                "undiscovered-system",
                withName(
                    buildUndiscoveredHiddenMarket(buildFaction("knights_of_selkie"), 5),
                    "Selkie Station"));

            var breakdowns = KnownMarketFootprints.readBreakdownByFaction(
                sector,
                buildOnlySystem(sector),
                buildRules().build(),
                true);

            assertThat(breakdowns)
                .containsOnlyKeys("knights_of_selkie");
            assertThat(breakdowns.get("knights_of_selkie"))
                .singleElement()
                .extracting(MarketWeightBreakdown::computeTotalWeight)
                .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readBreakdownByFactionCutsNothingFromAMarketWhoseFactorsHoldNothing() {
            // With every factor zeroed there is nothing for stability to scale, so the parts
            // report the nothing they hold and no penalty against it - while still recording
            // the stability itself, which the box states as a fact about the colony rather
            // than only as the cause of a cut. The colony is still listed, marking presence.
            var market = withName(
                buildMarketAtStability(buildFaction("hegemony"), 5, NO_STABILITY),
                "Jangala");
            var sector = buildSectorWith("weightless-system", market);

            var breakdown = readOnlyBreakdown(sector, buildRules().withColonyWeight(0.0).build());

            assertThat(breakdown.baseSize().contribution())
                .isZero();
            assertThat(breakdown.baseSize().stabilityPenaltyFraction())
                .isZero();
            assertThat(breakdown.computeTotalWeight())
                .isZero();
            assertThat(breakdown.marketStability())
                .isZero();

            verify(market, times(1))
                .getStabilityValue();
        }

        @Test
        void readBreakdownByFactionCarriesTheColonysOwnStabilityReading() {
            // Every penalty in the breakdown is derived from this one reading, so it travels
            // with them: a box showing three cuts and no cause explains nothing.
            var sector = buildSectorWith(
                "shaky-system",
                withName(
                    buildMarketAtStability(buildFaction("hegemony"), 4, HALF_STABILITY),
                    "Jangala"));

            assertThat(readOnlyBreakdown(sector, buildRules().build()).marketStability())
                .isEqualTo(5.0);
        }

        @Test
        void readBreakdownByFactionReportsNoPenaltyWhenTheStabilityMasterIsOff() {
            // With the master toggle off a collapsed colony keeps every factor whole, so the
            // parts must report nothing taken rather than the penalty that was not applied.
            var sector = buildSectorWith(
                "shaky-fortress-system",
                withName(
                    buildStationedMarketAtStability(buildFaction("hegemony"), 4, NO_STABILITY),
                    "Jangala"));

            var breakdown = readOnlyBreakdown(
                sector,
                buildRules().withStabilityMaster(false).withStationWeighting().build());

            assertThat(breakdown.baseSize().stabilityPenaltyFraction())
                .isZero();
            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(4.0);
            assertThat(breakdown.station().get().stabilityPenaltyFraction())
                .isZero();
            assertThat(breakdown.station().get().contribution())
                .isEqualTo(1.0);
        }

        // The rule the fortress assertions read under: the suite's defaults with both the
        // station and the patrol factor switched on, so all three factors run at once.
        private DominanceRules buildFortifiedGarrisonRules() {
            return buildRules().withStationWeighting().withPatrolWeighting().build();
        }

        // A one-system sector holding nothing but the fortress the assertions read.
        private SectorAPI buildFortifiedGarrisonSector() {
            return buildSectorWith(
                "fortress-system",
                withName(
                    buildFortifiedGarrisonAtStability(
                        buildFaction("hegemony"),
                        4,
                        2,
                        1,
                        0,
                        HALF_STABILITY),
                    "Chicomoztoc"));
        }

        // The sole market's breakdown, for the assertions that read one colony's parts.
        private MarketWeightBreakdown readOnlyBreakdown(SectorAPI sector, DominanceRules rules) {
            return KnownMarketFootprints.readBreakdownByFaction(
                    sector,
                    buildOnlySystem(sector),
                    rules,
                    false)
                .values()
                .iterator()
                .next()
                .get(0);
        }
    }

    // A visible garrisoned fortress at the given stability: one colony that owns a station
    // and fields patrols at once, so a single read exercises all three weight factors and
    // the stability cut each takes.
    private static MarketAPI buildFortifiedGarrisonAtStability(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large,
            float stability) {
        return withConnectedEntities(
            buildPatrolMarketAtStability(faction, size, small, medium, large, stability),
            buildStationEntity());
    }

    // Stubs a market's display name - the label a breakdown carries its parts under.
    private static MarketAPI withName(MarketAPI market, String name) {

        when(market.getName())
            .thenReturn(name);

        return market;
    }

    // Re-sites the given markets onto one shared entity - the shape a mod makes when it
    // supersedes a market by adding its own beside vanilla's rather than replacing it, and
    // the only way two market objects come to stand for the same place.
    private static void placeOnOneEntity(MarketAPI... markets) {

        var entityMock = mock(SectorEntityToken.class);

        for (var market : markets) {
            when(market.getPrimaryEntity())
                .thenReturn(entityMock);
        }
    }

    // A hidden market at the given stability, for pinning that its token rating scales
    // with stability like any other size rating.
    private static MarketAPI buildHiddenMarketAtStability(
            FactionAPI faction,
            int size,
            float stability) {
        return buildMarket(faction, size, false, true, false, stability);
    }

    // A visible owned market that owns an attached defensive station - a "station"-tagged
    // connected entity, the holding link vanilla itself reads.
    private static MarketAPI buildStationedMarket(FactionAPI faction, int size) {
        return withConnectedEntities(buildVisibleMarket(faction, size), buildStationEntity());
    }

    // A stationed market at the given stability, for pinning the station factor's own
    // low-stability penalty.
    private static MarketAPI buildStationedMarketAtStability(
            FactionAPI faction,
            int size,
            float stability) {
        return withConnectedEntities(buildMarketAtStability(faction, size, stability), buildStationEntity());
    }

    // A hidden market that owns a station, for pinning that it earns the station point
    // at the hidden rate on its token rating.
    private static MarketAPI buildHiddenStationedMarket(FactionAPI faction, int size) {
        return withConnectedEntities(buildHiddenMarket(faction, size), buildStationEntity());
    }

    // A market whose only connected "station"-tagged entity is opted out via
    // NO_ORBITAL_STATION, so it is not the market's orbital station and earns no bonus.
    private static MarketAPI buildMarketWithOptedOutStation(FactionAPI faction, int size) {
        return withConnectedEntities(buildVisibleMarket(faction, size), buildOptedOutStationEntity());
    }

    // A visible owned market that fields the given small/medium/large patrol counts,
    // stubbed onto its dynamic stats the way vanilla's military industries write them,
    // with the $patrol flag set so it reads as garrisoned by a functional patrol HQ.
    private static MarketAPI buildPatrolMarket(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large) {
        return withPatrols(buildVisibleMarket(faction, size), small, medium, large);
    }

    // A patrolling market at the given stability, for pinning the patrol factor's own
    // low-stability penalty.
    private static MarketAPI buildPatrolMarketAtStability(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large,
            float stability) {
        return withPatrols(buildMarketAtStability(faction, size, stability), small, medium, large);
    }

    // A market carrying the patrol-count stats but no $patrol flag - a hidden raider or
    // pather base, which writes the tier counts without a functional patrol HQ. Pins that
    // the patrol contribution gates on the flag, not the raw counts.
    private static MarketAPI buildPatrolStatOnlyMarket(
            FactionAPI faction,
            int size,
            int small,
            int medium,
            int large) {
        return withPatrolStats(buildVisibleMarket(faction, size), small, medium, large);
    }

    // Stubs the market's connected entities - the holding link the station scan reads -
    // to the given entities.
    private static MarketAPI withConnectedEntities(
            MarketAPI market,
            SectorEntityToken... entities) {

        when(market.getConnectedEntities())
            .thenReturn(Set.of(entities));

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

        // Build each tier's mock before the getMod stubbing: buildPatrolMod() stubs a mock
        // of its own, and Mockito rejects a nested when(...) inside a thenReturn(...).
        var smallMod = buildPatrolMod(small);
        var mediumMod = buildPatrolMod(medium);
        var largeMod = buildPatrolMod(large);
        var dynamicMock = mock(DynamicStatsAPI.class);

        when(dynamicMock.getMod(Stats.PATROL_NUM_LIGHT_MOD))
            .thenReturn(smallMod);
        when(dynamicMock.getMod(Stats.PATROL_NUM_MEDIUM_MOD))
            .thenReturn(mediumMod);
        when(dynamicMock.getMod(Stats.PATROL_NUM_HEAVY_MOD))
            .thenReturn(largeMod);

        var statsMock = mock(MutableMarketStatsAPI.class);

        when(statsMock.getDynamic())
            .thenReturn(dynamicMock);

        when(market.getStats())
            .thenReturn(statsMock);

        return market;
    }

    // Stubs the market's $patrol flag on, the signal a functional patrol HQ sets and the
    // gate the patrol contribution requires alongside the patrol-count stats.
    private static MarketAPI withPatrolFlag(MarketAPI market) {

        var memoryMock = mock(MemoryAPI.class);

        when(memoryMock.getBoolean(MemFlags.MARKET_PATROL))
            .thenReturn(true);

        when(market.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);

        return market;
    }

    // A patrol-count mod whose effective value at base 0 is the given count.
    private static StatBonus buildPatrolMod(float effective) {

        var modMock = mock(StatBonus.class);

        when(modMock.computeEffective(0.0f))
            .thenReturn(effective);

        return modMock;
    }

    // A station entity: carries the "station" tag and no opt-out, so the scan counts it
    // as the market's orbital station.
    private static SectorEntityToken buildStationEntity() {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.hasTag(Tags.STATION))
            .thenReturn(true);
        when(entityMock.getName())
            .thenReturn(STATION_NAME);

        return entityMock;
    }

    // A "station"-tagged entity flagged NO_ORBITAL_STATION, vanilla's own opt-out, so
    // the scan skips it.
    private static SectorEntityToken buildOptedOutStationEntity() {

        var entityMock = buildStationEntity();

        when(entityMock.hasTag("NO_ORBITAL_STATION"))
            .thenReturn(true);

        return entityMock;
    }
}
