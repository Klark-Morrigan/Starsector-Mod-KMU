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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StarsectorConditionPickerLocationFactoryTest {
    private static final Color FACTION_COLOUR = new Color(90, 150, 240);
    private static final Color RELATIONSHIP_COLOUR = new Color(200, 50, 50);

    private final StarsectorConditionPickerLocationFactory factory =
            new StarsectorConditionPickerLocationFactory(new StarsectorGravityWellResolver());

    @Nested
    class Create {

        @Test
        void returnsUnknownLocationForNonStarsectorMarket() {
            var fakeMarket = new KmuEditableMarket() {
                @Override public Set<String> getConditionIds() { return Set.of(); }
                @Override public boolean hasCondition(String id) { return false; }
                @Override public boolean isConditionSuppressed(String id) { return false; }
                @Override public void addCondition(String id) {}
                @Override public void markConditionSurveyed(String id) {}
                @Override public void reapplyConditions() {}
            };

            var location = factory.create(fakeMarket);

            assertThat(location.getPlanetName()).isEmpty();
            assertThat(location.getFaction()).isEmpty();
            assertThat(location.getStarSystemName()).isEmpty();
        }

        @Test
        void fallsBackToPrimaryEntityNameWhenPlanetNameIsBlank() {
            // planet.getName() is blank; primaryEntity.getName() provides the display name
            var blankNamePlanet = buildPlanet("  ", "terran world");
            var primary = buildEntity("Station Alpha");
            var market = buildMarket(null, blankNamePlanet, primary, null, null, null);

            var location = factory.create(new StarsectorEditableMarket(market));

            assertThat(location.getPlanetName()).contains("Station Alpha");
        }

        @Test
        void fallsBackToMarketNameWhenBothPlanetAndEntityNamesAreBlank() {
            var blankNamePlanet = buildPlanet("  ", "terran world");
            var blankNameEntity = buildEntity("  ");
            var market = buildMarket("Relay Station", blankNamePlanet, blankNameEntity, null, null, null);

            var location = factory.create(new StarsectorEditableMarket(market));

            assertThat(location.getPlanetName()).contains("Relay Station");
        }

        @Test
        void readsPlanetTypeFromPrimaryEntityWhenItIsAPlanetAndPlanetEntityIsNull() {
            // No planet entity; primaryEntity is a PlanetAPI so its type name is used
            var primaryPlanet = buildPlanet("Valis", "barren world");
            var market = buildMarket(null, null, primaryPlanet, null, null, null);

            var location = factory.create(new StarsectorEditableMarket(market));

            assertThat(location.getPlanetType()).contains("barren world");
        }

        @Test
        void readsFactionFromPrimaryEntityWhenMarketFactionIsNull() {
            // market.getFaction() returns null; primaryEntity.getFaction() provides the faction
            var hegemony = buildFaction("Hegemony");
            var primaryMock = mock(SectorEntityToken.class);
            when(primaryMock.getName()).thenReturn("Primary Entity");
            when(primaryMock.getFaction()).thenReturn(hegemony);
            var market = buildMarket(null, null, primaryMock, null, null, null);

            var location = factory.create(new StarsectorEditableMarket(market));

            assertThat(location.getFaction()).isPresent();
            assertThat(location.getFaction().get().getName()).isEqualTo("Hegemony");
        }

        @Test
        void readsLocationNameFromContainingLocationWhenStarSystemIsNull() {
            // getStarSystem() returns null; getContainingLocation() provides the name
            var containingLocation = buildLocation("Hyperspace");
            var market = buildMarket(null, null, null, null, null, containingLocation);

            var result = factory.create(new StarsectorEditableMarket(market));

            assertThat(result.getStarSystemName()).contains("Hyperspace");
        }

        @Test
        void readsConstellationFromContainingLocationWhenStarSystemHasNone() {
            // Star system has no constellation; containingLocation provides it
            var constellation = new Constellation(
                    Constellation.ConstellationType.NORMAL, StarAge.AVERAGE);
            constellation.setNameOverride("Serpens");
            var containingLocation = buildLocationWithConstellation("Outer Rim", constellation);
            var emptySystemMock = mock(StarSystemAPI.class);
            // getConstellation, getCenter, getStar all default to null on Mockito mocks.
            var market = buildMarket(null, null, null, emptySystemMock, null, containingLocation);

            var result = factory.create(new StarsectorEditableMarket(market));

            assertThat(result.getConstellationName()).isPresent().get().asString().contains("Serpens");
        }
    }

    // --- mock helpers ---

    private static PlanetAPI buildPlanet(String name, String type) {
        var planetMock = mock(PlanetAPI.class);
        when(planetMock.getName()).thenReturn(name);
        when(planetMock.getTypeNameWithWorld()).thenReturn(type);
        when(planetMock.getTypeNameWithWorldLowerCase()).thenReturn(type);
        return planetMock;
    }

    private static SectorEntityToken buildEntity(String name) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.getName()).thenReturn(name);
        return entityMock;
    }

    private static FactionAPI buildFaction(String name) {
        var relationship = createRelationship();
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getDisplayNameLong()).thenReturn(name);
        when(factionMock.getDisplayName()).thenReturn(name);
        when(factionMock.getBaseUIColor()).thenReturn(FACTION_COLOUR);
        when(factionMock.getRelToPlayer()).thenReturn(relationship);
        return factionMock;
    }

    private static RelationshipAPI createRelationship() {
        var relationshipMock = mock(RelationshipAPI.class);
        when(relationshipMock.getLevel()).thenReturn(RepLevel.VENGEFUL);
        when(relationshipMock.getRepInt()).thenReturn(-100);
        when(relationshipMock.getRelColor()).thenReturn(RELATIONSHIP_COLOUR);
        return relationshipMock;
    }

    private static LocationAPI buildLocation(String name) {
        var locationMock = mock(LocationAPI.class);
        when(locationMock.getNameWithTypeShort()).thenReturn(name);
        when(locationMock.getName()).thenReturn(name);
        return locationMock;
    }

    private static LocationAPI buildLocationWithConstellation(String name, Constellation constellation) {
        var locationMock = mock(LocationAPI.class);
        when(locationMock.getNameWithTypeShort()).thenReturn(name);
        when(locationMock.getName()).thenReturn(name);
        when(locationMock.getConstellation()).thenReturn(constellation);
        return locationMock;
    }

    private static MarketAPI buildMarket(
            String name,
            PlanetAPI planet,
            SectorEntityToken primaryEntity,
            StarSystemAPI system,
            FactionAPI faction,
            LocationAPI containingLocation) {
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getName()).thenReturn(name);
        when(marketMock.getPlanetEntity()).thenReturn(planet);
        when(marketMock.getPrimaryEntity()).thenReturn(primaryEntity);
        when(marketMock.getStarSystem()).thenReturn(system);
        when(marketMock.getFaction()).thenReturn(faction);
        when(marketMock.getContainingLocation()).thenReturn(
                containingLocation != null ? containingLocation : system);
        return marketMock;
    }
}
