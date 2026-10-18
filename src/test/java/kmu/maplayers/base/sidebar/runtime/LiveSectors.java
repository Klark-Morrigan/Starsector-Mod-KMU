package kmu.maplayers.base.sidebar.runtime;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;

import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stands in the running game's sector for a host, which reads both its picks and the memory they are
 * saved in off the live sector rather than being handed either.
 *
 * <p>One arrangement for every host suite, because a host resolves its screens through the machinery
 * installed on whatever sector is running: a sector posed without the screens seated on it reads the
 * unwired screens instead, and a case written against those would pass while pinning nothing.
 */
final class LiveSectors {

    private LiveSectors() {
    }

    // Answers the running game's sector with one carrying the given memory, its machinery installed
    // with the given screens seated on it. The caller releases the machinery afterwards, the index
    // being process-wide.
    static SectorAPI standInLiveSector(
            MockedStatic<Global> globalMock,
            MemoryAPI memory,
            MapLayerScreens screens) {

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getMemoryWithoutUpdate())
            .thenReturn(memory);

        globalMock
            .when(Global::getSector)
            .thenReturn(sectorMock);

        SectorMapMachineryIndex.installMachineryOn(sectorMock, screens);

        return sectorMock;
    }
}
