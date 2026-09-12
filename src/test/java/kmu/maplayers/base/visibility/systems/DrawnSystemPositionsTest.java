package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.profiling.ProfileSection;
import kmlib.profiling.recording.RecordingProfiler;
import kmlib.starsector.SectorWalkCounters;
import kmlib.testfixtures.profiling.ProfileCounts;
import kmlib.testfixtures.profiling.RecordedCapture;
import kmlib.testfixtures.starsector.systems.StarSystemFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the drawn-set walk: which systems earn a Voronoi site and where each one sits.
 *
 * <p>The membership rule itself is exercised next door; what only this can show is the walk
 * around it. It is driven through a real pass over a real sector, so a case admitting a system
 * on its colony read alone is what says that reading really happened - a walk over a hollow
 * pass would leave such a system off the map while every other case here went on passing.
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

    // Nothing about a traversal count is a duration, so one reading answers every clock read.
    private static final long FIXED_CLOCK_NANOS = 0L;

    // The one the pass itself makes, which is what everything reading through it shares.
    private static final long THE_PASSES_OWN_TRAVERSAL = 1L;

    @Nested
    class CollectLivePositions {

        @Test
        void collectsNoPositionsWithoutASectorToRead() {

            assertThat(collectPositionsUnder(null, FORCED_ONTO_MAP))
                .isEmpty();
        }

        @Test
        void keysEachDrawnSystemsLivePositionById() {

            var sector = buildSectorHolding(
                buildSystemAt("a", 3f, 4f),
                buildSystemAt("b", -5f, 6f));

            var positions = collectPositionsUnder(sector, FORCED_ONTO_MAP);

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
            var positions = collectPositionsUnder(
                buildSectorHolding(buildSystemWithoutAPosition("a")),
                FORCED_ONTO_MAP);

            assertThat(positions)
                .isEmpty();
        }

        @Test
        void leavesOutASystemTheRuleDoesNotDraw() {
            // Unreachable, no visible star anchor and nobody living there, so nothing admits
            // it once the force override is off.
            var positions = collectPositionsUnder(
                buildSectorHolding(buildSystemAt("a", 3f, 4f)),
                MapVisibilityRules.BASE);

            assertThat(positions)
                .isEmpty();
        }

        @Test
        void admitsASystemOnItsOwnColonyReadAlone() {
            // The pass's own reading of the sector, and the only case that can show it was
            // made. This system has no other route onto the map, so it is drawn if and only if
            // the colony index behind the pass answered for it.
            var system = buildSystemAt("a", 3f, 4f);
            var sector = buildSectorHolding(system);

            listColonyIn(sector, system, buildOpenColony());

            assertThat(collectPositionsUnder(sector, MapVisibilityRules.BASE))
                .containsOnlyKeys("a");
        }

        @Test
        void opensNoTraversalOfTheSystemListItsPassHasNotAlreadyMade() {
            // What holds a rebuild inside the one traversal the frame allows it. The band bake
            // resolves its systems off the same pass, so a traversal opened here would be the
            // second in a rebuild whichever of the two ran first - and the bound is per call,
            // so it would break on every full rebuild rather than on an unlucky one.
            var pass = MapVisibilityPass.over(
                buildSectorHolding(buildSystemAt("a", 3f, 4f)),
                FORCED_ONTO_MAP);

            var counts = RecordedCapture
                .recordWhile(new RecordingProfiler(() -> FIXED_CLOCK_NANOS), () -> {
                    pass.colonies().readSystemsById();
                    DrawnSystemPositions.collectLivePositions(pass);
                })
                .findNode(ProfileSection.UNSCOPED_COUNTS.getName());

            assertThat(ProfileCounts.readTotalOf(counts, SectorWalkCounters.SECTOR_WALKS))
                .isEqualTo(THE_PASSES_OWN_TRAVERSAL);
        }
    }

    // One walk over a pass opened the way a rebuild opens one: built per call and discarded
    // with it, which is what the production caller does - a kept pass would answer a second
    // walk off the sector the first one saw.
    private static Map<String, double[]> collectPositionsUnder(
            SectorAPI sector,
            MapVisibilityRules visibilityRules) {

        return DrawnSystemPositions.collectLivePositions(
            MapVisibilityPass.over(sector, visibilityRules));
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
        return StarSystemFixture.placeSystemAt(buildSystemWithoutAPosition(id), x, y);
    }

    private static StarSystemAPI buildSystemWithoutAPosition(String id) {

        var systemMock = StarSystemFixture.buildSystem(id);

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
