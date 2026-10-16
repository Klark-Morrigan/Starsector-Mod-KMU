package kmu.maplayers.politicalmap.claims.tooltip;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.StarSystems;
import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.testfixtures.starsector.StubbedGlobalLogger;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures;
import kmu.maplayers.politicalmap.tooltip.ContestWordingFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.ownermap.owners.holders.base.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.holders.base.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.starsector.SectorFactionFixtures.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins that the claims box reads the sector it was handed, all the way down to the claim mechanic
 * it opens per hover, rather than the sector the running game holds.
 *
 * <p>An integration test because the fault lived between the box and KMLib's claim reader: the
 * reader answers about whichever sector its walk was opened over, so only the real reader under the
 * real source can show the box opening that walk over the sector it was handed.
 */
final class SystemClaimTooltipIntegrationTests {

    private static final String SYSTEM_ID = "corvus";

    // The presentation is beside the point: what the case reads back is which faction is named.
    private static final String NO_CREST = null;

    private static final int COLONY_SIZE = 6;

    private MockedStatic<MapVisibilityRules> visibilityRulesMock;
    private MockedStatic<Global> globalMock;

    @BeforeEach
    void installColoursAndTheSeams() {

        CellTooltipPaletteFake.installPalette();

        // The colony rule is a live LunaLib read, unreachable from the test JVM.
        visibilityRulesMock = Mockito.mockStatic(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        // The running game holds a different sector, empty of colonies, so any read that reaches for
        // it reads the hovered system as holding nobody. The settings answer as they do without the
        // seam, since the box's wording is read through the ones the palette fake installed.
        var runningSector = SectorOwnershipFixtures.buildSectorWith(SYSTEM_ID);

        globalMock = StubbedGlobalLogger.openGlobalAnsweringLoggers();
        globalMock
            .when(Global::getSector)
            .thenReturn(runningSector);
        globalMock
            .when(Global::getSettings)
            .thenCallRealMethod();
    }

    @AfterEach
    void clearColoursAndTheSeams() {

        globalMock.close();
        visibilityRulesMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ComposeBody {

        @Test
        void listsTheFactionsOfTheHandedSectorWhileTheRunningGameHoldsAnother() {
            // Read off the running game, the contest would find no colony and list nobody, under a
            // banner the box's own walk calls populated.
            // Wired as the claims view wires it, bar the optional mod's gates: the vanilla decree
            // read, and the real source the scored contest comes through.
            var sector = buildSectorHoldingOneColony();
            var tooltip = new SystemClaimTooltip(
                StarSystems::readDecreedFactionId,
                VanillaClaimBreakdownReader::new,
                ClaimScoreRowResolver::resolveMarketRows,
                HolderGrouping::identity,
                ContestWordingFixtures.CONTESTED_WORDING);

            var sections = tooltip
                .composeBody(sector, buildOnlySystem(sector), FACTIONS)
                .blocks()
                .readSections();

            // The fixture's Hegemony carries no territorial flag, so it claims nothing and is listed
            // as present. Read off the running game it would not be listed at all.
            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "None",
                    "Non-territorial:",
                    "The Hegemony");
        }
    }

    // One system holding one open Hegemony colony, and nothing unlisted.
    private static SectorAPI buildSectorHoldingOneColony() {

        var sector = SectorOwnershipFixtures.buildSectorWith(
            SYSTEM_ID,
            buildVisibleMarket(SectorOwnershipFixtures.buildFaction(HEGEMONY), COLONY_SIZE));

        // The system is walked for colonies the economy does not list, which needs its entities to
        // answer.
        when(buildOnlySystem(sector).getAllEntities())
            .thenReturn(List.of());

        stubFaction(sector, HEGEMONY, "The Hegemony", NO_CREST);

        return sector;
    }
}
