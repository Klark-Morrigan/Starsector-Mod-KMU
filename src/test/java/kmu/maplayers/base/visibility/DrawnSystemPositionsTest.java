package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the drawn-set walk: which systems earn a Voronoi site and where each one sits.
 *
 * <p>The membership rule itself is exercised next door; what only this can show is the walk
 * around it. It opens the colony index and the hyperspace scan its own pass needs, so a case
 * admitting a system on its colony read alone is what says that reading really happened -
 * a walk that opened a hollow index would leave such a system off the map while every other
 * case here went on passing.
 *
 * <p>The other half is the skip: a system with no hyperspace position has no site to place a
 * cell at, and is left out whatever the rule says about it.
 */
class DrawnSystemPositionsTest {

    // The force override on, so membership is settled without staging an economy that owns
    // the system - which leaves a case free to be about the position rather than the rule.
    private static final MapVisibilityRules FORCED_ONTO_MAP =
        new MapVisibilityRules(BASE_FOG, true);

    // The size the staged colony carries. Nothing the drawn-set rule reads weighs a colony,
    // so a case varying this would vary nothing it can see.
    private static final int COLONY_SIZE = 5;

    private static final String OWNING_FACTION = "hegemony";

    @Nested
    class CollectLivePositions {

        @Test
        void collectsNoPositionsWithoutASectorToRead() {

            assertThat(DrawnSystemPositions.collectLivePositions(null, FORCED_ONTO_MAP))
                .isEmpty();
        }

        @Test
        void keysEachDrawnSystemsLivePositionById() {

            var sector = buildSectorHolding(
                buildSystemAt("a", 3f, 4f),
                buildSystemAt("b", -5f, 6f));

            var positions = DrawnSystemPositions.collectLivePositions(sector, FORCED_ONTO_MAP);

            assertThat(positions)
                .containsOnlyKeys("a", "b");
            assertThat(positions.get("a"))
                .containsExactly(3.0, 4.0);
            assertThat(positions.get("b"))
                .containsExactly(-5.0, 6.0);
        }

        @Test
        void leavesOutASystemWithNoPositionToSeedACellAt() {
            // A site is a position, so a system without one cannot enter the partition however
            // the rule judges it - which is why the force override is on here.
            var positions = DrawnSystemPositions.collectLivePositions(
                buildSectorHolding(buildSystemWithoutAPosition("a")),
                FORCED_ONTO_MAP);

            assertThat(positions)
                .isEmpty();
        }

        @Test
        void leavesOutASystemTheRuleDoesNotDraw() {
            // Unreachable, no visible star anchor and nobody living there, so nothing admits
            // it once the force override is off.
            var positions = DrawnSystemPositions.collectLivePositions(
                buildSectorHolding(buildSystemAt("a", 3f, 4f)),
                MapVisibilityRules.BASE);

            assertThat(positions)
                .isEmpty();
        }

        @Test
        void admitsASystemOnItsOwnColonyReadAlone() {
            // The walk's own reading of the sector, and the only case that can show it was
            // made. This system has no other route onto the map, so it is drawn if and only if
            // the colony index the walk opened answered for it.
            var system = buildSystemAt("a", 3f, 4f);
            var sector = buildSectorHolding(system);

            listColonyIn(sector, system, buildOpenColony());

            assertThat(DrawnSystemPositions.collectLivePositions(sector, MapVisibilityRules.BASE))
                .containsOnlyKeys("a");
        }
    }

    // A sector whose hyperspace carries no star anchor and whose economy lists nothing, so no
    // system is drawn by access and each case decides admission through what it stages.
    private static SectorAPI buildSectorHolding(StarSystemAPI... systems) {

        var hyperspaceMock = mock(LocationAPI.class);

        when(hyperspaceMock.getEntities(JumpPointAPI.class))
            .thenReturn(List.of());

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getStarSystems())
            .thenReturn(List.of(systems));
        when(sectorMock.getEconomy())
            .thenReturn(mock(EconomyAPI.class));
        when(sectorMock.getHyperspace())
            .thenReturn(hyperspaceMock);

        return sectorMock;
    }

    // Lists a colony in one system's economy. The economy is read into a local before the
    // stubbing opens, so Mockito sees no stubbing nested inside another.
    private static void listColonyIn(
            SectorAPI sector,
            StarSystemAPI system,
            MarketAPI colony) {

        var economyMock = sector.getEconomy();

        when(economyMock.getMarkets(system))
            .thenReturn(List.of(colony));
    }

    // An unreachable system standing at a hyperspace position: no jump points, so only the
    // force override or somebody living there can put it on the map.
    private static StarSystemAPI buildSystemAt(String id, float x, float y) {

        var systemMock = buildSystemWithoutAPosition(id);

        when(systemMock.getLocation())
            .thenReturn(new Vector2f(x, y));

        return systemMock;
    }

    private static StarSystemAPI buildSystemWithoutAPosition(String id) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getId())
            .thenReturn(id);
        when(systemMock.getJumpPoints())
            .thenReturn(List.of());

        return systemMock;
    }

    // A discovered, openly held colony - what the habitation read admits, and the one route
    // onto the map a case here can stage without the force override.
    private static MarketAPI buildOpenColony() {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.isDiscoverable())
            .thenReturn(false);

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn(OWNING_FACTION);
        // Answered off the faction as the engine answers it: the colony kind read parts an
        // unowned hulk from a settlement on exactly this question, so leaving it false-by-
        // default would pose this colony as something else entirely.
        when(factionMock.isNeutralFaction())
            .thenReturn(false);

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getFactionId())
            .thenReturn(OWNING_FACTION);
        when(marketMock.getSize())
            .thenReturn(COLONY_SIZE);
        when(marketMock.isHidden())
            .thenReturn(false);
        when(marketMock.isPlanetConditionMarketOnly())
            .thenReturn(false);
        when(marketMock.getPrimaryEntity())
            .thenReturn(entityMock);

        return marketMock;
    }
}
