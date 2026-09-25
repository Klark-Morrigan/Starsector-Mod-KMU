package kmu.maplayers.politicalmap.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.buildEmptySector;
import static kmu.maplayers.ownermap.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link CoreTerritoryHeading}: a system under no decree yields nothing, a decreed one shows its
 * faction's crest, name, and the core status marked out in the highlight colour as one centred line, a
 * box whose body states the decree yields nothing either, and a faction the sector cannot resolve still
 * shows as its bare id.
 *
 * <p>That the line is centred rather than laid in the box's columns is the contract worth fixing, not a
 * detail of how it happens to look: a decree settles the whole system, so it is read off the title
 * rather than found among the entries below - and a line in the columns would be indented under a block
 * it does not belong to. Its crest travelling as a run of the label is the same decision seen from the
 * other side: a crest charged to the gutter could not centre with the words it belongs to.
 *
 * <p>Both reasons the heading is dropped are asserted as the same empty answer, since that is the whole
 * point of them being settled together: a caller reads "nothing heads this box" and never which of the
 * two put it there.
 */
final class CoreTerritoryHeadingTest {

    private static final String CORE_FACTION = "hegemony";
    private static final String CREST = "graphics/hegemony_crest.png";

    // A faction ID no sector resolves, which is what the fallback cases are read through.
    private static final String UNKNOWN_FACTION = "ghost_faction";

    // Whether the box's body names the decree itself, spelled out so a case reads as the state it puts
    // the box in rather than as a bare boolean at the call.
    private static final boolean BODY_STATES_THE_DECREE = true;
    private static final boolean BODY_IS_SILENT_ON_THE_DECREE = false;

    // The three runs a crested line reads as: the faction's mark, its name, then the status it holds
    // the system under. A faction with no crest opens at its name instead, one run earlier.
    private static final int CREST_RUN = 0;
    private static final int FACTION_NAME_RUN = 1;
    private static final int CORE_STATUS_RUN = 2;
    private static final int CRESTLESS_FACTION_NAME_RUN = 0;

    // The one line a decreed box heads with.
    private static final int HEADING_ROW = 0;

    @BeforeEach
    void installStringsAndColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearStringsAndColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ResolveHeadingRows {

        @Test
        void resolveHeadingRowsIsEmptyWithoutACoreFaction() {
            assertThat(resolveHeadingUnder(null, BODY_IS_SILENT_ON_THE_DECREE))
                .isEmpty();
        }

        @Test
        void resolveHeadingRowsIsEmptyForABlankCoreFaction() {
            // A memory flag written empty is no decree, so it must not draw a nameless core line.
            assertThat(resolveHeadingUnder(" ", BODY_IS_SILENT_ON_THE_DECREE))
                .isEmpty();
        }

        @Test
        void resolveHeadingRowsIsEmptyWhenTheBodyAlreadyStatesTheDecree() {
            // The decree is real here - what makes the heading wrong is the box about to say it again a
            // few lines down, which reads as two findings rather than one fact.
            assertThat(resolveHeadingUnder(CORE_FACTION, BODY_STATES_THE_DECREE))
                .isEmpty();
        }

        @Test
        void resolveHeadingRowsNamesTheCoreFaction() {

            var rows = resolveHegemonyHeading();

            assertThat(readLabelRun(rows.get(HEADING_ROW), FACTION_NAME_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
        }

        @Test
        void resolveHeadingRowsLeadsWithTheCrestAsARunOfTheLine() {
            // The crest sits inside the label rather than in the gutter the entries below align to, so
            // it centres with the words it belongs to instead of anchoring to a column.
            var rows = resolveHegemonyHeading();

            assertThat(readLabelRun(rows.get(HEADING_ROW), CREST_RUN))
                .isEqualTo(new ImageSpan(CREST));
        }

        @Test
        void resolveHeadingRowsMarksTheCoreStatusInTheHighlightColour() {
            // The status is the point of the line, so it is picked out beside the plainly-coloured
            // faction name rather than blending into it - one line read in two colours.
            var rows = resolveHegemonyHeading();

            assertThat(readLabelRun(rows.get(HEADING_ROW), CORE_STATUS_RUN))
                .isEqualTo(new TextSpan("core territory", HIGHLIGHT));
        }

        @Test
        void resolveHeadingRowsCentresTheLineRatherThanLayingItInTheColumns() {
            // A decree settles the whole system, so the line speaks for the box and is set across it -
            // which is what a centred row is, and what carrying no crest gutter and no value column
            // makes it. Asserted as the kind of row it is, since that is the whole of the decision.
            var rows = resolveHegemonyHeading();

            assertThat(rows.get(HEADING_ROW))
                .isInstanceOf(TooltipRow.CentredRow.class);
            assertThat(rows.get(HEADING_ROW).labelRuns())
                .hasSize(3);
        }

        @Test
        void resolveHeadingRowsFallsBackToTheIdForAnUnknownFaction() {

            var rows = resolveGhostFactionHeading();

            assertThat(readLabelRun(rows.get(HEADING_ROW), CRESTLESS_FACTION_NAME_RUN))
                .isEqualTo(new TextSpan("ghost_faction", TEXT));
        }

        @Test
        void resolveHeadingRowsOpensAtItsNameWhenTheFactionHasNoCrest() {
            // A faction the game gives no crest yields no path, so the line is built from its words
            // alone rather than from an image run with nothing to load.
            var rows = resolveGhostFactionHeading();

            assertThat(rows.get(HEADING_ROW).labelRuns())
                .hasSize(2);
        }
    }

    // The heading for a system decreed to a faction the sector knows, which is what the cases reading
    // its runs are all built on.
    private static List<TooltipRow> resolveHegemonyHeading() {
        return resolveHeadingUnder(CORE_FACTION, BODY_IS_SILENT_ON_THE_DECREE);
    }

    // The heading over a sector that knows the decreed faction, which every case but the unresolvable
    // one runs against. Both varying facts are taken as parameters so a case states the state it is
    // about and nothing else.
    private static List<TooltipRow> resolveHeadingUnder(
            String coreFactionId,
            boolean isDecreeStatedInBody) {

        return CoreTerritoryHeading.resolveHeadingRows(
            buildSectorKnowingHegemony(),
            coreFactionId,
            isDecreeStatedInBody);
    }

    // The heading for a decree naming a faction the sector cannot resolve at all.
    private static List<TooltipRow> resolveGhostFactionHeading() {
        return CoreTerritoryHeading.resolveHeadingRows(
            buildEmptySector(),
            UNKNOWN_FACTION,
            BODY_IS_SILENT_ON_THE_DECREE);
    }

    private static SectorAPI buildSectorKnowingHegemony() {

        var sectorMock = buildEmptySector();

        stubFaction(sectorMock, CORE_FACTION, "The Hegemony", CREST);

        return sectorMock;
    }
}
