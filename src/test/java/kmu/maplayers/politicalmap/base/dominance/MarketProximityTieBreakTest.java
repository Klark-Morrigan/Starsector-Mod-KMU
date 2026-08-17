package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.Markets;
import kmlib.starsector.systems.SystemColonies;
import kmlib.starsector.systems.SystemColony;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOrbitingEntity;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStarAt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MarketProximityTieBreak}: a tie resolves by which bloc holds the market nearest
 * the system's central star, and the same physical market decides whatever view groups the
 * factions - so a tie never flips holder between the faction and alliance views. The geometry
 * itself - finding the central star and summing an orbit chain to it - is
 * {@link kmlib.starsector.systems.StarSystems}'s contract, pinned in its own suite; these tests
 * run the real geometry over simple single-star setups and turn on the bloc mapping, stubbing
 * the colony filter open so which markets count is not the variable under test.
 *
 * <p>The colonies are handed in as the pass's own set rather than walked out of a sector, which
 * is what keeps the tie decided among the very colonies the ranking that tied was read from.
 */
class MarketProximityTieBreakTest {

    // Orbit radii the tests place bodies at, one clearly nearer the star than the other so the
    // closer bloc wins.
    private static final float ORBIT_CLOSE = 100.0f;
    private static final float ORBIT_FAR = 200.0f;

    // The alliance grouping that folds hegemony into a hegemony-coloured bloc, so a test can
    // show the same market decides the tie under an alliance view as under the faction view.
    private static final HolderGrouping GREATER_HEGEMONY = new HolderGrouping(
        Map.of("hegemony", "greater_hegemony"),
        Map.of("greater_hegemony", "hegemony"),
        Map.of("greater_hegemony", "Greater Hegemony"));

    @Nested
    class ForSystem {

        @Test
        void ordersTheBlocWhoseNearestMarketOrbitsCloserToTheCentreFirst() {
            try (var marketsMock = mockStatic(Markets.class)) {

                stubEveryMarketOwnedAndKnown(marketsMock);

                var starMock = buildStarAt(0.0f, 0.0f);
                var systemMock = buildSystemCentredOn(starMock, starMock);
                var colonies = listColonies(
                    buildListedColonyOwnedBy("hegemony", buildOrbitingEntity(ORBIT_CLOSE, starMock)),
                    buildListedColonyOwnedBy("blackrock", buildOrbitingEntity(ORBIT_FAR, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                    systemMock,
                    colonies,
                    false,
                    HolderGrouping.identity());

                // hegemony's market orbits nearer the star, so it is ordered before blackrock.
                assertThat(comparator.compare("hegemony", "blackrock"))
                    .isNegative();
                assertThat(comparator.compare("blackrock", "hegemony"))
                    .isPositive();
            }
        }

        @Test
        void fallsBackToLowestColourFactionIdWhenNearestMarketsOrbitAtTheSameDepth() {
            try (var marketsMock = mockStatic(Markets.class)) {

                stubEveryMarketOwnedAndKnown(marketsMock);

                var starMock = buildStarAt(0.0f, 0.0f);
                var systemMock = buildSystemCentredOn(starMock, starMock);
                var colonies = listColonies(
                    buildListedColonyOwnedBy("hegemony", buildOrbitingEntity(ORBIT_CLOSE, starMock)),
                    buildListedColonyOwnedBy("blackrock", buildOrbitingEntity(ORBIT_CLOSE, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                    systemMock,
                    colonies,
                    false,
                    GREATER_HEGEMONY);

                // Equal distance: the colour-faction id decides - the alliance colours by
                // hegemony, and blackrock < hegemony - so the alliance is ordered last. This
                // backstop is grouping-invariant, so it lands the same on either view.
                assertThat(comparator.compare("greater_hegemony", "blackrock"))
                    .isPositive();
                assertThat(comparator.compare("blackrock", "greater_hegemony"))
                    .isNegative();
            }
        }

        @Test
        void resolvesTheSameWinnerUnderFactionAndAllianceGrouping() {
            try (var marketsMock = mockStatic(Markets.class)) {

                stubEveryMarketOwnedAndKnown(marketsMock);

                var starMock = buildStarAt(0.0f, 0.0f);
                var systemMock = buildSystemCentredOn(starMock, starMock);

                // The same two physical markets - hegemony's nearer the centre than
                // blackrock's - decide the tie, so the hegemony side wins whether hegemony
                // stands alone or folds into an alliance. No id-ordering flip between views.
                var colonies = listColonies(
                    buildListedColonyOwnedBy("hegemony", buildOrbitingEntity(ORBIT_CLOSE, starMock)),
                    buildListedColonyOwnedBy("blackrock", buildOrbitingEntity(ORBIT_FAR, starMock)));

                var factionComparator = MarketProximityTieBreak.forSystem(
                    systemMock,
                    colonies,
                    false,
                    HolderGrouping.identity());

                var allianceComparator = MarketProximityTieBreak.forSystem(
                    systemMock,
                    colonies,
                    false,
                    GREATER_HEGEMONY);

                assertThat(factionComparator.compare("hegemony", "blackrock"))
                    .isNegative();
                assertThat(allianceComparator.compare("greater_hegemony", "blackrock"))
                    .isNegative();
            }
        }

        @Test
        void readsNoGeometryUntilFirstCompared() {
            try (var marketsMock = mockStatic(Markets.class)) {

                stubEveryMarketOwnedAndKnown(marketsMock);

                var starMock = buildStarAt(0.0f, 0.0f);
                var systemMock = buildSystemCentredOn(starMock, starMock);
                var colonies = listColonies(
                    buildListedColonyOwnedBy("hegemony", buildOrbitingEntity(ORBIT_CLOSE, starMock)),
                    buildListedColonyOwnedBy("blackrock", buildOrbitingEntity(ORBIT_FAR, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                    systemMock,
                    colonies,
                    false,
                    HolderGrouping.identity());

                // Building the comparator reads nothing; only the first compare - the first tie -
                // looks for the system's centre, so a system that never ties costs nothing beyond
                // the colonies the pass had already read.
                verify(systemMock, never())
                    .getCenter();

                comparator.compare("hegemony", "blackrock");

                verify(systemMock)
                    .getCenter();
            }
        }

        @Test
        void ignoresAColonyTheEconomyDoesNotListHoweverCloseItOrbits() {
            try (var marketsMock = mockStatic(Markets.class)) {

                stubEveryMarketOwnedAndKnown(marketsMock);

                var starMock = buildStarAt(0.0f, 0.0f);
                var systemMock = buildSystemCentredOn(starMock, starMock);

                // blackrock's only colony here is one the economy does not list, orbiting nearer
                // the star than hegemony's does. Such a colony takes no dominance weight, so
                // letting it settle a dead heat would move a fill the mechanic never gave it -
                // hegemony's listed colony decides instead, and blackrock is placeless.
                var colonies = listColonies(
                    buildListedColonyOwnedBy("hegemony", buildOrbitingEntity(ORBIT_FAR, starMock)),
                    buildUnlistedColonyOwnedBy("blackrock", buildOrbitingEntity(ORBIT_CLOSE, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                    systemMock,
                    colonies,
                    false,
                    HolderGrouping.identity());

                assertThat(comparator.compare("hegemony", "blackrock"))
                    .isNegative();
            }
        }

        @Test
        void ignoresAnUndiscoveredColonyUntilTheRevealAdmitsIt() {
            // The tie-break reads the pass's own projection rather than a filter of its own, so a
            // colony the player has not found is out of the tie exactly while it is out of the
            // weights - and back in the moment the dev reveal admits it to both.
            try (var marketsMock = mockStatic(Markets.class)) {

                stubEveryMarketOwnedAndKnown(marketsMock);

                var starMock = buildStarAt(0.0f, 0.0f);
                var systemMock = buildSystemCentredOn(starMock, starMock);
                var undiscoveredColony =
                    buildListedColonyOwnedBy("blackrock", buildOrbitingEntity(ORBIT_CLOSE, starMock));

                stubColonyUndiscovered(marketsMock, undiscoveredColony);

                var colonies = listColonies(
                    buildListedColonyOwnedBy("hegemony", buildOrbitingEntity(ORBIT_FAR, starMock)),
                    undiscoveredColony);

                // Without the reveal blackrock's nearer colony is not counted, so it is placeless
                // and hegemony's farther one decides.
                assertThat(MarketProximityTieBreak
                        .forSystem(systemMock, colonies, false, HolderGrouping.identity())
                        .compare("hegemony", "blackrock"))
                    .isNegative();

                // Under the reveal the same colony counts, and being the nearer one it takes the
                // tie.
                assertThat(MarketProximityTieBreak
                        .forSystem(systemMock, colonies, true, HolderGrouping.identity())
                        .compare("hegemony", "blackrock"))
                    .isPositive();
            }
        }

        @Test
        void treatsAMarketWithNoPrimaryEntityAsFarthestFromTheCentre() {
            try (var marketsMock = mockStatic(Markets.class)) {

                stubEveryMarketOwnedAndKnown(marketsMock);

                var starMock = buildStarAt(0.0f, 0.0f);
                var systemMock = buildSystemCentredOn(starMock, starMock);

                // hegemony's colony has no primary entity to place, so it is unplaceable and
                // never wins the tie however the ids sort; the placed rival takes it.
                var colonies = listColonies(
                    buildListedColonyOwnedBy("hegemony", null),
                    buildListedColonyOwnedBy("blackrock", buildOrbitingEntity(ORBIT_FAR, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                    systemMock,
                    colonies,
                    false,
                    HolderGrouping.identity());

                assertThat(comparator.compare("hegemony", "blackrock"))
                    .isPositive();
            }
        }
    }

    // Stubs the colony filter open for every market, so the read turns on the bloc mapping over
    // the real orbit geometry rather than on which markets count as colonies.
    private static void stubEveryMarketOwnedAndKnown(MockedStatic<Markets> marketsMock) {
        marketsMock
            .when(() -> Markets.isCountedAsColony(any(), anyBoolean()))
            .thenReturn(true);
    }

    // Narrows the open filter for one colony: counted under the dev reveal and not without it,
    // which is what an undiscovered colony reads as.
    private static void stubColonyUndiscovered(
            MockedStatic<Markets> marketsMock,
            SystemColony colony) {

        marketsMock
            .when(() -> Markets.isCountedAsColony(same(colony.market()), eq(false)))
            .thenReturn(false);
    }

    // A market owned by a faction, sitting on the given primary entity - the body whose orbit
    // fixes the market's distance from the centre.
    private static MarketAPI buildMarketOwnedBy(String factionId, SectorEntityToken primaryEntity) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getId())
            .thenReturn(factionId);

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getFaction())
            .thenReturn(factionMock);
        when(marketMock.getPrimaryEntity())
            .thenReturn(primaryEntity);

        return marketMock;
    }

    // A system with a centre token and its stars, the two the reference-star search reads.
    private static StarSystemAPI buildSystemCentredOn(SectorEntityToken centre, PlanetAPI... stars) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getCenter())
            .thenReturn(centre);
        when(systemMock.getPlanets())
            .thenReturn(List.of(stars));

        return systemMock;
    }

    // The system's colony set as one walk of it would have reported, which is what a pass hands
    // the tie-break.
    private static SystemColonies listColonies(SystemColony... colonies) {
        return new SystemColonies(List.of(colonies));
    }

    // A colony the sector's economy lists - one the dominance rule weighed, and so one entitled
    // to settle a tie between the blocs it weighed.
    private static SystemColony buildListedColonyOwnedBy(
            String factionId,
            SectorEntityToken primaryEntity) {

        return new SystemColony(buildMarketOwnedBy(factionId, primaryEntity), true);
    }

    // A colony present in the system that the economy does not list - named by a box, weighed by
    // nothing.
    private static SystemColony buildUnlistedColonyOwnedBy(
            String factionId,
            SectorEntityToken primaryEntity) {

        return new SystemColony(buildMarketOwnedBy(factionId, primaryEntity), false);
    }
}
