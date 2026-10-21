package kmu.starsector;

import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StarsectorGravityWellResolverTests {

    private final StarsectorGravityWellResolver resolver = new StarsectorGravityWellResolver();

    @Nested
    class Resolve {

        @Test
        void returnsSystemCenterWhenEntityHasNoOrbitChain() {

            var center = buildEntity("Corvus");
            var planet = buildEntity("Valis");
            var market = buildMarket(null, planet, buildSystem(center, null));

            assertThat(resolver.resolve(market))
                .isSameAs(center);
        }

        @Test
        void returnsEntityItselfWhenNoOrbitChainAndNoSystem() {
            // When neither an orbit chain nor a system center is available, the entity
            // is the best known anchor point.
            var station = buildEntity("Station Alpha");
            var market = buildMarket(null, station, null);

            assertThat(resolver.resolve(market))
                .isSameAs(station);
        }

        @Test
        void returnsStarWhenSystemHasNoCenterButHasStar() {

            var planet = buildEntity("Valis");

            // getStar() returns PlanetAPI, so the mock must implement PlanetAPI.
            var starMock = mock(PlanetAPI.class);

            when(starMock.getName())
                .thenReturn("Corvus");

            var market = buildMarket(null, planet, buildSystem(null, starMock));

            assertThat(resolver.resolve(market))
                .isSameAs(starMock);
        }

        @Test
        void returnsOutermostFocusOverSystemCenterWhenEntityHasOrbitChain() {

            var center = buildEntity("Corvus");
            var star = buildEntity("Corvus Secondary");
            var planet = buildEntity("Valis");
            var station = buildEntity("Valis Orbital");

            when(planet.getOrbitFocus())
                .thenReturn(star);
            when(station.getOrbitFocus())
                .thenReturn(planet);

            var market = buildMarket(null, station, buildSystem(center, null));

            assertThat(resolver.resolve(market))
                .isSameAs(star);
        }
    }

    // --- mock helpers ---

    private static SectorEntityToken buildEntity(String name) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.getName())
            .thenReturn(name);

        return entityMock;
    }

    private static StarSystemAPI buildSystem(SectorEntityToken center, PlanetAPI star) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getCenter())
            .thenReturn(center);
        when(systemMock.getStar())
            .thenReturn(star);

        return systemMock;
    }

    private static MarketAPI buildMarket(
            PlanetAPI planet,
            SectorEntityToken primaryEntity,
            StarSystemAPI system) {

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getPlanetEntity())
            .thenReturn(planet);
        when(marketMock.getPrimaryEntity())
            .thenReturn(primaryEntity);
        when(marketMock.getStarSystem())
            .thenReturn(system);

        return marketMock;
    }
}
