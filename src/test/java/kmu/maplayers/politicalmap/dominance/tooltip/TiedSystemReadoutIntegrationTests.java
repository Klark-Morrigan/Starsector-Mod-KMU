package kmu.maplayers.politicalmap.dominance.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxSeamsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildHolderPassOver;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildPlanetMarket;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.dominance.DominancePassFixtures.buildStabilityWeightedRules;
import static kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxReads.readDominanceSections;
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
 * <p>The system is wired as that tie: two colonies of one size and stability, one on a planet and one
 * on a station. The planet's holder sorts after the station's by ID, so the ID order and the fill
 * name different blocs.
 */
final class TiedSystemReadoutIntegrationTests {

    private static final String SYSTEM_ID = "vanadium";

    // The station's holder sorts first by ID, which is the bloc a holder read off the ranking names.
    private static final String STATION_FACTION_ID = "hegemony";
    private static final String PLANET_FACTION_ID = "tritachyon";

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point here: what the case reads back is which faction the box named as holder.
    private static final String NO_CREST = null;

    // One size for both colonies, so they tie on every weight but the planet weight.
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
            var sector = buildSectorTiedBetweenAPlanetAndAStation();
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
    }

    // The tie: two economy-listed colonies of one size, the station's held by the bloc first by ID.
    private static SectorAPI buildSectorTiedBetweenAPlanetAndAStation() {

        var sector = SectorOwnershipFixtures.buildSectorWith(
            SYSTEM_ID,
            buildVisibleMarket(buildFaction(STATION_FACTION_ID), COLONY_SIZE),
            buildPlanetMarket(buildFaction(PLANET_FACTION_ID), COLONY_SIZE));

        stubFaction(sector, STATION_FACTION_ID, "The Hegemony", NO_CREST);
        stubFaction(sector, PLANET_FACTION_ID, "Tri-Tachyon", NO_CREST);

        return sector;
    }
}
