package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.politicalmap.domain.politics.KnownMarketFootprints.DOMINANCE_WEIGHT_SCALE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the economy read: {@link KnownMarketFootprints}
 * folding a stubbed system's markets into per-faction footprints through the
 * known-market filter. Exercises the filter (condition-only skipped, the
 * discovery / un-hidden gate), the hidden-market token sizing, and the
 * stability scaling of each market's dominance weight - both toggle states -
 * plus the presence predicate the map's inhabitation test leans on.
 */
class KnownMarketFootprintsIntegrationTest {

    private static final float FULL_STABILITY = 10.0f;
    private static final float HALF_STABILITY = 5.0f;
    // The two states of the player's stability-weighting toggle, named so a
    // readByFaction call site says which rule it exercises.
    private static final boolean IS_STABILITY_WEIGHTED = true;
    private static final boolean IS_NOT_STABILITY_WEIGHTED = false;

    @Nested
    class ReadByFaction {

        @Test
        void readByFactionWeighsAKnownMarketUnderItsFaction() {
            // At full stability a market is worth its whole size on the weight
            // grid: size 5 -> 5 grid units.
            var sector = sectorWith("owned-system", visibleMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED);

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(5 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesMarketWeightByStability() {
            // Stability scales the size rating before any footprint math: a
            // size-4 market at stability 5 contributes half its size.
            var sector = sectorWith("shaky-system",
                    marketAtStability(faction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(2 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionIgnoresStabilityWhenWeightingIsOff() {
            // With the player's toggle off, a destabilised market folds in at its
            // full size rating - the raw-size dominance the toggle opts back into.
            var sector = sectorWith("shaky-system",
                    marketAtStability(faction("hegemony"), 4, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_NOT_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(4 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionZeroStabilityMarketMarksPresenceAtNoWeight() {
            // At 0 stability a colony is worth nothing to dominance, but it is
            // still a known colony: the faction keeps its footprint entry, so
            // presence (and painting an unopposed system) is unaffected.
            var sector = sectorWith("collapsed-system",
                    marketAtStability(faction("hegemony"), 5, 0.0f));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED);

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalWeight()).isZero();
        }

        @Test
        void readByFactionClampsStabilityIntoTheVanillaBand() {
            // A modded market can report stability above 10; the fraction clamps
            // to full so a market never outweighs its own size.
            var sector = sectorWith("overstable-system",
                    marketAtStability(faction("hegemony"), 3, 12.0f));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(3 * DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionSkipsConditionOnlyMarket() {
            // A bare rock's condition-only placeholder is no colony, so it folds
            // into no footprint.
            var sector = sectorWith("bare-system",
                    market(faction("hegemony"), 6, true, false, false, FULL_STABILITY));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED)).isEmpty();
        }

        @Test
        void readByFactionCountsHiddenMarketAtTokenSizeOne() {
            // A hidden market (a concealed base) folds in at a token size rating
            // of 1 rather than its real size 5, so it flags presence without
            // skewing dominance.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE);
        }

        @Test
        void readByFactionScalesHiddenTokenByStability() {
            // The hidden token is a size rating like any other, so stability
            // scales it too: a destabilised concealed base marks less presence.
            var sector = sectorWith("hidden-shaky-system",
                    hiddenMarketAtStability(faction("hegemony"), 5, HALF_STABILITY));

            var footprints = KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED);

            assertThat(footprints.get("hegemony").totalWeight())
                    .isEqualTo(DOMINANCE_WEIGHT_SCALE / 2);
        }

        @Test
        void readByFactionExcludesUndiscoveredStation() {
            // A concealed station (hidden market on a still-discoverable entity)
            // fails the known-market gate, so it folds into no footprint.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.readByFaction(
                    sector, onlySystem(sector), IS_STABILITY_WEIGHTED)).isEmpty();
        }
    }

    @Nested
    class HasKnownOwnedMarket {

        @Test
        void hasKnownOwnedMarketIsTrueForVisibleOwnedMarket() {
            var sector = sectorWith("owned-system", visibleMarket(faction("hegemony"), 5));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isTrue();
        }

        @Test
        void hasKnownOwnedMarketIsTrueForZeroStabilityMarket() {
            // Presence is about knowing the colony exists, not its worth: a
            // weightless (stability 0) colony still inhabits its system.
            var sector = sectorWith("collapsed-system",
                    marketAtStability(faction("hegemony"), 5, 0.0f));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isTrue();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForConditionOnlyMarket() {
            // A bare rock's condition-only market is no colony, so the system has
            // no faction presence.
            var sector = sectorWith("bare-system",
                    market(faction("hegemony"), 6, true, false, false, FULL_STABILITY));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isFalse();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForUndiscoveredStation() {
            // A concealed station the player has not found yet - hidden market on a
            // still-discoverable entity - is absent from the map, so it confers no
            // presence.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isFalse();
        }

        @Test
        void hasKnownOwnedMarketIsTrueForRevealedColonyAwaitingApproach() {
            // A colony un-hidden ahead of its entity being found is public
            // knowledge, so it confers presence before the fleet closes in.
            var sector = sectorWith("revealed-system",
                    revealedColonyAwaitingApproach(faction("aEP_FSF"), 5));

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isTrue();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForUninhabitedSystem() {
            var sector = sectorWith("empty-system");

            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(sector, onlySystem(sector))).isFalse();
        }

        @Test
        void hasKnownOwnedMarketIsFalseForNullSector() {
            assertThat(KnownMarketFootprints.hasKnownOwnedMarket(null, mock(StarSystemAPI.class)))
                    .isFalse();
        }
    }

    private static StarSystemAPI onlySystem(SectorAPI sector) {
        return sector.getStarSystems().get(0);
    }

    private static MarketAPI visibleMarket(FactionAPI faction, int size) {
        return market(faction, size, false, false, false, FULL_STABILITY);
    }

    // A visible owned market at the given stability, the shape the weight
    // scaling tests vary.
    private static MarketAPI marketAtStability(FactionAPI faction, int size, float stability) {
        return market(faction, size, false, false, false, stability);
    }

    // A hidden market (vanilla concealed base) on a discovered entity: it still
    // marks its system, but folds into dominance at a token size rating of 1.
    private static MarketAPI hiddenMarket(FactionAPI faction, int size) {
        return market(faction, size, false, true, false, FULL_STABILITY);
    }

    // A hidden market at the given stability, for pinning that the token rating
    // scales with stability like any other size rating.
    private static MarketAPI hiddenMarketAtStability(FactionAPI faction, int size,
            float stability) {
        return market(faction, size, false, true, false, stability);
    }

    // A concealed station: a hidden market on a still-discoverable entity, the
    // shape a base wears before the player finds it. Fails both known-market arms,
    // so it confers no presence until discovery un-hides or reveals it.
    private static MarketAPI concealedStation(FactionAPI faction, int size) {
        return market(faction, size, false, true, true, FULL_STABILITY);
    }

    // A colony surfaced ahead of its entity being physically found: the market
    // un-hidden but the entity still discoverable. Public knowledge already, so it
    // counts as presence (FSF's DWR43 colonies between entry and approach).
    private static MarketAPI revealedColonyAwaitingApproach(FactionAPI faction, int size) {
        return market(faction, size, false, false, true, FULL_STABILITY);
    }

    private static MarketAPI market(FactionAPI faction, int size, boolean isConditionOnly,
            boolean isHidden, boolean isUndiscovered, float stability) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(isUndiscovered);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(faction);
        when(marketMock.getSize()).thenReturn(size);
        when(marketMock.getStabilityValue()).thenReturn(stability);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(isConditionOnly);
        when(marketMock.isHidden()).thenReturn(isHidden);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }

    private static FactionAPI faction(String id) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(id);
        return factionMock;
    }

    // Wires a sector with one system whose economy holds the given markets. No
    // faction palette or neutral faction is stubbed: the footprint read keys on
    // each market's own faction id and never resolves colors.
    private static SectorAPI sectorWith(String systemId, MarketAPI... markets) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);
        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(systemMock)).thenReturn(List.of(markets));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systemMock));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        return sectorMock;
    }
}
