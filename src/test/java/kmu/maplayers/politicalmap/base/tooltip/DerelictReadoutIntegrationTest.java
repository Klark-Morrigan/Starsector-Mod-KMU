package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.SectorScenarioFixtures.placeDerelictIn;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildFaction;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildVisibleMarket;
import static kmu.maplayers.politicalmap.base.tooltip.PoliticalMapBoxReads.buildClaimBreakdownReaderOver;
import static kmu.maplayers.politicalmap.base.tooltip.PoliticalMapBoxReads.buildDominanceBox;
import static kmu.maplayers.politicalmap.base.tooltip.PoliticalMapBoxReads.readClaimSections;
import static kmu.maplayers.politicalmap.base.tooltip.PoliticalMapBoxReads.readDominanceSections;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins what a derelict does to a hover box, which is the one place the two projections beneath the
 * map visibly disagree about one system.
 *
 * <p>A derelict is named in the listing - somebody has seen it, and the box's business is everything
 * the
 * player may be told about - and nobody has ever lived aboard it, so the status line above that
 * listing goes on saying the system is unpopulated. Read apart, either surface looks like a bug: a
 * banner calling a system empty over a box that names somebody in it is exactly the pairing every
 * other case in this package rules out. Read together, it is the true reading of a system with one
 * wreck in it, and this suite is where the pairing is stated as intended rather than accidental.
 *
 * <p>Integration because the disagreement is a property of the two projections over one colony walk:
 * stub either and what gets asserted is the stub. The economy, the entity walk and the claim
 * mechanic are all read for real, only the live settings reads and the tooltip palette standing in.
 *
 * <p>The claim case is here for the boundary the whole work is bound by: widening what is shown
 * never widens what is scored. The derelict reaches the listing, and the claimant and every score
 * behind it are the ones the same system resolves without it.
 */
final class DerelictReadoutIntegrationTest {

    private static final String SYSTEM_ID = "galatia";

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point here: what each case reads back is which factions the box named at all.
    private static final String NO_CREST = null;

    // The colony's size. Only a listed colony is ever weighed - a derelict is unregistered, so no
    // term
    // of the arithmetic can read one - which is why the derelict's size is the scenario fixture's
    // business and not stated here.
    private static final int COLONY = 6;

    // What the derelict is called on the one case that reads a box drawing its line. Named for a
    // place
    // rather than for what it is, so the word the boxes call out cannot have come from the name.
    private static final String DERELICT_NAME = "Sentinel Gantries";

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

        @Test
        void namesADerelictOverASystemItCallsUnpopulated() {
            // The parting itself. The derelict is the system's only market, so the listing names its
            // owner and the line above it says nobody lives here - which is what a system of wrecks
            // is, rather than the contradiction the two lines look like side by side.
            var sector = buildSectorHoldingADerelict();

            assertThat(readDominationLabels(sector))
                .contains("Unpopulated", "Neutral");
        }

        @Test
        void callsADerelictAbandonedInBothHoverFamiliesAtOnce() {
            // The whole reason the word is resolved once for the two boxes. They name the same
            // colonies of one system off carriers neither shares with the other - a claim admission
            // against a dominance weight - so a word resolved at each could have one box call a
            // place a derelict and the other say nothing about it, over one hovered cell.
            //
            // Asserted through the boxes that open a faction up, since the ordinary pair hangs
            // nothing beneath one and so never draws a colony's line at all.
            var sector = buildSectorHoldingANamedDerelict();

            var system = buildOnlySystem(sector);

            assertThat(readSpokenWords(readDominanceSections(sector, system, PATROL_DETAILS)))
                .contains(DERELICT_NAME, "abandoned");
            assertThat(readSpokenWords(readClaimSections(sector, system, PATROL_DETAILS)))
                .contains(DERELICT_NAME, "abandoned");
        }

        @Test
        void namesBothADerelictAndTheColonyBesideItOverAPopulatedSystem() {
            // The same system once somebody settles it. The derelict is staged unchanged, so what
            // silenced the status line is the colony rather than anything the derelict stopped
            // being - and the listing still names them both.
            var sector = buildSectorHoldingADerelict(buildColony());

            assertThat(readDominationLabels(sector))
                .contains("The Hegemony", "Neutral")
                .doesNotContain("Unpopulated");
        }
    }

    @Nested
    class ResolveDeepestHeldLevelFor {

        @Test
        void offersTheAccountOverASystemTheBoxCallsUnpopulated() {
            // The other half of the same disagreement, and the half a stubbed suite cannot pin: the
            // status line and the standings answer different questions of one walk, so the key hint
            // has to follow the listing rather than the banner. The derelict is the system's only
            // market, so the box says "Unpopulated" and still ranks its owner - and that owner's
            // colony is the whole of what the counterpart has to open up.
            var sector = buildSectorHoldingADerelict();

            assertThat(resolveDominationDeepestHeldLevel(sector))
                .isEqualTo(PATROL_DETAILS);
        }

        @Test
        void offersNothingOverASystemNobodyStandsIn() {
            // Nothing in the system at all, so nothing ranks and there is no account for the
            // counterpart to state. The banner is the same one the case above draws, which is what
            // makes the pair worth stating: the boxes part on the listing, not on the line.
            var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID);

            assertThat(resolveDominationDeepestHeldLevel(sector))
                .isEqualTo(FACTIONS);
        }
    }

    @Nested
    class ReadBreakdown {

        @Test
        void resolvesTheSameClaimantAndScoresWhetherOrNotADerelictStandsThere() {
            // The boundary the whole work is bound by: what the map shows is widened, what the
            // mechanic scores is not. The derelict reaches the listing above, and the contest behind
            // the claim resolves exactly as it does over the colony alone.
            var withDerelict = readClaimBreakdown(buildSectorHoldingADerelict(buildColony()));
            var withoutDerelict = readClaimBreakdown(
                SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, buildColony()));

            assertThat(withDerelict.claimantFactionId())
                .isEqualTo(withoutDerelict.claimantFactionId());
            assertThat(readScore(withDerelict, "hegemony"))
                .isEqualTo(readScore(withoutDerelict, "hegemony"));
        }
    }

    // The dominance box over the sector's one system, read top to bottom as the words a player
    // sees - the status line included, since what this suite is about is the pairing of that line
    // with the listing beneath it.
    //
    // Read at the shallowest level, which is that pairing and nothing else: the colonies behind the
    // listing are a different case's subject, and drawn here they would bury the two lines these
    // cases are actually about.
    private static List<String> readDominationLabels(SectorAPI sector) {

        return readSectionOpeningWords(
            readDominanceSections(sector, buildOnlySystem(sector), FACTIONS));
    }

    // How deep the dominance box goes over the sector's one system, which is what the key at its foot
    // is offered for - read through the same box the labels above come from.
    private static HoverTooltipDetailLevel resolveDominationDeepestHeldLevel(SectorAPI sector) {

        return buildDominanceBox()
            .resolveDeepestHeldLevelFor(sector, buildOnlySystem(sector));
    }

    // The claim contest behind the system, read through the real mechanic over the pass's own walk
    // - a hand-built breakdown would assert the boundary by construction rather than testing it.
    private static SystemClaimBreakdown readClaimBreakdown(SectorAPI sector) {

        return buildClaimBreakdownReaderOver(sector)
            .readBreakdown(buildOnlySystem(sector));
    }

    // One faction's score in a breakdown, or nought where the contest weighed nothing for it.
    private static int readScore(SystemClaimBreakdown breakdown, String factionId) {

        return breakdown
            .scores()
            .stream()
            .filter(standing -> factionId.equals(standing.factionId()))
            .mapToInt(FactionClaimStanding::score)
            .findFirst()
            .orElse(0);
    }

    // A system holding a derelict on one of its own entities, beside whatever colonies the economy
    // lists there.
    private static SectorAPI buildSectorHoldingADerelict(MarketAPI... listedColonies) {

        var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, listedColonies);

        placeDerelictIn(buildOnlySystem(sector));

        stubFaction(sector, "hegemony", "The Hegemony", NO_CREST);
        stubFaction(sector, Factions.NEUTRAL, "Neutral", NO_CREST);

        return sector;
    }

    // An ordinary colony the economy lists, held by a faction the sector knows by name.
    private static MarketAPI buildColony() {
        return buildVisibleMarket(buildFaction("hegemony"), COLONY);
    }

    // The same system the cases above pose, with the derelict named. Only the boxes that open a
    // faction
    // up draw a colony's own line, and a line is drawn by its name - so the one case reading those
    // boxes needs a derelict the account can call something.
    private static SectorAPI buildSectorHoldingANamedDerelict() {

        var colony = buildColony();

        // Both lines are drawn by name, and a market mock answers none until it is told to: the
        // colony is named so its own line can be built at all, and the derelict so the case has
        // something to read the word beside.
        when(colony.getName())
            .thenReturn("Ancyra");

        var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID, colony);
        var derelict = placeDerelictIn(buildOnlySystem(sector));

        when(derelict.getName())
            .thenReturn(DERELICT_NAME);

        stubFaction(sector, "hegemony", "The Hegemony", NO_CREST);
        stubFaction(sector, Factions.NEUTRAL, "Neutral", NO_CREST);

        return sector;
    }

    // Every word the box says anywhere in it, in draw order. Read as a flat bag rather than by run
    // position because what the case asserts is that the box says a thing at all - which run of
    // which line carries it is each box's own business and is pinned by its own suite.
    private static List<String> readSpokenWords(List<TooltipSection> sections) {

        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .flatMap(row -> row.labelRuns().stream())
            .filter(TextSpan.class::isInstance)
            .map(labelRun -> ((TextSpan) labelRun).text())
            .toList();
    }

}
