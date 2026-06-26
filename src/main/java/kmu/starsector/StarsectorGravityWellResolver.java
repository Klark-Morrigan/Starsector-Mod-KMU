package kmu.starsector;

import com.fs.starfarer.api.campaign.OrbitAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class StarsectorGravityWellResolver {
    private static final int MAX_ORBIT_FOCUS_CHAIN_DEPTH = 32;

    /**
     * Finds the largest known orbit focus attracting the market entity.
     *
     * <p>The resolver starts from the market planet, then the primary entity, and
     * walks each orbit focus outward until the chain ends, loops, or reaches the
     * depth guard. If the entity has no usable orbit chain, it falls back to the
     * star system center or star.</p>
     */
    public SectorEntityToken resolve(MarketAPI market) {
        SectorEntityToken entity = market.getPlanetEntity();
        if (entity == null) {
            entity = market.getPrimaryEntity();
        }
        if (entity == null) {
            return resolveSystemCenterOrStar(market);
        }

        var orbitRoot = resolveTerminalOrbitFocus(entity);
        if (orbitRoot != null) {
            return orbitRoot;
        }

        var systemRoot = resolveSystemCenterOrStar(market);
        return systemRoot == null ? entity : systemRoot;
    }

    private SectorEntityToken resolveTerminalOrbitFocus(SectorEntityToken entity) {
        var visited =
                Collections.newSetFromMap(new IdentityHashMap<SectorEntityToken, Boolean>());
        var current = entity;
        SectorEntityToken lastFocus = null;

        for (var depth = 0;
                depth < MAX_ORBIT_FOCUS_CHAIN_DEPTH && current != null && !visited.contains(current);
                depth++) {
            visited.add(current);
            var focus = resolveOrbitFocus(current);
            if (focus == null || focus == current || visited.contains(focus)) {
                break;
            }
            lastFocus = focus;
            current = focus;
        }

        return lastFocus;
    }

    private SectorEntityToken resolveOrbitFocus(SectorEntityToken entity) {
        var focus = entity.getOrbitFocus();
        if (focus != null) {
            return focus;
        }

        var orbit = entity.getOrbit();
        return orbit == null ? null : orbit.getFocus();
    }

    private SectorEntityToken resolveSystemCenterOrStar(MarketAPI market) {
        var system = market.getStarSystem();
        if (system == null) {
            return null;
        }

        var center = system.getCenter();
        if (center != null) {
            return center;
        }
        return system.getStar();
    }
}
