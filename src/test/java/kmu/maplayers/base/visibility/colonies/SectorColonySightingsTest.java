package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.CampaignClockAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.colonies.SystemColonies;
import kmlib.testfixtures.starsector.colonies.ColonyMarketFixture;
import kmlib.testfixtures.starsector.colonies.ColonyPlacementFixture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the sighting register kept in sector memory: what an unwritten register answers, what each
 * of the two observation routes records, which colonies and places are worth recording at all, and
 * what a load sheds.
 *
 * <p>The stored map is a real one behind a mocked memory, rather than a stub per key, because
 * every case here is about how the register accumulates across calls - a stubbed read could not
 * show a second visit overwriting the first, nor a reconciliation removing an entry.
 *
 * <p>The colonies are {@link ColonyMarketFixture}'s, shapes an observation decides for the most
 * part: a colony neither gate nor report is about is never recorded, so a suite posing only those
 * could not tell a working recorder from one that wrote nothing at all.
 */
final class SectorColonySightingsTest {

    private static final long OBSERVED_NOW = 4_200L;
    private static final String OTHER_SYSTEM_ID = "corvus";
    private static final String SIGHTINGS_KEY = "$kmu_colony_sightings";
    private static final String SYSTEM_ID = "kumari_kandam";

    private CampaignClockAPI clockMock;
    private EconomyAPI economyMock;
    private MemoryAPI memoryMock;
    private SectorAPI sectorMock;
    private StarSystemAPI systemMock;

    @BeforeEach
    void setUp() {

        clockMock = mock(CampaignClockAPI.class);
        economyMock = mock(EconomyAPI.class);
        memoryMock = mock(MemoryAPI.class);
        sectorMock = mock(SectorAPI.class);
        systemMock = mock(StarSystemAPI.class);

        when(clockMock.getTimestamp())
            .thenReturn(OBSERVED_NOW);
        when(sectorMock.getClock())
            .thenReturn(clockMock);
        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);
        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systemMock));
        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
    }

    @Nested
    class ReadSightings {

        @Test
        void reports_nothing_seen_where_the_register_has_never_been_written() {

            assertThat(readObservationOf("any"))
                .isNull();
        }

        @Test
        void reports_nothing_seen_where_there_is_no_sector_to_read() {

            assertThat(SectorColonySightings.readSightings(null).readObservation("any"))
                .isNull();
        }

        @Test
        void reports_nothing_seen_where_the_key_holds_something_that_is_not_a_register() {
            // Another party writing over the key must cost the sightings and nothing else: a read
            // that threw here would take down every colony set in the sector with it.
            storeSightings("not a register");

            assertThat(readObservationOf("any"))
                .isNull();
        }

        @Test
        void reports_where_and_when_a_recorded_colony_was_seen() {

            var stored = new HashMap<String, String>();

            stored.put("sentinel_gantries", "1720@" + SYSTEM_ID);
            storeSightings(stored);

            assertThat(readObservationOf("sentinel_gantries"))
                .isEqualTo(ColonyObservation.createObservationAt(SYSTEM_ID, 1720L));
        }

        @Test
        void reports_a_value_written_before_observations_were_timed_as_seen_at_no_stated_moment() {
            // The migration case, and the reason nothing here fails on an entry it did not write:
            // a save made before the time was kept names a place alone, and that is a complete
            // observation with one half missing rather than a broken one.
            var stored = new HashMap<String, String>();

            stored.put("sentinel_gantries", SYSTEM_ID);
            storeSightings(stored);

            assertThat(readObservationOf("sentinel_gantries"))
                .isEqualTo(ColonyObservation.createUndatedObservation(SYSTEM_ID));
        }

    }

    @Nested
    class RecordSightingsIn {

        @Test
        void records_every_gated_colony_standing_in_the_system_visited() {
            // Both listings, since a gated colony is most often the unregistered shape: hung on
            // one of the system's own entities and never entered in the economy.
            var listedBase = buildConcealedColony("pirate_base", "pirates");
            var unlistedDerelict = buildDerelict("sentinel_gantries");

            listColoniesInSystem(listedBase);
            placeColoniesOnSystemEntities(unlistedDerelict);
            openStoredSightings();

            SectorColonySightings.recordSightingsIn(sectorMock, systemMock);

            assertThat(readObservationOf("pirate_base"))
                .isEqualTo(ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_NOW));
            assertThat(readObservationOf("sentinel_gantries"))
                .isEqualTo(ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_NOW));
        }

        @Test
        void records_where_a_colony_stands_and_nothing_of_when_where_there_is_no_clock_to_read() {
            // The place is the half every visibility rule spends, so a sector that cannot say what
            // day it is must still record that somebody was here - dated at the next observation.
            when(sectorMock.getClock())
                .thenReturn(null);

            placeColoniesOnSystemEntities(buildDerelict("sentinel_gantries"));
            openStoredSightings();

            SectorColonySightings.recordSightingsIn(sectorMock, systemMock);

            assertThat(readObservationOf("sentinel_gantries"))
                .isEqualTo(ColonyObservation.createUndatedObservation(SYSTEM_ID));
        }

        @Test
        void records_nothing_for_a_colony_held_in_the_open() {
            // What bounds the register to what a gate actually reads. An open colony the economy
            // lists is permanently in the sector's own sight, so an entry for one would answer
            // nothing while costing an entry per colony in the sector.
            var colony = buildOpenColony("jangala", "hegemony");

            listColoniesInSystem(colony);
            openStoredSightings();

            SectorColonySightings.recordSightingsIn(sectorMock, systemMock);

            assertThat(readStoredSightings())
                .isEmpty();
        }

        @Test
        void records_nothing_for_a_dead_world_the_player_is_standing_in() {
            // The narrower half of the pair the two routes make. Arriving is the very act vanilla
            // stamps a permanent survey level for, so an entry here would restate what the fog
            // answers anyway - at the cost of one per dead world in every system ever entered.
            placeColoniesOnSystemEntities(buildDeadWorld("tibicena"));
            openStoredSightings();

            SectorColonySightings.recordSightingsIn(sectorMock, systemMock);

            assertThat(readStoredSightings())
                .isEmpty();
        }

        @Test
        void moves_a_colonys_sighting_to_wherever_it_was_last_seen() {
            // The mover's own case, from the register's side: meeting a colony again names the
            // new place rather than adding to a list of places it has ever been.
            var mover = buildConcealedColony("rat_exoship", "rat_exotech");
            var stored = openStoredSightings();

            stored.put("rat_exoship", OTHER_SYSTEM_ID);
            listColoniesInSystem(mover);

            SectorColonySightings.recordSightingsIn(sectorMock, systemMock);

            assertThat(readObservationOf("rat_exoship"))
                .isEqualTo(ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_NOW));
        }

        @Test
        void records_nothing_for_a_place_that_is_not_a_star_system() {
            // Hyperspace, which is where the sector's largest entity list lives and where a colony
            // reads sighted whatever the register says. Walking it would buy nothing at all.
            SectorColonySightings.recordSightingsIn(sectorMock, mock(LocationAPI.class));

            verify(memoryMock, never())
                .set(anyString(), any());
        }

        @Test
        void records_nothing_where_there_is_no_memory_to_write_into() {

            when(sectorMock.getMemoryWithoutUpdate())
                .thenReturn(null);

            listColoniesInSystem(buildDerelict("sentinel_gantries"));

            SectorColonySightings.recordSightingsIn(sectorMock, systemMock);

            verifyNoInteractions(memoryMock);
        }
    }

    @Nested
    class RecordSightingsByInhabitants {

        @Test
        void records_a_derelict_standing_beside_another_factions_colony() {
            // The route's own case, and the reason it is written down at all: a hulk in orbit over
            // an inhabited world is common knowledge there, and stays known once that world dies.
            var derelict = buildDerelict("sentinel_gantries");

            listColoniesInSystem(buildOpenColony("jangala", "hegemony"));
            placeColoniesOnSystemEntities(derelict);
            openStoredSightings();

            recordWhatTheSystemsInhabitantsSee();

            assertThat(readStoredSightings())
                .containsOnlyKeys("sentinel_gantries");
            assertThat(readObservationOf("sentinel_gantries"))
                .isEqualTo(ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_NOW));
        }

        @Test
        void records_a_dead_world_standing_beside_another_factions_colony() {
            // The wider half of the pair. No gate is about a dead world, and the neighbours' word
            // is nonetheless the whole of why the map shows one - so the entry is what keeps it
            // there after the colony that reported it has itself collapsed.
            listColoniesInSystem(buildOpenColony("jangala", "hegemony"));
            placeColoniesOnSystemEntities(buildDeadWorld("tibicena"));
            openStoredSightings();

            recordWhatTheSystemsInhabitantsSee();

            assertThat(readStoredSightings())
                .containsOnlyKeys("tibicena");
            assertThat(readObservationOf("tibicena"))
                .isEqualTo(ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_NOW));
        }

        @Test
        void records_nothing_for_a_derelict_alone_in_its_system() {
            // Nobody is here to have seen it, so nothing is written and no empty register is put
            // into the save on the strength of a system holding a hulk.
            placeColoniesOnSystemEntities(buildDerelict("sentinel_gantries"));

            recordWhatTheSystemsInhabitantsSee();

            verify(memoryMock, never())
                .set(anyString(), any());
        }

        @Test
        void records_nothing_for_a_base_its_own_faction_shelters() {
            // Owner-awareness carried into the write: the pirates do not announce their own base,
            // so the register is never given an observation the rule would decline to credit.
            listColoniesInSystem(
                buildConcealedColony("pirate_base", "pirates"),
                buildOpenColony("pirate_haven", "pirates"));
            openStoredSightings();

            recordWhatTheSystemsInhabitantsSee();

            assertThat(readStoredSightings())
                .isEmpty();
        }

        @Test
        void records_nothing_where_there_is_no_system_to_name_the_sighting_after() {
            // The write is handed a place and a set rather than sweeping for either, so a caller
            // mid-walk over a sector that answers nothing must cost the register nothing.
            SectorColonySightings.recordSightingsByInhabitants(
                sectorMock, null, Colonies.NONE, ColonyKnowledge.observingUnderTheFog());

            verifyNoInteractions(memoryMock);
        }

        @Test
        void records_nothing_where_there_is_no_sector_to_stamp_the_sighting_against() {
            // The only route that reaches the write with colonies to file and no sector: the set
            // arrives from the caller's own walk rather than from the sector, so nothing empties it
            // on the way in. It must cost the register nothing rather than fault over the clock it
            // has nowhere to read.
            listColoniesInSystem(buildOpenColony("jangala", "hegemony"));
            placeColoniesOnSystemEntities(buildDerelict("sentinel_gantries"));

            var observedColonies = SystemColonies.readColoniesIn(sectorMock, systemMock);

            SectorColonySightings.recordSightingsByInhabitants(
                null, systemMock, observedColonies, ColonyKnowledge.observingUnderTheFog());

            verifyNoInteractions(memoryMock);
        }

        @Test
        void records_nothing_where_there_is_no_colony_set_to_write_from() {
            // The other half of the same guard. A caller walking a sector mid-load holds places
            // it has no reading of yet, and handing one over must cost the register nothing
            // rather than fault on the way through.
            SectorColonySightings.recordSightingsByInhabitants(
                sectorMock, systemMock, null, ColonyKnowledge.observingUnderTheFog());

            verifyNoInteractions(memoryMock);
        }
    }

    @Nested
    class DropSightingsOfAbsentColonies {

        @Test
        void drops_a_sighting_of_a_colony_no_longer_anywhere_in_the_sector() {
            // A sighting outliving what it was about would go on answering for whatever next took
            // the id, which is an observation nobody ever made.
            var stored = openStoredSightings();

            stored.put("sentinel_gantries", SYSTEM_ID);
            stored.put("razed_base", SYSTEM_ID);
            placeColoniesOnSystemEntities(buildDerelict("sentinel_gantries"));

            SectorColonySightings.dropSightingsOfAbsentColonies(sectorMock);

            assertThat(readStoredSightings())
                .containsOnlyKeys("sentinel_gantries");
        }

        @Test
        void keeps_a_sighting_of_a_colony_that_has_moved_to_another_system() {
            // Present but elsewhere is not absent. The sighting stays and simply stops matching
            // where the colony stands, which is the rule's own way of saying it is unseen again.
            var stored = openStoredSightings();

            stored.put("rat_exoship", OTHER_SYSTEM_ID);
            listColoniesInSystem(buildConcealedColony("rat_exoship", "rat_exotech"));

            SectorColonySightings.dropSightingsOfAbsentColonies(sectorMock);

            assertThat(readObservationOf("rat_exoship"))
                .isEqualTo(ColonyObservation.createUndatedObservation(OTHER_SYSTEM_ID));
        }

        @Test
        void leaves_an_unwritten_register_alone() {

            SectorColonySightings.dropSightingsOfAbsentColonies(sectorMock);

            verify(memoryMock, never())
                .set(anyString(), any());
        }
    }

    @Nested
    class ReconcileWithLoadedSave {

        @Test
        void records_the_colonies_where_the_save_was_left_and_sheds_the_ones_that_have_gone() {
            // What a load owes the register. No location change fires until the player leaves, so
            // without this the place they are looking at is the one place nothing is known about -
            // and on a save written before any sighting was made, that is all it could learn.
            var stored = openStoredSightings();

            stored.put("razed_base", SYSTEM_ID);
            placeColoniesOnSystemEntities(buildDerelict("sentinel_gantries"));

            when(sectorMock.getCurrentLocation())
                .thenReturn(systemMock);

            SectorColonySightings.reconcileWithLoadedSave(sectorMock);

            assertThat(readStoredSightings())
                .containsOnlyKeys("sentinel_gantries");
            assertThat(readObservationOf("sentinel_gantries"))
                .isEqualTo(ColonyObservation.createObservationAt(SYSTEM_ID, OBSERVED_NOW));
        }

        @Test
        void records_nothing_where_the_save_was_left_outside_a_star_system() {

            openStoredSightings();
            placeColoniesOnSystemEntities(buildDerelict("sentinel_gantries"));

            when(sectorMock.getCurrentLocation())
                .thenReturn(mock(LocationAPI.class));

            SectorColonySightings.reconcileWithLoadedSave(sectorMock);

            assertThat(readStoredSightings())
                .isEmpty();
        }
    }

    // A derelict nobody ever lived on - the commonest gated shape, and the one the register was
    // introduced for.
    private static MarketAPI buildDerelict(String colonyId) {
        return nameColony(ColonyMarketFixture.buildDerelictStation(), colonyId);
    }

    // A world whose government has collapsed: no gate is about it, and only the inhabitants' route
    // records one. Unsurveyed, which is what makes the neighbours' word the only thing showing it.
    private static MarketAPI buildDeadWorld(String colonyId) {
        return nameColony(ColonyMarketFixture.buildUnsurveyedDecivilisedWorld(), colonyId);
    }

    // A base that conceals itself: the other gated shape, held by a real faction.
    private static MarketAPI buildConcealedColony(String colonyId, String factionId) {
        return nameColony(ColonyMarketFixture.buildFoundConcealedColony(factionId), colonyId);
    }

    // An ordinary colony, gated by nothing. Posed both as the shape that must never reach the
    // register and as the neighbour whose people do the observing.
    private static MarketAPI buildOpenColony(String colonyId, String factionId) {
        return nameColony(ColonyMarketFixture.buildVisibleColony(factionId), colonyId);
    }

    // The id a sighting is kept against. Given here rather than by the colony builders, none of
    // which needs one - only the register does, and only because a stored entry has to be named.
    private static MarketAPI nameColony(MarketAPI colony, String colonyId) {

        when(colony.getId())
            .thenReturn(colonyId);

        return colony;
    }

    // The inhabitants' write as a sweeping caller makes it: the system's colonies selected once,
    // then handed in. Bound here because the write no longer sweeps for the set itself, and a case
    // reading the sector its own way could hand the register a set the production walk never
    // produces.
    private void recordWhatTheSystemsInhabitantsSee() {

        SectorColonySightings.recordSightingsByInhabitants(
            sectorMock,
            systemMock,
            SystemColonies.readColoniesIn(sectorMock, systemMock),
            ColonyKnowledge.observingUnderTheFog());
    }

    private void listColoniesInSystem(MarketAPI... colonies) {
        ColonyPlacementFixture.listColonies(economyMock, systemMock, colonies);
    }

    private void placeColoniesOnSystemEntities(MarketAPI... colonies) {
        ColonyPlacementFixture.hangColoniesOnEntitiesIn(systemMock, colonies);
    }

    // Opens the register the way a first sighting would, so a case can seed it and then assert
    // against the very map the code under test writes into.
    private Map<String, String> openStoredSightings() {

        var stored = new HashMap<String, String>();

        storeSightings(stored);

        return stored;
    }

    private void storeSightings(Object storedValue) {

        when(memoryMock.contains(SIGHTINGS_KEY))
            .thenReturn(true);
        when(memoryMock.get(SIGHTINGS_KEY))
            .thenReturn(storedValue);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readStoredSightings() {
        return (Map<String, String>) memoryMock.get(SIGHTINGS_KEY);
    }

    // What the register says about one colony, read back through its own reader. The stored text
    // is this class's private business - a case asserting against it would be pinning an encoding
    // rather than an observation, and would have to be rewritten the day the encoding changes.
    private ColonyObservation readObservationOf(String colonyId) {
        return SectorColonySightings
            .readSightings(sectorMock)
            .readObservation(colonyId);
    }
}
