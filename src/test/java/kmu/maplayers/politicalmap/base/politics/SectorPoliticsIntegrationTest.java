package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    // parameterless entry points read the live LunaLib settings, which only the
    // running game provides. Station and patrol weighting are off here so these tests
    // pin the stability rule alone, with the colony-size weight at its identity and the
    // colony penalty at a full collapse (the old whole-rating stability behaviour); the
    // station and patrol bonuses are covered in KnownMarketFootprints.
    private static final DominanceRules STABILITY_WEIGHTED =
            new DominanceRules(1.0, HiddenMarketScalingChoice.FIXED, 1.0, true, 1.0,
                    false, 1.0, 0.5, 0.5, false, 0.25, 0.5, 1.0, 0.5);
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

            var owners = SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED);

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
            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
                    .doesNotContainKey("bare-system");
        }

        @Test
        void resolveDominantOwnerSkipsUninhabitedSystems() {
            var sector = sectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED)).isEmpty();
        }

        @Test
        void resolveDominantOwnerPaintsSystemHoldingOnlyAHiddenMarket() {
            // A hidden market (vanilla hidden market) still marks its system as
            // owned once its entity is on the map - it is no longer disqualified.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("hidden-system", List.of(hegemony),
                    hiddenMarket(hegemony, 5));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
                    .containsEntry("hidden-faction-system",
                            new DominantOwner("zea_dusk", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerExcludesUndiscoveredStation() {
            // An undiscovered hidden market on a still-discoverable entity,
            // e.g. Knights of Ludd's Battlestar Libra - is absent from the map
            // until found, so it claims no territory.
            var knights = faction("knights_of_selkie", HEGEMONY_BRIGHT);
            var sector = sectorWith("undiscovered-system", List.of(knights),
                    undiscoveredHiddenMarket(knights, 5));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_WEIGHTED))
                    .containsEntry("discovered-system",
                            new DominantOwner("knights_of_selkie", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerReproducesFactionOwnerMapUnderIdentityGrouping() {
            // The identity grouping makes every faction its own bloc, so the winner
            // and palette are the plain faction owner - byte-for-byte the faction view.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(
                    sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity()))
                    .containsEntry("owned-system",
                            new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerSumsAlliedColoniesPastLargerLoneRivalUnderAllianceGrouping() {
            // Two small allied colonies (weight 2 each) sum to 4 under the alliance
            // grouping, outweighing a lone rival's larger single colony (weight 3)
            // that beats either ally alone. The cell carries the alliance bloc id and
            // paints in the alliance's dominant member's (hegemony's) palette.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var persean = faction("persean", NEUTRAL_BASE);
            var sector = sectorWith("contested-system", List.of(hegemony, tritachyon, persean),
                    visibleMarket(hegemony, 2), visibleMarket(tritachyon, 2),
                    visibleMarket(persean, 3));
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(
                    sector, STABILITY_WEIGHTED, false, grouping))
                    .containsEntry("contested-system",
                            new DominantOwner("alliance-1", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
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
            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), STABILITY_WEIGHTED))
                    .isEqualTo(new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerReturnsNullForUninhabitedSystem() {
            var sector = sectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), STABILITY_WEIGHTED)).isNull();
        }

        @Test
        void resolveDominantOwnerIgnoresConditionOnlyMarket() {
            // A bare rock's condition-only market is no colony, so the system has no
            // owner - matching the bulk pass.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("bare-system", List.of(hegemony),
                    market(hegemony, 6, true, false, false));

            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), STABILITY_WEIGHTED)).isNull();
        }

        @Test
        void resolveDominantOwnerReturnsNullForNullSystem() {
            var sector = sectorWith("owned-system", List.of(faction("hegemony", HEGEMONY_BRIGHT)),
                    visibleMarket(faction("hegemony", HEGEMONY_BRIGHT), 5));

            assertThat(SectorPolitics.resolveDominantOwner(sector, null, STABILITY_WEIGHTED)).isNull();
        }

        @Test
        void resolveDominantOwnerReturnsNullForNullSector() {
            assertThat(SectorPolitics.resolveDominantOwner(null, mock(StarSystemAPI.class), STABILITY_WEIGHTED)).isNull();
        }
    }

    @Nested
    class ResolveVisiblyWeightedBlocIds {

        @Test
        void resolveVisiblyWeightedBlocIdsListsABlocHoldingMarketsInSeveralSystemsOnce() {
            // A bloc's footprint accumulates across systems rather than overwriting, so a faction
            // present in two systems is one selectable option, not two.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony),
                    systemMarkets("system-a", visibleMarket(hegemony, 5)),
                    systemMarkets("system-b", visibleMarket(hegemony, 3)));

            assertThat(SectorPolitics.resolveVisiblyWeightedBlocIds(
                    sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity()))
                    .containsExactly("hegemony");
        }

        @Test
        void resolveVisiblyWeightedBlocIdsListsAnAllianceAsOneBlocNotItsMembers() {
            // The alliance grouping folds allied members into one bloc, so the alliance is gated as a
            // single unit and its members never surface as separate options.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony, tritachyon),
                    systemMarkets("system-a", visibleMarket(hegemony, 2)),
                    systemMarkets("system-b", visibleMarket(tritachyon, 3)));
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));

            assertThat(SectorPolitics.resolveVisiblyWeightedBlocIds(
                    sector, STABILITY_WEIGHTED, false, grouping)).containsExactly("alliance-1");
        }

        @Test
        void resolveVisiblyWeightedBlocIdsExcludesAWeightlessBloc() {
            // A size-0 colony marks presence but carries no weight, so the > 0 gate drops it - a bloc
            // is offered only when spotlighting it would highlight something.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony),
                    systemMarkets("weightless-system", visibleMarket(hegemony, 0)));

            assertThat(SectorPolitics.resolveVisiblyWeightedBlocIds(
                    sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity())).isEmpty();
        }

        @Test
        void resolveVisiblyWeightedBlocIdsSkipsConditionOnlyMarkets() {
            // A bare rock's condition-only market is no colony, so it never marks a bloc's presence -
            // matching the ownership pass, so a bloc is selectable exactly when it could paint.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWithSystems(List.of(hegemony),
                    systemMarkets("bare-system", market(hegemony, 6, true, false, false)));

            assertThat(SectorPolitics.resolveVisiblyWeightedBlocIds(
                    sector, STABILITY_WEIGHTED, false, OwnershipGrouping.identity())).isEmpty();
        }

        @Test
        void resolveVisiblyWeightedBlocIdsIsEmptyForNullSector() {
            assertThat(SectorPolitics.resolveVisiblyWeightedBlocIds(
                    null, STABILITY_WEIGHTED, false, OwnershipGrouping.identity())).isEmpty();
        }
    }

    private static StarSystemAPI onlySystem(SectorAPI sector) {
        return sector.getStarSystems().get(0);
    }

    private static MarketAPI visibleMarket(FactionAPI faction, int size) {
        return market(faction, size, false, false, false);
    }

    // A hidden market (vanilla hidden market) on a discovered entity: it still
    // marks its system, but folds into dominance at a token size of 1.
    private static MarketAPI hiddenMarket(FactionAPI faction, int size) {
        return market(faction, size, false, true, false);
    }

    // An undiscovered hidden market on a still-discoverable entity, the
    // shape a market wears before the player finds it. Fails both known-market
    // arms, so it confers no presence until discovery un-hides or reveals it.
    private static MarketAPI undiscoveredHiddenMarket(FactionAPI faction, int size) {
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

    // Wires a sector with one system whose economy holds the given markets and
    // the owning factions resolvable by id.
    private static SectorAPI sectorWith(String systemId, List<FactionAPI> factions,
            MarketAPI... markets) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(systemId);

        var economyMock = mock(EconomyAPI.class);
        when(economyMock.getMarkets(systemMock)).thenReturn(List.of(markets));

        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(List.of(systemMock));
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        for (var faction : factions) {
            when(sectorMock.getFaction(faction.getId())).thenReturn(faction);
        }
        return sectorMock;
    }

    // One system's id paired with the markets its economy holds, so a multi-system
    // sector can be wired for the sector-wide accumulation the single-system sectorWith
    // cannot express.
    private record SystemMarkets(String id, List<MarketAPI> markets) {
    }

    private static SystemMarkets systemMarkets(String id, MarketAPI... markets) {
        return new SystemMarkets(id, List.of(markets));
    }

    // Wires a sector spanning several systems, each with its own markets, so a bloc's
    // footprint accumulates across the sector rather than within one system.
    private static SectorAPI sectorWithSystems(List<FactionAPI> factions, SystemMarkets... systems) {
        var economyMock = mock(EconomyAPI.class);
        var systemMocks = new ArrayList<StarSystemAPI>();
        for (var system : systems) {
            var systemMock = mock(StarSystemAPI.class);
            when(systemMock.getId()).thenReturn(system.id());
            when(economyMock.getMarkets(systemMock)).thenReturn(system.markets());
            systemMocks.add(systemMock);
        }

        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getStarSystems()).thenReturn(systemMocks);
        when(sectorMock.getEconomy()).thenReturn(economyMock);
        for (var faction : factions) {
            when(sectorMock.getFaction(faction.getId())).thenReturn(faction);
        }
        return sectorMock;
    }
}
