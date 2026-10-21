package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.factions.FactionCrests;
import kmlib.starsector.factions.FactionNames;
import kmlib.starsector.factions.relation.PlayerRelations;

import kmu.conditions.domain.KmuEditableMarket;
import kmu.starsector.StarsectorGravityWellResolver;
import kmu.util.KmuMarketConditionStringKeys;
import kmu.util.KmuStringKeys;

import java.util.Objects;

import static kmlib.starsector.factions.relation.RelationFormatter.formatRelation;
import static kmlib.text.KmlibStrings.normaliseText;

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

        return market
            .findStarsectorMarket()
            .map(this::createStarsectorLocation)
            .orElseGet(KmuConditionPickerLocation::createUnknownLocation);
    }

    private KmuConditionPickerLocation createStarsectorLocation(MarketAPI starsectorMarket) {

        var faction = factionFor(starsectorMarket);

        // Resolved once for both its fields: the walk climbs the whole orbit chain.
        var gravityWell = gravityWellResolver.resolve(starsectorMarket);

        return new KmuConditionPickerLocation.Builder()
            .withPlanetName(planetName(starsectorMarket))
            .withPlanetType(planetType(starsectorMarket))
            .withFaction(pickerFaction(faction))
            .withStarSystemName(starSystemName(starsectorMarket))
            .withGravityWellTypeName(gravityWellDisplayName(gravityWell))
            .withGravityWellName(gravityWellName(gravityWell))
            .withConstellationName(constellationName(starsectorMarket))
            .build();
    }

    private String marketName(MarketAPI market) {
        return normaliseText(market.getName());
    }

    private String planetName(MarketAPI market) {

        var planet = market.getPlanetEntity();
        var planetName = planet == null
            ? null
            : normaliseText(planet.getName());

        if (planetName != null) {
            return planetName;
        }

        var primaryEntity = market.getPrimaryEntity();
        var entityName = primaryEntity == null ? null : normaliseText(primaryEntity.getName());

        return entityName == null
            ? marketName(market)
            : entityName;
    }

    private String planetType(MarketAPI market) {

        var planet = market.getPlanetEntity();
        if (planet == null) {

            var primaryEntity = market.getPrimaryEntity();
            if (primaryEntity instanceof PlanetAPI) {
                planet = (PlanetAPI) primaryEntity;
            }
        }

        var planetType = planet == null
            ? null
            : planetTypeName(planet);

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

        return primaryEntity == null
            ? null
            : primaryEntity.getFaction();
    }

    private KmuPickerFaction pickerFaction(FactionAPI faction) {

        if (faction == null) {
            return null;
        }

        var name = FactionNames.resolveFullestName(faction);
        if (name == null) {
            return null;
        }

        var colour = faction.getBaseUIColor();
        if (colour == null) {
            colour = faction.getColor();
        }

        var crestSprite = FactionCrests.resolveCrestPath(faction);

        // The row words the standing and paints itself in its colour, so both come off one read;
        // a faction with no standing to read leaves the row carrying neither.
        var standing = PlayerRelations
            .readPlayerRelation(faction)
            .orElse(null);

        return new KmuPickerFaction(
            name,
            colour,
            crestSprite,
            standing == null ? null : formatRelation(standing),
            standing == null ? null : standing.colour());
    }

    private String starSystemName(MarketAPI market) {

        var system = market.getStarSystem();
        var systemName = system == null
            ? null
            : normaliseText(system.getNameWithTypeShort());

        if (systemName == null && system != null) {
            systemName = normaliseText(system.getName());
        }

        if (systemName != null) {
            return systemName;
        }

        var location = market.getContainingLocation();
        var locationName = location == null
            ? null
            : normaliseText(location.getNameWithTypeShort());

        if (locationName == null && location != null) {
            locationName = normaliseText(location.getName());
        }

        return locationName;
    }

    private String gravityWellName(SectorEntityToken gravityWell) {

        return gravityWell == null
            ? null
            : normaliseText(gravityWell.getName());
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
            : normaliseText(entity.getCustomEntitySpec().getNameInText());

        if (customSpecName != null) {
            return customSpecName;
        }

        var customType = normaliseText(entity.getCustomEntityType());
        if (customType != null) {
            return customType;
        }

        var entityName = normaliseText(entity.getName());
        if (entityName != null) {
            return entityName;
        }

        // Named "system center" because that is the one name true of every kind vanilla makes. A system with a
        // star has the star as its centre, named above. Vanilla adds a starless centre in two other cases:
        // - a close binary or trinary, whose stars orbit it, so it is their barycentre;
        // - a system with no star at all - a nebula, a rogue object, a hidden site - where it is only the
        //   system's middle, and nothing orbits it for its mass.
        // "Barycentre" or "centre of gravity" would be true of the first case alone.
        return entity.isSystemCenter()
            ? KmuStringKeys.get(KmuMarketConditionStringKeys.CONDITION_MANAGER_LOCATION_SYSTEM_CENTER)
            : null;
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

        var entityName = normaliseText(entity.getName());
        if (entityName != null) {
            return entityName;
        }

        return entityTypeName(entity);
    }

    private String planetTypeName(PlanetAPI planet) {

        var typeName = normaliseText(planet.getTypeNameWithWorld());
        if (typeName != null) {
            return typeName;
        }

        typeName = normaliseText(planet.getTypeNameWithWorldLowerCase());
        if (typeName != null) {
            return typeName;
        }

        return planet.getSpec() == null
            ? null
            : normaliseText(planet.getSpec().getName());
    }

    private String constellationName(MarketAPI market) {

        var system = market.getStarSystem();
        var constellation = system == null
            ? null
            : system.getConstellation();

        if (constellation == null) {

            var location = market.getContainingLocation();

            constellation = location == null
                ? null
                : location.getConstellation();
        }

        var constellationName = constellation == null
            ? null
            : normaliseText(constellation.getNameWithType());

        if (constellationName == null && constellation != null) {
            constellationName = normaliseText(constellation.getName());
        }

        return constellationName;
    }
}
