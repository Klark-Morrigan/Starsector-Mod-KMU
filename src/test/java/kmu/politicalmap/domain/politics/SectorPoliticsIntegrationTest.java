package kmu.politicalmap.domain.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the ownership pipeline end to end: {@link SectorPolitics}
 * reading a stubbed economy through {@link KnownMarketFootprints} and the real
 * {@link SystemDominance}, then resolving the winner's palette. Exercises them
 * together because the value of the adapter is the wiring (footprint read,
 * dominance rule, color lookup), which mocking either collaborator would hide.
 */
class SectorPoliticsIntegrationTest {
    private static final float FULL_STABILITY = 10.0f;
    // The pipeline is exercised through the explicit-rule overloads: the
    // parameterless entry points read the live LunaLib toggle, which only the
    // running game provides.
    private static final boolean IS_STABILITY_WEIGHTED = true;
    private static final Color HEGEMONY_BRIGHT = new Color(120, 160, 200);
    private static final Color TRITACHYON_BRIGHT = new Color(140, 180, 220);
    private static final Color NEUTRAL_BASE = new Color(150, 150, 150);

    @Nested
    class ResolveDominantOwnerBySystemId {

        @Test
        void resolveDominantOwnerNamesOwnerAndColorByDominantFaction() {
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            var owners = SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED);

            // The owner carries the id the renderer styles by, the bright UI color
            // the cell is filled and outlined in, and the dark UI color its
            // interior seams are stroked in.
            assertThat(owners).containsEntry("owned-system",
                    new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerNamesIndependentWhenItHolds() {
            var independent = faction("independent", NEUTRAL_BASE);
            var sector = sectorWith("frontier-system", List.of(independent),
                    visibleMarket(independent, 4));

            // An independent-held system resolves to the "independent" id, the one
            // the renderer's fill-alpha rule dims on.
            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("frontier-system",
                            new DominantOwner("independent", NEUTRAL_BASE, dark(NEUTRAL_BASE)));
        }

        @Test
        void resolveDominantOwnerWeighsMarketsByStability() {
            // Stability scales each market's worth before dominance: hegemony's
            // size-5 colony at stability 4 weighs 2, so tritachyon's smaller but
            // fully stable size-3 colony (weight 3) takes the system.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("unrest-system", List.of(hegemony, tritachyon),
                    marketAtStability(hegemony, 5, 4.0f), visibleMarket(tritachyon, 3));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("unrest-system",
                            new DominantOwner("tritachyon", TRITACHYON_BRIGHT, dark(TRITACHYON_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerIgnoresConditionOnlyMarkets() {
            // A bare rock's condition-only market must not make its faction the
            // owner; the system has no real colony, so it gets no owner.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("bare-system", List.of(hegemony),
                    market(hegemony, 6, true, false, false));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .doesNotContainKey("bare-system");
        }

        @Test
        void resolveDominantOwnerSkipsUninhabitedSystems() {
            var sector = sectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED)).isEmpty();
        }

        @Test
        void resolveDominantOwnerPaintsSystemHoldingOnlyAHiddenMarket() {
            // A hidden market (vanilla concealed base) still marks its system as
            // owned once its entity is on the map - it is no longer disqualified.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("hidden-system", List.of(hegemony),
                    hiddenMarket(hegemony, 5));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("hidden-system",
                            new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerCountsHiddenMarketAsTokenSizeOne() {
            // The hidden hegemony market is size 5, but folds into dominance at a
            // token size rating of 1, so the openly held tritachyon size-2 market
            // wins the combined-weight comparison and claims the cell.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("mixed-system", List.of(hegemony, tritachyon),
                    hiddenMarket(hegemony, 5), visibleMarket(tritachyon, 2));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("mixed-system",
                            new DominantOwner("tritachyon", TRITACHYON_BRIGHT, dark(TRITACHYON_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerIncludesFactionHiddenFromIntelDirectory() {
            // A faction kept out of the intel directory is NOT barred from the map;
            // what matters is the market being discovered. Its discovered, visible
            // market claims its system like any other.
            var hiddenFaction = faction("zea_dusk", HEGEMONY_BRIGHT);
            var sector = sectorWith("hidden-faction-system", List.of(hiddenFaction),
                    market(hiddenFaction, 5, false, false, false));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("hidden-faction-system",
                            new DominantOwner("zea_dusk", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerExcludesUndiscoveredStation() {
            // A concealed station - hidden market on a still-discoverable entity,
            // e.g. Knights of Ludd's Battlestar Libra - is absent from the map
            // until found, so it claims no territory.
            var knights = faction("knights_of_selkie", HEGEMONY_BRIGHT);
            var sector = sectorWith("undiscovered-system", List.of(knights),
                    concealedStation(knights, 5));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .doesNotContainKey("undiscovered-system");
        }

        @Test
        void resolveDominantOwnerPaintsRevealedColonyAwaitingApproach() {
            // A colony surfaced ahead of its entity being physically found - the
            // market un-hidden but the entity still discoverable, as FSF's DWR43
            // colonies sit between first entry and the fleet closing in. It is
            // public knowledge, so it claims its system at once.
            var fsf = faction("aEP_FSF", HEGEMONY_BRIGHT);
            var sector = sectorWith("revealed-system", List.of(fsf),
                    revealedColonyAwaitingApproach(fsf, 5));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("revealed-system",
                            new DominantOwner("aEP_FSF", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerBreaksWeightTieByPlanetOwnership() {
            // Equal-weight footprints: the faction on a planet outranks the one
            // on a station, overriding the lower-faction-id tie-break that would
            // otherwise hand the cell to hegemony.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("tie-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), planetMarket(tritachyon, 5));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("tie-system",
                            new DominantOwner("tritachyon", TRITACHYON_BRIGHT, dark(TRITACHYON_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerIncludesStationOnceDiscovered() {
            // Once the entity is discovered (no longer discoverable), the station's
            // faction claims its system.
            var knights = faction("knights_of_selkie", HEGEMONY_BRIGHT);
            var sector = sectorWith("discovered-system", List.of(knights),
                    market(knights, 5, false, false, false));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, IS_STABILITY_WEIGHTED))
                    .containsEntry("discovered-system",
                            new DominantOwner("knights_of_selkie", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }
    }

    @Nested
    class ResolveDominantOwner {

        @Test
        void resolveDominantOwnerNamesDominantFactionWithPalette() {
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            // The single-system resolve returns the same winner and palette the bulk
            // pass would put under this system's id.
            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), IS_STABILITY_WEIGHTED))
                    .isEqualTo(new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerReturnsNullForUninhabitedSystem() {
            var sector = sectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), IS_STABILITY_WEIGHTED)).isNull();
        }

        @Test
        void resolveDominantOwnerIgnoresConditionOnlyMarket() {
            // A bare rock's condition-only market is no colony, so the system has no
            // owner - matching the bulk pass.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("bare-system", List.of(hegemony),
                    market(hegemony, 6, true, false, false));

            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), IS_STABILITY_WEIGHTED)).isNull();
        }

        @Test
        void resolveDominantOwnerReturnsNullForNullSystem() {
            var sector = sectorWith("owned-system", List.of(faction("hegemony", HEGEMONY_BRIGHT)),
                    visibleMarket(faction("hegemony", HEGEMONY_BRIGHT), 5));

            assertThat(SectorPolitics.resolveDominantOwner(sector, null, IS_STABILITY_WEIGHTED)).isNull();
        }

        @Test
        void resolveDominantOwnerReturnsNullForNullSector() {
            assertThat(SectorPolitics.resolveDominantOwner(null, mock(StarSystemAPI.class), IS_STABILITY_WEIGHTED)).isNull();
        }
    }

    @Nested
    class ResolveNeutralColor {

        @Test
        void resolveNeutralColorReturnsNeutralFactionBaseColor() {
            var sector = sectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveNeutralColor(sector)).isEqualTo(NEUTRAL_BASE);
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
    // shape a base wears before the player finds it. Fails both known-market
    // arms, so it confers no presence until discovery un-hides or reveals it.
    private static MarketAPI concealedStation(FactionAPI faction, int size) {
        return market(faction, size, false, true, true);
    }

    // A colony surfaced ahead of its entity being physically found: the market
    // un-hidden but the entity still discoverable. Public knowledge already, so
    // it counts as presence (FSF's DWR43 colonies between entry and approach).
    private static MarketAPI revealedColonyAwaitingApproach(FactionAPI faction, int size) {
        return market(faction, size, false, false, true);
    }

    // A visible market sitting on a planet (getPlanetEntity() non-null), which
    // the dominance rule prefers over a station at an otherwise exact weight tie.
    private static MarketAPI planetMarket(FactionAPI faction, int size) {
        var marketMock = market(faction, size, false, false, false);
        when(marketMock.getPlanetEntity()).thenReturn(mock(PlanetAPI.class));
        return marketMock;
    }

    // A visible owned market at the given stability, for exercising the
    // stability scaling of dominance weights through the full pipeline.
    private static MarketAPI marketAtStability(FactionAPI faction, int size, float stability) {
        var marketMock = market(faction, size, false, false, false);
        when(marketMock.getStabilityValue()).thenReturn(stability);
        return marketMock;
    }

    private static MarketAPI market(FactionAPI faction, int size, boolean isConditionOnly,
            boolean isHidden, boolean isUndiscovered) {
        var entityMock = mock(SectorEntityToken.class);
        when(entityMock.isDiscoverable()).thenReturn(isUndiscovered);
        var marketMock = mock(MarketAPI.class);
        when(marketMock.getFaction()).thenReturn(faction);
        when(marketMock.getSize()).thenReturn(size);
        when(marketMock.getStabilityValue()).thenReturn(FULL_STABILITY);
        when(marketMock.isPlanetConditionMarketOnly()).thenReturn(isConditionOnly);
        when(marketMock.isHidden()).thenReturn(isHidden);
        when(marketMock.getPrimaryEntity()).thenReturn(entityMock);
        return marketMock;
    }

    private static FactionAPI faction(String id, Color bright) {
        var factionMock = mock(FactionAPI.class);
        when(factionMock.getId()).thenReturn(id);
        when(factionMock.getBrightUIColor()).thenReturn(bright);
        when(factionMock.getDarkUIColor()).thenReturn(dark(bright));
        return factionMock;
    }

    // The dark UI shade the faction stub returns for its seam color, distinct
    // from the bright fill/border color so a test can tell the two apart. Any
    // stable transform does; the pipeline only forwards whichever color the
    // faction hands back, it does not compute the shade.
    private static Color dark(Color bright) {
        return bright.darker();
    }

    // Wires a sector with one system whose economy holds the given markets, the
    // owning factions resolvable by id, and a neutral faction for the unowned
    // outline color.
    private static SectorAPI sectorWith(String systemId, List<FactionAPI> factions,
            MarketAPI... markets) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);

        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(systemMock)).thenReturn(List.of(markets));

        var neutralMock = mock(FactionAPI.class);
        when(neutralMock.getBaseUIColor()).thenReturn(NEUTRAL_BASE);

        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systemMock));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        when(sectorMock.getFaction("neutral")).thenReturn(neutralMock);
        for (var faction : factions) {
            when(sectorMock.getFaction(faction.getId())).thenReturn(faction);
        }
        return sectorMock;
    }
}
