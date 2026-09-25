package kmu.maplayers.politicalmap;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.ownermap.MapLayerViewRegistry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins which half of the installer each half of the pair is. The two calls are one word apart and
 * nothing downstream would notice them swapped: the framework asks for a stand-up on load and gets a
 * sector cleared of the listeners it is about to need, which reads as a map that simply stopped
 * updating.
 *
 * <p>What each half registers is {@link PoliticalMapInstallerTest}'s, and when the framework asks for
 * either is the standings' own; this is the wiring between them and nothing else.
 */
final class PoliticalMapStandingTest {

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    // The layer's views, handed through to the heal the install runs first.
    private final MapLayerViewRegistry viewRegistryMock = mock(MapLayerViewRegistry.class);

    private final PoliticalMapStanding standing = new PoliticalMapStanding(viewRegistryMock);

    @Nested
    class StandLayerUpOn {

        @Test
        void standLayerUpOnInstallsThePoliticalMapOnThatSector() {

            try (var installerMock = mockStatic(PoliticalMapInstaller.class)) {

                standing.standLayerUpOn(sectorMock);

                installerMock.verify(() -> PoliticalMapInstaller.installAll(sectorMock, viewRegistryMock));
            }
        }
    }

    @Nested
    class StandLayerDownFrom {

        @Test
        void standLayerDownFromClearsThePoliticalMapFromThatSector() {

            try (var installerMock = mockStatic(PoliticalMapInstaller.class)) {

                standing.standLayerDownFrom(sectorMock);

                installerMock.verify(() -> PoliticalMapInstaller.uninstallAll(sectorMock));
            }
        }
    }
}
