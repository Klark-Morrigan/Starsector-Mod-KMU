package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readTableRow;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.VIEW_GROUPING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins how much of the contest the ordinary hover states, and where the rest of the answer is: a group
 * listed as who it is and nothing beneath, over a counterpart box the framework can draw in its place.
 *
 * <p>Listing a group flat is the shared default rather than an answer of this box's own, so what is
 * asserted here is that it stays the default - a group made up of nothing reads as one flat line, and
 * one carrying members reads over them indented, exactly as the resolver handed it over.
 *
 * <p>The ranking, the status line, the headings and the two blocks belong to the shape every standings
 * box shares and are pinned with it ({@link SystemStandingsTooltipTest}); the decree heading the box
 * belongs to every one of the layer's boxes and is pinned with the heading itself
 * ({@link PoliticalMapCellTooltipTest}). What the counterpart goes on to say is
 * {@link ExpandedSystemDominationTooltipTest}'s.
 */
final class SystemDominationTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String CORE_FACTION = "hegemony";

    private static final String BLOC_CREST = "graphics/rebel_pact_crest.png";
    private static final String MEMBER_CREST = "graphics/hegemony_crest.png";

    // The lines a box with no status and no decree lays out, in draw order: the heading naming who
    // holds the system, that group's own header, then its members beneath.
    private static final int GROUP_HEADER_ROW = 1;
    private static final int FIRST_MEMBER_ROW = 2;
    private static final int SECOND_MEMBER_ROW = 3;

    // The scores reach this box already worded by the resolver, so they stand in as the text they draw
    // as - what the box does with them is carry them into the value column.
    private static final String BLOC_SCORE = "1,200";
    private static final String MEMBER_SCORE = "900";
    private static final String OTHER_MEMBER_SCORE = "300";

    // The weights are forwarded to the (stood-in) ranking, so they never reach an assertion.
    private static final DominanceRules ANY_RULES = new DominanceRules(false,
        new BaseSizeWeighting(1.0, null, 1.0, 1.0),
        new StationWeighting(false, 1.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    private static final DominancePass ANY_PASS =
        new DominancePass(ANY_RULES, false, VIEW_GROUPING);

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();
    private final SystemDominationTooltip tooltip =
        new SystemDominationTooltip(claimBreakdownReaderFake);

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    @BeforeEach
    void installColoursAndTheRankingSeams() {
        CellTooltipPaletteFake.installPalette();
        StandingsTooltipSeamsFake.installSeams(ANY_PASS);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, CORE_FACTION, "The Hegemony", MEMBER_CREST);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {
        StandingsTooltipSeamsFake.clearSeams();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsDrawsAGroupMadeUpOfNothingAsOneFlatLine() {
            // The faction view's shape: a lone faction resolves to a group made up of nothing, so the
            // box lists it and nothing beneath it - and its number reads called-out like every value.
            StandingsTooltipSeamsFake.stubGroupEntries(createLoneGroupEntry());

            var rows = readBodyRows();

            assertThat(rows)
                .hasSize(2);

            var header = readTableRow(rows, GROUP_HEADER_ROW);

            assertThat(readLabelRun(header, MARK_RUN))
                .isEqualTo(new ImageSpan(BLOC_CREST));

            assertThat(readLabelRun(header, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("Rebel Pact", PLAYER_BRIGHT));

            assertThat(header.indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));

            assertThat(header.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);

            assertThat(header.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan(BLOC_SCORE, HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsDrawsAnAllianceAboveItsIndentedMembers() {
            // The alliances view's shape: the bloc is listed and its members read as belonging to it, by
            // the indent and the plainer colour rather than by any label saying so.
            StandingsTooltipSeamsFake.stubGroupEntries(createGroupEntry(
                createMemberLine(MEMBER_SCORE),
                createMemberLine(OTHER_MEMBER_SCORE)));

            var rows = readBodyRows();

            assertThat(rows)
                .hasSize(4);

            var firstMember = readTableRow(rows, FIRST_MEMBER_ROW);

            assertThat(readLabelRun(readTableRow(rows, GROUP_HEADER_ROW), MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("Rebel Pact", PLAYER_BRIGHT));

            assertThat(readLabelRun(firstMember, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", TEXT));

            assertThat(firstMember.indent())
                .isCloseTo(MEMBER_INDENT, within(TOLERANCE));

            assertThat(firstMember.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan(MEMBER_SCORE, TEXT)));

            // Ranked as the standing ranked them, so the box reads strongest first like the fills.
            assertThat(readTableRow(rows, SECOND_MEMBER_ROW).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan(OTHER_MEMBER_SCORE, TEXT)));
        }
    }

    @Nested
    class CreateFactionAccountResolver {

        @Test
        void createFactionAccountResolverHangsNothingBeneathAFaction() {
            // What keeps the glance a glance: this box answers who holds the system on the score
            // alone, so it asks for no account and every faction it lists reads as its line alone.
            // The account behind those scores is the counterpart's, an F1 away.
            assertThat(tooltip
                    .createFactionAccountResolver(sectorMock, systemMock, ANY_PASS)
                    .resolveAccountEntries(new FactionStanding(CORE_FACTION, 900)))
                .isEmpty();
        }
    }

    @Nested
    class ResolveExpandedVariant {

        @Test
        void resolveExpandedVariantOffersTheAccountBehindTheScores() {
            // The ordinary box answers who holds the system; the detail mode has a fuller answer to
            // offer, so this box opts into it by naming a counterpart rather than by branching on a
            // mode of its own.
            assertThat(tooltip.resolveExpandedVariant())
                .containsInstanceOf(ExpandedSystemDominationTooltip.class);
        }

        @Test
        void resolveExpandedVariantAnswersADecreeThroughThisBoxsOwnReader() {
            // A decree read one way on the glance and another on the detail would answer one hover two
            // ways, an F1 apart, so the counterpart is built on this box's reader rather than reaching
            // for the layer's shared one.
            var expandedVariant = (PoliticalMapCellTooltip) tooltip.resolveExpandedVariant().get();

            assertThat(expandedVariant.claimBreakdownReader)
                .isSameAs(claimBreakdownReaderFake);
        }
    }

    // The body read top to bottom as the lines a player sees, which is the shape these cases are about.
    private List<TooltipRow> readBodyRows() {
        return TooltipSection.readRowsInOrder(tooltip.buildBodySections(sectorMock, systemMock));
    }

    // One group as the resolver hands it over: a bloc carrying its crest and summed score, gathering
    // the factions in it as its peers, since a bloc and its members answer who holds the system at two
    // granularities rather than one accounting for the other. Whether a group gathers anything is the
    // resolver's decision, so a case here states it by handing over the members or none. A member
    // faction breaks down no further, so each is entered as an entry carrying nothing.
    private static CellTooltipEntry createGroupEntry(CellTooltipEntryLine... memberLines) {
        return CellTooltipEntry
            .createEntry(CellTooltipEntryLine.createLine(BLOC_CREST, "Rebel Pact", BLOC_SCORE))
            .grouping(Arrays
                .stream(memberLines)
                .map(CellTooltipEntry::createEntry)
                .toList());
    }

    // A group that breaks down no further - what a lone faction in the faction view resolves to.
    private static CellTooltipEntry createLoneGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(BLOC_CREST, "Rebel Pact", BLOC_SCORE));
    }

    // One member faction beneath a bloc, told apart from its siblings by its score alone.
    private static CellTooltipEntryLine createMemberLine(String scoreText) {
        return CellTooltipEntryLine.createLine(MEMBER_CREST, "The Hegemony", scoreText);
    }
}
