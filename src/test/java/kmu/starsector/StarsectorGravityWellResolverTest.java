package kmu.starsector;

import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorGravityWellResolverTest {
    private final StarsectorGravityWellResolver resolver = new StarsectorGravityWellResolver();

    @Test
    void returnsSystemCenterWhenEntityHasNoOrbitChain() {
        SectorEntityToken center = entity("Corvus", null, null);
        SectorEntityToken planet = entity("Valis", null, null);
        // Use primaryEntity (SectorEntityToken) to avoid PlanetAPI cast from getPlanetEntity()
        MarketAPI market = market(null, planet, system(center, null));

        assertThat(resolver.resolve(market)).isSameAs(center);
    }

    @Test
    void returnsEntityItselfWhenNoOrbitChainAndNoSystem() {
        // When neither an orbit chain nor a system center is available, the entity
        // is the best known anchor point.
        SectorEntityToken station = entity("Station Alpha", null, null);
        MarketAPI market = market(null, station, null);

        assertThat(resolver.resolve(market)).isSameAs(station);
    }

    @Test
    void returnsStarWhenSystemHasNoCenterButHasStar() {
        SectorEntityToken planet = entity("Valis", null, null);
        // getStar() declares PlanetAPI as return type, so the proxy must implement PlanetAPI
        SectorEntityToken star = planetProxy("Corvus");
        MarketAPI market = market(null, planet, system(null, star));

        assertThat(resolver.resolve(market)).isSameAs(star);
    }

    @Test
    void resolvesViaOrbitApiWhenDirectOrbitFocusIsUnavailable() {
        // getOrbitFocus() is unavailable; getOrbit().getFocus() provides the focus.
        SectorEntityToken star = entity("Corvus", null, null);
        OrbitAPI orbit = orbitWith(star);
        SectorEntityToken moon = entityWithOrbitApi("Valis", orbit);
        MarketAPI market = market(null, moon, null);

        assertThat(resolver.resolve(market)).isSameAs(star);
    }

    @Test
    void detectsOrbitCycleAndReturnsLastValidFocus() {
        // A->B->A: the cycle is caught when B's focus (A) is already in visited set.
        // The resolver returns B, the last focus reached before the cycle.
        SectorEntityToken[] nodes = new SectorEntityToken[2];
        SectorEntityToken[] focusHolder = new SectorEntityToken[1];
        nodes[1] = proxy(SectorEntityToken.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return "node1";
                case "getOrbitFocus": return focusHolder[0]; // points back to node0
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
        nodes[0] = entity("node0", nodes[1], null);
        focusHolder[0] = nodes[0]; // close the cycle: node1 -> node0
        MarketAPI market = market(null, nodes[0], null);

        assertThat(resolver.resolve(market)).isSameAs(nodes[1]);
    }

    @Test
    void haltAtMaxChainDepthAndReturnsLastFocusWithinLimit() {
        // Chain of 34 nodes (node0..node33). MAX_ORBIT_FOCUS_CHAIN_DEPTH=32 means
        // the resolver takes 32 steps from node0, reaching node32, and stops before node33.
        int chainLength = 34;
        SectorEntityToken[] nodes = new SectorEntityToken[chainLength];
        nodes[chainLength - 1] = entity("node" + (chainLength - 1), null, null);
        for (int i = chainLength - 2; i >= 0; i--) {
            nodes[i] = entity("node" + i, nodes[i + 1], null);
        }
        MarketAPI market = market(null, nodes[0], null);

        assertThat(resolver.resolve(market)).isSameAs(nodes[32]);
    }

    // --- proxy helpers ---

    private static SectorEntityToken entity(
            String name, SectorEntityToken orbitFocus, OrbitAPI orbit) {
        return proxy(SectorEntityToken.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return name;
                case "getOrbitFocus": return orbitFocus;
                case "getOrbit": return orbit;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static SectorEntityToken entityWithOrbitApi(String name, OrbitAPI orbit) {
        // Intentionally does not handle getOrbitFocus so readValueOrNull returns null
        // and the resolver falls back to the getOrbit() path.
        return proxy(SectorEntityToken.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return name;
                case "getOrbit": return orbit;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    /** Returns a PlanetAPI proxy. Required when the token is returned from getStar(). */
    private static SectorEntityToken planetProxy(String name) {
        return proxy(PlanetAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return name;
                case "getOrbitFocus": return null;
                case "getOrbit": return null;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static OrbitAPI orbitWith(SectorEntityToken focus) {
        return proxy(OrbitAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getFocus": return focus;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static StarSystemAPI system(SectorEntityToken center, SectorEntityToken star) {
        return proxy(StarSystemAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getCenter": return center;
                case "getStar": return star;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    /**
     * Builds a market proxy. Pass the entity as primaryEntity (not planet) to avoid
     * a ClassCastException: getPlanetEntity() declares PlanetAPI, but the tests use
     * plain SectorEntityToken proxies as entities.
     */
    private static MarketAPI market(
            PlanetAPI planet, SectorEntityToken primaryEntity, StarSystemAPI system) {
        return proxy(MarketAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getPlanetEntity": return planet;
                case "getPrimaryEntity": return primaryEntity;
                case "getStarSystem": return system;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static Object handleObjectMethodOrThrow(Object proxy, Method method, Object[] args) {
        if (method.getDeclaringClass().equals(Object.class)) {
            switch (method.getName()) {
                case "toString":
                    return proxy.getClass().getInterfaces()[0].getSimpleName() + "Proxy";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == args[0];
                default:
                    break;
            }
        }
        throw new UnsupportedOperationException(method.toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
