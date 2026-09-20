package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.starsector.markets.colonies.SystemColonies;
import kmlib.testfixtures.starsector.markets.colonies.ColonyPlacementFixture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins what the two observation routes buy once they are written down rather than merely tested:
 * the recorders, the register in sector memory and the rule over a colony set, exercised together
 * against one world.
 *
 * <p>Integration rather than unit because the claim is about the seam between them. Each half
 * answers correctly on its own - the sweep records what a place can see, the rule shows what the
 * register holds - and what is worth pinning is the behaviour when the world moves in between: an
 * informant dying, a colony warping off, a colony met somewhere new.
 *
 * <p>The world is {@link ColonyKnowledgeFixture}'s, with its sector memory opened wherever a recorder is
 * exercised so the production writer puts an observation where the rule really reads one.
 */
final class ColonyObservationIntegrationTest {

    // The rule as it ships: both leaking shapes held back until somebody has seen them, and
    // nothing admitted that the player has not found.
    private static final ColonyVisibility BOTH_GATES_ON = new ColonyVisibility(
        false,
        DecivilisedMarkets.DEFAULT_SURVEY_LEVEL,
        Set.of(RevelationGate.SPACE_DERELICTS, RevelationGate.HIDDEN_COLONIES));

    // The same rule with the survey bar raised as far as it goes - what a player asking for
    // readings off the world itself has set, and the one thing that tells the two routes into a
    // collapsed world apart.
    private static final ColonyVisibility ASKING_A_FULL_SURVEY = new ColonyVisibility(
        false,
        MarketAPI.SurveyLevel.FULL,
        Set.of(RevelationGate.SPACE_DERELICTS, RevelationGate.HIDDEN_COLONIES));

    private static final String DEAD_WORLD_ID = "tibicena";
    private static final String DERELICT_ID = "sentinel_gantries";
    private static final String MOVER_ID = "rat_exoship";
    private static final String NEIGHBOUR_ID = "jangala";
    private static final String OTHER_SYSTEM_ID = "corvus";
    private static final String SYSTEM_ID = "kumari_kandam";

    @Nested
    class RecordSightingsByInhabitants {

        @Test
        void keepsADerelictShownOnceTheColonyThatObservedItHasDecivilised() {
            // The whole of why the place's route is written down instead of read live. The hulk
            // has been on the map for cycles on its neighbours' account, and their dying is no
            // reason for it to blink out of a sector the player learnt long ago.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);
            var derelict = placeDerelictIn(fixture);
            var neighbour = listNeighbourIn(fixture);

            fixture.openSectorMemory();

            recordWhatTheSystemsInhabitantsSee(fixture);
            decivilise(neighbour);

            assertThat(readKnownColoniesIn(fixture))
                .containsExactly(new Colony(derelict, false));
        }

        @Test
        void keepsADeadWorldShownOnceTheColonyThatReportedItHasDecivilised() {
            // The same claim for the report route, and the failure that makes the widened write
            // worth its entries: the world is on the map on the neighbours' word alone, so without
            // a record of what they saw it would vanish with the last of them.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);
            var deadWorld = placeDeadWorldIn(fixture);
            var neighbour = listNeighbourIn(fixture);

            fixture.openSectorMemory();

            recordWhatTheSystemsInhabitantsSee(fixture);
            decivilise(neighbour);

            assertThat(readKnownColoniesIn(fixture))
                .containsExactly(new Colony(deadWorld, false));
        }

        @Test
        void withholdsADeadWorldOnARecordedReportAloneWhereAFullSurveyIsAskedFor() {
            // What the raised bar costs, stated where it happens rather than left to be
            // discovered. A recorded report is written into the same register the player's own
            // sightings are, and that register is what the bar is put to - so once the last
            // neighbour is gone, all that is left of the world is a second-hand note from a player
            // who asked for readings instead. While somebody is standing there the map still says
            // so, which is the case below.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);

            placeDeadWorldIn(fixture);

            var neighbour = listNeighbourIn(fixture);

            fixture.openSectorMemory();

            recordWhatTheSystemsInhabitantsSee(fixture);
            decivilise(neighbour);

            assertThat(readKnownColoniesIn(fixture, fixture.getSystem(), ASKING_A_FULL_SURVEY))
                .isEmpty();
        }

        @Test
        void withholdsADerelictThatMovesAfterTheColonyThatObservedItHasDecivilised() {
            // An observation names a place, so it stops answering the moment the colony stands
            // somewhere else. That is what keeps a recorded observation from becoming a permanent
            // pass for a colony that wanders.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);
            var derelict = placeDerelictIn(fixture);
            var neighbour = listNeighbourIn(fixture);

            fixture.openSectorMemory();

            recordWhatTheSystemsInhabitantsSee(fixture);
            decivilise(neighbour);

            var elsewhere = buildSystemElsewhere();

            ColonyPlacementFixture.placeColonies(elsewhere, derelict);

            assertThat(readKnownColoniesIn(fixture, elsewhere))
                .isEmpty();
        }

        @Test
        void recordsNothingForTheOrdinaryColonyDoingTheObserving() {
            // The register holds what a gate reads and nothing besides. An open colony the economy
            // lists is permanently in the sector's own sight, so an entry for one would cost an
            // entry per colony in the sector to answer a question nobody asks.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);

            placeDerelictIn(fixture);
            listNeighbourIn(fixture);

            fixture.openSectorMemory();

            recordWhatTheSystemsInhabitantsSee(fixture);

            var sightings = SectorColonySightings.readSightings(fixture.getSector());

            assertThat(sightings.readObservation(DERELICT_ID).locationId())
                .isEqualTo(SYSTEM_ID);
            assertThat(sightings.readObservation(NEIGHBOUR_ID))
                .isNull();
        }
    }

    @Nested
    class ReadKnownColonies {

        @Test
        void showsADerelictItsNeighboursCanSeeBeforeAnythingHasBeenRecorded() {
            // The live half of the rule, kept beside the recorded one so a colony arriving among
            // witnesses is shown at once rather than at whatever cadence a sweep runs on - and so
            // an install where nothing has swept still shows what is plainly there. The sector's
            // memory is left shut, which is what "nothing has been recorded" means.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);
            var derelict = placeDerelictIn(fixture);

            listNeighbourIn(fixture);

            assertThat(readKnownColoniesIn(fixture))
                .contains(new Colony(derelict, false));
        }

        @Test
        void showsADeadWorldItsNeighboursCanSeeWhereAFullSurveyIsAskedFor() {
            // The route the bar does not reach, through the production walk rather than a staged
            // set: the neighbour is standing in the system, so the world is on the map however
            // much the player has asked of their own instruments. The sector's memory is left
            // shut, which is what makes the live neighbour the only thing answering.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);
            var deadWorld = placeDeadWorldIn(fixture);

            listNeighbourIn(fixture);

            assertThat(readKnownColoniesIn(fixture, fixture.getSystem(), ASKING_A_FULL_SURVEY))
                .contains(new Colony(deadWorld, false));
        }
    }

    @Nested
    class RecordSightingsIn {

        @Test
        void followsAMoverFromEachSystemItIsMetInToTheNext() {
            // The player's own route, and why an observation names a place rather than a day:
            // meeting the exoship again rewrites where it was last seen, and the system it left
            // stops answering for it.
            var fixture = new ColonyKnowledgeFixture(SYSTEM_ID);
            var mover = placeMoverIn(fixture);

            fixture.openSectorMemory();

            SectorColonySightings.recordSightingsIn(fixture.getSector(), fixture.getSystem());

            var elsewhere = buildSystemElsewhere();

            ColonyPlacementFixture.placeColonies(elsewhere, mover);
            SectorColonySightings.recordSightingsIn(fixture.getSector(), elsewhere);

            assertThat(readKnownColoniesIn(fixture, elsewhere))
                .containsExactly(new Colony(mover, false));
            assertThat(SectorColonySightings.readSightings(fixture.getSector())
                    .readObservation(MOVER_ID)
                    .locationId())
                .isEqualTo(OTHER_SYSTEM_ID);
        }
    }

    // The inhabitants' write as a sweeping caller makes it: the system selected once, then handed
    // to the register. The seam this suite exists for is between that selection and the rule read
    // below, so both go through the same production walk rather than the case staging a set.
    private static void recordWhatTheSystemsInhabitantsSee(ColonyKnowledgeFixture fixture) {

        SectorColonySightings.recordSightingsByInhabitants(
            fixture.getSector(),
            fixture.getSystem(),
            SystemColonies.readColoniesIn(fixture.getSector(), fixture.getSystem()),
            ColonyKnowledge.observingUnderTheFog());
    }

    // A derelict standing in the fixture's system, unregistered as vanilla builds one.
    private static MarketAPI placeDerelictIn(ColonyKnowledgeFixture fixture) {

        var derelict = nameColony(fixture.buildDerelictStation(), DERELICT_ID);

        fixture.placeColoniesInSystem(derelict);

        return derelict;
    }

    // A world whose government has collapsed, standing where the derelict does. Unsurveyed, so the
    // fog refuses it outright and the neighbours' report is the only thing that can put it on the
    // map - which is what makes the record of that report the whole of the case.
    private static MarketAPI placeDeadWorldIn(ColonyKnowledgeFixture fixture) {

        var deadWorld = nameColony(fixture.buildUnsurveyedDecivilisedWorld(), DEAD_WORLD_ID);

        fixture.placeColoniesInSystem(deadWorld);

        return deadWorld;
    }

    // A concealed colony that wanders, standing where the derelict does - the shape that makes a
    // recorded observation stop answering by going somewhere else.
    private static MarketAPI placeMoverIn(ColonyKnowledgeFixture fixture) {

        var mover = nameColony(fixture.buildFoundConcealedColony("rat_exotech"), MOVER_ID);

        fixture.placeColoniesInSystem(mover);

        return mover;
    }

    // The ordinary colony whose people do the observing: open, held by somebody other than the
    // derelict's neutral owner, and listed by the economy as an inhabited world is.
    private static MarketAPI listNeighbourIn(ColonyKnowledgeFixture fixture) {

        var neighbour = nameColony(fixture.buildVisibleColony("hegemony"), NEIGHBOUR_ID);

        fixture.listColoniesInEconomy(neighbour);

        return neighbour;
    }

    // A star system the fixture's sector does not list - somewhere a colony can be found standing
    // after it has moved, which is all a case about moving asks of a second place.
    private static StarSystemAPI buildSystemElsewhere() {
        return StarSystemFixture.buildSystem(OTHER_SYSTEM_ID);
    }

    // Kills a colony the way the game does, by turning its market into the condition-only shell a
    // decivilised world carries - which every ownership read then refuses.
    private static void decivilise(MarketAPI colony) {

        when(colony.isPlanetConditionMarketOnly())
            .thenReturn(true);
    }

    // The ID an observation is kept against. Colonies are built without one because almost nothing
    // reads it, and every unnamed colony would otherwise share one entry in the register.
    private static MarketAPI nameColony(MarketAPI colony, String colonyId) {

        when(colony.getId())
            .thenReturn(colonyId);

        return colony;
    }

    private static List<Colony> readKnownColoniesIn(ColonyKnowledgeFixture fixture) {
        return readKnownColoniesIn(fixture, fixture.getSystem());
    }

    private static List<Colony> readKnownColoniesIn(
            ColonyKnowledgeFixture fixture,
            StarSystemAPI system) {

        return readKnownColoniesIn(fixture, system, BOTH_GATES_ON);
    }

    // The rule stated by the case rather than defaulted, for the cases about which route into a
    // collapsed world the survey bar reaches - the register being read the same way whichever bar
    // is posed, which is the seam those cases are about.
    private static List<Colony> readKnownColoniesIn(
            ColonyKnowledgeFixture fixture,
            StarSystemAPI system,
            ColonyVisibility rule) {

        return new ColonyKnowledge(
                rule,
                SectorColonySightings.readSightings(fixture.getSector()))
            .readKnownColonies(SystemColonies.readColoniesIn(fixture.getSector(), system));
    }
}
