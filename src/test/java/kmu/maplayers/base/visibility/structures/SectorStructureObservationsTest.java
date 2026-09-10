package kmu.maplayers.base.visibility.structures;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the key the structure register is held under and what a reader gets back through it: the
 * key because it is save state, and the reading because an absent entry has to arrive as nothing
 * observed rather than as an empty answer a rule could mistake for one.
 */
final class SectorStructureObservationsTest {

    private static final String OBSERVATIONS_KEY = "$kmu_structure_observations";
    private static final String STRUCTURE_ID = "kumari_kandam_relay";

    private MemoryAPI memoryMock;
    private SectorAPI sectorMock;

    @BeforeEach
    void setUp() {

        memoryMock = mock(MemoryAPI.class);
        sectorMock = mock(SectorAPI.class);

        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memoryMock);
    }

    @Nested
    class ReadObservations {

        @Test
        void reports_nothing_observed_where_the_register_has_never_been_written() {

            assertThat(readObservationOf(STRUCTURE_ID))
                .isNull();
        }

        @Test
        void reports_nothing_observed_where_there_is_no_sector_to_read() {

            assertThat(SectorStructureObservations.readObservations(null)
                    .readObservation(STRUCTURE_ID))
                .isNull();
        }

        @Test
        void reports_nothing_observed_of_a_structure_the_register_has_never_held() {

            var stored = new HashMap<String, String>();

            stored.put("corvus_relay", "4200|4300||hegemony");
            storeObservations(stored);

            assertThat(readObservationOf(STRUCTURE_ID))
                .isNull();
        }

        @Test
        void reports_what_a_recorded_structure_was_last_observed_to_be() {

            var stored = new HashMap<String, String>();

            stored.put(STRUCTURE_ID, "4200|4300|n|hegemony");
            storeObservations(stored);

            assertThat(readObservationOf(STRUCTURE_ID))
                .isEqualTo(new StructureObservation(
                    Optional.of("hegemony"),
                    false,
                    true,
                    Optional.of(4_200L),
                    Optional.of(4_300L)));
        }
    }

    // What the register answers about one structure, through the port a rule reads it by.
    private StructureObservation readObservationOf(String structureId) {

        return SectorStructureObservations
            .readObservations(sectorMock)
            .readObservation(structureId);
    }

    // Puts a register into the sector's memory under the key the reader looks for, so a case can
    // pose a save that already holds observations.
    private void storeObservations(Object storedRegister) {

        when(memoryMock.contains(anyString()))
            .thenReturn(false);
        when(memoryMock.contains(OBSERVATIONS_KEY))
            .thenReturn(true);
        when(memoryMock.get(OBSERVATIONS_KEY))
            .thenReturn(storedRegister);
    }
}
