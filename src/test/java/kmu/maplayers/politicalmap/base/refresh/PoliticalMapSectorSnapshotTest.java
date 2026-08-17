package kmu.maplayers.politicalmap.base.refresh;

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

import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the single-walk snapshot: {@link PoliticalMapSectorSnapshot}
 * driving the real visibility rule ({@link kmu.maplayers.base.visibility.MapVisibility})
 * and dominance rule ({@link kmu.maplayers.politicalmap.base.dominance.SystemDominance}) over a
 * stubbed economy. Exercised together because the point of the snapshot is the
 * separation of shapes: one sector walk yields a scalar visibility fingerprint that
 * moves when the on-map set changes and a per-system holder map that moves when a
 * drawn system changes hands, each without the other.
 */
class PoliticalMapSectorSnapshotTest {

    // The scan is exercised through the explicit-rule signature: the shorter entry
    // point reads the live LunaLib settings, which only the running game provides.
    // Station and patrol weighting are off so the snapshot tests turn on the
    // stability rule alone, with the colony-size weight at its identity and the colony
    // penalty at a full collapse (the whole-rating stability behaviour).
    private static final DominanceRules STABILITY_WEIGHTED =
            new DominanceRules(true,
                    new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.FIXED, 1.0, 1.0),
                    new StationWeighting(false, 1.0, 0.5, 0.5),
                    new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    @Nested
    class Scan {

        @Test
        void scanReturnsEmptySnapshotForNullSector() {
            var snapshot = scanUnderStabilityWeighting(null);

            assertThat(snapshot.visibilityFingerprint()).isZero();
            assertThat(snapshot.ownerBySystemId()).isEmpty();
        }

        @Test
        void ownerMapNamesTheDominantFactionOfAnOwnedSystem() {
            var snapshot = scanUnderStabilityWeighting(
                    buildSectorWith(buildSystem("a"), buildOwnedMarket("hegemony", 5)));

            assertThat(snapshot.ownerBySystemId()).containsExactly(entry("a", "hegemony"));
        }

        @Test
        void visibilityFingerprintShiftsWhenASystemBecomesInhabited() {
            // An empty system is off the map; a colony admits it, so the on-map set
            // - and the visibility fingerprint - changes.
            var before = scanUnderStabilityWeighting(buildSectorWith(buildSystem("a")))
                    .visibilityFingerprint();

            var after = scanUnderStabilityWeighting(
                    buildSectorWith(buildSystem("a"), buildOwnedMarket("hegemony", 5)))
                    .visibilityFingerprint();

            assertThat(after).isNotEqualTo(before);
        }

        @Test
        void ownerMapChangesWhenAColonysHolderChangesButVisibilityHolds() {
            // The AI-captures-or-founds case: the same system stays on the map, but
            // its holder flips. The holder map must move while the visibility hash
            // stays put, so only that system reshapes and no geometry rebuilds.
            var before = scanUnderStabilityWeighting(
                    buildSectorWith(buildSystem("a"), buildOwnedMarket("hegemony", 5)));

            var after = scanUnderStabilityWeighting(
                    buildSectorWith(buildSystem("a"), buildOwnedMarket("tritachyon", 5)));

            assertThat(after.ownerBySystemId()).containsExactly(entry("a", "tritachyon"));
            assertThat(after.visibilityFingerprint()).isEqualTo(before.visibilityFingerprint());
        }

        @Test
        void ownerMapOmitsADecivilisedShellThatStillCountsForVisibility() {
            // A revealed dead colony is drawn (visibility) but confers no holder, so
            // it is absent from the holder map - the mirror image of an owned system.
            var snapshot = scanUnderStabilityWeighting(buildSectorWith(buildDecivilisedSystem("a")));

            assertThat(snapshot.visibilityFingerprint()).isNotZero();
            assertThat(snapshot.ownerBySystemId()).isEmpty();
        }
    }

    // Binds the two arguments every case here shares - the stability-only rule and no
    // dev reveals - so each test reads as the sector it scans and nothing else.
    private static PoliticalMapSectorSnapshot scanUnderStabilityWeighting(SectorAPI sector) {
        return PoliticalMapSectorSnapshot.scan(
                sector,
                STABILITY_WEIGHTED,
                MapVisibilityOverrides.NONE);
    }

    // A single-system sector whose economy returns the given markets for that
    // system. Hyperspace holds no star anchor, so no system reads as star-visible:
    // inhabitation is the only route onto the map here.
    private static SectorAPI buildSectorWith(StarSystemAPI system, MarketAPI... markets) {
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
    private static StarSystemAPI buildSystem(String id) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        when(systemMock.getLocation()).thenReturn(new Vector2f(2f, 2f));
        when(systemMock.getJumpPoints()).thenReturn(List.of());
        return systemMock;
    }

    // An unreachable system holding a revealed decivilised planet: on the map as a
    // dead colony, yet unowned, so it drives visibility without an holder.
    private static StarSystemAPI buildDecivilisedSystem(String id) {
        var planet = buildDecivilisedPlanet();
        var systemMock = buildSystem(id);
        when(systemMock.getPlanets()).thenReturn(List.of(planet));
        return systemMock;
    }

    private static PlanetAPI buildDecivilisedPlanet() {
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
    private static MarketAPI buildOwnedMarket(String factionId, int size) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(false);
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(factionId);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(factionMock);
        when(marketMock.getSize()).thenReturn(size);
        // Full stability, so the market weighs its whole size and the snapshot
        // tests stay about visibility and holder diffs, not the weight scaling.
        when(marketMock.getStabilityValue()).thenReturn(FULL_STABILITY);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(false);
        when(marketMock.isHidden()).thenReturn(false);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }
}
