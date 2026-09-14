package kmu.maplayers.base.refresh;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.SectorColonySightings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Pins the sweep the substrate owns: every system in the sector is handed to the sighting register
 * on every poll, no layer being asked whether it wants one. What an entry then means, and what the
 * register does with a place it is handed twice, are the register's own suite's business.
 *
 * <p>The register is static-mocked rather than written for real. What this class decides is which
 * places are swept and how often, and that reads off the calls; the write landing in sector memory
 * is pinned where the poll is driven end to end.
 */
final class MapSubstrateStalenessSourceTest {

    private static final String ALPHA_ID = "alpha";
    private static final String BETA_ID = "beta";

    // Two systems and two polls, which is what separates "once per system" from "once per sweep"
    // and "every poll" from "the first one only".
    private static final int TWO_SYSTEMS_OVER_TWO_POLLS = 4;

    @Nested
    class MarkChangesSinceLastPoll {

        @Test
        void handsEverySystemToTheRegisterOnEveryPoll() {
            // Nothing announces a colony arriving among witnesses, so there is nothing to diff
            // and no baseline to seed: every poll sweeps the whole sector, the first included.
            try (var sightingsMock = mockStatic(SectorColonySightings.class)) {

                var source = new MapSubstrateStalenessSource(
                    buildSectorWithSystems(ALPHA_ID, BETA_ID));

                source.markChangesSinceLastPoll();
                source.markChangesSinceLastPoll();

                sightingsMock.verify(
                    () -> SectorColonySightings.recordSightingsByInhabitants(
                        any(SectorAPI.class),
                        any(StarSystemAPI.class),
                        any(Colonies.class),
                        any(ColonyKnowledge.class)),
                    times(TWO_SYSTEMS_OVER_TWO_POLLS));
            }
        }

        @Test
        void sweepsNothingWithoutASector() {
            // Mid-load, before the sector stands up. The poll is installed against the sector it
            // was made over, so a null one is a sweep over nothing rather than a fault.
            try (var sightingsMock = mockStatic(SectorColonySightings.class)) {

                new MapSubstrateStalenessSource(null)
                    .markChangesSinceLastPoll();

                sightingsMock.verifyNoInteractions();
            }
        }

        @Test
        void sweepsNothingWhereTheSectorListsNoSystems() {
            // A sector that answers null for its systems - which the engine does mid-generation -
            // rather than an empty list.
            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getStarSystems())
                .thenReturn(null);

            try (var sightingsMock = mockStatic(SectorColonySightings.class)) {

                var sweepOverASectorWithNoSystems = (Runnable) () ->
                    new MapSubstrateStalenessSource(sectorMock).markChangesSinceLastPoll();

                assertThatCode(sweepOverASectorWithNoSystems::run)
                    .doesNotThrowAnyException();

                sightingsMock.verifyNoInteractions();
            }
        }
    }

    // A sector listing one mock system per ID, which is all the sweep asks of it before handing
    // each on to the register.
    private static SectorAPI buildSectorWithSystems(String... systemIds) {

        // Built before the sector is stubbed: a mock made inside another mock's stubbing is what
        // Mockito reports as an unfinished stubbing.
        var systems = Arrays
            .stream(systemIds)
            .map(StarSystemFixture::buildSystem)
            .toList();

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(systems);

        return sectorMock;
    }
}
