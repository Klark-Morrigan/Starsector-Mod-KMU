package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.NEUTRAL_BASE;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.centreSystemOn;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.dark;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.faction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.hiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.marketAtStability;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.onlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketOnOrbit;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.sectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.stabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.starAt;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.undiscoveredHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.visibleMarket;
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
    // The stability-only weighting rule the palette-resolving suites share; the parameterless
    // entry points instead read the live LunaLib settings only the running game provides.
    private static final DominanceRules STABILITY_WEIGHTED = stabilityWeightedRules();

    // The pass most suites resolve under: the stability-only rule, the normal known-to-player
    // filter, and the faction (identity) grouping. The grouping-varying suites build their own.
    private static final DominancePass STABILITY_PASS =
            new DominancePass(STABILITY_WEIGHTED, false, OwnershipGrouping.identity());

    @Nested
    class ResolveDominantOwnerBySystemId {

        @Test
        void resolveDominantOwnerNamesOwnerAndColorByDominantFaction() {
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = faction("tritachyon", TRITACHYON_BRIGHT);
            var sector = sectorWith("owned-system", List.of(hegemony, tritachyon),
                    visibleMarket(hegemony, 5), visibleMarket(tritachyon, 3));

            var owners = SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS);

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
            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
                    .doesNotContainKey("bare-system");
        }

        @Test
        void resolveDominantOwnerSkipsUninhabitedSystems() {
            var sector = sectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS)).isEmpty();
        }

        @Test
        void resolveDominantOwnerPaintsSystemHoldingOnlyAHiddenMarket() {
            // A hidden market (vanilla hidden market) still marks its system as
            // owned once its entity is on the map - it is no longer disqualified.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("hidden-system", List.of(hegemony),
                    hiddenMarket(hegemony, 5));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
                    .containsEntry("tie-system",
                            new DominantOwner("tritachyon", TRITACHYON_BRIGHT, dark(TRITACHYON_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerBreaksAFullTieByMarketProximity() {
            // Two size-5 station colonies tie on every weight level, so the winner falls to the
            // tie-break. hegemony's market orbits nearer the star, so it takes the system even
            // though the lowest-id fallback would hand it to blackrock - proximity, not id.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var blackrock = faction("blackrock", TRITACHYON_BRIGHT);
            var hegemonyMarket = visibleMarket(hegemony, 5);
            var blackrockMarket = visibleMarket(blackrock, 5);
            var sector = sectorWith("rama", List.of(hegemony, blackrock),
                    hegemonyMarket, blackrockMarket);
            var star = starAt(0.0f, 0.0f);
            centreSystemOn(onlySystem(sector), star);
            placeMarketOnOrbit(hegemonyMarket, 100.0f, star);
            placeMarketOnOrbit(blackrockMarket, 200.0f, star);

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
                    .containsEntry("rama",
                            new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerResolvesATiedSystemAlikeUnderFactionAndAllianceGrouping() {
            // The same two tied markets - hegemony's nearer the star than blackrock's - decide
            // the system, so the hegemony side wins whether hegemony stands alone (faction view)
            // or folds into an alliance (alliance view). The tie never flips between the views.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var blackrock = faction("blackrock", TRITACHYON_BRIGHT);
            var hegemonyMarket = visibleMarket(hegemony, 5);
            var blackrockMarket = visibleMarket(blackrock, 5);
            var sector = sectorWith("rama", List.of(hegemony, blackrock),
                    hegemonyMarket, blackrockMarket);
            var star = starAt(0.0f, 0.0f);
            centreSystemOn(onlySystem(sector), star);
            placeMarketOnOrbit(hegemonyMarket, 100.0f, star);
            placeMarketOnOrbit(blackrockMarket, 200.0f, star);
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "greater_hegemony"),
                    Map.of("greater_hegemony", "hegemony"),
                    Map.of("greater_hegemony", "Greater Hegemony"));

            // Faction view: hegemony wins. Alliance view: the hegemony-led alliance wins the
            // same system, painted in hegemony's palette under the alliance bloc id.
            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
                    .containsEntry("rama",
                            new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(
                    sector, new DominancePass(STABILITY_WEIGHTED, false, grouping)))
                    .containsEntry("rama", new DominantOwner(
                            "greater_hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerIncludesStationOnceDiscovered() {
            // Once the entity is discovered (no longer discoverable), the station's
            // faction claims its system.
            var knights = faction("knights_of_selkie", HEGEMONY_BRIGHT);
            var sector = sectorWith("discovered-system", List.of(knights),
                    market(knights, 5, false, false, false));

            assertThat(SectorPolitics.resolveDominantOwnerBySystemId(sector, STABILITY_PASS))
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
                    sector, STABILITY_PASS))
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
                    sector, new DominancePass(STABILITY_WEIGHTED, false, grouping)))
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
            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), STABILITY_PASS))
                    .isEqualTo(new DominantOwner("hegemony", HEGEMONY_BRIGHT, dark(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantOwnerReturnsNullForUninhabitedSystem() {
            var sector = sectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), STABILITY_PASS)).isNull();
        }

        @Test
        void resolveDominantOwnerIgnoresConditionOnlyMarket() {
            // A bare rock's condition-only market is no colony, so the system has no
            // owner - matching the bulk pass.
            var hegemony = faction("hegemony", HEGEMONY_BRIGHT);
            var sector = sectorWith("bare-system", List.of(hegemony),
                    market(hegemony, 6, true, false, false));

            assertThat(SectorPolitics.resolveDominantOwner(sector, onlySystem(sector), STABILITY_PASS)).isNull();
        }

        @Test
        void resolveDominantOwnerReturnsNullForNullSystem() {
            var sector = sectorWith("owned-system", List.of(faction("hegemony", HEGEMONY_BRIGHT)),
                    visibleMarket(faction("hegemony", HEGEMONY_BRIGHT), 5));

            assertThat(SectorPolitics.resolveDominantOwner(sector, null, STABILITY_PASS)).isNull();
        }

        @Test
        void resolveDominantOwnerReturnsNullForNullSector() {
            assertThat(SectorPolitics.resolveDominantOwner(null, mock(StarSystemAPI.class), STABILITY_PASS)).isNull();
        }
    }

    // A visible market sitting on a planet (getPlanetEntity() non-null), which the dominance
    // rule prefers over a station at an otherwise exact weight tie. Local to this suite: only
    // the tie-break test reads a planet market.
    private static MarketAPI planetMarket(FactionAPI faction, int size) {
        var marketMock = visibleMarket(faction, size);
        when(marketMock.getPlanetEntity()).thenReturn(mock(PlanetAPI.class));
        return marketMock;
    }

    // A colony surfaced ahead of its entity being physically found: the market un-hidden but the
    // entity still discoverable. Public knowledge already, so it counts as presence (FSF's DWR43
    // colonies between entry and approach). Local to this suite.
    private static MarketAPI revealedColonyAwaitingApproach(FactionAPI faction, int size) {
        return market(faction, size, false, false, true);
    }

    // The five-argument market shape this suite's condition-only and discovery tests read: a
    // visible-stability colony varying only its condition-only, hidden, and undiscovered flags.
    // Delegates to the shared builder, which takes stability as its sixth argument.
    private static MarketAPI market(FactionAPI faction, int size, boolean isConditionOnly,
            boolean isHidden, boolean isUndiscovered) {
        return SectorPoliticsFixtures.market(faction, size, isConditionOnly, isHidden,
                isUndiscovered, FULL_STABILITY);
    }
}
