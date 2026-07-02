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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the economy read: {@link KnownMarketFootprints}
 * folding a stubbed system's markets into per-faction footprints through the
 * known-market filter. Exercises the filter (condition-only skipped, the
 * discovery / un-hidden gate) and the hidden-market token sizing, plus the
 * presence predicate the map's inhabitation test leans on.
 */
class KnownMarketFootprintsIntegrationTest {

    @Nested
    class ReadByFaction {

        @Test
        void readByFactionSumsAKnownMarketUnderItsFaction() {
            var sector = sectorWith("owned-system", visibleMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector));

            assertThat(footprints).containsOnlyKeys("hegemony");
            assertThat(footprints.get("hegemony").totalSize()).isEqualTo(5);
        }

        @Test
        void readByFactionSkipsConditionOnlyMarket() {
            // A bare rock's condition-only placeholder is no colony, so it folds
            // into no footprint.
            var sector = sectorWith("bare-system", market(faction("hegemony"), 6, true, false, false));

            assertThat(KnownMarketFootprints.readByFaction(sector, onlySystem(sector))).isEmpty();
        }

        @Test
        void readByFactionCountsHiddenMarketAtTokenSizeOne() {
            // A hidden market (a concealed base) folds in at a token size of 1
            // rather than its real size 5, so it flags presence without skewing
            // dominance.
            var sector = sectorWith("hidden-system", hiddenMarket(faction("hegemony"), 5));

            var footprints = KnownMarketFootprints.readByFaction(sector, onlySystem(sector));

            assertThat(footprints.get("hegemony").totalSize()).isEqualTo(1);
        }

        @Test
        void readByFactionExcludesUndiscoveredStation() {
            // A concealed station (hidden market on a still-discoverable entity)
            // fails the known-market gate, so it folds into no footprint.
            var sector = sectorWith("undiscovered-system",
                    concealedStation(faction("knights_of_selkie"), 5));

            assertThat(KnownMarketFootprints.readByFaction(sector, onlySystem(sector))).isEmpty();
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
        void hasKnownOwnedMarketIsFalseForConditionOnlyMarket() {
            // A bare rock's condition-only market is no colony, so the system has
            // no faction presence.
            var sector = sectorWith("bare-system", market(faction("hegemony"), 6, true, false, false));

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
        return market(faction, size, false, false, false);
    }

    // A hidden market (vanilla concealed base) on a discovered entity: it still
    // marks its system, but folds into dominance at a token size of 1.
    private static MarketAPI hiddenMarket(FactionAPI faction, int size) {
        return market(faction, size, false, true, false);
    }

    // A concealed station: a hidden market on a still-discoverable entity, the
    // shape a base wears before the player finds it. Fails both known-market arms,
    // so it confers no presence until discovery un-hides or reveals it.
    private static MarketAPI concealedStation(FactionAPI faction, int size) {
        return market(faction, size, false, true, true);
    }

    // A colony surfaced ahead of its entity being physically found: the market
    // un-hidden but the entity still discoverable. Public knowledge already, so it
    // counts as presence (FSF's DWR43 colonies between entry and approach).
    private static MarketAPI revealedColonyAwaitingApproach(FactionAPI faction, int size) {
        return market(faction, size, false, false, true);
    }

    private static MarketAPI market(FactionAPI faction, int size, boolean isConditionOnly,
            boolean isHidden, boolean isUndiscovered) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(isUndiscovered);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(faction);
        when(marketMock.getSize()).thenReturn(size);
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
