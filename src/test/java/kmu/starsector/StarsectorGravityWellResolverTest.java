package kmu.starsector;

import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StarsectorGravityWellResolverTest {
    private final StarsectorGravityWellResolver resolver = new StarsectorGravityWellResolver();

    @Test
    void returnsSystemCenterWhenEntityHasNoOrbitChain() {
        var center = entity("Corvus");
        var planet = entity("Valis");
        var market = market(null, planet, system(center, null));

        assertThat(resolver.resolve(market)).isSameAs(center);
    }

    @Test
    void returnsEntityItselfWhenNoOrbitChainAndNoSystem() {
        // When neither an orbit chain nor a system center is available, the entity
        // is the best known anchor point.
        var station = entity("Station Alpha");
        var market = market(null, station, null);

        assertThat(resolver.resolve(market)).isSameAs(station);
    }

    @Test
    void returnsStarWhenSystemHasNoCenterButHasStar() {
        var planet = entity("Valis");
        // getStar() returns PlanetAPI, so the mock must implement PlanetAPI.
        var star = mock(PlanetAPI.class);
        when(star.getName()).thenReturn("Corvus");
        var market = market(null, planet, system(null, star));

        assertThat(resolver.resolve(market)).isSameAs(star);
    }

    @Test
    void resolvesViaOrbitApiWhenDirectOrbitFocusIsUnavailable() {
        // getOrbitFocus() returns null; getOrbit().getFocus() provides the focus.
        var star = entity("Corvus");
        var orbit = mock(OrbitAPI.class);
        when(orbit.getFocus()).thenReturn(star);
        var moon = mock(SectorEntityToken.class);
        when(moon.getName()).thenReturn("Valis");
        when(moon.getOrbit()).thenReturn(orbit);
        // getOrbitFocus defaults to null on a Mockito mock.
        var market = market(null, moon, null);

        assertThat(resolver.resolve(market)).isSameAs(star);
    }

    @Test
    void detectsOrbitCycleAndReturnsLastValidFocus() {
        // A->B->A: the cycle is caught when B's focus (A) is already in visited set.
        // The resolver returns B, the last focus reached before the cycle.
        var node0 = mock(SectorEntityToken.class);
        var node1 = mock(SectorEntityToken.class);
        when(node0.getOrbitFocus()).thenReturn(node1);
        when(node1.getOrbitFocus()).thenReturn(node0);
        var market = market(null, node0, null);

        assertThat(resolver.resolve(market)).isSameAs(node1);
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
        var market = market(null, nodes[0], null);

        assertThat(resolver.resolve(market)).isSameAs(nodes[32]);
    }

    // --- mock helpers ---

    private static SectorEntityToken entity(String name) {
        var entity = mock(SectorEntityToken.class);
        when(entity.getName()).thenReturn(name);
        return entity;
    }

    private static StarSystemAPI system(SectorEntityToken center, PlanetAPI star) {
        var system = mock(StarSystemAPI.class);
        when(system.getCenter()).thenReturn(center);
        when(system.getStar()).thenReturn(star);
        return system;
    }

    private static MarketAPI market(
            PlanetAPI planet, SectorEntityToken primaryEntity, StarSystemAPI system) {
        var market = mock(MarketAPI.class);
        when(market.getPlanetEntity()).thenReturn(planet);
        when(market.getPrimaryEntity()).thenReturn(primaryEntity);
        when(market.getStarSystem()).thenReturn(system);
        return market;
    }
}
