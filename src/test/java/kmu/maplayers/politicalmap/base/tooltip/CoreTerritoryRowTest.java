package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link CoreTerritoryRow}: a system under no decree yields nothing, a decreed one shows its
 * faction's crest, name, and the core status marked out in the highlight colour as one centred line,
 * and a faction the sector cannot resolve still shows as its bare id.
 *
 * <p>That the line is centred rather than laid in the box's columns is the contract worth fixing, not a
 * detail of how it happens to look: a decree settles the whole system, so it is read off the title
 * rather than found among the entries below - and a line in the columns would be indented under a block
 * it does not belong to. Its crest travelling as a run of the label is the same decision seen from the
 * other side: a crest charged to the gutter could not centre with the words it belongs to.
 */
final class CoreTerritoryRowTest {

    private static final String CREST = "graphics/hegemony_crest.png";

    // The three runs a crested line reads as: the faction's mark, its name, then the status it holds
    // the system under. A faction with no crest opens at its name instead, one run earlier.
    private static final int CREST_RUN = 0;
    private static final int FACTION_NAME_RUN = 1;
    private static final int CORE_STATUS_RUN = 2;
    private static final int CRESTLESS_FACTION_NAME_RUN = 0;

    @BeforeEach
    void installStringsAndColours() {
        CellTooltipPaletteFake.installPalette();
    }

    @AfterEach
    void clearStringsAndColours() {
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ResolveCoreTerritoryRow {

        @Test
        void resolveCoreTerritoryRowIsEmptyWithoutACoreFaction() {
            assertThat(CoreTerritoryRow.resolveCoreTerritoryRow(buildSectorKnowingHegemony(), null))
                .isEmpty();
        }

        @Test
        void resolveCoreTerritoryRowIsEmptyForABlankCoreFaction() {
            // A memory flag written empty is no decree, so it must not draw a nameless core line.
            assertThat(CoreTerritoryRow.resolveCoreTerritoryRow(buildSectorKnowingHegemony(), " "))
                .isEmpty();
        }

        @Test
        void resolveCoreTerritoryRowNamesTheCoreFaction() {
            var row = CoreTerritoryRow
                .resolveCoreTerritoryRow(buildSectorKnowingHegemony(), "hegemony")
                .orElseThrow();

            assertThat(readLabelRun(row, FACTION_NAME_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));
        }

        @Test
        void resolveCoreTerritoryRowLeadsWithTheCrestAsARunOfTheLine() {
            // The crest sits inside the label rather than in the gutter the entries below align to, so
            // it centres with the words it belongs to instead of anchoring to a column.
            var row = CoreTerritoryRow
                .resolveCoreTerritoryRow(buildSectorKnowingHegemony(), "hegemony")
                .orElseThrow();

            assertThat(readLabelRun(row, CREST_RUN))
                .isEqualTo(new ImageSpan(CREST));
        }

        @Test
        void resolveCoreTerritoryRowMarksTheCoreStatusInTheHighlightColour() {
            // The status is the point of the line, so it is picked out beside the plainly-coloured
            // faction name rather than blending into it - one line read in two colours.
            var row = CoreTerritoryRow
                .resolveCoreTerritoryRow(buildSectorKnowingHegemony(), "hegemony")
                .orElseThrow();

            assertThat(readLabelRun(row, CORE_STATUS_RUN))
                .isEqualTo(new TextSpan("core territory", HIGHLIGHT));
        }

        @Test
        void resolveCoreTerritoryRowCentresTheLineRatherThanLayingItInTheColumns() {
            // A decree settles the whole system, so the line speaks for the box and is set across it -
            // which is what a centred row is, and what carrying no crest gutter and no value column
            // makes it. Asserted as the kind of row it is, since that is the whole of the decision.
            var row = CoreTerritoryRow
                .resolveCoreTerritoryRow(buildSectorKnowingHegemony(), "hegemony")
                .orElseThrow();

            assertThat(row.labelRuns())
                .hasSize(3);
        }

        @Test
        void resolveCoreTerritoryRowFallsBackToTheIdForAnUnknownFaction() {

            var sectorMock = mock(SectorAPI.class);
            var row = CoreTerritoryRow
                .resolveCoreTerritoryRow(sectorMock, "ghost_faction")
                .orElseThrow();

            assertThat(readLabelRun(row, CRESTLESS_FACTION_NAME_RUN))
                .isEqualTo(new TextSpan("ghost_faction", TEXT));
        }

        @Test
        void resolveCoreTerritoryRowOpensAtItsNameWhenTheFactionHasNoCrest() {
            // A faction the game gives no crest yields no path, so the line is built from its words
            // alone rather than from an image run with nothing to load.
            var row = CoreTerritoryRow
                .resolveCoreTerritoryRow(mock(SectorAPI.class), "ghost_faction")
                .orElseThrow();

            assertThat(row.labelRuns())
                .hasSize(2);
        }
    }

    private static SectorAPI buildSectorKnowingHegemony() {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayNameLong())
            .thenReturn("The Hegemony");
        when(factionMock.getCrest())
            .thenReturn(CREST);

        var sectorMock = mock(SectorAPI.class);

        when(sectorMock.getFaction("hegemony"))
            .thenReturn(factionMock);

        return sectorMock;
    }
}
