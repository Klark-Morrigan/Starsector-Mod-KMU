package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.VanillaClaimBreakdownReader;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRowReads;
import kmu.maplayers.base.visibility.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures;
import kmu.maplayers.politicalmap.base.ribbon.RibbonSegment;
import kmu.maplayers.politicalmap.claims.ribbon.ClaimedSystemRibbonPlanner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static kmlib.starsector.colonies.ColonyVisibility.BASE_FOG;

import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildHiddenMarket;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_BRIGHT;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.HEGEMONY_DARK;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON;
import static kmu.maplayers.politicalmap.base.ribbon.RibbonPlanFixtures.TRITACHYON_BRIGHT;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the agreement the whole widening exists for: every faction the band under a cell draws a run
 * for is a faction the box over that cell names.
 *
 * <p>Neither surface alone can show it, which is why this is an integration test rather than a case
 * in either suite. The band counts the shared colony set and the box lists the contest's standings,
 * so the two answer through different reads of one system - and they parted company exactly where a
 * faction held nothing the mechanic weighed: the band drew its run while the box, listing weighed
 * standings alone, named nobody and printed {@code Claim: None} over a station the map was plainly
 * drawing in that faction's colours.
 *
 * <p>The system is wired as that very case, which is the common one across a sector rather than a
 * corner of it: a faction holding an open colony beside one holding a concealed base alone. Which
 * heading each of them lands under is the box's own suite's subject and is left alone here - neither
 * faction carries the territorial flag, so both read as present and ineligible, and what this case
 * asserts is the one thing neither suite can: that the two surfaces name one set of factions.
 *
 * <p>Driven through the real claim reader over a stubbed sector, since a fake contest would be the
 * one thing that cannot be posed here: what is on trial is whether two surfaces reading one system
 * report one set of factions, and handing both the same hand-built answer would assert it by
 * construction.
 */
final class ClaimPresenceReadoutIntegrationTest {

    private static final String SYSTEM_ID = "corvus";

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point here: what the case reads back is which factions the box named at all.
    private static final String NO_CREST = null;

    // Colony sizes. Only the open colony's is ever weighed - the concealed one is skipped before
    // scoring - so the two differ merely to read apart.
    private static final int OPEN_COLONY = 6;
    private static final int CONCEALED_BASE = 4;

    // What the design lays a colony's run and the parting after it at, restated as the literals a
    // case reads a band back in rather than taken off the rules the band was planned under.
    private static final int COLONY_RUN = 3;
    private static final int PARTING_RUN = 1;

    private MockedStatic<MapVisibilityRules> visibilityRulesMock;

    @BeforeEach
    void installColoursAndTheRevealSeam() {

        CellTooltipPaletteFake.installPalette();

        // The reveal is a live LunaLib read, unreachable from the test JVM; stood in as off, which
        // is the state the case is posed under - both colonies here are ones the player has found.
        visibilityRulesMock = Mockito.mockStatic(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);
    }

    @AfterEach
    void clearColoursAndTheRevealSeam() {

        visibilityRulesMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildBodySections {

        @Test
        void namesEveryFactionTheBandBeneathTheCellDrawsARunFor() {
            // One system, two surfaces, one reading: the band draws a run apiece for the open colony
            // and the concealed base, and the box names both the faction the mechanic weighed and
            // the one it never reached.
            var sector = buildSectorHoldingAConcealedBase();
            var system = buildOnlySystem(sector);
            var inputs = RibbonPlanFixtures.buildInputsOver(
                sector,
                HolderGrouping.identity(),
                BASE_FOG);

            // One reader for both surfaces, over the one walk of the system, exactly as a live bake
            // and the hover above it read a claim.
            var claimBreakdownReader = new VanillaClaimBreakdownReader(
                inputs.pass().colonyVisibility(),
                inputs.pass().colonies());

            var band = new ClaimedSystemRibbonPlanner(claimBreakdownReader, inputs)
                .planSystemRibbon(system);

            var sections = new SystemClaimTooltip(claimBreakdownReader)
                .buildBodySections(sector, system);

            assertThat(band.segments())
                .containsExactly(
                    new RibbonSegment(HEGEMONY_BRIGHT, COLONY_RUN),
                    new RibbonSegment(HEGEMONY_DARK, PARTING_RUN),
                    new RibbonSegment(TRITACHYON_BRIGHT, COLONY_RUN));

            assertThat(readLabelTexts(sections))
                .containsExactly(
                    "Claim:",
                    "None",
                    "Non-territorial:",
                    "The Hegemony",
                    "Tri-Tachyon");
        }
    }

    // The Anathema shape: a system one faction holds an open colony in and another is present in
    // through a concealed base alone, which the contest carries without ever scoring. The concealed
    // holder therefore takes no weighed standing, and before the widening it reached no block of the
    // box at all.
    private static SectorAPI buildSectorHoldingAConcealedBase() {

        var sector = SectorPoliticsFixtures.buildSectorWith(
            SYSTEM_ID,
            buildVisibleMarket(SectorPoliticsFixtures.buildFaction(HEGEMONY), OPEN_COLONY),
            buildHiddenMarket(SectorPoliticsFixtures.buildFaction(TRITACHYON), CONCEALED_BASE));

        // The system is walked for colonies the economy does not list, which needs its entities to
        // answer - none of them carrying a market of its own here, the case being about a listed
        // colony the mechanic skipped rather than about an unlisted one.
        when(buildOnlySystem(sector).getAllEntities())
            .thenReturn(List.of());

        stubFaction(sector, HEGEMONY, "The Hegemony", NO_CREST);
        stubFaction(sector, TRITACHYON, "Tri-Tachyon", NO_CREST);

        return sector;
    }

    // The box read top to bottom as the words a player sees, headings and entries alike - which is
    // what "the box names a faction" means to the reader this case is about.
    private static List<String> readLabelTexts(List<TooltipSection> sections) {

        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }
}
