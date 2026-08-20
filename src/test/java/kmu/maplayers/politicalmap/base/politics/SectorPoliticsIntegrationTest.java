package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.NEUTRAL_BASE;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildDarkTheme;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildMarketAtStability;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildSectorWith;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildStarAt;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.centreSystemOn;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.placeMarketOnOrbit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the holder pipeline end to end: {@link SectorPolitics}
 * reading a stubbed economy through {@link KnownMarketFootprints} and the real
 * {@link SystemDominance}, then resolving the winner's palette. Exercises them
 * together because the value of the adapter is the wiring (footprint read,
 * dominance rule, colour lookup), which mocking either collaborator would hide.
 */
class SectorPoliticsIntegrationTest {

    // The stability-only weighting rule the palette-resolving suites share; the parameterless
    // entry points instead read the live LunaLib settings only the running game provides.
    private static final DominanceRules STABILITY_WEIGHTED = buildStabilityWeightedRules();

    @Nested
    class ResolveDominantHolderBySystemId {

        @Test
        void resolveDominantHolderNamesHolderAndColourByDominantFaction() {

            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            var holders = SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector));

            // The holder carries the id the renderer styles by, the bright UI colour
            // the cell is filled and outlined in, and the dark UI colour its
            // interior seams are stroked in.
            assertThat(holders)
                .containsEntry(
                    "owned-system",
                    new DominantHolder("hegemony", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderNamesIndependentWhenItHolds() {

            var independent = buildFaction("independent", NEUTRAL_BASE);
            var sector = buildSectorWith(
                "frontier-system",
                List.of(independent),
                buildVisibleMarket(independent, 4));

            // An independent-held system resolves to the "independent" id, the one
            // the renderer's fill-alpha rule dims on.
            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "frontier-system",
                    new DominantHolder("independent", NEUTRAL_BASE, buildDarkTheme(NEUTRAL_BASE)));
        }

        @Test
        void resolveDominantHolderWeighsMarketsByStability() {
            // Stability scales each market's worth before dominance: hegemony's
            // size-5 colony at stability 4 weighs 2, so tritachyon's smaller but
            // fully stable size-3 colony (weight 3) takes the system.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "unrest-system",
                List.of(hegemony, tritachyon),
                buildMarketAtStability(hegemony, 5, 4.0f),
                buildVisibleMarket(tritachyon, 3));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "unrest-system",
                    new DominantHolder("tritachyon", TRITACHYON_BRIGHT, buildDarkTheme(TRITACHYON_BRIGHT)));
        }

        @Test
        void resolveDominantHolderIgnoresConditionOnlyMarkets() {
            // A bare rock's condition-only market must not make its faction the
            // holder; the system has no real colony, so it gets no holder.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "bare-system",
                List.of(hegemony),
                buildMarket(hegemony, 6, true, false, false));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .doesNotContainKey("bare-system");
        }

        @Test
        void resolveDominantHolderSkipsUninhabitedSystems() {

            var sector = buildSectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .isEmpty();
        }

        @Test
        void resolveDominantHolderPaintsSystemHoldingOnlyAHiddenMarket() {
            // A hidden market (vanilla hidden market) still marks its system as
            // owned once its entity is on the map - it is no longer disqualified.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "hidden-system",
                List.of(hegemony),
                buildHiddenMarket(hegemony, 5));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "hidden-system",
                    new DominantHolder("hegemony", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderCountsHiddenMarketAsTokenSizeOne() {
            // The hidden hegemony market is size 5, but folds into dominance at a
            // token size rating of 1, so the openly held tritachyon size-2 market
            // wins the combined-weight comparison and claims the cell.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "mixed-system",
                List.of(hegemony, tritachyon),
                buildHiddenMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 2));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "mixed-system",
                    new DominantHolder("tritachyon", TRITACHYON_BRIGHT, buildDarkTheme(TRITACHYON_BRIGHT)));
        }

        @Test
        void resolveDominantHolderIncludesFactionHiddenFromIntelDirectory() {
            // A faction kept out of the intel directory is NOT barred from the map;
            // what matters is the market being discovered. Its discovered, visible
            // market claims its system like any other.
            var hiddenFaction = buildFaction("zea_dusk", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "hidden-faction-system",
                List.of(hiddenFaction),
                buildMarket(hiddenFaction, 5, false, false, false));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "hidden-faction-system",
                    new DominantHolder("zea_dusk", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderExcludesUndiscoveredStation() {
            // An undiscovered hidden market on a still-discoverable entity,
            // e.g. Knights of Ludd's Battlestar Libra - is absent from the map
            // until found, so it claims no territory.
            var knights = buildFaction("knights_of_selkie", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "undiscovered-system",
                List.of(knights),
                buildUndiscoveredHiddenMarket(knights, 5));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .doesNotContainKey("undiscovered-system");
        }

        @Test
        void resolveDominantHolderExcludesAnUnfoundColonyHoweverPubliclyItIsListed() {
            // A colony surfaced ahead of its entity being physically found - the
            // market un-hidden but the entity still discoverable, as FSF's DWR43
            // colonies sit between first entry and the fleet closing in. Being
            // publicly listed somewhere the player cannot see does not make it
            // known, so it paints nothing until the entity is found.
            //
            // Beside the case above rather than folded into it: that one is
            // concealed as well as unfound, so it would go on passing under a fog
            // that had quietly regained an "or it is listed" arm.
            var fsf = buildFaction("aEP_FSF", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "revealed-system",
                List.of(fsf),
                buildUnfoundColonyAwaitingApproach(fsf, 5));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .doesNotContainKey("revealed-system");
        }

        @Test
        void resolveDominantHolderBreaksWeightTieByPlanetHolder() {
            // Equal-weight footprints: the faction on a planet outranks the one
            // on a station, overriding the lower-faction-id tie-break that would
            // otherwise hand the cell to hegemony.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "tie-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5), buildPlanetMarket(tritachyon, 5));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "tie-system",
                    new DominantHolder("tritachyon", TRITACHYON_BRIGHT, buildDarkTheme(TRITACHYON_BRIGHT)));
        }

        @Test
        void resolveDominantHolderBreaksAFullTieByMarketProximity() {
            // Two size-5 station colonies tie on every weight level, so the winner falls to the
            // tie-break. hegemony's market orbits nearer the star, so it takes the system even
            // though the lowest-id fallback would hand it to blackrock - proximity, not id.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var blackrock = buildFaction("blackrock", TRITACHYON_BRIGHT);
            var hegemonyMarket = buildVisibleMarket(hegemony, 5);
            var blackrockMarket = buildVisibleMarket(blackrock, 5);
            var sector = buildSectorWith(
                "rama",
                List.of(hegemony, blackrock),
                hegemonyMarket,
                blackrockMarket);

            var star = buildStarAt(0.0f, 0.0f);

            centreSystemOn(buildOnlySystem(sector), star);

            placeMarketOnOrbit(hegemonyMarket, 100.0f, star);
            placeMarketOnOrbit(blackrockMarket, 200.0f, star);

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "rama",
                    new DominantHolder("hegemony", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderResolvesATiedSystemAlikeUnderFactionAndAllianceGrouping() {
            // The same two tied markets - hegemony's nearer the star than blackrock's - decide
            // the system, so the hegemony side wins whether hegemony stands alone (faction view)
            // or folds into an alliance (alliance view). The tie never flips between the views.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var blackrock = buildFaction("blackrock", TRITACHYON_BRIGHT);
            var hegemonyMarket = buildVisibleMarket(hegemony, 5);
            var blackrockMarket = buildVisibleMarket(blackrock, 5);
            var sector = buildSectorWith(
                "rama",
                List.of(hegemony, blackrock),
                hegemonyMarket,
                blackrockMarket);

            var star = buildStarAt(0.0f, 0.0f);

            centreSystemOn(buildOnlySystem(sector), star);

            placeMarketOnOrbit(hegemonyMarket, 100.0f, star);
            placeMarketOnOrbit(blackrockMarket, 200.0f, star);

            var grouping = new HolderGrouping(
                Map.of("hegemony", "greater_hegemony"),
                Map.of("greater_hegemony", "hegemony"),
                Map.of("greater_hegemony", "Greater Hegemony"));

            // Faction view: hegemony wins. Alliance view: the hegemony-led alliance wins the
            // same system, painted in hegemony's palette under the alliance bloc id.
            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "rama",
                    new DominantHolder("hegemony", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(
                    DominancePass.over(sector, STABILITY_WEIGHTED, BASE_FOG, grouping)))
                .containsEntry(
                    "rama",
                    new DominantHolder(
                        "greater_hegemony",
                        HEGEMONY_BRIGHT,
                        buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderIncludesStationOnceDiscovered() {
            // Once the entity is discovered (no longer discoverable), the station's
            // faction claims its system.
            var knights = buildFaction("knights_of_selkie", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "discovered-system",
                List.of(knights),
                buildMarket(knights, 5, false, false, false));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "discovered-system",
                    new DominantHolder("knights_of_selkie", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderReproducesFactionHolderMapUnderIdentityGrouping() {
            // The identity grouping makes every faction its own bloc, so the winner
            // and palette are the plain faction holder - byte-for-byte the faction view.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5), buildVisibleMarket(tritachyon, 3));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(buildPassOver(sector)))
                .containsEntry(
                    "owned-system",
                    new DominantHolder("hegemony", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderSumsAlliedColoniesPastLargerLoneRivalUnderAllianceGrouping() {
            // Two small allied colonies (weight 2 each) sum to 4 under the alliance
            // grouping, outweighing a lone rival's larger single colony (weight 3)
            // that beats either ally alone. The cell carries the alliance bloc id and
            // paints in the alliance's dominant member's (hegemony's) palette.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var persean = buildFaction("persean", NEUTRAL_BASE);
            var sector = buildSectorWith(
                "contested-system",
                List.of(hegemony, tritachyon, persean),
                buildVisibleMarket(hegemony, 2), buildVisibleMarket(tritachyon, 2),
                buildVisibleMarket(persean, 3));

            var grouping = new HolderGrouping(
                Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));

            assertThat(SectorPolitics.resolveDominantHolderBySystemId(
                    DominancePass.over(sector, STABILITY_WEIGHTED, BASE_FOG, grouping)))
                .containsEntry(
                    "contested-system",
                    new DominantHolder("alliance-1", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }
    }

    @Nested
    class ResolveDominantHolder {

        @Test
        void resolveDominantHolderNamesDominantFactionWithPalette() {

            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));

            // The single-system resolve returns the same winner and palette the bulk
            // pass would put under this system's id.
            assertThat(SectorPolitics.resolveDominantHolder(buildOnlySystem(sector), buildPassOver(sector)))
                .isEqualTo(new DominantHolder("hegemony", HEGEMONY_BRIGHT, buildDarkTheme(HEGEMONY_BRIGHT)));
        }

        @Test
        void resolveDominantHolderReturnsNullForUninhabitedSystem() {

            var sector = buildSectorWith("empty-system", List.of());

            assertThat(SectorPolitics.resolveDominantHolder(buildOnlySystem(sector), buildPassOver(sector)))
                .isNull();
        }

        @Test
        void resolveDominantHolderIgnoresConditionOnlyMarket() {
            // A bare rock's condition-only market is no colony, so the system has no
            // holder - matching the bulk pass.
            var hegemony = buildFaction("hegemony", HEGEMONY_BRIGHT);
            var sector = buildSectorWith(
                "bare-system",
                List.of(hegemony),
                buildMarket(hegemony, 6, true, false, false));

            assertThat(SectorPolitics.resolveDominantHolder(buildOnlySystem(sector), buildPassOver(sector)))
                .isNull();
        }

        @Test
        void resolveDominantHolderReturnsNullForNullSystem() {

            var sector = buildSectorWith(
                "owned-system",
                List.of(buildFaction("hegemony", HEGEMONY_BRIGHT)),
                buildVisibleMarket(buildFaction("hegemony", HEGEMONY_BRIGHT), 5));

            assertThat(SectorPolitics.resolveDominantHolder(null, buildPassOver(sector)))
                .isNull();
        }

        @Test
        void resolveDominantHolderReturnsNullForNullSector() {

            assertThat(SectorPolitics.resolveDominantHolder(mock(StarSystemAPI.class), buildPassOver(null)))
                .isNull();
        }
    }

    // A visible market sitting on a planet (getPlanetEntity() non-null), which the dominance
    // rule prefers over a station at an otherwise exact weight tie. Local to this suite: only
    // the tie-break test reads a planet market.
    private static MarketAPI buildPlanetMarket(FactionAPI faction, int size) {

        var marketMock = buildVisibleMarket(faction, size);

        when(marketMock.getPlanetEntity())
            .thenReturn(mock(PlanetAPI.class));

        return marketMock;
    }

    // A colony whose entity is still to be found, with nothing concealed about the market itself
    // (FSF's DWR43 colonies between entry and approach). Local to this suite.
    private static MarketAPI buildUnfoundColonyAwaitingApproach(FactionAPI faction, int size) {
        return buildMarket(faction, size, false, false, true);
    }

    // The five-argument market shape this suite's condition-only and discovery tests read: a
    // visible-stability colony varying only its condition-only, hidden, and undiscovered flags.
    // Delegates to the shared builder, which takes stability as its sixth argument.
    private static MarketAPI buildMarket(
            FactionAPI faction,
            int size,
            boolean isConditionOnly,
            boolean isHidden,
            boolean isUndiscovered) {

        return SectorPoliticsFixtures.buildMarket(
            faction,
            size,
            isConditionOnly,
            isHidden,
            isUndiscovered,
            FULL_STABILITY);
    }
}
