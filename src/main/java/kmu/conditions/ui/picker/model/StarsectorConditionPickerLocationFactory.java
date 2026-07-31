package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.relation.StarsectorPlayerRelationshipFormatter;

import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.domain.StarsectorEditableMarket;
import kmu.starsector.StarsectorGravityWellResolver;

import java.util.Objects;

import static kmu.util.KmuValues.normalizeText;

final class StarsectorConditionPickerLocationFactory {
    private final StarsectorGravityWellResolver gravityWellResolver;

    StarsectorConditionPickerLocationFactory() {
        this(new StarsectorGravityWellResolver());
    }

    StarsectorConditionPickerLocationFactory(StarsectorGravityWellResolver gravityWellResolver) {
        this.gravityWellResolver = Objects.requireNonNull(gravityWellResolver, "gravityWellResolver");
    }

    /**
     * Builds the picker's display-only location metadata from a Starsector market.
     *
     * <p>Missing API reads (null returns) are converted to absent fields. The
     * picker renderer decides which absent fields to omit; this factory does not
     * decide whether the picker should open. Any {@link RuntimeException} from a
     * modded API implementation propagates to the caller.</p>
     */
    KmuConditionPickerLocation create(KmuEditableMarket market) {
        if (!(market instanceof StarsectorEditableMarket)) {
            return unknownLocation();
        }

        var starsectorMarket = ((StarsectorEditableMarket) market).getMarket();
        if (starsectorMarket == null) {
            return unknownLocation();
        }

        var faction = factionFor(starsectorMarket);
        return new KmuConditionPickerLocation(
            planetName(starsectorMarket),
            planetType(starsectorMarket),
            pickerFaction(faction),
            starSystemName(starsectorMarket),
            gravityWellTypeName(starsectorMarket),
            gravityWellName(starsectorMarket),
            constellationName(starsectorMarket));
    }

    private KmuConditionPickerLocation unknownLocation() {
        return new KmuConditionPickerLocation(
            null,
            null,
            null,
            null,
            null,
            null,
            null);
    }

    private String marketName(MarketAPI market) {
        return normalizeText(market.getName());
    }

    private String planetName(MarketAPI market) {
        var planet = market.getPlanetEntity();
        var planetName = planet == null ? null : normalizeText(planet.getName());
        if (planetName != null) {
            return planetName;
        }

        var primaryEntity = market.getPrimaryEntity();
        var entityName = primaryEntity == null ? null : normalizeText(primaryEntity.getName());
        return entityName == null ? marketName(market) : entityName;
    }

    private String planetType(MarketAPI market) {
        var planet = market.getPlanetEntity();
        if (planet == null) {
            var primaryEntity = market.getPrimaryEntity();
            if (primaryEntity instanceof PlanetAPI) {
                planet = (PlanetAPI) primaryEntity;
            }
        }

        var planetType = planet == null ? null : planetTypeName(planet);
        if (planetType != null) {
            return planetType;
        }

        return entityTypeName(market.getPrimaryEntity());
    }

    private FactionAPI factionFor(MarketAPI market) {
        var faction = market.getFaction();
        if (faction != null) {
            return faction;
        }

        var primaryEntity = market.getPrimaryEntity();
        return primaryEntity == null ? null : primaryEntity.getFaction();
    }

    private KmuPickerFaction pickerFaction(FactionAPI faction) {
        if (faction == null) {
            return null;
        }

        var name = normalizeText(faction.getDisplayNameLong());
        if (name == null) {
            name = normalizeText(faction.getDisplayName());
        }
        if (name == null) {
            return null;
        }

        var color = faction.getBaseUIColor();
        if (color == null) {
            color = faction.getColor();
        }

        var crestSprite = FactionCrests.resolveCrestPath(faction);
        var relationship =
            StarsectorPlayerRelationshipFormatter.formatPlayerRelationship(faction);

        return new KmuPickerFaction(
            name,
            color,
            crestSprite,
            relationship.getDescription(),
            relationship.getColor());
    }

    private String starSystemName(MarketAPI market) {
        var system = market.getStarSystem();
        var systemName = system == null ? null : normalizeText(system.getNameWithTypeShort());
        if (systemName == null && system != null) {
            systemName = normalizeText(system.getName());
        }
        if (systemName != null) {
            return systemName;
        }

        var location = market.getContainingLocation();
        var locationName = location == null ? null : normalizeText(location.getNameWithTypeShort());
        if (locationName == null && location != null) {
            locationName = normalizeText(location.getName());
        }
        return locationName;
    }

    private String gravityWellTypeName(MarketAPI market) {
        var gravityWell = gravityWellResolver.resolve(market);
        return gravityWellDisplayName(gravityWell);
    }

    private String gravityWellName(MarketAPI market) {
        var gravityWell = gravityWellResolver.resolve(market);
        return gravityWell == null ? null : normalizeText(gravityWell.getName());
    }

    private String entityTypeName(SectorEntityToken entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof PlanetAPI) {
            var planetTypeName = planetTypeName((PlanetAPI) entity);
            if (planetTypeName != null) {
                return planetTypeName;
            }
        }

        var customSpecName = entity.getCustomEntitySpec() == null
            ? null
            : normalizeText(entity.getCustomEntitySpec().getNameInText());
        if (customSpecName != null) {
            return customSpecName;
        }

        var customType = normalizeText(entity.getCustomEntityType());
        if (customType != null) {
            return customType;
        }

        var entityName = normalizeText(entity.getName());
        if (entityName != null) {
            return entityName;
        }

        return entity.isSystemCenter() ? "center of gravity" : null;
    }

    private String gravityWellDisplayName(SectorEntityToken entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof PlanetAPI) {
            var planetTypeName = planetTypeName((PlanetAPI) entity);
            if (planetTypeName != null) {
                return planetTypeName;
            }
        }

        var entityName = normalizeText(entity.getName());
        if (entityName != null) {
            return entityName;
        }

        return entityTypeName(entity);
    }

    private String planetTypeName(PlanetAPI planet) {
        var typeName = normalizeText(planet.getTypeNameWithWorld());
        if (typeName != null) {
            return typeName;
        }
        typeName = normalizeText(planet.getTypeNameWithWorldLowerCase());
        if (typeName != null) {
            return typeName;
        }
        return planet.getSpec() == null
            ? null
            : normalizeText(planet.getSpec().getName());
    }

    private String constellationName(MarketAPI market) {
        var system = market.getStarSystem();
        var constellation = system == null ? null : system.getConstellation();
        if (constellation == null) {
            var location = market.getContainingLocation();
            constellation = location == null ? null : location.getConstellation();
        }

        var constellationName = constellation == null
            ? null
            : normalizeText(constellation.getNameWithType());
            
        if (constellationName == null && constellation != null) {
            constellationName = normalizeText(constellation.getName());
        }
        return constellationName;
    }
}
