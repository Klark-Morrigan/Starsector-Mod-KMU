package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxSeamsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHolderPassOver;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildPlanetMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildSectorSplitBetweenStationAndPlanet;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxReads.readDominanceSections;
import static kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxReads.readDominanceSectionsUnder;
import static kmu.starsector.SectorFactionFixtures.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the domination box names as holder the bloc the fill beneath it was painted for, over a
 * system two blocs tie for on combined weight.
 *
 * <p>No single suite can show it. The box ranks its groups by combined weight and then by ID, while
 * the fill breaks a tie on that weight by the heaviest market, the planet weight and proximity first.
 * The two agree everywhere but on such a tie, where a box picking its own holder off the ranking
 * would name the bloc the ID order picks while the cell is painted for the other.
 *
 * <p>Each system is wired as that tie: colonies of one size and stability, the first bloc by ID on
 * stations and the other on planets, so the ID order and the fill name different blocs. Posed once
 * per view, since the alliances view reaches the tie only after its members' footprints are summed.
 */
final class TiedSystemReadoutIntegrationTests {

    private static final String SYSTEM_ID = "vanadium";

    // The station holders sort first by ID, which is the bloc a holder read off the ranking names.
    private static final String STATION_FACTION_ID = "hegemony";
    private static final String OTHER_STATION_FACTION_ID = "sindrian_diktat";
    private static final String PLANET_FACTION_ID = "tritachyon";
    private static final String OTHER_PLANET_FACTION_ID = "persean";

    // The alliances view's blocs: the station holders' alliance sorts first by ID.
    private static final String STATION_ALLIANCE_ID = "alliance-a";
    private static final String PLANET_ALLIANCE_ID = "alliance-b";

    // The two station holders in one alliance and the two planet holders in another, each bloc
    // painting in its first member's colours.
    private static final HolderGrouping TWO_ALLIANCES = new HolderGrouping(
        Map.of(
            STATION_FACTION_ID, STATION_ALLIANCE_ID,
            OTHER_STATION_FACTION_ID, STATION_ALLIANCE_ID,
            PLANET_FACTION_ID, PLANET_ALLIANCE_ID,
            OTHER_PLANET_FACTION_ID, PLANET_ALLIANCE_ID),
        Map.of(
            STATION_ALLIANCE_ID, STATION_FACTION_ID,
            PLANET_ALLIANCE_ID, PLANET_FACTION_ID),
        Map.of(
            STATION_ALLIANCE_ID, "Station League",
            PLANET_ALLIANCE_ID, "Planet League"));

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point here: what the case reads back is which faction the box named as holder.
    private static final String NO_CREST = null;

    // One size for every colony, so the two sides tie on every weight but the planet weight.
    private static final int COLONY_SIZE = 5;

    @BeforeEach
    void installBoxSeams() {
        PoliticalMapBoxSeamsFake.installSeams();
    }

    @AfterEach
    void clearBoxSeams() {
        PoliticalMapBoxSeamsFake.clearSeams();
    }

    @Nested
    class ComposeBody {

        // One system, two surfaces: the fill is painted for the planet's holder, and the box names
        // that same bloc as holding the system, with the station's holder contesting it.
        @Test
        void namesTheBlocTheFillWasPaintedForAsHolderOfATiedSystem() {

            // ARRANGE
            var sector = buildSectorSplitBetweenStationAndPlanet(
                SYSTEM_ID,
                buildFaction(STATION_FACTION_ID),
                buildFaction(PLANET_FACTION_ID),
                COLONY_SIZE);

            stubFaction(sector, STATION_FACTION_ID, "The Hegemony", NO_CREST);
            stubFaction(sector, PLANET_FACTION_ID, "Tri-Tachyon", NO_CREST);

            var system = buildOnlySystem(sector);
            var pass = DominancePass.createOver(buildHolderPassOver(sector), buildStabilityWeightedRules());

            var expectedPaintedBlocId = PLANET_FACTION_ID;
            var expectedOpeningWords = List.of(
                "Dominated by:",
                "Tri-Tachyon",
                "Contested by:",
                "The Hegemony");

            // ACT
            var actualPaintedBlocId = pass.resolveDominantBlocId(system, pass.readBlocFootprints(system));
            var actualOpeningWords = readSectionOpeningWords(readDominanceSections(sector, system, FACTIONS));

            // ASSERT
            assertThat(actualPaintedBlocId)
                .isEqualTo(expectedPaintedBlocId);
            assertThat(actualOpeningWords)
                .isEqualTo(expectedOpeningWords);
        }

        // The same tie on the alliances view, where no single colony decides it: each alliance holds
        // two colonies of one size, so the two tie on combined weight and on their heaviest market,
        // and only the planet weight parts them. The box ranks the summed footprints the fill does,
        // and names the alliance the fill was painted for.
        @Test
        void namesTheAllianceTheFillWasPaintedForAsHolderOfATiedSystem() {

            // ARRANGE
            var sector = buildSectorTiedBetweenTwoAlliances();
            var system = buildOnlySystem(sector);
            var pass = DominancePass.createOver(sector, buildStabilityWeightedRules(), UNDER_THE_FOG, TWO_ALLIANCES);

            // Each alliance reads over its members, who tie on their own score and so run by ID.
            var expectedPaintedBlocId = PLANET_ALLIANCE_ID;
            var expectedOpeningWords = List.of(
                "Dominated by:",
                "Planet League",
                "Persean League",
                "Tri-Tachyon",
                "Contested by:",
                "Station League",
                "The Hegemony",
                "Sindrian Diktat");

            // ACT
            var actualPaintedBlocId = pass.resolveDominantBlocId(system, pass.readBlocFootprints(system));
            var actualOpeningWords = readSectionOpeningWords(
                readDominanceSectionsUnder(TWO_ALLIANCES, sector, system, FACTIONS));

            // ASSERT
            assertThat(actualPaintedBlocId)
                .isEqualTo(expectedPaintedBlocId);
            assertThat(actualOpeningWords)
                .isEqualTo(expectedOpeningWords);
        }
    }

    // The alliances tie: four economy-listed colonies of one size, the station alliance's two on
    // stations and the planet alliance's two on planets.
    private static SectorAPI buildSectorTiedBetweenTwoAlliances() {

        var stationFaction = buildFaction(STATION_FACTION_ID);
        var otherStationFaction = buildFaction(OTHER_STATION_FACTION_ID);
        var planetFaction = buildFaction(PLANET_FACTION_ID);
        var otherPlanetFaction = buildFaction(OTHER_PLANET_FACTION_ID);

        var sector = SectorOwnershipFixtures.buildSectorWith(
            SYSTEM_ID,
            List.of(stationFaction, otherStationFaction, planetFaction, otherPlanetFaction),
            buildVisibleMarket(stationFaction, COLONY_SIZE),
            buildVisibleMarket(otherStationFaction, COLONY_SIZE),
            buildPlanetMarket(planetFaction, COLONY_SIZE),
            buildPlanetMarket(otherPlanetFaction, COLONY_SIZE));

        stubFaction(sector, STATION_FACTION_ID, "The Hegemony", NO_CREST);
        stubFaction(sector, OTHER_STATION_FACTION_ID, "Sindrian Diktat", NO_CREST);
        stubFaction(sector, PLANET_FACTION_ID, "Tri-Tachyon", NO_CREST);
        stubFaction(sector, OTHER_PLANET_FACTION_ID, "Persean League", NO_CREST);

        return sector;
    }
}
