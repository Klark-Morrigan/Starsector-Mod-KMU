package kmu.politicalmap.refresh;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the single-walk snapshot: {@link PoliticalMapSectorSnapshot}
 * driving the real visibility rule ({@link kmu.politicalmap.domain.visibility.PoliticalMapVisibility})
 * and dominance rule ({@link kmu.politicalmap.domain.politics.SystemDominance}) over a
 * stubbed economy. Exercised together because the point of the snapshot is the
 * separation of shapes: one sector walk yields a scalar visibility fingerprint that
 * moves when the on-map set changes and a per-system owner map that moves when a
 * drawn system changes hands, each without the other.
 */
class PoliticalMapSectorSnapshotTest {

    private static final float FULL_STABILITY = 10.0f;
    // The scan is exercised through the explicit-rule overload: the one-argument
    // entry point reads the live LunaLib toggle, which only the running game
    // provides.
    private static final boolean IS_STABILITY_WEIGHTED = true;

    @Nested
    class Scan {

        @Test
        void scanReturnsEmptySnapshotForNullSector() {
            var snapshot = PoliticalMapSectorSnapshot.scan(null, IS_STABILITY_WEIGHTED);

            assertThat(snapshot.visibilityFingerprint()).isZero();
            assertThat(snapshot.ownerBySystemId()).isEmpty();
        }

        @Test
        void ownerMapNamesTheDominantFactionOfAnOwnedSystem() {
            var snapshot = PoliticalMapSectorSnapshot.scan(
                    sectorWith(system("a"), ownedMarket("hegemony", 5)), IS_STABILITY_WEIGHTED);

            assertThat(snapshot.ownerBySystemId()).containsExactly(entry("a", "hegemony"));
        }

        @Test
        void visibilityFingerprintShiftsWhenASystemBecomesInhabited() {
            // An empty system is off the map; a colony admits it, so the on-map set
            // - and the visibility fingerprint - changes.
            var before = PoliticalMapSectorSnapshot.scan(sectorWith(system("a")),
                    IS_STABILITY_WEIGHTED).visibilityFingerprint();

            var after = PoliticalMapSectorSnapshot.scan(
                    sectorWith(system("a"), ownedMarket("hegemony", 5)), IS_STABILITY_WEIGHTED)
                    .visibilityFingerprint();

            assertThat(after).isNotEqualTo(before);
        }

        @Test
        void ownerMapChangesWhenAColonysOwnerChangesButVisibilityHolds() {
            // The AI-captures-or-founds case: the same system stays on the map, but
            // its owner flips. The owner map must move while the visibility hash
            // stays put, so only that system reshapes and no geometry rebuilds.
            var before = PoliticalMapSectorSnapshot.scan(
                    sectorWith(system("a"), ownedMarket("hegemony", 5)), IS_STABILITY_WEIGHTED);

            var after = PoliticalMapSectorSnapshot.scan(
                    sectorWith(system("a"), ownedMarket("tritachyon", 5)), IS_STABILITY_WEIGHTED);

            assertThat(after.ownerBySystemId()).containsExactly(entry("a", "tritachyon"));
            assertThat(after.visibilityFingerprint()).isEqualTo(before.visibilityFingerprint());
        }

        @Test
        void ownerMapOmitsADecivilisedShellThatStillCountsForVisibility() {
            // A revealed dead colony is drawn (visibility) but confers no owner, so
            // it is absent from the owner map - the mirror image of an owned system.
            var snapshot = PoliticalMapSectorSnapshot.scan(sectorWith(decivilisedSystem("a")),
                    IS_STABILITY_WEIGHTED);

            assertThat(snapshot.visibilityFingerprint()).isNotZero();
            assertThat(snapshot.ownerBySystemId()).isEmpty();
        }
    }

    // A single-system sector whose economy returns the given markets for that
    // system. Hyperspace holds no star anchor, so no system reads as star-visible:
    // inhabitation is the only route onto the map here.
    private static SectorAPI sectorWith(StarSystemAPI system, MarketAPI... markets) {
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(system)).thenReturn(List.of(markets));
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(List.of());
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(system));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    // An unreachable system: no jump point and its star not drawn, so it appears
    // only when inhabited. getPlanets() defaults to an empty list under Mockito, so
    // it is uninhabited unless a caller adds a colony or a decivilised planet.
    private static StarSystemAPI system(String id) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(2f, 2f));
        when(systemMock.getJumpPoints()).thenReturn(List.of());
        return systemMock;
    }

    // An unreachable system holding a revealed decivilised planet: on the map as a
    // dead colony, yet unowned, so it drives visibility without an owner.
    private static StarSystemAPI decivilisedSystem(String id) {
        var planet = decivilisedPlanet();
        var systemMock = system(id);
        when(systemMock.getPlanets()).thenReturn(List.of(planet));
        return systemMock;
    }

    private static PlanetAPI decivilisedPlanet() {
        var conditionMock = mock(MarketConditionAPI.class);
        when(conditionMock.requiresSurveying()).thenReturn(false);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getSurveyLevel()).thenReturn(MarketAPI.SurveyLevel.FULL);
        when(marketMock.getFirstCondition(Conditions.DECIVILIZED)).thenReturn(conditionMock);
        var planetMock = mock(PlanetAPI.class);
        when(planetMock.getMarket()).thenReturn(marketMock);
        return planetMock;
    }

    // A discovered, openly owned colony of the given faction and size: what the
    // footprint read counts as presence, and what the dominance rule ranks.
    private static MarketAPI ownedMarket(String factionId, int size) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(false);
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(factionId);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(factionMock);
        when(marketMock.getSize()).thenReturn(size);
        // Full stability, so the market weighs its whole size and the snapshot
        // tests stay about visibility and owner diffs, not the weight scaling.
        when(marketMock.getStabilityValue()).thenReturn(FULL_STABILITY);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(false);
        when(marketMock.isHidden()).thenReturn(false);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }
}
