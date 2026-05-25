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
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StarsectorConditionPickerLocationFactoryTest {
    private static final Color FACTION_COLOR = new Color(90, 150, 240);
    private static final Color RELATIONSHIP_COLOR = new Color(200, 50, 50);

    private final StarsectorConditionPickerLocationFactory factory =
            new StarsectorConditionPickerLocationFactory(new StarsectorGravityWellResolver());

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
        PlanetAPI blankNamePlanet = planet("  ", "terran world");
        SectorEntityToken primary = entity("Station Alpha");
        MarketAPI market = market(null, blankNamePlanet, primary, null, null, null);

        KmuConditionPickerLocation location = factory.create(new StarsectorEditableMarket(market));

        assertThat(location.getPlanetName()).contains("Station Alpha");
    }

    @Test
    void fallsBackToMarketNameWhenBothPlanetAndEntityNamesAreBlank() {
        PlanetAPI blankNamePlanet = planet("  ", "terran world");
        SectorEntityToken blankNameEntity = entity("  ");
        MarketAPI market = market("Relay Station", blankNamePlanet, blankNameEntity, null, null, null);

        KmuConditionPickerLocation location = factory.create(new StarsectorEditableMarket(market));

        assertThat(location.getPlanetName()).contains("Relay Station");
    }

    @Test
    void readsPlanetTypeFromPrimaryEntityWhenItIsAPlanetAndPlanetEntityIsNull() {
        // No planet entity; primaryEntity is a PlanetAPI so its type name is used
        PlanetAPI primaryPlanet = planet("Valis", "barren world");
        MarketAPI market = market(null, null, primaryPlanet, null, null, null);

        KmuConditionPickerLocation location = factory.create(new StarsectorEditableMarket(market));

        assertThat(location.getPlanetType()).contains("barren world");
    }

    @Test
    void readsFactionFromPrimaryEntityWhenMarketFactionIsNull() {
        // market.getFaction() returns null; primaryEntity.getFaction() provides the faction
        FactionAPI hegemony = faction("Hegemony");
        SectorEntityToken primary = mock(SectorEntityToken.class);
        when(primary.getName()).thenReturn("Primary Entity");
        when(primary.getFaction()).thenReturn(hegemony);
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
        StarSystemAPI emptySystem = mock(StarSystemAPI.class);
        // getConstellation, getCenter, getStar all default to null on Mockito mocks.
        MarketAPI market = market(null, null, null, emptySystem, null, containingLocation);

        KmuConditionPickerLocation result = factory.create(new StarsectorEditableMarket(market));

        assertThat(result.getConstellationName()).isPresent().get().asString().contains("Serpens");
    }

    // --- mock helpers ---

    private static PlanetAPI planet(String name, String type) {
        PlanetAPI planet = mock(PlanetAPI.class);
        when(planet.getName()).thenReturn(name);
        when(planet.getTypeNameWithWorld()).thenReturn(type);
        when(planet.getTypeNameWithWorldLowerCase()).thenReturn(type);
        return planet;
    }

    private static SectorEntityToken entity(String name) {
        SectorEntityToken entity = mock(SectorEntityToken.class);
        when(entity.getName()).thenReturn(name);
        return entity;
    }

    private static FactionAPI faction(String name) {
        RelationshipAPI relationship = createRelationship();
        FactionAPI faction = mock(FactionAPI.class);
        when(faction.getDisplayNameLong()).thenReturn(name);
        when(faction.getDisplayName()).thenReturn(name);
        when(faction.getBaseUIColor()).thenReturn(FACTION_COLOR);
        when(faction.getRelToPlayer()).thenReturn(relationship);
        return faction;
    }

    private static RelationshipAPI createRelationship() {
        RelationshipAPI relationship = mock(RelationshipAPI.class);
        when(relationship.getLevel()).thenReturn(RepLevel.VENGEFUL);
        when(relationship.getRepInt()).thenReturn(-100);
        when(relationship.getRelColor()).thenReturn(RELATIONSHIP_COLOR);
        return relationship;
    }

    private static LocationAPI location(String name) {
        LocationAPI location = mock(LocationAPI.class);
        when(location.getNameWithTypeShort()).thenReturn(name);
        when(location.getName()).thenReturn(name);
        return location;
    }

    private static LocationAPI locationWithConstellation(String name, Constellation constellation) {
        LocationAPI location = mock(LocationAPI.class);
        when(location.getNameWithTypeShort()).thenReturn(name);
        when(location.getName()).thenReturn(name);
        when(location.getConstellation()).thenReturn(constellation);
        return location;
    }

    private static MarketAPI market(
            String name,
            PlanetAPI planet,
            SectorEntityToken primaryEntity,
            StarSystemAPI system,
            FactionAPI faction,
            LocationAPI containingLocation) {
        MarketAPI market = mock(MarketAPI.class);
        when(market.getName()).thenReturn(name);
        when(market.getPlanetEntity()).thenReturn(planet);
        when(market.getPrimaryEntity()).thenReturn(primaryEntity);
        when(market.getStarSystem()).thenReturn(system);
        when(market.getFaction()).thenReturn(faction);
        when(market.getContainingLocation()).thenReturn(
                containingLocation != null ? containingLocation : system);
        return market;
    }
}
