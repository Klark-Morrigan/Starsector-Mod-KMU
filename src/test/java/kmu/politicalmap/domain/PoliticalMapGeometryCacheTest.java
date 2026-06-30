package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PoliticalMapGeometryCache}: the first update builds outlines for
 * the reachable systems and skips inaccessible ones; a later update rebuilds
 * only the cells near a changed system, leaving distant ones in place (asserted
 * by object identity); and a removed system's outline is dropped.
 */
final class PoliticalMapGeometryCacheTest {
    // Comfortably beyond 2 * MAX_CELL_RADIUS (8000), so changes near one do not
    // reach the other.
    private static final float FAR = 20_000f;

    @Nested
    class UpdateFromSector {

        @Test
        void updateBuildsOutlinesForReachableSystemsAndSkipsInaccessibleOnes() {
            var cache = new PoliticalMapGeometryCache();

            cache.updateFromSector(sectorOf(
                    accessibleSystem("a", 0, 0),
                    accessibleSystem("b", 4000, 0),
                    inaccessibleSystem("hidden", 8000, 0)));

            assertThat(cache.getOutlineBySystemId()).containsOnlyKeys("a", "b");
            assertThat(cache.getOutlineBySystemId().get("a")).isNotEmpty();
        }

        @Test
        void updateLeavesDistantCellsUntouchedWhenASystemIsAdded() {
            var cache = new PoliticalMapGeometryCache();
            cache.updateFromSector(sectorOf(accessibleSystem("a", 0, 0), accessibleSystem("b", FAR, 0)));
            var distantBefore = cache.getOutlineBySystemId().get("b");

            // Add a system next to "a"; "b" is far away, so its outline must be the
            // very same object - proof it was not recomputed.
            cache.updateFromSector(sectorOf(
                    accessibleSystem("a", 0, 0),
                    accessibleSystem("b", FAR, 0),
                    accessibleSystem("c", 100, 0)));

            assertThat(cache.getOutlineBySystemId()).containsKey("c");
            assertThat(cache.getOutlineBySystemId().get("b")).isSameAs(distantBefore);
        }

        @Test
        void updateDropsTheOutlineOfASystemThatLosesAccess() {
            var cache = new PoliticalMapGeometryCache();
            cache.updateFromSector(sectorOf(accessibleSystem("a", 0, 0), accessibleSystem("b", FAR, 0)));

            cache.updateFromSector(sectorOf(accessibleSystem("a", 0, 0)));

            assertThat(cache.getOutlineBySystemId()).containsOnlyKeys("a");
        }

        @Test
        void updateSeedsAnUnreachableSystemHoldingARevealedDecivilisedPlanet() {
            // No jump point, so the access rule rejects it, but the revealed ruin
            // makes it inhabited - it must still seed a cell.
            var cache = new PoliticalMapGeometryCache();

            cache.updateFromSector(sectorOf(
                    accessibleSystem("a", 0, 0),
                    decivilisedUnreachableSystem("ruin", 4000, 0)));

            assertThat(cache.getOutlineBySystemId()).containsOnlyKeys("a", "ruin");
        }
    }

    private static SectorAPI sectorOf(StarSystemAPI... systems) {
        // The hyperspace mock is built before the getHyperspace() stubbing so its
        // own stubbing is not nested inside this one.
        var hyperspaceMock = hyperspaceWithVisibleStarAnchorsFor(systems);
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systems));
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    // A visible (untagged) star anchor at each system's location, so a system
    // with a jump point reads as map-visible. A cut-off or jump-point-less system
    // stays off the map regardless, so its co-located anchor cannot admit it.
    private static LocationAPI hyperspaceWithVisibleStarAnchorsFor(StarSystemAPI... systems) {
        var anchors = new ArrayList<JumpPointAPI>();
        for (var system : systems) {
            // Read the location before stubbing, so this mock call is not nested
            // inside the getLocation() stubbing on the anchor.
            var location = system.getLocation();
            var anchorMock = mock(JumpPointAPI.class);
            when(anchorMock.isStarAnchor()).thenReturn(true);
            when(anchorMock.getLocation()).thenReturn(location);
            anchors.add(anchorMock);
        }
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(anchors);
        return hyperspaceMock;
    }

    private static StarSystemAPI accessibleSystem(String id, float x, float y) {
        // Wired into hyperspace by a jump point, so the access rule admits it.
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(x, y));
        when(systemMock.getJumpPoints()).thenReturn(List.of(mock(SectorEntityToken.class)));
        return systemMock;
    }

    private static StarSystemAPI inaccessibleSystem(String id, float x, float y) {
        var system = accessibleSystem(id, x, y);
        when(system.hasTag(Tags.SYSTEM_CUT_OFF_FROM_HYPER)).thenReturn(true);
        when(system.getEntitiesWithTag(Tags.GATE)).thenReturn(List.of());
        return system;
    }

    private static StarSystemAPI decivilisedUnreachableSystem(String id, float x, float y) {
        // No jump point (Mockito defaults the list empty) and not cut off, so the
        // access rule rejects it; the revealed decivilised planet is its only
        // route onto the map. The planet is built before the getPlanets() stubbing
        // so Mockito does not see one stubbing nested inside another.
        var planet = decivilisedPlanet();
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(x, y));
        when(systemMock.getPlanets()).thenReturn(List.of(planet));
        return systemMock;
    }

    private static PlanetAPI decivilisedPlanet() {
        var conditionMock = mock(MarketConditionAPI.class);
        when(conditionMock.requiresSurveying()).thenReturn(false);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getSurveyLevel()).thenReturn(MarketAPI.SurveyLevel.FULL);
        when(marketMock.getSpecificCondition(Conditions.DECIVILIZED)).thenReturn(conditionMock);
        var planetMock = mock(PlanetAPI.class);
        when(planetMock.getMarket()).thenReturn(marketMock);
        return planetMock;
    }
}
