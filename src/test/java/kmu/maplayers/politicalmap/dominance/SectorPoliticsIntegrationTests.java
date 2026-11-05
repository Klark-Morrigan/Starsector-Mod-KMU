package kmu.maplayers.politicalmap.dominance;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.dominance.weighting.KnownMarketFootprints;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.HEGEMONY_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.NEUTRAL_BASE;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.TRITACHYON_FACTION_BRIGHT;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildConditionOnlyMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildDarkTheme;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildMarketAtStability;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOrbitingEntity;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorSplitBetweenStationAndPlanet;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorWith;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildStarAt;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildUndiscoveredHiddenMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildPassOver;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the holder pipeline end to end: {@link SectorPolitics}
 * reading a stubbed economy through {@link KnownMarketFootprints} and the real
 * {@link SystemDominance}, then resolving the winner's palette. Exercises them
 * together because the value of the adapter is the wiring (footprint read,
 * dominance rule, colour lookup), which mocking either collaborator would hide.
 *
 * <p>The cases over a whole sector ask each system as a rebuild does, through
 * {@link HolderResolution#collectOver}.
 */
class SectorPoliticsIntegrationTests {

    // The stability-only weighting rule the palette-resolving suites share; the parameterless
    // entry points instead read the live LunaLib settings only the running game provides.
    private static final DominanceRules STABILITY_WEIGHTED = buildStabilityWeightedRules();

    @Nested
    class ResolveDominantHolder {

        // The holder carries the ID the renderer styles by, the bright UI colour the cell is filled
        // and outlined in, and the dark UI colour its interior seams are stroked in.
        @Test
        void namesHolderAndColourByDominantFaction() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));
            var expectedHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("owned-system"), expectedHolder);
        }

        // An independent-held system resolves to the "independent" ID, the one the renderer's
        // fill-alpha rule dims on.
        @Test
        void namesIndependentWhenItHolds() {

            // ARRANGE
            var independent = buildFaction("independent", NEUTRAL_BASE);
            var sector = buildSectorWith(
                "frontier-system",
                List.of(independent),
                buildVisibleMarket(independent, 4));
            var expectedHolder = new SystemOwner(
                "independent",
                new ColourPair(NEUTRAL_BASE, buildDarkTheme(NEUTRAL_BASE)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("frontier-system"), expectedHolder);
        }

        // Neutral is where nobody is rather than a faction with interests, so it takes no part in
        // the contest for a system - it outweighs the colony here and still loses it.
        @Test
        void namesTheFactionSharingASystemWithAHeavierNeutralMarket() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var neutral = buildFaction(Factions.NEUTRAL, NEUTRAL_BASE);
            var sector = buildSectorWith(
                "salvage-system",
                List.of(neutral, hegemony),
                buildVisibleMarket(neutral, 6),
                buildVisibleMarket(hegemony, 3));
            var expectedHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("salvage-system"), expectedHolder);
        }

        // Barred from the contest, not from the map: a bloc kept out of the ranking still holds
        // what nobody contests, so a system only neutral is present in paints exactly as before.
        @Test
        void stillNamesNeutralWhereItStandsAlone() {

            // ARRANGE
            var neutral = buildFaction(Factions.NEUTRAL, NEUTRAL_BASE);
            var sector = buildSectorWith(
                "derelict-system",
                List.of(neutral),
                buildVisibleMarket(neutral, 4));
            var expectedHolder = new SystemOwner(
                Factions.NEUTRAL,
                new ColourPair(NEUTRAL_BASE, buildDarkTheme(NEUTRAL_BASE)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("derelict-system"), expectedHolder);
        }

        // Stability scales each market's worth before dominance: hegemony's size-5 colony at
        // stability 4 weighs 2, so tritachyon's smaller but fully stable size-3 colony (weight 3)
        // takes the system.
        @Test
        void weighsMarketsByStability() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "unrest-system",
                List.of(hegemony, tritachyon),
                buildMarketAtStability(hegemony, 5, 4.0f),
                buildVisibleMarket(tritachyon, 3));
            var expectedHolder = new SystemOwner(
                "tritachyon",
                new ColourPair(TRITACHYON_FACTION_BRIGHT, buildDarkTheme(TRITACHYON_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("unrest-system"), expectedHolder);
        }

        // A bare rock's condition-only market must not make its faction the holder; the system
        // has no real colony, so it gets no holder.
        @Test
        void ignoresConditionOnlyMarkets() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "bare-system",
                List.of(hegemony),
                buildConditionOnlyMarket(hegemony, 6));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .doesNotContainKey(buildCellKey("bare-system"));
        }

        @Test
        void skipsUninhabitedSystems() {

            // ARRANGE
            var sector = buildSectorWith("empty-system", List.of());

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .isEmpty();
        }

        // A hidden market (vanilla hidden market) still marks its system as owned once its entity
        // is on the map - it is no longer disqualified.
        @Test
        void paintsSystemHoldingOnlyAHiddenMarket() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "hidden-system",
                List.of(hegemony),
                buildHiddenMarket(hegemony, 5));
            var expectedHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("hidden-system"), expectedHolder);
        }

        // The hidden hegemony market is size 5, but folds into dominance at a token size rating of
        // 1, so the openly held tritachyon size-2 market wins the combined-weight comparison and
        // claims the cell.
        @Test
        void countsHiddenMarketAsTokenSizeOne() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "mixed-system",
                List.of(hegemony, tritachyon),
                buildHiddenMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 2));
            var expectedHolder = new SystemOwner(
                "tritachyon",
                new ColourPair(TRITACHYON_FACTION_BRIGHT, buildDarkTheme(TRITACHYON_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("mixed-system"), expectedHolder);
        }

        // A faction kept out of the intel directory is NOT barred from the map; what matters is
        // the market being discovered. Its discovered, visible market claims its system like any
        // other.
        @Test
        void includesFactionHiddenFromIntelDirectory() {

            // ARRANGE
            var hiddenFaction = buildFaction("zea_dusk", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "hidden-faction-system",
                List.of(hiddenFaction),
                buildVisibleMarket(hiddenFaction, 5));
            var expectedHolder = new SystemOwner(
                "zea_dusk",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("hidden-faction-system"), expectedHolder);
        }

        // An undiscovered hidden market on a still-discoverable entity, e.g. Knights of Ludd's
        // Battlestar Libra - is absent from the map until found, so it claims no territory.
        @Test
        void excludesUndiscoveredStation() {

            // ARRANGE
            var knights = buildFaction("knights_of_selkie", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "undiscovered-system",
                List.of(knights),
                buildUndiscoveredHiddenMarket(knights, 5));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .doesNotContainKey(buildCellKey("undiscovered-system"));
        }

        // A colony surfaced ahead of its entity being physically found - the market un-hidden but
        // the entity still discoverable, as FSF's DWR43 colonies sit between first entry and the
        // fleet closing in. Being publicly listed somewhere the player cannot see does not make it
        // known, so it paints nothing until the entity is found.
        //
        // Beside the case above rather than folded into it: that one is concealed as well as
        // undiscovered, so it would go on passing under a fog that had quietly regained an "or it
        // is listed" arm.
        @Test
        void excludesAnUndiscoveredColonyHoweverPubliclyItIsListed() {

            // ARRANGE
            var fsf = buildFaction("aEP_FSF", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "revealed-system",
                List.of(fsf),
                buildUndiscoveredColonyAwaitingApproach(fsf, 5));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .doesNotContainKey(buildCellKey("revealed-system"));
        }

        // Equal-weight footprints: the faction on a planet outranks the one on a station,
        // overriding the lower-faction-ID tie-break that would otherwise hand the cell to hegemony.
        @Test
        void breaksWeightTieByPlanetHolder() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorSplitBetweenStationAndPlanet("tie-system", hegemony, tritachyon, 5);
            var expectedHolder = new SystemOwner(
                "tritachyon",
                new ColourPair(TRITACHYON_FACTION_BRIGHT, buildDarkTheme(TRITACHYON_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("tie-system"), expectedHolder);
        }

        // Two size-5 station colonies tie on every weight level, so the winner falls to the
        // tie-break. hegemony's market orbits nearer the star, so it takes the system even though
        // the lowest-ID fallback would hand it to blackrock - proximity, not ID.
        @Test
        void breaksAFullTieByMarketProximity() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var blackrock = buildFaction("blackrock", TRITACHYON_FACTION_BRIGHT);
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

            var expectedHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("rama"), expectedHolder);
        }

        // The same two tied markets - hegemony's nearer the star than blackrock's - decide the
        // system, so the hegemony side wins whether hegemony stands alone (faction view) or folds
        // into an alliance (alliance view). The tie never flips between the views.
        @Test
        void resolvesATiedSystemAlikeUnderFactionAndAllianceGrouping() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var blackrock = buildFaction("blackrock", TRITACHYON_FACTION_BRIGHT);
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

            // Faction view: hegemony wins. Alliance view: the hegemony-led alliance wins the same
            // system, painted in hegemony's palette under the alliance bloc ID.
            var expectedFactionHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));
            var expectedAllianceHolder = new SystemOwner(
                "greater_hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualFactionHolders = collectDominantHolders(buildPassOver(sector));
            var actualAllianceHolders = collectDominantHolders(
                DominancePass.createOver(sector, STABILITY_WEIGHTED, UNDER_THE_FOG, grouping));

            // ASSERT
            assertThat(actualFactionHolders)
                .containsEntry(buildCellKey("rama"), expectedFactionHolder);
            assertThat(actualAllianceHolders)
                .containsEntry(buildCellKey("rama"), expectedAllianceHolder);
        }

        // Once the entity is discovered (no longer discoverable), the station's faction claims its
        // system.
        @Test
        void includesStationOnceDiscovered() {

            // ARRANGE
            var knights = buildFaction("knights_of_selkie", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "discovered-system",
                List.of(knights),
                buildVisibleMarket(knights, 5));
            var expectedHolder = new SystemOwner(
                "knights_of_selkie",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("discovered-system"), expectedHolder);
        }

        // The identity grouping makes every faction its own bloc, so the winner and palette are
        // the plain faction holder - byte-for-byte the faction view.
        @Test
        void reproducesFactionHolderMapUnderIdentityGrouping() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));
            var expectedHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(buildPassOver(sector));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("owned-system"), expectedHolder);
        }

        // Two small allied colonies (weight 2 each) sum to 4 under the alliance grouping,
        // outweighing a lone rival's larger single colony (weight 3) that beats either ally alone.
        // The cell carries the alliance bloc ID and paints in the alliance's dominant member's
        // (hegemony's) palette.
        @Test
        void sumsAlliedColoniesPastLargerLoneRivalUnderAllianceGrouping() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var persean = buildFaction("persean", NEUTRAL_BASE);
            var sector = buildSectorWith(
                "contested-system",
                List.of(hegemony, tritachyon, persean),
                buildVisibleMarket(hegemony, 2),
                buildVisibleMarket(tritachyon, 2),
                buildVisibleMarket(persean, 3));

            var grouping = new HolderGrouping(
                Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));

            var expectedHolder = new SystemOwner(
                "alliance-1",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolders = collectDominantHolders(
                DominancePass.createOver(sector, STABILITY_WEIGHTED, UNDER_THE_FOG, grouping));

            // ASSERT
            assertThat(actualHolders)
                .containsEntry(buildCellKey("contested-system"), expectedHolder);
        }

        // The single-system resolve returns the same winner and palette a rebuild puts under this
        // system's ID.
        @Test
        void namesDominantFactionWithPalette() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var tritachyon = buildFaction("tritachyon", TRITACHYON_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "owned-system",
                List.of(hegemony, tritachyon),
                buildVisibleMarket(hegemony, 5),
                buildVisibleMarket(tritachyon, 3));
            var expectedHolder = new SystemOwner(
                "hegemony",
                new ColourPair(HEGEMONY_FACTION_BRIGHT, buildDarkTheme(HEGEMONY_FACTION_BRIGHT)));

            // ACT
            var actualHolder = SectorPolitics.resolveDominantHolder(buildOnlySystem(sector), buildPassOver(sector));

            // ASSERT
            assertThat(actualHolder)
                .isEqualTo(expectedHolder);
        }

        @Test
        void returnsNullForUninhabitedSystem() {

            // ARRANGE
            var sector = buildSectorWith("empty-system", List.of());

            // ACT
            var actualHolder = SectorPolitics.resolveDominantHolder(buildOnlySystem(sector), buildPassOver(sector));

            // ASSERT
            assertThat(actualHolder)
                .isNull();
        }

        // A bare rock's condition-only market is no colony, so the system has no holder - as a
        // rebuild leaves it.
        @Test
        void ignoresConditionOnlyMarket() {

            // ARRANGE
            var hegemony = buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT);
            var sector = buildSectorWith(
                "bare-system",
                List.of(hegemony),
                buildConditionOnlyMarket(hegemony, 6));

            // ACT
            var actualHolder = SectorPolitics.resolveDominantHolder(buildOnlySystem(sector), buildPassOver(sector));

            // ASSERT
            assertThat(actualHolder)
                .isNull();
        }

        @Test
        void returnsNullForNullSystem() {

            // ARRANGE
            var sector = buildSectorWith(
                "owned-system",
                List.of(buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT)),
                buildVisibleMarket(buildFaction("hegemony", HEGEMONY_FACTION_BRIGHT), 5));

            // ACT
            var actualHolder = SectorPolitics.resolveDominantHolder(null, buildPassOver(sector));

            // ASSERT
            assertThat(actualHolder)
                .isNull();
        }

        @Test
        void returnsNullForNullSector() {

            // ARRANGE
            var systemMock = mock(StarSystemAPI.class);
            var pass = buildPassOver(null);

            // ACT
            var actualHolder = SectorPolitics.resolveDominantHolder(systemMock, pass);

            // ASSERT
            assertThat(actualHolder)
                .isNull();
        }
    }

    // Every system's dominant holder as a rebuild collects it: the plain contest asked of each
    // system the pass walks.
    private static Map<SystemKey, SystemOwner> collectDominantHolders(DominancePass pass) {

        return HolderResolution
            .collectOver(
                pass.holding(),
                system -> ResolvedSystemOwner.createSolidlyFilledBy(
                    SectorPolitics.resolveDominantHolder(system, pass)))
            .ownerBySystemKey();
    }

    // Centres a system on a star - both its centre and its one star - so a distance-from-centre read
    // resolves that star as its reference.
    private static void centreSystemOn(StarSystemAPI system, PlanetAPI star) {

        when(system.getCenter())
            .thenReturn(star);
        when(system.getPlanets())
            .thenReturn(List.of(star));
    }

    // Re-points a market's primary entity to a body on the given orbit, so a resolve reading orbit
    // geometry places the market that far from the system centre.
    private static void placeMarketOnOrbit(MarketAPI market, float radius, SectorEntityToken focus) {

        // The orbiting entity stubs its own orbit before the market's stubbing opens, so the two do
        // not nest into an unfinished-stubbing error.
        var entityMock = buildOrbitingEntity(radius, focus);

        when(market.getPrimaryEntity())
            .thenReturn(entityMock);
    }

    // A colony whose entity is still to be found, with nothing concealed about the market itself
    // (FSF's DWR43 colonies between entry and approach). Named locally because that install's
    // shape is what this suite poses; what the market is made of is the shared fixture's.
    private static MarketAPI buildUndiscoveredColonyAwaitingApproach(FactionAPI faction, int size) {
        return SectorOwnershipFixtures.buildUndiscoveredOpenMarket(faction, size);
    }
}
