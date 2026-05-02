package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.domain.StarsectorEditableMarket;
import kmu.starsector.StarsectorGravityWellResolver;
import kmu.starsector.StarsectorPlayerRelationshipFormatter;
import kmu.starsector.StarsectorPlayerRelationshipFormatter.RelationshipSummary;

import java.awt.Color;
import java.util.Objects;

import static kmu.KmuValues.readTextOrNull;
import static kmu.KmuValues.readValueOrNull;

final class StarsectorConditionPickerLocationFactory {
    private final StarsectorGravityWellResolver gravityWellResolver;
    private final StarsectorPlayerRelationshipFormatter relationshipFormatter;

    StarsectorConditionPickerLocationFactory() {
        this(new StarsectorGravityWellResolver(), new StarsectorPlayerRelationshipFormatter());
    }

    StarsectorConditionPickerLocationFactory(
            StarsectorGravityWellResolver gravityWellResolver,
            StarsectorPlayerRelationshipFormatter relationshipFormatter) {
        this.gravityWellResolver = Objects.requireNonNull(gravityWellResolver, "gravityWellResolver");
        this.relationshipFormatter = Objects.requireNonNull(relationshipFormatter, "relationshipFormatter");
    }

    /**
     * Builds the picker's display-only location metadata from a Starsector market.
     *
     * <p>Missing or failing Starsector API reads are converted to absent fields. The
     * picker renderer decides which absent fields to omit; this factory does not
     * decide whether the picker should open.</p>
     */
    KmuConditionPickerLocation create(KmuEditableMarket market) {
        if (!(market instanceof StarsectorEditableMarket)) {
            return unknownLocation();
        }

        MarketAPI starsectorMarket = readValueOrNull(() -> ((StarsectorEditableMarket) market).getMarket());
        if (starsectorMarket == null) {
            return unknownLocation();
        }

        FactionAPI faction = factionFor(starsectorMarket);
        RelationshipSummary relationship = relationshipFormatter.formatPlayerRelationship(faction);
        return new KmuConditionPickerLocation(
                planetName(starsectorMarket),
                planetType(starsectorMarket),
                factionName(faction),
                factionColor(faction),
                relationship.getDescription(),
                relationship.getColor(),
                starSystemName(starsectorMarket),
                gravityWellName(starsectorMarket),
                constellationName(starsectorMarket));
    }

    private KmuConditionPickerLocation unknownLocation() {
        return new KmuConditionPickerLocation(
                null, null, null, null, null, null, null, null, null);
    }

    private String marketName(MarketAPI market) {
        return readTextOrNull(market::getName);
    }

    private String planetName(MarketAPI market) {
        PlanetAPI planet = readValueOrNull(market::getPlanetEntity);
        String planetName = planet == null ? null : readTextOrNull(planet::getName);
        if (planetName != null) {
            return planetName;
        }

        SectorEntityToken primaryEntity = readValueOrNull(market::getPrimaryEntity);
        String entityName = primaryEntity == null ? null : readTextOrNull(primaryEntity::getName);
        return entityName == null ? marketName(market) : entityName;
    }

    private String planetType(MarketAPI market) {
        PlanetAPI planet = readValueOrNull(market::getPlanetEntity);
        if (planet == null) {
            SectorEntityToken primaryEntity = readValueOrNull(market::getPrimaryEntity);
            if (primaryEntity instanceof PlanetAPI) {
                planet = (PlanetAPI) primaryEntity;
            }
        }

        String planetType = planet == null ? null : planetTypeName(planet);
        if (planetType != null) {
            return planetType;
        }

        SectorEntityToken primaryEntity = readValueOrNull(market::getPrimaryEntity);
        return entityTypeName(primaryEntity);
    }

    private FactionAPI factionFor(MarketAPI market) {
        FactionAPI faction = readValueOrNull(market::getFaction);
        if (faction != null) {
            return faction;
        }

        SectorEntityToken primaryEntity = readValueOrNull(market::getPrimaryEntity);
        return primaryEntity == null ? null : readValueOrNull(primaryEntity::getFaction);
    }

    private String factionName(FactionAPI faction) {
        if (faction == null) {
            return null;
        }

        String name = readTextOrNull(faction::getDisplayNameLong);
        if (name == null) {
            name = readTextOrNull(faction::getDisplayName);
        }
        return name;
    }

    private Color factionColor(FactionAPI faction) {
        if (faction == null) {
            return null;
        }

        Color color = readValueOrNull(faction::getBaseUIColor);
        if (color == null) {
            color = readValueOrNull(faction::getColor);
        }
        return color;
    }

    private String starSystemName(MarketAPI market) {
        StarSystemAPI system = readValueOrNull(market::getStarSystem);
        String systemName = system == null ? null : readTextOrNull(system::getNameWithTypeShort);
        if (systemName == null && system != null) {
            systemName = readTextOrNull(system::getName);
        }
        if (systemName != null) {
            return systemName;
        }

        LocationAPI location = readValueOrNull(market::getContainingLocation);
        String locationName = location == null ? null : readTextOrNull(location::getNameWithTypeShort);
        if (locationName == null && location != null) {
            locationName = readTextOrNull(location::getName);
        }
        return locationName;
    }

    private String gravityWellName(MarketAPI market) {
        SectorEntityToken gravityWell = gravityWellResolver.resolve(market);
        return gravityWellDisplayName(gravityWell);
    }

    private String entityTypeName(SectorEntityToken entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof PlanetAPI) {
            String planetTypeName = planetTypeName((PlanetAPI) entity);
            if (planetTypeName != null) {
                return planetTypeName;
            }
        }

        String customSpecName = readValueOrNull(entity::getCustomEntitySpec) == null
                ? null
                : readTextOrNull(() -> entity.getCustomEntitySpec().getNameInText());
        if (customSpecName != null) {
            return customSpecName;
        }

        String customType = readTextOrNull(entity::getCustomEntityType);
        if (customType != null) {
            return customType;
        }

        String entityName = readTextOrNull(entity::getName);
        if (entityName != null) {
            return entityName;
        }

        Boolean systemCenter = readValueOrNull(entity::isSystemCenter);
        return Boolean.TRUE.equals(systemCenter) ? "center of gravity" : null;
    }

    private String gravityWellDisplayName(SectorEntityToken entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof PlanetAPI) {
            String planetTypeName = planetTypeName((PlanetAPI) entity);
            if (planetTypeName != null) {
                return planetTypeName;
            }
        }

        String entityName = readTextOrNull(entity::getName);
        if (entityName != null) {
            return entityName;
        }

        return entityTypeName(entity);
    }

    private String planetTypeName(PlanetAPI planet) {
        String typeName = readTextOrNull(planet::getTypeNameWithWorld);
        if (typeName != null) {
            return typeName;
        }
        typeName = readTextOrNull(planet::getTypeNameWithWorldLowerCase);
        if (typeName != null) {
            return typeName;
        }
        return readValueOrNull(planet::getSpec) == null
                ? null
                : readTextOrNull(() -> planet.getSpec().getName());
    }

    private String constellationName(MarketAPI market) {
        StarSystemAPI system = readValueOrNull(market::getStarSystem);
        Constellation constellation = system == null ? null : readValueOrNull(system::getConstellation);
        if (constellation == null) {
            LocationAPI location = readValueOrNull(market::getContainingLocation);
            constellation = location == null ? null : readValueOrNull(location::getConstellation);
        }

        String constellationName = constellation == null ? null : readTextOrNull(constellation::getNameWithType);
        if (constellationName == null && constellation != null) {
            constellationName = readTextOrNull(constellation::getName);
        }
        return constellationName;
    }
}
