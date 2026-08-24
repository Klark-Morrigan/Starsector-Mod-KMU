package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmu.maplayers.ColonyShapeFixtures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a journey records: both ends of the move, and nothing outside them.
 *
 * <p>Posed against two systems and a third nobody goes near, because the claim being made is as
 * much about what is left alone as about what is written - a recorder that swept the sector would
 * pass every case about the two ends and still cost a campaign's worth of walking.
 */
final class ColonySightingRecorderTest {

    private static final String ARRIVAL_SYSTEM_ID = "corvus";
    private static final String DEPARTURE_SYSTEM_ID = "kumari_kandam";
    private static final String SIGHTINGS_KEY = "$kmu_colony_sightings";
    private static final String UNVISITED_SYSTEM_ID = "askonia";

    private EconomyAPI economyMock;
    private MemoryAPI memoryMock;
    private SectorAPI sectorMock;
    private Map<String, String> storedSightings;

    @BeforeEach
    void setUp() {

        economyMock = mock(EconomyAPI.class);
        memoryMock = mock(MemoryAPI.class);
        sectorMock = mock(SectorAPI.class);
        storedSightings = new HashMap<>();

        when(sectorMock.getEconomy())
            .thenReturn(economyMock);
        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);
        when(memoryMock.contains(SIGHTINGS_KEY))
            .thenReturn(true);
        when(memoryMock.get(SIGHTINGS_KEY))
            .thenReturn(storedSightings);
    }

    @Nested
    class ReportCurrentLocationChanged {

        @Test
        void records_the_colonies_where_the_player_arrives() {

            var arrivalSystem = buildSystemHolding(ARRIVAL_SYSTEM_ID, buildGatedColony("jangala"));

            new ColonySightingRecorder(sectorMock)
                .reportCurrentLocationChanged(null, arrivalSystem);

            assertThat(storedSightings)
                .containsEntry("jangala", ARRIVAL_SYSTEM_ID);
        }

        @Test
        void records_the_colonies_where_the_player_leaves_from() {
            // The departure end is what catches a colony that appeared during the stay: it was
            // not there to be recorded on arrival, and the register would otherwise go on saying
            // the player had never seen it.
            var departureSystem =
                buildSystemHolding(DEPARTURE_SYSTEM_ID, buildGatedColony("sentinel_gantries"));

            new ColonySightingRecorder(sectorMock)
                .reportCurrentLocationChanged(departureSystem, null);

            assertThat(storedSightings)
                .containsEntry("sentinel_gantries", DEPARTURE_SYSTEM_ID);
        }

        @Test
        void records_nothing_of_a_system_the_move_does_not_name() {
            // The cost claim. A journey pays for the two places it joins, and a sector full of
            // systems the player is nowhere near is not walked at all.
            var departureSystem =
                buildSystemHolding(DEPARTURE_SYSTEM_ID, buildGatedColony("sentinel_gantries"));
            var arrivalSystem = buildSystemHolding(ARRIVAL_SYSTEM_ID, buildGatedColony("jangala"));

            buildSystemHolding(UNVISITED_SYSTEM_ID, buildGatedColony("mairaath"));

            new ColonySightingRecorder(sectorMock)
                .reportCurrentLocationChanged(departureSystem, arrivalSystem);

            assertThat(storedSightings)
                .containsOnlyKeys("sentinel_gantries", "jangala");
        }
    }

    // A colony that conceals itself, named by the id an observation is kept against. Gated, since
    // an ungated colony is never recorded and a case posing one could not tell a recorder that
    // walked the wrong places from one that walked none.
    private static MarketAPI buildGatedColony(String colonyId) {

        var marketMock = ColonyShapeFixtures.buildFoundConcealedColony("pirates");

        when(marketMock.getId())
            .thenReturn(colonyId);

        return marketMock;
    }

    // A system the economy lists the given colonies in. Registered rather than hung on entities
    // because what is under test is which places are walked, not which listing a colony sits in.
    private StarSystemAPI buildSystemHolding(String systemId, MarketAPI... colonies) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(systemId);

        ColonyShapeFixtures.listColoniesInEconomy(economyMock, systemMock, colonies);

        return systemMock;
    }
}
