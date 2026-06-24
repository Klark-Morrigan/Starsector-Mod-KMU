package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PoliticalMapBorderGeometryTest {
    @Test
    void buildBorderSegmentsReturnsFourComponentSegmentsForPopulatedSector() {
        SectorAPI sector = sectorWithSystemsAt(
                new float[] {0, 0},
                new float[] {4000, 0},
                new float[] {2000, 3000});

        List<double[]> segments = PoliticalMapBorderGeometry.buildBorderSegments(sector);

        assertThat(segments).isNotEmpty();
        assertThat(segments).allMatch(segment -> segment.length == 4);
    }

    @Test
    void buildBorderSegmentsReturnsEmptyForSectorWithoutSystems() {
        SectorAPI sector = mock(SectorAPI.class);
        when(sector.getStarSystems()).thenReturn(List.of());

        assertThat(PoliticalMapBorderGeometry.buildBorderSegments(sector)).isEmpty();
    }

    @Test
    void buildBorderSegmentsReturnsEmptyForNullSector() {
        assertThat(PoliticalMapBorderGeometry.buildBorderSegments(null)).isEmpty();
    }

    private static SectorAPI sectorWithSystemsAt(float[]... positions) {
        java.util.List<StarSystemAPI> systems = new java.util.ArrayList<>();
        for (float[] position : positions) {
            StarSystemAPI system = mock(StarSystemAPI.class);
            when(system.getLocation()).thenReturn(new Vector2f(position[0], position[1]));
            systems.add(system);
        }
        SectorAPI sector = mock(SectorAPI.class);
        when(sector.getStarSystems()).thenReturn(systems);
        return sector;
    }
}
