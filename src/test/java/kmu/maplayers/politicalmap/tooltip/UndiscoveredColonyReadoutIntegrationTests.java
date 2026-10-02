package kmu.maplayers.politicalmap.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.ui.text.RedactedSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;

import kmu.maplayers.ownermap.owners.SectorOwnershipFixtures;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildFaction;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildOnlySystem;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildUndiscoveredOpenMarket;
import static kmu.maplayers.ownermap.owners.SectorOwnershipFixtures.buildVisibleMarket;
import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.stubFaction;
import static kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxReads.readClaimSections;
import static kmu.maplayers.politicalmap.tooltip.PoliticalMapBoxReads.readDominanceSections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Pins what the two hover families do with one colony the player has not discovered, standing among
 * colonies they have: open, listed by the economy, and on an entity still to be found.
 *
 * <p>The two boxes part over it on purpose. The claim mechanic weighs every open listed market
 * whether anybody has found it or not, so the colony moves numbers the claims box states - and the
 * claims box lists it to make those numbers add up, with its name blocked out and called out as
 * undiscovered. The dominance weighs only what the player knows of, so the colony moves nothing
 * there, and the dominance box has no row to account for and no name to withhold.
 *
 * <p>Integration because each half is pinned on its own elsewhere - the redaction over a hand-built
 * breakdown, the dominance projection over a footprint read - and those suites can each stay green
 * while the boxes drift apart: the claims box reads knowledge off a rule it opens per hover, the
 * dominance box off the pass it ranks under. The economy, the entity walk and the claim mechanic
 * are real here, only the live settings reads and the tooltip palette standing in.
 */
final class UndiscoveredColonyReadoutIntegrationTests {

    private static final String SYSTEM_ID = "galatia";

    // A faction the sector knows by name and marks with no crest, the presentation being beside the
    // point: what these cases read back is which colonies each box names.
    private static final String NO_CREST = null;

    // The known colony of the faction holding the undiscovered one. The claimant, being the larger,
    // so the undiscovered colony is weighed on its own account without carrying the claim.
    private static final String KNOWN_COLONY_NAME = "Ancyra";
    private static final int KNOWN_COLONY_SIZE = 6;

    // The undiscovered colony. Two words, so the blocked-out shape is seen to go word by word rather
    // than standing for the name as one run.
    private static final String UNDISCOVERED_COLONY_NAME = "Nomios Deep";
    private static final int UNDISCOVERED_COLONY_SIZE = 4;

    // A rival's known colony, so the undiscovered one stands among more than its own sibling.
    private static final String RIVAL_COLONY_NAME = "Eventide";
    private static final int RIVAL_COLONY_SIZE = 5;

    // Stated as a literal rather than read off the name, which is the expectation it is checked
    // against: one length per word of the undiscovered colony's name, in reading order.
    private static final List<Integer> UNDISCOVERED_NAME_SHAPE = List.of(6, 4);

    // The first word of the undiscovered colony's name. Matched as a fragment anywhere in a run, so a
    // name split across runs still counts as spelled.
    private static final String UNDISCOVERED_NAME_FRAGMENT = "Nomios";

    private static final String UNDISCOVERED_QUALIFIER = "undiscovered";

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
        void listsTheUndiscoveredColonyInTheClaimBoxUnderItsBlockedOutName() {
            // The claim counted it, so the claims box draws its row - one line, the name standing as
            // the shape of its words.
            var sector = buildSectorHoldingAnUndiscoveredColony();

            assertThat(readRedactedSpans(readClaimSections(sector, buildOnlySystem(sector), PATROL_DETAILS)))
                .extracting(RedactedSpan::wordLengths)
                .containsExactly(UNDISCOVERED_NAME_SHAPE);
        }

        @Test
        void callsTheBlockedOutColonyUndiscoveredInTheClaimBox() {
            // A blocked-out name says what is withheld but not why. The qualifier is what tells the
            // player the place is out of sight rather than censored.
            var sector = buildSectorHoldingAnUndiscoveredColony();

            assertThat(readTextRuns(readRedactedRow(
                    readClaimSections(sector, buildOnlySystem(sector), PATROL_DETAILS))))
                .contains(UNDISCOVERED_QUALIFIER);
        }

        @Test
        void neverSpellsTheUndiscoveredColonysNameInTheClaimBox() {
            // The row is drawn and the name is not: no run anywhere in the box carries it, the known
            // colonies beside it staying named.
            var sector = buildSectorHoldingAnUndiscoveredColony();

            var spokenWords = readSpokenWords(
                readClaimSections(sector, buildOnlySystem(sector), PATROL_DETAILS));

            assertThat(spokenWords)
                .contains(KNOWN_COLONY_NAME, RIVAL_COLONY_NAME)
                .noneMatch(word -> word.contains(UNDISCOVERED_NAME_FRAGMENT));
        }

        @Test
        void leavesTheUndiscoveredColonyOutOfTheDominanceBox() {
            // The dominance weighs only what the player knows of, so the colony moves nothing there
            // and has no row: neither its name, nor a blocked-out stand-in, nor the word that would
            // call one out. The known colonies are read first, so an empty box cannot pass.
            var sector = buildSectorHoldingAnUndiscoveredColony();

            var sections = readDominanceSections(sector, buildOnlySystem(sector), PATROL_DETAILS);

            assertThat(readSpokenWords(sections))
                .contains(KNOWN_COLONY_NAME, RIVAL_COLONY_NAME)
                .doesNotContain(UNDISCOVERED_QUALIFIER)
                .noneMatch(word -> word.contains(UNDISCOVERED_NAME_FRAGMENT));
            assertThat(readRedactedSpans(sections))
                .isEmpty();
        }
    }

    // One system holding two known colonies of rival factions and, beside the larger faction's, an
    // undiscovered one of the same owner - open and economy-listed, so the claim mechanic weighs it.
    //
    // Every market is named because a line is drawn by its name, and a market mock answers none until
    // told to - the undiscovered one included, its blocked-out shape being measured off that name.
    private static SectorAPI buildSectorHoldingAnUndiscoveredColony() {

        var hegemony = buildFaction("hegemony");

        var knownColony = buildVisibleMarket(hegemony, KNOWN_COLONY_SIZE);
        var undiscoveredColony = buildUndiscoveredOpenMarket(hegemony, UNDISCOVERED_COLONY_SIZE);
        var rivalColony = buildVisibleMarket(buildFaction("tritachyon"), RIVAL_COLONY_SIZE);

        nameMarket(knownColony, KNOWN_COLONY_NAME);
        nameMarket(undiscoveredColony, UNDISCOVERED_COLONY_NAME);
        nameMarket(rivalColony, RIVAL_COLONY_NAME);

        var sector = SectorOwnershipFixtures.buildSectorWith(
            SYSTEM_ID,
            knownColony,
            undiscoveredColony,
            rivalColony);

        stubFaction(sector, "hegemony", "The Hegemony", NO_CREST);
        stubFaction(sector, "tritachyon", "Tri-Tachyon", NO_CREST);

        return sector;
    }

    // Stubbed in its own frame, so a market's stubbing never opens inside another's.
    private static void nameMarket(MarketAPI marketMock, String name) {
        when(marketMock.getName())
            .thenReturn(name);
    }

    // Every blocked-out name the box draws, in draw order.
    private static List<RedactedSpan> readRedactedSpans(List<TooltipSection> sections) {

        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .flatMap(row -> row.labelRuns().stream())
            .filter(RedactedSpan.class::isInstance)
            .map(RedactedSpan.class::cast)
            .toList();
    }

    // The one line drawing a blocked-out name. Found by the stand-in rather than by position, since
    // where the box puts the line is the account's business and not what these cases are about.
    private static TooltipRow readRedactedRow(List<TooltipSection> sections) {

        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .filter(row -> row.labelRuns().stream().anyMatch(RedactedSpan.class::isInstance))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No line in the box blocks out a name"));
    }

    // The words one line says, in run order.
    private static List<String> readTextRuns(TooltipRow row) {

        return row
            .labelRuns()
            .stream()
            .filter(TextSpan.class::isInstance)
            .map(labelRun -> ((TextSpan) labelRun).text())
            .toList();
    }

    // Every word the box says anywhere in it, in draw order. Read as a flat bag because what these
    // cases assert is whether the box says a thing at all, not which run of which line carries it.
    private static List<String> readSpokenWords(List<TooltipSection> sections) {

        return TooltipSection
            .readRowsInOrder(sections)
            .stream()
            .flatMap(row -> readTextRuns(row).stream())
            .toList();
    }
}
