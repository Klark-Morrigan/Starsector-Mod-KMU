package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.colonies.Colony;

import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildAbandonedStationMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.markSystemAsEnteredByPlayer;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsInSystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketsOnSystemEntities;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the two spoiler toggles all the way from the settings read to the surfaces they reach: the
 * colony listing every painting surface composes over, and the whole-sector totals the filter
 * picker offers its options from.
 *
 * <p>Driven through the live {@link MapVisibilityRules#readFromLunaSettings()} rather than a rule
 * written out here, because what these cases are about is the wiring between a toggle and a
 * colony. A case handing the gates down directly would pass whatever the settings read did with
 * them, and that read is the one link in the chain with nothing behind it until now.
 *
 * <p>Every case stands its markets in a star system the player has not been in, which is the only
 * way an unrevealed colony can be posed at all: a colony reads as sighted wherever its containing
 * location is not a star system, so a market left unstubbed answers as though the player had
 * already been to see it and no gate could be shown to hold anything back.
 *
 * <p>Both routes into a gate are exercised beside the toggles, because the toggle is only half of
 * what decides: a gate in force still shows its colony once somebody has seen it, whether that is
 * the player arriving or the people already living there. A suite that moved only the toggles
 * would pass over a map that had stopped revealing anything at all.
 *
 * <p>The picker cases are here rather than beside the stats aggregation because what they are
 * about is the gate reaching it. The selectable set has been fogged since the known projection was
 * introduced, five hops down from the fold, and nothing said so; a toggle the player can move is
 * what makes that observable rather than incidental.
 *
 * <p>The claim count is the one picker metric no case here covers, because no colony rule reaches
 * it - {@link ClaimStatsAggregator} folds the claimant straight off the mechanic. That stays
 * honest only because vanilla excludes a hidden market from a claim outright, which is vanilla's
 * rule rather than this mod's.
 */
final class SpoilerGateIntegrationTest {

    private static final String SYSTEM_ID = "unvisited-system";

    // The three markets each case draws from. Sizes differ only so a reader can tell them apart;
    // nothing here turns on a weight.
    private static final int COLONY_SIZE = 6;
    private static final int BASE_SIZE = 4;
    private static final int DERELICT_SIZE = 3;

    // The faction holding the concealed base, and so the picker option that either surfaces or
    // does not. Its own faction rather than one shared with the open colony, since what each
    // picker case reads back is whether that one holder was offered at all.
    private static final String CONCEALED_HOLDER = "pirates";

    // The faction holding the open colony, whose presence is what settles the system.
    private static final String OPEN_HOLDER = "hegemony";

    // The live settings read, which is unreachable from the test JVM and so stood in for every
    // case. Held for the whole case rather than wrapped around one statement of it, because the
    // read happens several layers down inside the pass the helpers below open.
    //
    // An unstubbed toggle answers false, which is the shipped state - so a case names only the
    // toggle it means to move, and a case that names none is posing a fresh install.
    private MockedStatic<KmuMapLayerSettings> settingsMock;

    @BeforeEach
    void installTheSettingsSeam() {
        settingsMock = Mockito.mockStatic(KmuMapLayerSettings.class);
    }

    @AfterEach
    void clearTheSettingsSeam() {
        settingsMock.close();
    }

    @Nested
    class ReadKnownColoniesIn {

        @Test
        void withholdsADerelictAndAConcealedBaseFromAnUnvisitedEmptySystem() {
            // The shipped state, both gates in force. Nobody has been here and nobody lives here,
            // so neither the hulk nor the base has been seen by anyone the player could have heard
            // it from - and the fog alone would have shown both, neither entity being discoverable.
            var sector = buildUnvisitedSystemHoldingTheGatedPair();

            assertThat(readKnownOwnerIds(sector))
                .isEmpty();
        }

        @Test
        void namesBothOnceAnOpenColonyIsFoundedBesideThem() {
            // The settled route, with the toggles untouched. The hulk and the base are staged
            // unchanged, so what reveals them is the colony's inhabitants rather than anything
            // either stopped being - word of a wreck in orbit travels as far as the people who can
            // see it.
            var sector = buildUnvisitedSystemHoldingTheGatedPair(buildOpenColony());

            assertThat(readKnownOwnerIds(sector))
                .containsExactlyInAnyOrder(OPEN_HOLDER, CONCEALED_HOLDER, Factions.NEUTRAL);
        }

        @Test
        void namesBothOnceThePlayerHasBeenInTheirSystem() {
            // The player's own route, and it reaches both shapes at once: a visit is a sighting of
            // everything standing there, so neither gate has anything left to hold.
            var sector = buildUnvisitedSystemHoldingTheGatedPair();
            markSystemAsEnteredByPlayer(buildOnlySystem(sector));

            assertThat(readKnownOwnerIds(sector))
                .containsExactlyInAnyOrder(CONCEALED_HOLDER, Factions.NEUTRAL);
        }

        @Test
        void namesTheDerelictAloneOnceItsOwnGateIsTurnedOff() {
            // One toggle, one shape. A player asking to see unseen derelicts gets the hulk and not
            // the base beside it, which is what makes these two settings rather than one spoiler
            // switch - and what a transposed reading of them would fail on.
            settingsMock
                .when(KmuMapLayerSettings::shouldShowUnseenAbandonedStations)
                .thenReturn(true);

            var sector = buildUnvisitedSystemHoldingTheGatedPair();

            assertThat(readKnownOwnerIds(sector))
                .containsExactly(Factions.NEUTRAL);
        }

        @Test
        void namesTheConcealedBaseAloneOnceItsOwnGateIsTurnedOff() {

            settingsMock
                .when(KmuMapLayerSettings::shouldShowUnseenHiddenMarkets)
                .thenReturn(true);

            var sector = buildUnvisitedSystemHoldingTheGatedPair();

            assertThat(readKnownOwnerIds(sector))
                .containsExactly(CONCEALED_HOLDER);
        }
    }

    @Nested
    class AggregateDominanceStats {

        @Test
        void withholdsAFactionWhoseOnlyColonyIsAnUnseenConcealedBase() {
            // The picker's selectable set is folded from the known listing five hops down, so a
            // holder the map declines to draw is a holder the picker declines to offer - and
            // contributes to none of the metrics its options are sorted by, there being no entry
            // of its own to carry them.
            var sector = buildUnvisitedSystemHoldingTheGatedPair();

            assertThat(readPickerHolderIds(sector))
                .doesNotContain(CONCEALED_HOLDER);
        }

        @Test
        void offersThatFactionOnceThePlayerHasBeenInItsSystem() {

            var sector = buildUnvisitedSystemHoldingTheGatedPair();
            markSystemAsEnteredByPlayer(buildOnlySystem(sector));

            assertThat(readPickerHolderIds(sector))
                .contains(CONCEALED_HOLDER);
        }

        @Test
        void offersThatFactionOnceTheConcealmentGateIsTurnedOff() {
            // The other way into the same entry, and the one this step adds: the player has been
            // nowhere near the base and has asked to be shown concealed colonies anyway.
            settingsMock
                .when(KmuMapLayerSettings::shouldShowUnseenHiddenMarkets)
                .thenReturn(true);

            var sector = buildUnvisitedSystemHoldingTheGatedPair();

            assertThat(readPickerHolderIds(sector))
                .contains(CONCEALED_HOLDER);
        }
    }

    // A sector of one star system the player has never entered, holding a derelict hulk and a
    // concealed base - the two shapes the gates cover, one each - beside whatever open colonies the
    // caller founds there.
    //
    // The two sets are kept apart because the difference between them is the point: the hulk hangs
    // on one of the system's entities and the rest are registered with the economy, which is how
    // each really turns up. The routine that builds an abandoned station pointedly never registers
    // one, so a fixture that listed it would be posing a colony no sector ever holds.
    private static SectorAPI buildUnvisitedSystemHoldingTheGatedPair(MarketAPI... openColonies) {

        var derelict = buildAbandonedStationMarket(DERELICT_SIZE);
        var economyMarkets = new ArrayList<>(List.of(openColonies));

        economyMarkets.add(buildHiddenMarket(buildFaction(CONCEALED_HOLDER), BASE_SIZE));

        var sector = buildSectorWith(SYSTEM_ID, economyMarkets.toArray(new MarketAPI[0]));
        var system = buildOnlySystem(sector);

        placeMarketsOnSystemEntities(system, derelict);

        // Where each market stands, which is a separate question from which listing found it - and
        // one every market here answers alike. Standing in a system the player has not entered is
        // what makes the gated pair unseen; leave it out and both read as already sighted.
        var standingMarkets = new ArrayList<>(economyMarkets);

        standingMarkets.add(derelict);
        placeMarketsInSystem(system, standingMarkets.toArray(new MarketAPI[0]));

        return sector;
    }

    // An ordinary colony nobody conceals: what makes the system somewhere people live, and so
    // somewhere with inhabitants to have seen the two gated markets beside them.
    private static MarketAPI buildOpenColony() {
        return buildVisibleMarket(buildFaction(OPEN_HOLDER), COLONY_SIZE);
    }

    // Who the map may name in the sector's one system, under the player's live settings.
    private static List<String> readKnownOwnerIds(SectorAPI sector) {

        return buildLivePassOver(sector)
            .readKnownColoniesIn(buildOnlySystem(sector))
            .stream()
            .map(SpoilerGateIntegrationTest::readOwnerId)
            .toList();
    }

    // The blocs the filter picker would offer, whole-sector, under those same live settings.
    private static List<String> readPickerHolderIds(SectorAPI sector) {

        var pass = DominancePass.over(buildLivePassOver(sector), buildStabilityWeightedRules());

        return List.copyOf(DominanceStatsAggregator.aggregateDominanceStats(pass).keySet());
    }

    // The pass every read here is made through: the player's live colony rule, sampled the way a
    // rebuild samples it, over the identity grouping that leaves each faction its own bloc.
    private static HolderPass buildLivePassOver(SectorAPI sector) {

        return HolderPass.over(
            sector,
            MapVisibilityRules.readFromLunaSettings().colonyVisibility(),
            HolderGrouping.identity());
    }

    private static String readOwnerId(Colony colony) {
        return colony.market().getFaction().getId();
    }
}
