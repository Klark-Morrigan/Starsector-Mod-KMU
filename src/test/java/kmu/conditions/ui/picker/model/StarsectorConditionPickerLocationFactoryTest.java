package kmu.conditions.ui.picker.model;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.RelationshipAPI;
import com.fs.starfarer.api.impl.campaign.procgen.Constellation;
import com.fs.starfarer.api.impl.campaign.procgen.StarAge;
import kmu.conditions.domain.KmuEditableMarket;
import kmu.conditions.domain.StarsectorEditableMarket;
import kmu.starsector.StarsectorGravityWellResolver;
import kmu.starsector.StarsectorPlayerRelationshipFormatter;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StarsectorConditionPickerLocationFactoryTest {
    private static final Color FACTION_COLOR = new Color(90, 150, 240);
    private static final Color RELATIONSHIP_COLOR = new Color(200, 50, 50);

    private final StarsectorConditionPickerLocationFactory factory =
            new StarsectorConditionPickerLocationFactory(
                    new StarsectorGravityWellResolver(),
                    new StarsectorPlayerRelationshipFormatter());

    @Test
    void returnsUnknownLocationForNonStarsectorMarket() {
        KmuEditableMarket fakeMarket = new KmuEditableMarket() {
            @Override public Set<String> getConditionIds() { return Set.of(); }
            @Override public boolean hasCondition(String id) { return false; }
            @Override public boolean isConditionSuppressed(String id) { return false; }
            @Override public void addCondition(String id) {}
            @Override public void markConditionSurveyed(String id) {}
            @Override public void reapplyConditions() {}
        };

        KmuConditionPickerLocation location = factory.create(fakeMarket);

        assertThat(location.getPlanetName()).isEmpty();
        assertThat(location.getFaction()).isEmpty();
        assertThat(location.getStarSystemName()).isEmpty();
    }

    @Test
    void fallsBackToPrimaryEntityNameWhenPlanetNameIsBlank() {
        // planet.getName() is blank; primaryEntity.getName() provides the display name
        PlanetAPI blankNamePlanet = planet("  ", "terran world", null);
        SectorEntityToken primary = entity("Station Alpha");
        MarketAPI market = market(null, blankNamePlanet, primary, null, null, null);

        KmuConditionPickerLocation location = factory.create(new StarsectorEditableMarket(market));

        assertThat(location.getPlanetName()).contains("Station Alpha");
    }

    @Test
    void fallsBackToMarketNameWhenBothPlanetAndEntityNamesAreBlank() {
        PlanetAPI blankNamePlanet = planet("  ", "terran world", null);
        SectorEntityToken blankNameEntity = entity("  ");
        MarketAPI market = market("Relay Station", blankNamePlanet, blankNameEntity, null, null, null);

        KmuConditionPickerLocation location = factory.create(new StarsectorEditableMarket(market));

        assertThat(location.getPlanetName()).contains("Relay Station");
    }

    @Test
    void readsPlanetTypeFromPrimaryEntityWhenItIsAPlanetAndPlanetEntityIsNull() {
        // No planet entity; primaryEntity is a PlanetAPI so its type name is used
        PlanetAPI primaryPlanet = planet("Valis", "barren world", null);
        MarketAPI market = market(null, null, primaryPlanet, null, null, null);

        KmuConditionPickerLocation location = factory.create(new StarsectorEditableMarket(market));

        assertThat(location.getPlanetType()).contains("barren world");
    }

    @Test
    void readsFactionFromPrimaryEntityWhenMarketFactionIsNull() {
        // market.getFaction() returns null; primaryEntity.getFaction() provides the faction
        SectorEntityToken primary = entityWithFaction(faction("Hegemony"));
        MarketAPI market = market(null, null, primary, null, null, null);

        KmuConditionPickerLocation location = factory.create(new StarsectorEditableMarket(market));

        assertThat(location.getFaction()).isPresent();
        assertThat(location.getFaction().get().getName()).isEqualTo("Hegemony");
    }

    @Test
    void readsLocationNameFromContainingLocationWhenStarSystemIsNull() {
        // getStarSystem() returns null; getContainingLocation() provides the name
        LocationAPI containingLocation = location("Hyperspace");
        MarketAPI market = market(null, null, null, null, null, containingLocation);

        KmuConditionPickerLocation result = factory.create(new StarsectorEditableMarket(market));

        assertThat(result.getStarSystemName()).contains("Hyperspace");
    }

    @Test
    void readsConstellationFromContainingLocationWhenStarSystemHasNone() {
        // Star system has no constellation; containingLocation provides it
        Constellation constellation = new Constellation(
                Constellation.ConstellationType.NORMAL, StarAge.AVERAGE);
        constellation.setNameOverride("Serpens");
        LocationAPI containingLocation = locationWithConstellation("Outer Rim", constellation);
        MarketAPI market = market(null, null, null, starSystemWithoutConstellation(), null, containingLocation);

        KmuConditionPickerLocation result = factory.create(new StarsectorEditableMarket(market));

        assertThat(result.getConstellationName()).isPresent().get().asString().contains("Serpens");
    }

    // --- proxy helpers ---

    private static PlanetAPI planet(String name, String type, SectorEntityToken orbitFocus) {
        return proxy(PlanetAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return name;
                case "getTypeNameWithWorld":
                case "getTypeNameWithWorldLowerCase": return type;
                case "getOrbitFocus": return orbitFocus;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static SectorEntityToken entity(String name) {
        return proxy(SectorEntityToken.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return name;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static SectorEntityToken entityWithFaction(FactionAPI faction) {
        return proxy(SectorEntityToken.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return "Primary Entity";
                case "getFaction": return faction;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static FactionAPI faction(String name) {
        return proxy(FactionAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getDisplayNameLong":
                case "getDisplayName": return name;
                case "getBaseUIColor": return FACTION_COLOR;
                case "getRelToPlayer": return relationship();
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static RelationshipAPI relationship() {
        return proxy(RelationshipAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getLevel": return RepLevel.VENGEFUL;
                case "getRepInt": return -100;
                case "getRelColor": return RELATIONSHIP_COLOR;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static LocationAPI location(String name) {
        return proxy(LocationAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getNameWithTypeShort":
                case "getName": return name;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static LocationAPI locationWithConstellation(String name, Constellation constellation) {
        return proxy(LocationAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getNameWithTypeShort":
                case "getName": return name;
                case "getConstellation": return constellation;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static StarSystemAPI starSystemWithoutConstellation() {
        return proxy(StarSystemAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getConstellation": return null;
                case "getCenter":
                case "getStar": return null;
                default: return handleObjectMethodOrThrow(p, method, args);
            }
        });
    }

    private static MarketAPI market(
            String name,
            PlanetAPI planet,
            SectorEntityToken primaryEntity,
            StarSystemAPI system,
            FactionAPI faction,
            LocationAPI containingLocation) {
        return proxy(MarketAPI.class, (p, method, args) -> {
            switch (method.getName()) {
                case "getName": return name;
                case "getPlanetEntity": return planet;
                case "getPrimaryEntity": return primaryEntity;
                case "getStarSystem": return system;
                case "getContainingLocation": return containingLocation != null
                        ? containingLocation
                        : system;
                case "getFaction": return faction;
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
