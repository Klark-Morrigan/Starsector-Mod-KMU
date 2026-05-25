package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;

import kmlib.starsector.relation.StarsectorPlayerRelationshipFormatter;
import kmlib.starsector.relation.StarsectorPlayerRelationshipFormatter.RelationshipSummary;

import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.domain.StarsectorEditableMarket;
import kmu.starsector.StarsectorGravityWellResolver;

import java.awt.Color;
import java.util.Objects;

import static kmu.util.KmuValues.normalizeText;

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
     * <p>Missing API reads (null returns) are converted to absent fields. The
     * picker renderer decides which absent fields to omit; this factory does not
     * decide whether the picker should open. Any {@link RuntimeException} from a
     * modded API implementation propagates to the caller.</p>
     */
    KmuConditionPickerLocation create(KmuEditableMarket market) {
        if (!(market instanceof StarsectorEditableMarket)) {
            return unknownLocation();
        }

        MarketAPI starsectorMarket = ((StarsectorEditableMarket) market).getMarket();
        if (starsectorMarket == null) {
            return unknownLocation();
        }

        FactionAPI faction = factionFor(starsectorMarket);
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
                null, null, null, null, null, null, null);
    }

    private String marketName(MarketAPI market) {
        return normalizeText(market.getName());
    }

    private String planetName(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        String planetName = planet == null ? null : normalizeText(planet.getName());
        if (planetName != null) {
            return planetName;
        }

        SectorEntityToken primaryEntity = market.getPrimaryEntity();
        String entityName = primaryEntity == null ? null : normalizeText(primaryEntity.getName());
        return entityName == null ? marketName(market) : entityName;
    }

    private String planetType(MarketAPI market) {
        PlanetAPI planet = market.getPlanetEntity();
        if (planet == null) {
            SectorEntityToken primaryEntity = market.getPrimaryEntity();
            if (primaryEntity instanceof PlanetAPI) {
                planet = (PlanetAPI) primaryEntity;
            }
        }

        String planetType = planet == null ? null : planetTypeName(planet);
        if (planetType != null) {
            return planetType;
        }

        return entityTypeName(market.getPrimaryEntity());
    }

    private FactionAPI factionFor(MarketAPI market) {
        FactionAPI faction = market.getFaction();
        if (faction != null) {
            return faction;
        }

        SectorEntityToken primaryEntity = market.getPrimaryEntity();
        return primaryEntity == null ? null : primaryEntity.getFaction();
    }

    private KmuPickerFaction pickerFaction(FactionAPI faction) {
        if (faction == null) {
            return null;
        }

        String name = normalizeText(faction.getDisplayNameLong());
        if (name == null) {
            name = normalizeText(faction.getDisplayName());
        }
        if (name == null) {
            return null;
        }

        Color color = faction.getBaseUIColor();
        if (color == null) {
            color = faction.getColor();
        }

        String crestSprite = normalizeText(faction.getCrest());
        RelationshipSummary relationship = relationshipFormatter.formatPlayerRelationship(faction);
        return new KmuPickerFaction(name, color, crestSprite,
                relationship.getDescription(), relationship.getColor());
    }

    private String starSystemName(MarketAPI market) {
        StarSystemAPI system = market.getStarSystem();
        String systemName = system == null ? null : normalizeText(system.getNameWithTypeShort());
        if (systemName == null && system != null) {
            systemName = normalizeText(system.getName());
        }
        if (systemName != null) {
            return systemName;
        }

        LocationAPI location = market.getContainingLocation();
        String locationName = location == null ? null : normalizeText(location.getNameWithTypeShort());
        if (locationName == null && location != null) {
            locationName = normalizeText(location.getName());
        }
        return locationName;
    }

    private String gravityWellTypeName(MarketAPI market) {
        SectorEntityToken gravityWell = gravityWellResolver.resolve(market);
        return gravityWellDisplayName(gravityWell);
    }

    private String gravityWellName(MarketAPI market) {
        SectorEntityToken gravityWell = gravityWellResolver.resolve(market);
        return gravityWell == null ? null : normalizeText(gravityWell.getName());
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

        String customSpecName = entity.getCustomEntitySpec() == null
                ? null
                : normalizeText(entity.getCustomEntitySpec().getNameInText());
        if (customSpecName != null) {
            return customSpecName;
        }

        String customType = normalizeText(entity.getCustomEntityType());
        if (customType != null) {
            return customType;
        }

        String entityName = normalizeText(entity.getName());
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
            String planetTypeName = planetTypeName((PlanetAPI) entity);
            if (planetTypeName != null) {
                return planetTypeName;
            }
        }

        String entityName = normalizeText(entity.getName());
        if (entityName != null) {
            return entityName;
        }

        return entityTypeName(entity);
    }

    private String planetTypeName(PlanetAPI planet) {
        String typeName = normalizeText(planet.getTypeNameWithWorld());
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
        StarSystemAPI system = market.getStarSystem();
        Constellation constellation = system == null ? null : system.getConstellation();
        if (constellation == null) {
            LocationAPI location = market.getContainingLocation();
            constellation = location == null ? null : location.getConstellation();
        }

        String constellationName = constellation == null
                ? null
                : normalizeText(constellation.getNameWithType());
        if (constellationName == null && constellation != null) {
            constellationName = normalizeText(constellation.getName());
        }
        return constellationName;
    }
}
