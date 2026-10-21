package kmu.starsector;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.entities.EntityOrbits;

public final class StarsectorGravityWellResolver {

    /**
     * Finds the largest known orbit focus attracting the market entity.
     *
     * <p>The resolver starts from the market planet, then the primary entity, and
     * takes the outermost body its orbit chain reaches. If the entity has no usable
     * orbit chain, it falls back to the star system center or star.</p>
     */
    public SectorEntityToken resolve(MarketAPI market) {

        SectorEntityToken entity = market.getPlanetEntity();
        if (entity == null) {
            entity = market.getPrimaryEntity();
        }

        if (entity == null) {
            return resolveSystemCenterOrStar(market);
        }

        var orbitRoot = EntityOrbits.findOutermostFocus(entity);
        if (orbitRoot != null) {
            return orbitRoot;
        }

        var systemRoot = resolveSystemCenterOrStar(market);
        return systemRoot == null
            ? entity
            : systemRoot;
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
