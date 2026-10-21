package kmu.maplayers.politicalmap.dominance.weighting;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.entities.EntityMapIcon;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.HALF_STABILITY;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.NO_STABILITY;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHiddenMarketAtStability;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildMarketAtStability;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.STATION_NAME;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildFortifiedColonyAtStability;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildHiddenStationedMarket;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildMarketWithOptedOutStation;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildPatrolMarket;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildPatrolMarketAtStability;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildPatrolStatOnlyMarket;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildRules;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildStationSitedMarket;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildStationedMarket;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildStationedMarketAtStability;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildStationedMarketWithMarkedStation;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.withMapIcon;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.withName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MarketWeights}: one colony's worth to the dominance rule, factor by factor, and the
 * grid that worth is rounded onto.
 *
 * <p>Each case weighs a single stubbed market directly, so a factor's arithmetic is read apart
 * from the selection and the fold that decide which colonies are weighed at all. Expectations
 * are stated in size points, the unit each factor reports in.
 */
class MarketWeightsTests {

    // The station scan reads Misc, whose static initialiser reads Global.getSettings(), so the
    // settings proxy must be in place before the first scan loads the class.
    @BeforeEach
    void installSettings() {

        StarsectorSettingsFake.installSettings();
    }

    @AfterEach
    void clearSettings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class RoundToWeight {

        @Test
        void putsOneSizePointAtAThousandWeightUnits() {

            assertThat(MarketWeights.roundToWeight(3.5))
                .isEqualTo(3500);
        }

        @Test
        void roundsAWorthBelowHalfAUnitDown() {

            assertThat(MarketWeights.roundToWeight(0.0004))
                .isEqualTo(0);
        }

        @Test
        void roundsAWorthAboveHalfAUnitUp() {

            assertThat(MarketWeights.roundToWeight(0.0006))
                .isEqualTo(1);
        }
    }

    @Nested
    class ReadBreakdown {

        @Test
        void scalesColonySizeByStabilityAtFullPenalty() {

            // The colony penalty defaults to a full collapse, so a size-4 market at stability 5
            // keeps half its size.
            var market = buildMarketAtStability(buildFaction("hegemony"), 4, HALF_STABILITY);
            var baseSize = MarketWeights.readBreakdown(market, buildRules().build()).baseSize();

            assertThat(baseSize.contribution())
                .isEqualTo(2.0);
            assertThat(baseSize.stabilityPenaltyFraction())
                .isEqualTo(0.5);
        }

        @Test
        void ignoresStabilityWhenTheMasterIsOff() {

            // With the master toggle off, a destabilised market keeps its full size regardless of
            // any penalty - the raw-size dominance the toggle opts back into.
            var market = buildMarketAtStability(buildFaction("hegemony"), 4, HALF_STABILITY);

            var baseSize = MarketWeights.readBreakdown(
                    market,
                    buildRules().withStabilityMaster(false).build())
                .baseSize();

            assertThat(baseSize.contribution())
                .isEqualTo(4.0);
        }

        @Test
        void keepsPartOfColonyWeightAtZeroStabilityUnderAPartialPenalty() {

            // A half colony penalty leaves half the colony's worth at 0 stability: 4 x (1 - 0.5).
            var market = buildMarketAtStability(buildFaction("hegemony"), 4, NO_STABILITY);

            var baseSize = MarketWeights.readBreakdown(
                    market,
                    buildRules().withNormalPenalty(0.5).build())
                .baseSize();

            assertThat(baseSize.contribution())
                .isEqualTo(2.0);
            assertThat(baseSize.stabilityPenaltyFraction())
                .isEqualTo(0.5);
        }

        @Test
        void clampsStabilityIntoTheVanillaBand() {

            // A modded market can report stability above 10; the fraction clamps to full so a
            // market never outweighs its own size.
            var market = buildMarketAtStability(buildFaction("hegemony"), 3, 12.0f);
            var baseSize = MarketWeights.readBreakdown(market, buildRules().build()).baseSize();

            assertThat(baseSize.contribution())
                .isEqualTo(3.0);
        }

        @Test
        void ratesAHiddenMarketAtItsFixedTokenByDefault() {

            // A hidden market's real size and the token it counts as part company under Fixed
            // scaling, so the breakdown carries both - the pair that explains why a large secret
            // base weighs almost nothing.
            var market = buildHiddenMarket(buildFaction("hegemony"), 5);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().build());

            assertThat(breakdown.isHiddenMarket())
                .isTrue();
            assertThat(breakdown.baseSize().rawMarketSize())
                .isEqualTo(5);
            assertThat(breakdown.baseSize().sizeRating())
                .isEqualTo(1.0);
            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(1.0);
        }

        @Test
        void scalesTheFixedHiddenMarketTokenByStability() {

            // The fixed token is a size rating like any other, so the colony penalty scales it
            // too: a destabilised hidden market marks less presence.
            var market = buildHiddenMarketAtStability(buildFaction("hegemony"), 5, HALF_STABILITY);
            var baseSize = MarketWeights.readBreakdown(market, buildRules().build()).baseSize();

            assertThat(baseSize.contribution())
                .isEqualTo(0.5);
        }

        @Test
        void ratesAHiddenMarketByRealSizeUnderNormalScaling() {

            var market = buildHiddenMarket(buildFaction("hegemony"), 5);

            var baseSize = MarketWeights.readBreakdown(
                    market,
                    buildRules().withHiddenMarketScaling(HiddenMarketScalingChoice.NORMAL).build())
                .baseSize();

            assertThat(baseSize.sizeRating())
                .isEqualTo(5.0);
            assertThat(baseSize.contribution())
                .isEqualTo(5.0);
        }

        @Test
        void usesTheConfiguredFixedHiddenMarketWeight() {

            // The fixed hidden-market weight is the token size a hidden market of any real size
            // is rated at.
            var market = buildHiddenMarket(buildFaction("hegemony"), 5);

            var baseSize = MarketWeights.readBreakdown(
                    market,
                    buildRules().withHiddenMarketFixedWeight(2.0).build())
                .baseSize();

            assertThat(baseSize.sizeRating())
                .isEqualTo(2.0);
            assertThat(baseSize.contribution())
                .isEqualTo(2.0);
        }

        @Test
        void scalesTheFixedHiddenMarketTokenByTheColonyWeight() {

            // The colony-size weight scales a hidden market's fixed token of 1 the same way as
            // any base size: 1 x 2.
            var market = buildHiddenMarket(buildFaction("hegemony"), 5);

            var baseSize = MarketWeights.readBreakdown(
                    market,
                    buildRules().withColonyWeight(2.0).build())
                .baseSize();

            assertThat(baseSize.sizeRating())
                .isEqualTo(1.0);
            assertThat(baseSize.contribution())
                .isEqualTo(2.0);
        }

        @Test
        void scalesVisibleMarketSizeByTheColonyWeight() {

            // The colony-size weight multiplies a visible market's raw size: 4 x 2.
            var market = buildVisibleMarket(buildFaction("hegemony"), 4);

            var baseSize = MarketWeights.readBreakdown(
                    market,
                    buildRules().withColonyWeight(2.0).build())
                .baseSize();

            assertThat(baseSize.sizeRating())
                .isEqualTo(4.0);
            assertThat(baseSize.contribution())
                .isEqualTo(8.0);
        }

        @Test
        void addsTheStationWeightForAnAttachedStation() {

            // A stationed size-5 market at full stability is worth the station weight of 1 on
            // top of its size.
            var market = buildStationedMarket(buildFaction("hegemony"), 5);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withStationWeighting().build());

            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(5.0);
            assertThat(breakdown.station())
                .isPresent();
            assertThat(breakdown.station().get().contribution())
                .isEqualTo(1.0);
        }

        @Test
        void keepsHalfTheStationBonusAtZeroStabilityByDefault() {

            // Isolating the station factor (colony weight zeroed), the station bonus keeps half
            // its worth at 0 stability under its default half penalty: 1 x (1 - 0.5).
            var market = buildStationedMarketAtStability(buildFaction("hegemony"), 5, NO_STABILITY);

            var station = MarketWeights.readBreakdown(
                    market,
                    buildRules().withColonyWeight(0.0).withStationWeighting().build())
                .station();

            assertThat(station)
                .isPresent();
            assertThat(station.get().stabilityPenaltyFraction())
                .isEqualTo(0.5);
            assertThat(station.get().contribution())
                .isEqualTo(0.5);
        }

        @Test
        void cutsAHiddenMarketsStationByTheHiddenRate() {

            // A hidden stationed market earns the station weight times the hidden rate on top of
            // its token rating of 1. The rate is stated as the share it removes, matching the way
            // the stability cut reads, so both of the station's cuts read one way round.
            var market = buildHiddenStationedMarket(buildFaction("hegemony"), 5);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withStationWeighting().build());

            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(1.0);
            assertThat(breakdown.station())
                .isPresent();
            assertThat(breakdown.station().get().hiddenMarketPenaltyFraction())
                .isEqualTo(0.5);
            assertThat(breakdown.station().get().contribution())
                .isEqualTo(0.5);
        }

        @Test
        void scalesTheStationBonusByTheStationWeight() {

            // The station weight sets how many size points a station is worth.
            var market = buildStationedMarket(buildFaction("hegemony"), 4);

            var station = MarketWeights.readBreakdown(
                    market,
                    buildRules()
                        .withStabilityMaster(false)
                        .withStationWeighting()
                        .withStationWeight(2.0)
                        .build())
                .station();

            assertThat(station)
                .isPresent();
            assertThat(station.get().weight())
                .isEqualTo(2.0);
            assertThat(station.get().contribution())
                .isEqualTo(2.0);
        }

        @Test
        void appliesTheColonyWeightToSizeButNotTheStationPoint() {

            // The weight multiplies base size only; the station point is added unweighted:
            // (4 x 2) + 1, not (4 + 1) x 2.
            var market = buildStationedMarket(buildFaction("hegemony"), 4);

            var breakdown = MarketWeights.readBreakdown(
                market,
                buildRules()
                    .withStabilityMaster(false)
                    .withColonyWeight(2.0)
                    .withStationWeighting()
                    .build());

            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(8.0);
            assertThat(breakdown.station().get().contribution())
                .isEqualTo(1.0);
        }

        @Test
        void carriesNoStationPartWhenStationWeightingIsOff() {

            // The player has the factor switched off, so no station moved this market's number
            // even though it owns one.
            var market = buildStationedMarket(buildFaction("hegemony"), 5);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().build());

            assertThat(breakdown.station())
                .isEmpty();
            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(5.0);
        }

        @Test
        void carriesNoStationPartForAMarketWithoutAStation() {

            // An absent part means the factor never ran, which is a different answer from a factor
            // that ran and contributed nothing.
            var market = buildVisibleMarket(buildFaction("hegemony"), 4);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withStationWeighting().build());

            assertThat(breakdown.station())
                .isEmpty();
        }

        @Test
        void carriesNoStationPartForAStationTaggedNoOrbitalStation() {

            // A "station"-tagged connected entity opted out via NO_ORBITAL_STATION is not a
            // market's orbital station, so it earns no bonus.
            var market = buildMarketWithOptedOutStation(buildFaction("hegemony"), 5);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withStationWeighting().build());

            assertThat(breakdown.station())
                .isEmpty();
            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(5.0);
        }

        @Test
        void carriesNoStationPartForAStationSitedMarket() {

            // A market sited on a station is connected to its own primary entity, which is
            // "station"-tagged for what the place is. Without the fleet an orbital-station industry
            // raises it defends nothing, so the market is not fortified by itself and the box has
            // no station line to label it with.
            var market = buildStationSitedMarket(buildFaction("hegemony"), 4);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withStationWeighting().build());

            assertThat(breakdown.station())
                .isEmpty();
            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(4.0);
        }

        @Test
        void skipsTheStationScanWhenTheStationWeightIsZero() {

            // A zero station weight can add nothing, so the connected-entity station scan is
            // skipped and the stationed market is worth its size alone.
            var market = buildStationedMarket(buildFaction("hegemony"), 5);

            var breakdown = MarketWeights.readBreakdown(
                market,
                buildRules()
                    .withStabilityMaster(false)
                    .withStationWeighting()
                    .withStationWeight(0.0)
                    .build());

            assertThat(breakdown.station())
                .isEmpty();
            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(5.0);

            verify(market, never())
                .getConnectedEntities();
        }

        @Test
        void reportsNoPenaltyWhenTheStabilityMasterIsOff() {

            // With the master toggle off a collapsed colony keeps every factor whole, so the parts
            // report nothing taken rather than the penalty that was not applied.
            var market = buildStationedMarketAtStability(buildFaction("hegemony"), 4, NO_STABILITY);

            var breakdown = MarketWeights.readBreakdown(
                market,
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

        @Test
        void addsPatrolStrengthWhenPatrolWeightingIsOn() {

            // Each tier folds in at its own weight: 2 small x 0.25 and 1 medium x 0.5, on top of the
            // size-3 colony, at full stability.
            var market = buildPatrolMarket(buildFaction("hegemony"), 3, 2, 1, 0);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withPatrolWeighting().build());

            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(3.0);
            assertThat(breakdown.patrols())
                .isPresent();
            assertThat(breakdown.patrols().get().small())
                .isEqualTo(new PatrolTierFactor(2, 0.25, 0.5));
            assertThat(breakdown.patrols().get().medium())
                .isEqualTo(new PatrolTierFactor(1, 0.5, 0.5));
            assertThat(breakdown.patrols().get().large())
                .isEqualTo(new PatrolTierFactor(0, 1.0, 0.0));
        }

        @Test
        void weighsPatrolTiersByTheirOwnWeights() {

            // Isolating the patrol factor (colony weight zeroed, master off): two large patrols at
            // the default large weight of 1.
            var market = buildPatrolMarket(buildFaction("hegemony"), 5, 0, 0, 2);

            var patrols = MarketWeights.readBreakdown(
                    market,
                    buildRules()
                        .withColonyWeight(0.0)
                        .withStabilityMaster(false)
                        .withPatrolWeighting()
                        .build())
                .patrols();

            assertThat(patrols)
                .isPresent();
            assertThat(patrols.get().large())
                .isEqualTo(new PatrolTierFactor(2, 1.0, 2.0));
        }

        @Test
        void cutsPatrolStrengthByThePatrolPenaltyAtZeroStability() {

            // Two medium patrols worth 0.5 each keep half their worth at 0 stability under the
            // default half patrol penalty: 2 x 0.5 x 0.5.
            var market = buildPatrolMarketAtStability(buildFaction("hegemony"), 5, 0, 2, 0, NO_STABILITY);

            var patrols = MarketWeights.readBreakdown(
                    market,
                    buildRules().withColonyWeight(0.0).withPatrolWeighting().build())
                .patrols();

            assertThat(patrols)
                .isPresent();
            assertThat(patrols.get().medium())
                .isEqualTo(new PatrolTierFactor(2, 0.5, 0.5));
            assertThat(patrols.get().stabilityPenaltyFraction())
                .isEqualTo(0.5);
        }

        @Test
        void keepsPatrolStrengthFullWhenTheStabilityMasterIsOff() {

            // The same colony with the master toggle off keeps its full patrol worth at 0
            // stability: 2 x 0.5.
            var market = buildPatrolMarketAtStability(buildFaction("hegemony"), 5, 0, 2, 0, NO_STABILITY);

            var patrols = MarketWeights.readBreakdown(
                    market,
                    buildRules()
                        .withColonyWeight(0.0)
                        .withStabilityMaster(false)
                        .withPatrolWeighting()
                        .build())
                .patrols();

            assertThat(patrols)
                .isPresent();
            assertThat(patrols.get().medium())
                .isEqualTo(new PatrolTierFactor(2, 0.5, 1.0));
            assertThat(patrols.get().stabilityPenaltyFraction())
                .isZero();
        }

        @Test
        void doesNotReadPatrolStatsWhenPatrolWeightingIsOff() {

            // With patrols off the economy patrol read is skipped, so the market's stats are never
            // touched.
            var market = buildVisibleMarket(buildFaction("hegemony"), 5);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().build());

            assertThat(breakdown.patrols())
                .isEmpty();

            verify(market, never())
                .getStats();
        }

        @Test
        void carriesNoPatrolPartForAMarketWithoutThePatrolFlag() {

            // The patrol-count stats are also written by hidden raider and pather bases that set no
            // $patrol flag. Without a functional patrol HQ the factor never runs, so the patrols
            // add nothing and the breakdown says so.
            var market = buildPatrolStatOnlyMarket(buildFaction("hegemony"), 3, 2, 1, 0);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withPatrolWeighting().build());

            assertThat(breakdown.patrols())
                .isEmpty();
            assertThat(breakdown.baseSize().contribution())
                .isEqualTo(3.0);
        }

        @Test
        void cutsNothingFromAMarketWhoseFactorsHoldNothing() {

            // With every factor zeroed there is nothing for stability to scale, so the scaling is
            // skipped: the parts report no penalty, and the one stability read is the one the
            // breakdown records as a fact about the colony.
            var market = buildMarketAtStability(buildFaction("hegemony"), 5, NO_STABILITY);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withColonyWeight(0.0).build());

            assertThat(breakdown.baseSize().contribution())
                .isZero();
            assertThat(breakdown.baseSize().stabilityPenaltyFraction())
                .isZero();
            assertThat(breakdown.marketStability())
                .isZero();

            verify(market, times(1))
                .getStabilityValue();
        }

        @Test
        void carriesTheColonysOwnStabilityReading() {

            // Every penalty in the breakdown is derived from this one reading, so it travels with
            // them: a box showing three cuts and no cause explains nothing.
            var market = buildMarketAtStability(buildFaction("hegemony"), 4, HALF_STABILITY);

            assertThat(MarketWeights.readBreakdown(market, buildRules().build()).marketStability())
                .isEqualTo(5.0);
        }

        @Test
        void namesTheMarketEachSetOfPartsBelongsTo() {

            // The tooltip lists a bloc's markets by name, so the breakdown carries the colony's own
            // name rather than leaving the caller to re-read the economy.
            var breakdown = MarketWeights.readBreakdown(buildFortifiedColony(), buildFortifiedColonyRules());

            assertThat(breakdown.marketNameplate().displayName())
                .isEqualTo("Chicomoztoc");
            assertThat(breakdown.isHiddenMarket())
                .isFalse();
        }

        @Test
        void recordsThatAColonySitsOnAStation() {

            // A colony with no planet under it is one, and the box naming its defending station
            // needs to know: only there can the colony and that station arrive under the same name.
            var breakdown = MarketWeights.readBreakdown(buildFortifiedColony(), buildFortifiedColonyRules());

            assertThat(breakdown.isStationMarket())
                .isTrue();
        }

        @Test
        void recordsThatAColonySitsOnAPlanet() {

            // The economy answers a planet entity for a planet colony, which is also what the
            // dominance tie-break reads - so the two cannot part company over what a colony sits on.
            var breakdown = MarketWeights.readBreakdown(
                withPlanet(buildFortifiedColony()),
                buildFortifiedColonyRules());

            assertThat(breakdown.isStationMarket())
                .isFalse();
        }

        @Test
        void carriesTheBaseSizePartOfAMarket() {

            // The base-size part states the rating that entered the weight, what it was worth
            // after the stability cut, and how much of it that cut took: 4 x (1 - 1.0 x 0.5).
            var baseSize = MarketWeights.readBreakdown(buildFortifiedColony(), buildFortifiedColonyRules())
                .baseSize();

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
        void carriesTheStationPartOfAMarket() {

            // The station part names the station that earned it, so the line the tooltip draws
            // points at something the player can find on the map: 1 x (1 - 0.5 x 0.5).
            var station = MarketWeights.readBreakdown(buildFortifiedColony(), buildFortifiedColonyRules())
                .station();

            assertThat(station)
                .isPresent();
            assertThat(station.get().stationNameplate().displayName())
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
        void carriesEachPatrolTierOfAMarket() {

            // Every tier is stated on its own - its headcount, what one patrol of it is worth, and
            // what it folded in at once the 0.25 patrol cut took its share - so the patrol worth is
            // explicable rather than a single opaque number.
            var patrols = MarketWeights.readBreakdown(buildFortifiedColony(), buildFortifiedColonyRules())
                .patrols();

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
        }

        @Test
        void carriesTheGlyphTheMapMarksTheColonyWith() {

            // The box leads a colony's line with the map's own glyph, and reads it off the breakdown
            // rather than looking the market up a second time - so the icon drawn can only belong to
            // the colony whose weight is stated beside it. The colour travels with the path because
            // vanilla draws a whole family from one sprite and tells the types apart by nothing else.
            var market = withMapIcon(
                buildVisibleMarket(buildFaction("independent"), 4),
                "graphics/icons/station0.png",
                new Color(200, 200, 255));

            assertThat(MarketWeights.readBreakdown(market, buildRules().build()).marketNameplate().mapIcon())
                .contains(new EntityMapIcon(
                    "graphics/icons/station0.png",
                    new Color(200, 200, 255)));
        }

        @Test
        void carriesNoGlyphForAColonyTheMapMarksWithNone() {

            // An entity with no icon spec at all reaches the box as an absence rather than as a path
            // to a sprite that does not exist, which is what lets the line open on its name.
            var market = buildVisibleMarket(buildFaction("hegemony"), 4);

            assertThat(MarketWeights.readBreakdown(market, buildRules().build()).marketNameplate().mapIcon())
                .isEmpty();
        }

        @Test
        void carriesTheGlyphTheMapMarksTheStationWith() {

            // The station line leads with its own glyph, read where the scan answered the token
            // rather than looked up again beside the name, so the icon drawn can only belong to the
            // very station whose bonus is stated by it. The colony is marked too and marked
            // differently, since reading the colony's icon into the station part is the one way this
            // can go wrong and still look right.
            var market = withMapIcon(
                buildStationedMarketWithMarkedStation(
                    buildFaction("hegemony"),
                    4,
                    "graphics/icons/station0.png",
                    new Color(200, 200, 255)),
                "graphics/warroom/icon_planet.png",
                new Color(120, 200, 90));

            var breakdown = MarketWeights.readBreakdown(market, buildRules().withStationWeighting().build());

            assertThat(breakdown.station())
                .isPresent();

            assertThat(breakdown.station().get().stationNameplate().mapIcon())
                .contains(new EntityMapIcon(
                    "graphics/icons/station0.png",
                    new Color(200, 200, 255)));

            assertThat(breakdown.marketNameplate().mapIcon())
                .contains(new EntityMapIcon(
                    "graphics/warroom/icon_planet.png",
                    new Color(120, 200, 90)));
        }

        @Test
        void carriesNoGlyphForAStationTheMapMarksWithNone() {

            // A station with no icon spec at all reaches the box as an absence rather than as a path
            // to a sprite that does not exist, which is what lets its line open on its name.
            var market = buildStationedMarket(buildFaction("hegemony"), 4);
            var breakdown = MarketWeights.readBreakdown(market, buildRules().withStationWeighting().build());

            assertThat(breakdown.station())
                .isPresent();
            assertThat(breakdown.station().get().stationNameplate().mapIcon())
                .isEmpty();
        }

        // The rule the fortress cases read under: the suite's defaults with both the station and
        // the patrol factor switched on, so all three factors run at once.
        private DominanceRules buildFortifiedColonyRules() {

            return buildRules().withStationWeighting().withPatrolWeighting().build();
        }

        // A size-4 fortress at stability 5 fielding two light and one medium patrol.
        private MarketAPI buildFortifiedColony() {

            return withName(
                buildFortifiedColonyAtStability(buildFaction("hegemony"), 4, 2, 1, 0, HALF_STABILITY),
                "Chicomoztoc");
        }
    }

    // Stubs the planet a colony sits on. The plain stubbed market answers none, which is what the
    // economy answers for a colony on a station - so a case names the planet rather than its absence.
    private static MarketAPI withPlanet(MarketAPI market) {

        when(market.getPlanetEntity())
            .thenReturn(mock(PlanetAPI.class));

        return market;
    }
}
