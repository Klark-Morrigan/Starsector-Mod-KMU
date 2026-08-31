package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.maplayers.base.installation.MapLayerInstallations;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The surroundings a render surface needs before it will draw at all: a terrain entity, the location
 * that entity sits in, and the machinery installed on the sector owning it.
 *
 * <p>A surface finds the sector it is drawing through its own entity, so a plugin built bare draws
 * nothing whatever else a case has staged. Seating one is therefore the shared setup of every case
 * about what a surface paints, and it is here rather than copied per suite so all three surfaces are
 * posed the same way.
 */
final class MapSurfaceFixtures {

    private MapSurfaceFixtures() {
        // fixture of static wiring, no instances.
    }

    /**
     * Seats every one of {@code surfaces} on one sector with the map machinery installed on it,
     * which is how the several surfaces painting a single map are posed.
     *
     * @param surfaces the surfaces being posed
     * @return that sector's installation, which is what each of them resolves as it draws
     */
    static MapLayerInstallation seatSurfacesInAnInstalledSector(CampaignTerrainPlugin... surfaces) {

        var hyperspaceMock = mock(LocationAPI.class);
        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getHyperspace())
            .thenReturn(hyperspaceMock);

        for (var surface : surfaces) {
            seatSurfaceIn(surface, hyperspaceMock);
        }
        return MapLayerInstallations.installMachineryOn(sectorMock);
    }

    /**
     * Seats {@code surface}'s terrain entity in {@code location}, installing nothing - which is how a
     * surface belonging to a sector nothing is drawing is posed.
     *
     * @param surface  the surface being posed
     * @param location where its terrain sits
     */
    static void seatSurfaceIn(CampaignTerrainPlugin surface, LocationAPI location) {

        var terrainEntityMock = mock(SectorEntityToken.class);

        when(terrainEntityMock.getContainingLocation())
            .thenReturn(location);

        surface.setEntity(terrainEntityMock);
    }
}
