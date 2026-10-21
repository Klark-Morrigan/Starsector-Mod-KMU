package kmu.maplayers.politicalmap.dominance.weighting;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.entities.EntityMapIcon;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.SystemColonies;
import kmlib.testfixtures.starsector.settings.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Optional;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.buildKnowledgeUnderTheFog;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.buildKnowledgeUnderTheReveal;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.HALF_STABILITY;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.NO_STABILITY;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildConditionOnlyMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildMarketAtStability;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.placeMarketsOnSystemEntities;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildFortifiedColonyAtStability;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.buildRules;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.withMapIcon;
import static kmu.maplayers.politicalmap.dominance.weighting.MarketWeightFixtures.withName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the economy read: {@link KnownMarketFootprints} folding a stubbed
 * system's markets into per-faction footprints through the known-market filter. Exercises the
 * filter (condition-only skipped, the discovery / un-hidden gate and its dev reveal), the
 * per-place resolution of two markets standing on one entity, the count beside the summed
 * weight, and a weightless colony still marking presence.
 *
 * <p>Both halves of that read are covered here, over one economy: the weights the map paints by,
 * and the {@link MarketWeightBreakdown} they are summed over. The two are asserted against the
 * same figure where they meet, since a total that disagreed with its own parts is the failure the
 * one-arithmetic read exists to make impossible. What one colony is worth, factor by factor, is
 * pinned on a single market in {@link MarketWeightsTests}, and rounding a fractional sum onto the
 * grid on hand-built parts in {@link MarketWeightBreakdownTests}.
 *
 * <p>Weights are stated in grid units: a thousand to one size point at full stability.
 *
 * <p>The third read - the colonies present that the economy does not list - is covered over the same
 * economy, and beside what it finds sits what it must not move: the footprints and the standings a
 * system holding one resolves to are asserted to be the ones it resolves to without it, since the
 * whole reason those colonies are read apart is that no mechanic may see them.
 */
class KnownMarketFootprintsIntegrationTests {

    // The unlisted colony every off-economy case poses, identified as the read answers it - vanilla's
    // own deliberately unregistered market, whose stubbed entity carries no icon spec.
    private static final EntityNameplate UNLISTED_ACADEMY =
        EntityNameplate.createUnmarkedNameplate("Galatia Academy");

    // The sector's one system read as the shared colony set, which is what a pass hands the reads
    // below. Posed through the real selection rather than a hand-built set, since what these cases
    // exercise is the weighing of what that rule admits - a set assembled here could admit markets
    // the rule never would and pin arithmetic over colonies no pass could produce.
    private static Colonies readColoniesIn(SectorAPI sector) {
        return SystemColonies.readColoniesIn(sector, buildOnlySystem(sector));
    }

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
    class ReadByFaction {

        @Test
        void weighsAKnownMarketUnderItsFaction() {
            // At full stability a market is worth its whole size on the weight grid: size 5.
            var sector = buildSectorWith("owned-system", buildVisibleMarket(buildFaction("hegemony"), 5));
            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(footprints)
                .containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5000);
        }

        @Test
        void countsEachHeldColonyAndSumsTheirWeights() {
            // Two colonies of one faction standing on two places: the weights sum (5 + 3 size points)
            // and the count reaches 2. The count is what the weights cannot be divided back into -
            // one size-8 colony and two smaller ones can weigh the same without being the same
            // holding.
            var hegemony = buildFaction("hegemony");
            var sector = buildSectorWith(
                "twin-colony-system",
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(hegemony, 3));

            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(8000);
            assertThat(footprints.get("hegemony").marketCount())
                .isEqualTo(2);
        }

        @Test
        void banksOneColonyOnceWhenTwoMarketsShareItsEntity() {
            // A mod that supersedes a market by adding its own beside vanilla's leaves two
            // markets on one station. Summing both would read the owner as holding 3 + 5;
            // only the larger stands for the place, so the weight is size 5 alone.
            var independent = buildFaction("independent");
            var supersededMarket = buildVisibleMarket(independent, 3);
            var supersedingMarket = buildVisibleMarket(independent, 5);

            placeMarketsOnOneEntity(supersededMarket, supersedingMarket);

            var sector = buildSectorWith("academy-system", supersededMarket, supersedingMarket);
            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(footprints.get("independent").totalWeight())
                .isEqualTo(5000);

            // The count follows the same resolution: one place held is one holding, however many
            // markets the mod left standing on the entity.
            assertThat(footprints.get("independent").marketCount())
                .isEqualTo(1);
        }

        @Test
        void keepsBothOwnersWhenTheirMarketsShareOneEntity() {
            // Resolving per place alone would drop whichever owner lost the size contest,
            // erasing a faction's only foothold in the system rather than deduplicating it.
            var hegemonyMarket = buildVisibleMarket(buildFaction("hegemony"), 3);
            var pirateMarket = buildVisibleMarket(buildFaction("pirates"), 5);

            placeMarketsOnOneEntity(hegemonyMarket, pirateMarket);

            var sector = buildSectorWith("contested-station-system", hegemonyMarket, pirateMarket);
            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(footprints)
                .containsOnlyKeys("hegemony", "pirates");
            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(3000);
            assertThat(footprints.get("pirates").totalWeight())
                .isEqualTo(5000);
        }

        @Test
        void zeroStabilityAtFullPenaltyMarksPresenceAtNoWeight() {
            // At a full colony penalty a colony at 0 stability is worth nothing to
            // dominance, but it is still a known colony: the faction keeps its
            // footprint entry, so presence (and painting an unopposed system) holds.
            var sector = buildSectorWith(
                "collapsed-system",
                buildMarketAtStability(buildFaction("hegemony"), 5, NO_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(footprints)
                .containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                .isZero();
        }

        @Test
        void skipsConditionOnlyMarket() {
            // A bare rock's condition-only placeholder is no colony, so it folds into
            // no footprint.
            var sector = buildSectorWith(
                "bare-system",
                buildConditionOnlyMarket(buildFaction("hegemony"), 6));

            assertThat(KnownMarketFootprints.readByFaction(
                    readColoniesIn(sector),
                    buildRules().build(),
                    buildKnowledgeUnderTheFog()))
                .isEmpty();
        }

        @Test
        void excludesUndiscoveredStation() {
            // An undiscovered hidden market on a still-discoverable entity fails
            // the known-market gate, so it folds into no footprint.
            var sector = buildSectorWith(
                "undiscovered-system",
                buildUndiscoveredHiddenMarket(buildFaction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.readByFaction(
                    readColoniesIn(sector),
                    buildRules().build(),
                    buildKnowledgeUnderTheFog()))
                .isEmpty();
        }

        @Test
        void includesAnUndiscoveredStationWhenIncludingUndiscoveredMarkets() {
            // The show-undiscovered-markets dev reveal drops the known-to-player gate, so an
            // undiscovered hidden market folds in at its fixed token size of 1 rather
            // than being skipped as it is under the normal filter.
            var sector = buildSectorWith(
                "undiscovered-system",
                buildUndiscoveredHiddenMarket(buildFaction("knights_of_selkie"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheReveal());

            assertThat(footprints)
                .containsOnlyKeys("knights_of_selkie");
            assertThat(footprints.get("knights_of_selkie").totalWeight())
                .isEqualTo(1000);
        }

        @Test
        void leavesAnUndiscoveredColonyOutOfTheCountBesideAKnownOne() {
            // The count passes the known-to-player filter exactly as the weights do: beside a
            // discovered colony the undiscovered station lifts neither, so the faction reads as
            // holding one place worth 5 size points. The exclusion cases above cannot show this -
            // a faction present through no other colony has no footprint to inspect either way.
            var selkie = buildFaction("knights_of_selkie");
            var sector = buildSectorWith(
                "half-known-system",
                buildVisibleMarket(selkie, 5),
                buildUndiscoveredHiddenMarket(selkie, 5));

            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(footprints.get("knights_of_selkie").totalWeight())
                .isEqualTo(5000);
            assertThat(footprints.get("knights_of_selkie").marketCount())
                .isEqualTo(1);
        }

        @Test
        void countsAnUndiscoveredColonyBesideAKnownOneUnderTheDevReveal() {
            // The dev reveal drops that filter for the count as well: the same two colonies read
            // as two holdings, the hidden one at its fixed token weight (5 + 1 size points), so the
            // count always describes exactly the set of colonies the pass admitted.
            var selkie = buildFaction("knights_of_selkie");
            var sector = buildSectorWith(
                "half-known-system",
                buildVisibleMarket(selkie, 5),
                buildUndiscoveredHiddenMarket(selkie, 5));

            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheReveal());

            assertThat(footprints.get("knights_of_selkie").totalWeight())
                .isEqualTo(6000);
            assertThat(footprints.get("knights_of_selkie").marketCount())
                .isEqualTo(2);
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
        private static final int FORTRESS_WEIGHT = 3500;

        @Test
        void listsOneColonyOnceWhenTwoMarketsShareItsEntity() {
            // The detail box hangs a line under the faction per breakdown, so a place carrying
            // two market objects would be read out to the player as two colonies at one
            // station - the same duplicate the summed weight hides, here with a name on it.
            var independent = buildFaction("independent");
            var supersededMarket = withName(buildVisibleMarket(independent, 3), "Galatia Academy");
            var supersedingMarket = withName(buildVisibleMarket(independent, 5), "Galatia Academy");

            placeMarketsOnOneEntity(supersededMarket, supersedingMarket);

            var sector = buildSectorWith("academy-system", supersededMarket, supersedingMarket);
            var breakdowns = KnownMarketFootprints.readBreakdownByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(breakdowns.get("independent"))
                .singleElement()
                .extracting(MarketWeightBreakdown::computeTotalWeight)
                .isEqualTo(5000);
        }

        @Test
        void sumsAMarketsPartsIntoTheWeightTheFootprintFolds() {
            // The scalar weight is the sum over the breakdown, so the parts the tooltip
            // explains and the total the map paints by are one arithmetic, not two.
            var sector = buildFortifiedColonySector();
            var rules = buildRules().withStationWeighting().withPatrolWeighting().build();

            var breakdowns = KnownMarketFootprints.readBreakdownByFaction(
                readColoniesIn(sector),
                rules,
                buildKnowledgeUnderTheFog());

            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                rules,
                buildKnowledgeUnderTheFog());

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
        void groupsEachFactionsMarketsUnderItsOwnId() {
            // The tooltip lists a bloc's markets beneath it, so the read hands back each
            // faction's markets rather than one flat list.
            var sector = buildSectorWith(
                "contested-system",
                withName(buildVisibleMarket(buildFaction("hegemony"), 4), "Jangala"),
                withName(buildVisibleMarket(buildFaction("hegemony"), 3), "Culann"),
                withName(buildVisibleMarket(buildFaction("tritachyon"), 5), "Eventide"));

            var breakdowns = KnownMarketFootprints.readBreakdownByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(breakdowns)
                .containsOnlyKeys("hegemony", "tritachyon");
            assertThat(breakdowns.get("hegemony"))
                .extracting(breakdown -> breakdown.marketNameplate().displayName())
                .containsExactly("Jangala", "Culann");
            assertThat(breakdowns.get("tritachyon"))
                .extracting(breakdown -> breakdown.marketNameplate().displayName())
                .containsExactly("Eventide");
        }

        @Test
        void skipsAMarketTheColonyFilterExcludes() {
            // The parts are read over exactly the markets the totals are folded from, so a
            // bare rock's placeholder is absent from both.
            var sector = buildSectorWith(
                "bare-system",
                buildConditionOnlyMarket(buildFaction("hegemony"), 6));

            assertThat(KnownMarketFootprints.readBreakdownByFaction(
                    readColoniesIn(sector),
                    buildRules().build(),
                    buildKnowledgeUnderTheFog()))
                .isEmpty();
        }

        @Test
        void explainsAnUndiscoveredMarketUnderTheDevReveal() {
            // The reveal drops the known-to-player gate for the parts exactly as it does for
            // the totals, so a revealed colony the box paints is a colony the box can explain.
            // It is weighed at its fixed token of 1 size point.
            var sector = buildSectorWith(
                "undiscovered-system",
                withName(
                    buildUndiscoveredHiddenMarket(buildFaction("knights_of_selkie"), 5),
                    "Selkie Station"));

            var breakdowns = KnownMarketFootprints.readBreakdownByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheReveal());

            assertThat(breakdowns)
                .containsOnlyKeys("knights_of_selkie");
            assertThat(breakdowns.get("knights_of_selkie"))
                .singleElement()
                .extracting(MarketWeightBreakdown::computeTotalWeight)
                .isEqualTo(1000);
        }

        // A one-system sector holding nothing but the fortress the assertions read.
        private SectorAPI buildFortifiedColonySector() {
            return buildSectorWith(
                "fortress-system",
                withName(
                    buildFortifiedColonyAtStability(
                        buildFaction("hegemony"),
                        4,
                        2,
                        1,
                        0,
                        HALF_STABILITY),
                    "Chicomoztoc"));
        }
    }

    @Nested
    class ReadUnweighedColoniesByFaction {

        @Test
        void namesTheColonyTheEconomyDoesNotList() {
            // Vanilla builds Galatia Academy as a real market on a real station and never registers
            // it, so the weighed walk cannot see it and a box reading that walk alone reports the
            // station the player is looking at as nobody's.
            var independent = buildFaction("independent");
            var academy = withName(buildVisibleMarket(independent, 3), "Galatia Academy");
            var listedColony = withName(buildVisibleMarket(independent, 5), "Ancyra");
            var sector = buildSectorWith("galatia", listedColony);

            placeMarketsOnSystemEntities(buildOnlySystem(sector), listedColony, academy);

            var colonies = KnownMarketFootprints.readUnweighedColoniesByFaction(
                readColoniesIn(sector),
                buildKnowledgeUnderTheFog());

            assertThat(colonies)
                .containsOnlyKeys("independent");
            assertThat(colonies.get("independent"))
                .extracting(UnweighedColony::nameplate)
                .containsExactly(UNLISTED_ACADEMY);
        }

        @Test
        void recordsThatAColonyConcealsItself() {
            // Concealed and unregistered at once is Galatia's shape and Daybreak's, and the account
            // has to say which of the two facts it is calling out. Read here rather than at the box,
            // because the box meets such a colony as a row and never as a market.
            var independent = buildFaction("independent");
            var academy = withName(buildHiddenMarket(independent, 3), "Galatia Academy");
            var sector = buildSectorWith("galatia");

            placeMarketsOnSystemEntities(buildOnlySystem(sector), academy);

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog())
                .get("independent"))
                .extracting(UnweighedColony::isHiddenMarket)
                .containsExactly(true);
        }

        @Test
        void recordsAnOpenColonyAsConcealingNothing() {
            // The other half of the pair, so the flag is read from the market rather than standing
            // at whatever a record's default happens to be.
            var independent = buildFaction("independent");
            var academy = withName(buildVisibleMarket(independent, 3), "Galatia Academy");
            var sector = buildSectorWith("galatia");

            placeMarketsOnSystemEntities(buildOnlySystem(sector), academy);

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog())
                .get("independent"))
                .extracting(UnweighedColony::isHiddenMarket)
                .containsExactly(false);
        }

        @Test
        void carriesTheGlyphTheMapMarksTheColonyWith() {
            // No score above accounts for such a colony, so the map's glyph is the only trace of it
            // the player has beside the name - which makes it the line least able to spare the mark.
            var academy = withMapIcon(
                withName(buildVisibleMarket(buildFaction("independent"), 3), "Galatia Academy"),
                "graphics/icons/station0.png",
                new Color(200, 200, 255));

            var sector = buildSectorWith("galatia");

            placeMarketsOnSystemEntities(buildOnlySystem(sector), academy);

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog())
                .get("independent"))
                .extracting(UnweighedColony::nameplate)
                .containsExactly(new EntityNameplate(
                    "Galatia Academy",
                    Optional.of(new EntityMapIcon(
                        "graphics/icons/station0.png",
                        new Color(200, 200, 255)))));
        }

        @Test
        void keepsAFactionsColoniesInEntityOrder() {
            // The widened read answers in the system's own entity order and this carries it through
            // rather than imposing one of its own - a caller wanting a ranking sorts, and cannot
            // sort back to an order that was never there. Named against the alphabet on purpose, so
            // the case cannot pass on an ordering it did not ask for.
            var independent = buildFaction("independent");
            var sector = buildSectorWith("galatia");

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                withName(buildVisibleMarket(independent, 3), "Tibicena"),
                withName(buildVisibleMarket(independent, 4), "Galatia Academy"));

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog())
                .get("independent"))
                .extracting(UnweighedColony::nameplate)
                .containsExactly(
                    EntityNameplate.createUnmarkedNameplate("Tibicena"),
                    UNLISTED_ACADEMY);
        }

        @Test
        void refusesAMarketThatIsNoColony() {
            // The same filter both walks read: an uninhabited planet's condition-only placeholder is
            // nobody's holding, and one reaching the box would name a colony that does not exist.
            var sector = buildSectorWith("empty-system");

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                withName(
                    buildConditionOnlyMarket(buildFaction("hegemony"), 0),
                    "Barren Placeholder"));

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog()))
                .isEmpty();
        }

        @Test
        void withholdsAnUndiscoveredColonyUntilTheRevealIsOn() {
            // Discovery decides what the box may name, exactly as it does for a weighed colony:
            // undiscovered it is withheld, and the dev reveal states it like anything else.
            var sector = buildSectorWith("hidden-system");

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                withName(
                    buildUndiscoveredHiddenMarket(buildFaction("pirates"), 3),
                    "Selkie Station"));

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog()))
                .isEmpty();
            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheReveal()))
                .containsOnlyKeys("pirates");
        }

        @Test
        void namesOnePlaceOnceWhenTwoMarketsShareIt() {
            // A mod that supersedes a market by adding its own beside vanilla's leaves two market
            // objects on one station. The box hangs a line per colony, so counted twice the place
            // would be read out to the player as two separate holdings at one station.
            var independent = buildFaction("independent");
            var supersededMarket = withName(buildVisibleMarket(independent, 3), "Galatia Academy");
            var supersedingMarket = withName(buildVisibleMarket(independent, 5), "Galatia Academy");

            placeMarketsOnOneEntity(supersededMarket, supersedingMarket);

            var sector = buildSectorWith("galatia");

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                supersededMarket,
                supersedingMarket);

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog())
                .get("independent"))
                .extracting(UnweighedColony::nameplate)
                .containsExactly(UNLISTED_ACADEMY);
        }

        @Test
        void carriesTheColonysOwnId() {
            // The identity a box matches this line against anything else it knows about the same
            // colony - how current its news of it is, say. A display name cannot serve: vanilla
            // names a station colony and its defending station alike.
            var academy = withId(
                withName(buildVisibleMarket(buildFaction("independent"), 3), "Galatia Academy"),
                "galatia_academy");

            var sector = buildSectorWith("galatia");

            placeMarketsOnSystemEntities(buildOnlySystem(sector), academy);

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog())
                .get("independent"))
                .extracting(UnweighedColony::marketId)
                .containsExactly("galatia_academy");
        }

        @Test
        void leavesTheWeighedFootprintsWhereTheyWere() {
            // The whole point of the second walk: an unlisted colony reaches the account and not the
            // pass. Admitted to the weight it would fold in at a nominal size nobody worked out - it
            // has no industries, no conditions and no computed stability - and could hand the system
            // to a faction the game does not. The listed size-5 colony alone is weighed.
            var hegemony = buildFaction("hegemony");
            var listedColony = withName(buildVisibleMarket(hegemony, 5), "Chicomoztoc");
            var sector = buildSectorWith("galatia", listedColony);

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                listedColony,
                withName(buildVisibleMarket(hegemony, 4), "Galatia Academy"));

            var footprints = KnownMarketFootprints.readByFaction(
                readColoniesIn(sector),
                buildRules().build(),
                buildKnowledgeUnderTheFog());

            assertThat(footprints.get("hegemony").totalWeight())
                .isEqualTo(5000);

            // The count is held to the same line as the weight: the unlisted colony does not lift
            // it either, so nothing reading the count can show a holding the pass never weighed.
            assertThat(footprints.get("hegemony").marketCount())
                .isEqualTo(1);
        }

        @Test
        void leavesAFactionHoldingOnlyOneOutOfThePassEntirely() {
            // A faction whose only colony here is unlisted takes no contribution, and that is
            // deliberate: a weight synthesised for it is a weight nobody worked out, and it could
            // move a fill. Naming the faction is a listing's job, answered off who is present
            // rather than off what was weighed.
            var sector = buildSectorWith(
                "galatia",
                withName(buildVisibleMarket(buildFaction("hegemony"), 5), "Chicomoztoc"));

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                withName(buildVisibleMarket(buildFaction("independent"), 3), "Galatia Academy"));

            assertThat(KnownMarketFootprints.readByFaction(
                    readColoniesIn(sector),
                    buildRules().build(),
                    buildKnowledgeUnderTheFog()))
                .containsOnlyKeys("hegemony");
        }

        @Test
        void yieldsNothingForASystemTheEconomyListsWhole() {
            // The ordinary system: every colony present is one the weighed walk already accounts
            // for, so the widened read adds nothing and the box reads exactly as it did.
            var listedColony = withName(
                buildVisibleMarket(buildFaction("hegemony"), 5),
                "Chicomoztoc");
            var sector = buildSectorWith("hegemony-system", listedColony);

            placeMarketsOnSystemEntities(buildOnlySystem(sector), listedColony);

            assertThat(KnownMarketFootprints.readUnweighedColoniesByFaction(
                    readColoniesIn(sector),
                    buildKnowledgeUnderTheFog()))
                .isEmpty();
        }

        @Test
        void takesExactlyWhatTheWeighedReadLeaves() {
            // The two reads divide one set on one fact rather than seeking their markets by two
            // walks of their own. Stated head-on because it is what the division buys: a colony
            // cannot be admitted by both and counted twice, nor refused by both and vanish from a
            // box that is meant to account for the whole system.
            var hegemony = buildFaction("hegemony");
            var listedColony = withName(buildVisibleMarket(hegemony, 5), "Chicomoztoc");
            var colonies = new ArrayList<String>();
            var sector = buildSectorWith("galatia", listedColony);

            placeMarketsOnSystemEntities(
                buildOnlySystem(sector),
                listedColony,
                withName(buildVisibleMarket(hegemony, 4), "Galatia Academy"));

            var systemColonies = readColoniesIn(sector);

            for (var breakdown : KnownMarketFootprints
                    .readBreakdownByFaction(systemColonies, buildRules().build(), buildKnowledgeUnderTheFog())
                    .get("hegemony")) {

                colonies.add(breakdown.marketNameplate().displayName());
            }
            for (var colony : KnownMarketFootprints
                    .readUnweighedColoniesByFaction(systemColonies, buildKnowledgeUnderTheFog())
                    .get("hegemony")) {

                colonies.add(colony.nameplate().displayName());
            }

            // Every colony the projection holds, named once between the two reads.
            assertThat(colonies)
                .containsExactlyInAnyOrder("Chicomoztoc", "Galatia Academy");
        }
    }

    // Stubs the ID the economy knows a colony by, which a plain stubbed market answers null for.
    // Given only where a case reads it, since nothing else about a colony turns on it.
    private static MarketAPI withId(MarketAPI market, String marketId) {

        when(market.getId())
            .thenReturn(marketId);

        return market;
    }

    // Re-sites the given markets onto one shared primary entity, so they stand for the same place -
    // the shape a mod makes when it supersedes a market by adding its own beside vanilla's rather than
    // replacing it, and the only way two market objects come to name one colony.
    private static void placeMarketsOnOneEntity(MarketAPI... markets) {

        var entityMock = mock(SectorEntityToken.class);

        for (var market : markets) {

            when(market.getPrimaryEntity())
                .thenReturn(entityMock);
        }
    }
}
