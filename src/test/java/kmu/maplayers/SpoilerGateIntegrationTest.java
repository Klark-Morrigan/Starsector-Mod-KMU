package kmu.maplayers;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.settings.KmuMapLayerSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static kmu.maplayers.SectorScenarioFixtures.CONCEALED_HOLDER_ID;
import static kmu.maplayers.SectorScenarioFixtures.buildUnvisitedSectorHoldingGatedPair;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.markSystemAsVisitedByPlayer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the Map - Visibility knobs from the settings read to the colony listing every painting
 * surface composes over - the one link in that chain with nothing behind it until they shipped.
 *
 * <p>Driven through the live {@link MapVisibilityRules#readFromLunaSettings()} rather than a rule
 * written out here, because the wiring between a toggle and a colony is the subject. A case handing
 * the gates down directly would pass whatever the settings read did with them.
 *
 * <p>Both routes into a gate are exercised beside the toggles, because a toggle is only half of
 * what decides: a gate in force still shows its colony once somebody has seen it, whether that is
 * the player arriving or the people already living there. A suite that moved only the toggles would
 * pass over a map that had stopped revealing anything at all.
 *
 * <p>The reveals are here on the same grounds, and for one rule beyond it: each toggle on this tab
 * clears the one thing it names and nothing beside it. That is the failure a player cannot
 * diagnose - they turn one thing on and a second thing they never asked about appears with it - and
 * it is invisible to any case that moves one toggle over a colony only that toggle could reach.
 *
 * <p>At {@code kmu.maplayers} rather than beside the rule it drives, because it reads that rule
 * through the political map's pass and fixtures and the layering gate forbids {@code maplayers.base}
 * reaching into the political map. Above both trees is the one place a case can hold the framework's
 * rule against a real colony walk.
 */
final class SpoilerGateIntegrationTest {

    private static final String SYSTEM_ID = "unvisited-system";

    // The faction holding the open colony, whose presence is what settles the system. Its own
    // faction rather than the concealed base's, so a case reads back which of the two was named.
    private static final String OPEN_HOLDER = "hegemony";
    private static final int COLONY_SIZE = 6;

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
            var sector = buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID);

            assertThat(readKnownOwnerIds(sector))
                .isEmpty();
        }

        @Test
        void namesBothOnceAnOpenColonyIsFoundedBesideThem() {
            // The settled route, with the toggles untouched. The hulk and the base are staged
            // unchanged, so what reveals them is the colony's inhabitants rather than anything
            // either stopped being - word of a wreck in orbit travels as far as the people who can
            // see it.
            var sector = buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID, buildOpenColony());

            assertThat(readKnownOwnerIds(sector))
                .containsExactlyInAnyOrder(OPEN_HOLDER, CONCEALED_HOLDER_ID, Factions.NEUTRAL);
        }

        @Test
        void namesBothOnceThePlayerHasBeenInTheirSystem() {
            // The player's own route, and it reaches both shapes at once: a visit is a sighting of
            // everything standing there, so neither gate has anything left to hold.
            var sector = buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID);
            markSystemAsVisitedByPlayer(sector, buildOnlySystem(sector));

            assertThat(readKnownOwnerIds(sector))
                .containsExactlyInAnyOrder(CONCEALED_HOLDER_ID, Factions.NEUTRAL);
        }

        @Test
        void namesTheDerelictAloneOnceItsOwnGateIsTurnedOff() {
            // One toggle, one shape. A player asking to see unseen derelicts gets the hulk and not
            // the base beside it, which is what makes these two settings rather than one spoiler
            // switch - and what a transposed reading of them would fail on.
            settingsMock
                .when(KmuMapLayerSettings::shouldShowUnseenAbandonedStations)
                .thenReturn(true);

            var sector = buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID);

            assertThat(readKnownOwnerIds(sector))
                .containsExactly(Factions.NEUTRAL);
        }

        @Test
        void namesTheConcealedBaseAloneOnceItsOwnGateIsTurnedOff() {

            settingsMock
                .when(KmuMapLayerSettings::shouldShowUnseenHiddenMarkets)
                .thenReturn(true);

            var sector = buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID);

            assertThat(readKnownOwnerIds(sector))
                .containsExactly(CONCEALED_HOLDER_ID);
        }

        @Test
        void namesNeitherWhereOnlyTheDiscoveryRevealIsTurnedOn() {
            // The rule every one of these toggles is written to: each clears the one thing it names
            // and nothing beside it. This reveal says both markets may be shown though nobody found
            // them, which is no answer at all to whether anybody has seen them standing here - so
            // both gates go on holding, and a player who asked for one thing is shown one thing.
            settingsMock
                .when(KmuMapLayerSettings::shouldShowUndiscoveredMarkets)
                .thenReturn(true);

            var sector = buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID);

            assertThat(readKnownOwnerIds(sector))
                .isEmpty();
        }

        @Test
        void withholdsAnUnsurveyedDecivilisedWorldUntilNoSurveyIsAskedFor() {
            // The collapsed colony's own arm of the fog, and its own knob. Nothing gates it - the
            // survey read leaks nothing - so what the knob moves is the fog itself, and it moves no
            // other arm: the world's planet is found throughout.
            var sector = buildSectorHoldingAnUnsurveyedDecivilisedWorld();

            assertThat(readKnownOwnerIds(sector))
                .isEmpty();

            settingsMock
                .when(KmuMapLayerSettings::getDecivilisedWorldSurveyLevel)
                .thenReturn(SurveyLevel.NONE);

            assertThat(readKnownOwnerIds(sector))
                .containsExactly(Factions.NEUTRAL);
        }
    }

    // A sector of one system whose only market is a collapsed colony nobody has looked at. Its
    // economy lists nothing, which is what such a world always reaches a reader as: vanilla drops
    // the market from the economy as the colony falls.
    private static SectorAPI buildSectorHoldingAnUnsurveyedDecivilisedWorld() {

        var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID);

        DecivilisedPlanetFixtures.placeUnsurveyedDecivilisedPlanetIn(buildOnlySystem(sector));

        return sector;
    }

    // An ordinary colony nobody conceals: what makes the system somewhere people live, and so
    // somewhere with inhabitants to have seen the two gated markets beside them.
    private static MarketAPI buildOpenColony() {
        return buildVisibleMarket(buildFaction(OPEN_HOLDER), COLONY_SIZE);
    }

    // Who the map may name in the sector's one system, read through a pass opened on the player's
    // live rule the way a rebuild opens one.
    private static List<String> readKnownOwnerIds(SectorAPI sector) {

        var pass = HolderPass.over(
            sector,
            MapVisibilityRules.readFromLunaSettings().colonyVisibility(),
            HolderGrouping.identity());

        return pass
            .readKnownColoniesIn(buildOnlySystem(sector))
            .stream()
            .map(colony -> colony.market().getFaction().getId())
            .toList();
    }
}
