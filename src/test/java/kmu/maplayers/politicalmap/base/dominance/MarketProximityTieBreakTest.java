package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.Markets;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.orbitingEntity;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.starAt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MarketProximityTieBreak}: a tie resolves by which bloc holds the market nearest
 * the system's central star, and the same physical market decides whatever view groups the
 * factions - so a tie never flips owner between the faction and alliance views. The geometry
 * itself - finding the central star and summing an orbit chain to it - is
 * {@link kmlib.starsector.systems.StarSystems}'s contract, pinned in its own suite; these tests
 * run the real geometry over simple single-star setups and turn on the bloc mapping, stubbing
 * the colony filter open so which markets count is not the variable under test.
 */
class MarketProximityTieBreakTest {

    // Orbit radii the tests place bodies at, one clearly nearer the star than the other so the
    // closer bloc wins.
    private static final float ORBIT_CLOSE = 100.0f;
    private static final float ORBIT_FAR = 200.0f;

    // The alliance grouping that folds hegemony into a hegemony-coloured bloc, so a test can
    // show the same market decides the tie under an alliance view as under the faction view.
    private static final OwnershipGrouping GREATER_HEGEMONY = new OwnershipGrouping(
            Map.of("hegemony", "greater_hegemony"),
            Map.of("greater_hegemony", "hegemony"),
            Map.of("greater_hegemony", "Greater Hegemony"));

    @Nested
    class ForSystem {

        @Test
        void ordersTheBlocWhoseNearestMarketOrbitsCloserToTheCentreFirst() {
            try (MockedStatic<Markets> marketsMock = mockStatic(Markets.class)) {
                stubEveryMarketOwnedAndKnown(marketsMock);
                var starMock = starAt(0.0f, 0.0f);
                var systemMock = systemCentredOn(starMock, starMock);
                var sectorMock = sectorHolding(systemMock,
                        marketOwnedBy("hegemony", orbitingEntity(ORBIT_CLOSE, starMock)),
                        marketOwnedBy("blackrock", orbitingEntity(ORBIT_FAR, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                        sectorMock, systemMock, false, OwnershipGrouping.identity());

                // hegemony's market orbits nearer the star, so it is ordered before blackrock.
                assertThat(comparator.compare("hegemony", "blackrock")).isNegative();
                assertThat(comparator.compare("blackrock", "hegemony")).isPositive();
            }
        }

        @Test
        void fallsBackToLowestColourFactionIdWhenNearestMarketsOrbitAtTheSameDepth() {
            try (MockedStatic<Markets> marketsMock = mockStatic(Markets.class)) {
                stubEveryMarketOwnedAndKnown(marketsMock);
                var starMock = starAt(0.0f, 0.0f);
                var systemMock = systemCentredOn(starMock, starMock);
                var sectorMock = sectorHolding(systemMock,
                        marketOwnedBy("hegemony", orbitingEntity(ORBIT_CLOSE, starMock)),
                        marketOwnedBy("blackrock", orbitingEntity(ORBIT_CLOSE, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                        sectorMock, systemMock, false, GREATER_HEGEMONY);

                // Equal distance: the colour-faction id decides - the alliance colours by
                // hegemony, and blackrock < hegemony - so the alliance is ordered last. This
                // backstop is grouping-invariant, so it lands the same on either view.
                assertThat(comparator.compare("greater_hegemony", "blackrock")).isPositive();
                assertThat(comparator.compare("blackrock", "greater_hegemony")).isNegative();
            }
        }

        @Test
        void resolvesTheSameWinnerUnderFactionAndAllianceGrouping() {
            try (MockedStatic<Markets> marketsMock = mockStatic(Markets.class)) {
                stubEveryMarketOwnedAndKnown(marketsMock);
                var starMock = starAt(0.0f, 0.0f);
                var systemMock = systemCentredOn(starMock, starMock);
                // The same two physical markets - hegemony's nearer the centre than
                // blackrock's - decide the tie, so the hegemony side wins whether hegemony
                // stands alone or folds into an alliance. No id-ordering flip between views.
                var sectorMock = sectorHolding(systemMock,
                        marketOwnedBy("hegemony", orbitingEntity(ORBIT_CLOSE, starMock)),
                        marketOwnedBy("blackrock", orbitingEntity(ORBIT_FAR, starMock)));

                var factionComparator = MarketProximityTieBreak.forSystem(
                        sectorMock, systemMock, false, OwnershipGrouping.identity());
                var allianceComparator = MarketProximityTieBreak.forSystem(
                        sectorMock, systemMock, false, GREATER_HEGEMONY);

                assertThat(factionComparator.compare("hegemony", "blackrock")).isNegative();
                assertThat(allianceComparator.compare("greater_hegemony", "blackrock"))
                        .isNegative();
            }
        }

        @Test
        void readsNoEconomyUntilFirstCompared() {
            try (MockedStatic<Markets> marketsMock = mockStatic(Markets.class)) {
                stubEveryMarketOwnedAndKnown(marketsMock);
                var starMock = starAt(0.0f, 0.0f);
                var systemMock = systemCentredOn(starMock, starMock);
                var sectorMock = sectorHolding(systemMock,
                        marketOwnedBy("hegemony", orbitingEntity(ORBIT_CLOSE, starMock)),
                        marketOwnedBy("blackrock", orbitingEntity(ORBIT_FAR, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                        sectorMock, systemMock, false, OwnershipGrouping.identity());

                // Building the comparator touches no geometry; only the first compare - the
                // first tie - walks the economy, so a system that never ties costs nothing.
                verify(sectorMock, never()).getEconomy();
                comparator.compare("hegemony", "blackrock");
                verify(sectorMock).getEconomy();
            }
        }

        @Test
        void treatsAMarketWithNoPrimaryEntityAsFarthestFromTheCentre() {
            try (MockedStatic<Markets> marketsMock = mockStatic(Markets.class)) {
                stubEveryMarketOwnedAndKnown(marketsMock);
                var starMock = starAt(0.0f, 0.0f);
                var systemMock = systemCentredOn(starMock, starMock);
                // hegemony's colony has no primary entity to place, so it is unplaceable and
                // never wins the tie however the ids sort; the placed rival takes it.
                var sectorMock = sectorHolding(systemMock,
                        marketOwnedBy("hegemony", null),
                        marketOwnedBy("blackrock", orbitingEntity(ORBIT_FAR, starMock)));

                var comparator = MarketProximityTieBreak.forSystem(
                        sectorMock, systemMock, false, OwnershipGrouping.identity());

                assertThat(comparator.compare("hegemony", "blackrock")).isPositive();
            }
        }
    }

    // Stubs the colony filter open for every market, so the read turns on the bloc mapping over
    // the real orbit geometry rather than on which markets count as colonies.
    private static void stubEveryMarketOwnedAndKnown(MockedStatic<Markets> marketsMock) {
        marketsMock.when(() -> Markets.isCountedAsColony(any(), anyBoolean())).thenReturn(true);
    }

    // A market owned by a faction, sitting on the given primary entity - the body whose orbit
    // fixes the market's distance from the centre.
    private static MarketAPI marketOwnedBy(String factionId, SectorEntityToken primaryEntity) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(factionId);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(factionMock);
        when(marketMock.getPrimaryEntity()).thenReturn(primaryEntity);
        return marketMock;
    }

    // A system with a centre token and its stars, the two the reference-star search reads.
    private static StarSystemAPI systemCentredOn(SectorEntityToken centre, PlanetAPI... stars) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getCenter()).thenReturn(centre);
        when(systemMock.getPlanets()).thenReturn(List.of(stars));
        return systemMock;
    }

    // A sector whose economy holds the given markets for the system, the walk the tie-break
    // reads once on its first compare.
    private static SectorAPI sectorHolding(StarSystemAPI system, MarketAPI... markets) {
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(system)).thenReturn(List.of(markets));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        return sectorMock;
    }
}
