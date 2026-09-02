package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.base.tooltip.layout.CellTooltipRowReads;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.SectorScenarioFixtures.placeDerelictIn;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.buildOnlySystem;
import static kmu.maplayers.politicalmap.base.tooltip.ColonyObservationFixture.OBSERVED_AT;
import static kmu.maplayers.politicalmap.base.tooltip.ColonyObservationFixture.installObservedClock;
import static kmu.maplayers.politicalmap.base.tooltip.PoliticalMapBoxReads.readClaimSections;
import static kmu.maplayers.politicalmap.base.tooltip.PoliticalMapBoxReads.readDominanceSections;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins the whole route a colony's remark travels, from an observation the player's own visit wrote
 * into the sector's memory to the run it is drawn as: the register read back through the pass's
 * knowledge, dated by the notes, laid onto the colony's line, and spoken last in the box's quiet
 * shade.
 *
 * <p>Integration because every unit case along that route poses the step before it. The notes are
 * exercised over a handed-in register, the row resolvers over stubbed notes, and the label runs over
 * a hand-built line - so each half is pinned against a stand-in for the other, and a stamp written
 * in one form and read in another would leave all of them green while the player is told nothing.
 * The economy, the entity walk, the sighting register and the claim mechanic are all real here, only
 * the live settings reads, the campaign clock and the tooltip palette standing in.
 *
 * <p>Both hover families are read against one shared expected run, which is the invariant the shared
 * formatter exists to hold: they name the same colonies of one system off carriers neither shares
 * with the other, so a date composed at each could have one box state an age in words the other
 * never uses, over one hovered cell.
 *
 * <p>The system holds nothing anybody lives on. That is what keeps the remark due at all - a colony
 * standing among another faction's people is observed by them as the box is drawn, and a colony in
 * plain sight needs no date beside it - so a hulk nobody was ever aboard is the whole of what stands
 * here.
 */
final class ColonyObservationReadoutIntegrationTest {

    private static final String SYSTEM_ID = "kumari_kandam";

    // What the hulk is called. Its line is found by its name, so it is named for a place rather than
    // for what it is - nothing the boxes say about it can have come from the name.
    private static final String DERELICT_NAME = "Sentinel Gantries";

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point: what these cases read back is the run at the end of one colony's line.
    private static final String NO_CREST = null;

    // How long ago the visit was, as the clock reports it. Stated here rather than taken off the
    // remark below, which is the expectation it has to be checked against.
    private static final float ELAPSED_DAYS = 34.0f;

    /**
     * What the end of the remarked line reads as, in both families alike: the whole composed
     * sentence, in the quiet shade that parts a note about the box's own account from the gold the
     * box's findings are stated in.
     */
    private static final TextSpan LAST_SEEN_RUN =
        new TextSpan("last seen 34 days ago (c206.05.12)", GRAY);

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
        void remarksHowOldTheNewsOfAColonyIsOnTheDominanceBoxsOwnLineForIt() {
            // The whole route in one case: the visit wrote a stamp into sector memory, the pass read
            // it back, and the line naming the hulk runs on into how old that news is - spoken last
            // and in the quiet shade, so a reader scanning for findings passes over it.
            var sector = buildSectorTheFleetHasLongSinceLeft();

            assertThat(readRowNamed(
                    readDominanceSections(sector, buildOnlySystem(sector), PATROL_DETAILS),
                    DERELICT_NAME)
                .labelRuns())
                .endsWith(LAST_SEEN_RUN);
        }

        @Test
        void remarksHowOldTheNewsOfAColonyIsOnTheClaimBoxsOwnLineForIt() {
            // The same colony, the same age, through the other family - which reaches its lines off
            // a claim breakdown rather than a dominance weight, and must nonetheless state the run
            // above word for word and shade for shade.
            var sector = buildSectorTheFleetHasLongSinceLeft();

            assertThat(readRowNamed(
                    readClaimSections(sector, buildOnlySystem(sector), PATROL_DETAILS),
                    DERELICT_NAME)
                .labelRuns())
                .endsWith(LAST_SEEN_RUN);
        }
    }

    // The one line naming the given colony, found by the words it opens with. Read by name rather
    // than by position because the two families order their blocks differently, and what these cases
    // are about is the line for one colony rather than where either box puts it.
    private static TooltipRow readRowNamed(List<TooltipSection> sections, String colonyName) {

        for (var row : TooltipSection.readRowsInOrder(sections)) {

            if (colonyName.equals(CellTooltipRowReads.readOpeningWords(row))) {
                return row;
            }
        }
        throw new IllegalStateException("No line in the box names " + colonyName);
    }

    // One system holding a hulk and nothing else, visited once and left.
    //
    // Nobody was ever aboard it and nothing living stands beside it, so no inhabitant observes it as
    // the box is drawn. The fleet is elsewhere, an unstubbed current location already being somewhere
    // other than this system. That leaves the register as the only thing either box can date it by -
    // which is the point.
    private static SectorAPI buildSectorTheFleetHasLongSinceLeft() {

        var sector = SectorPoliticsFixtures.buildSectorWith(SYSTEM_ID);
        var system = buildOnlySystem(sector);
        var derelict = placeDerelictIn(system);

        when(derelict.getName())
            .thenReturn(DERELICT_NAME);

        // Where the hulk stands, which the recorded observation names and every later read of it is
        // matched against.
        SectorPoliticsFixtures.placeMarketsInSystem(system, derelict);

        stubFaction(sector, Factions.NEUTRAL, "Neutral", NO_CREST);

        // The clock is stood up before its span is stated: a call on a mock made inside when(...)
        // lands in the middle of an unfinished stubbing and fails the next interaction instead.
        var clockMock = installObservedClock(sector);

        when(clockMock.getElapsedDaysSince(OBSERVED_AT))
            .thenReturn(ELAPSED_DAYS);

        SectorPoliticsFixtures.markSystemAsVisitedByPlayer(sector, system);

        return sector;
    }
}
