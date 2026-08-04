package kmu.starsector;

import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StarsectorGravityWellResolverTest {

    private final StarsectorGravityWellResolver resolver = new StarsectorGravityWellResolver();

    @Nested
    class Resolve {

        @Test
        void returnsSystemCenterWhenEntityHasNoOrbitChain() {
            var center = buildEntity("Corvus");
            var planet = buildEntity("Valis");
            var market = buildMarket(null, planet, buildSystem(center, null));

            assertThat(resolver.resolve(market)).isSameAs(center);
        }

        @Test
        void returnsEntityItselfWhenNoOrbitChainAndNoSystem() {
            // When neither an orbit chain nor a system center is available, the entity
            // is the best known anchor point.
            var station = buildEntity("Station Alpha");
            var market = buildMarket(null, station, null);

            assertThat(resolver.resolve(market)).isSameAs(station);
        }

        @Test
        void returnsStarWhenSystemHasNoCenterButHasStar() {
            var planet = buildEntity("Valis");
            // getStar() returns PlanetAPI, so the mock must implement PlanetAPI.
            var starMock = mock(PlanetAPI.class);
            when(starMock.getName()).thenReturn("Corvus");
            var market = buildMarket(null, planet, buildSystem(null, starMock));

            assertThat(resolver.resolve(market)).isSameAs(starMock);
        }

        @Test
        void resolvesViaOrbitApiWhenDirectOrbitFocusIsUnavailable() {
            // getOrbitFocus() returns null; getOrbit().getFocus() provides the focus.
            var star = buildEntity("Corvus");
            var orbitMock = mock(OrbitAPI.class);
            when(orbitMock.getFocus()).thenReturn(star);
            var moonMock = mock(SectorEntityToken.class);
            when(moonMock.getName()).thenReturn("Valis");
            when(moonMock.getOrbit()).thenReturn(orbitMock);
            // getOrbitFocus defaults to null on a Mockito mock.
            var market = buildMarket(null, moonMock, null);

            assertThat(resolver.resolve(market)).isSameAs(star);
        }

        @Test
        void detectsOrbitCycleAndReturnsLastValidFocus() {
            // A->B->A: the cycle is caught when B's focus (A) is already in visited set.
            // The resolver returns B, the last focus reached before the cycle.
            var node0Mock = mock(SectorEntityToken.class);
            var node1Mock = mock(SectorEntityToken.class);
            when(node0Mock.getOrbitFocus()).thenReturn(node1Mock);
            when(node1Mock.getOrbitFocus()).thenReturn(node0Mock);
            var market = buildMarket(null, node0Mock, null);

            assertThat(resolver.resolve(market)).isSameAs(node1Mock);
        }

        @Test
        void haltAtMaxChainDepthAndReturnsLastFocusWithinLimit() {
            // Chain of 34 nodes (node0..node33). MAX_ORBIT_FOCUS_CHAIN_DEPTH=32 means
            // the resolver takes 32 steps from node0, reaching node32, and stops before node33.
            var chainLength = 34;
            var nodes = new SectorEntityToken[chainLength];
            for (var i = 0; i < chainLength; i++) {
                nodes[i] = mock(SectorEntityToken.class);
            }
            for (var i = 0; i < chainLength - 1; i++) {
                when(nodes[i].getOrbitFocus()).thenReturn(nodes[i + 1]);
            }
            var market = buildMarket(null, nodes[0], null);

            assertThat(resolver.resolve(market)).isSameAs(nodes[32]);
        }
    }

    // --- mock helpers ---

    private static SectorEntityToken buildEntity(String name) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.getName()).thenReturn(name);
        return entityMock;
    }

    private static StarSystemAPI buildSystem(SectorEntityToken center, PlanetAPI star) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getCenter()).thenReturn(center);
        when(systemMock.getStar()).thenReturn(star);
        return systemMock;
    }

    private static MarketAPI buildMarket(
            PlanetAPI planet, SectorEntityToken primaryEntity, StarSystemAPI system) {
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getPlanetEntity()).thenReturn(planet);
        when(marketMock.getPrimaryEntity()).thenReturn(primaryEntity);
        when(marketMock.getStarSystem()).thenReturn(system);
        return marketMock;
    }
}
