package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemColoniesIndex;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.CellTooltipRowReads;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readTableRow;
import static kmu.maplayers.base.visibility.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.buildAllianceOf;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.VIEW_GROUPING;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shape every box built on a hovered system's standings takes, whichever of them drew it:
 * ranked under the view that painted the fills, what the system is stated above the contest, and the
 * groups split between whoever dominates it, whoever stands with them, and whoever else is present.
 *
 * <p>Asserted over the shape rather than over either box, because the point is not that the glance and
 * the detail agree today but that agreeing is not either box's decision to make: a case here that
 * passed for one and failed for the other would be describing two contests over one system.
 *
 * <p>Driven through a box that lists whatever it is handed, so no case depends on what either real box
 * goes on to say. What each of them does decide belongs to its own suite -
 * {@link SystemDominationTooltipTest} and {@link ExpandedSystemDominationTooltipTest} - and the decree
 * heading the box to {@link PoliticalMapCellTooltipTest}.
 */
final class SystemStandingsTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String BLOC_CREST = "graphics/rebel_pact_crest.png";
    private static final CellTooltipMark BLOC_MARK =
        CellTooltipMark.resolveMarkAsAuthored(BLOC_CREST);

    private static final String RIVAL_CREST = "graphics/persean_league_crest.png";
    private static final CellTooltipMark RIVAL_MARK =
        CellTooltipMark.resolveMarkAsAuthored(RIVAL_CREST);

    private static final String ALLY_CREST = "graphics/tritachyon_crest.png";
    private static final CellTooltipMark ALLY_MARK =
        CellTooltipMark.resolveMarkAsAuthored(ALLY_CREST);

    // The blocs the three groups are, which is all a case about routing needs of them: an alliance
    // set is read against bloc ids alone.
    private static final String LEADER_BLOC = "hegemony";
    private static final String ALLY_BLOC = "tritachyon";
    private static final String RIVAL_BLOC = "persean_league";

    // The lines a box with no status and no decree lays out, in draw order.
    private static final int DOMINATED_HEADING_ROW = 0;

    // The scores reach a box already worded by the resolver, so they stand in as the text they draw as.
    private static final String BLOC_SCORE = "1,200";
    private static final String ALLY_SCORE = "800";
    private static final String RIVAL_SCORE = "400";

    // What a stood-up group is weighed at. Forwarded to the (stood-in) naming, so it never reaches
    // an assertion - which block a group falls in is read off its bloc alone.
    private static final int ANY_SCORE = 0;

    // The weights are forwarded to the (stood-in) ranking, so they never reach an assertion.
    private static final DominanceRules ANY_RULES = new DominanceRules(false,
        new BaseSizeWeighting(1.0, null, 1.0, 1.0),
        new StationWeighting(false, 1.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    // Opened over no sector: the shared shape reads no colonies of its own, so the set behind the
    // pass is nothing any case here has an opinion about.
    private static final DominancePass ANY_PASS =
        DominancePass.over(null, ANY_RULES, BASE_FOG, VIEW_GROUPING);

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();

    // The alliance set the box routes its blocks against, restated by the cases about an ally and left
    // ungrouped for the rest - which is both the state an install with nothing grouping factions is
    // permanently in and the state every case predating the allied block was written under.
    private HolderGrouping allianceSet = HolderGrouping.identity();

    private final SystemStandingsTooltip tooltip =
        new ListingStandingsTooltip(claimBreakdownReaderFake, () -> allianceSet);

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    @BeforeEach
    void installColoursAndTheRankingSeams() {

        CellTooltipPaletteFake.installPalette();
        StandingsTooltipSeamsFake.installSeams(ANY_PASS);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {
        StandingsTooltipSeamsFake.clearSeams();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ResolveExpandedDetailName {

        @Test
        void resolveExpandedDetailNameOffersTheAccountBehindTheScoresRanked() {
            // What the key at the foot of the box offers the player, in their words. Answered for the
            // pair at once because it is the one thing they agree on - the counterpart accounts for the
            // very scores the ordinary box ranks by, so switching either way offers the same account.
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());

            assertThat(tooltip.resolveExpandedDetailName(sectorMock, systemMock))
                .contains("score contributions");
        }

        @Test
        void resolveExpandedDetailNameOffersTheAccountBehindAStandingOverACollapsedSystem() {
            // The status line and the standings answer different questions of the one pass: a system
            // whose colonies have all collapsed is headed Decivilised and still ranks whoever holds
            // them, and those colonies are exactly what the counterpart opens up. Judged off the
            // line instead, such a system - the only kind whose whole account is the collapse - is
            // the one where the detail is withheld.
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());
            StandingsTooltipSeamsFake.stubStatusRow("Decivilised");

            assertThat(tooltip.resolveExpandedDetailName(sectorMock, systemMock))
                .contains("score contributions");
        }

        @Test
        void resolveExpandedDetailNameOffersNothingForASystemRankingNobody() {
            // Nobody ranks, so there is no score for the counterpart to account for and it would state
            // the same banner the ordinary box already does - a key press the player could not see the
            // result of. The hint goes with it rather than advertising one.
            assertThat(tooltip.resolveExpandedDetailName(sectorMock, systemMock))
                .isEmpty();
        }

        @Test
        void resolveExpandedDetailNameOffersNothingWhileNoViewIsPainting() {
            // The tab is switched away, so there is no pass to judge the system under and no body being
            // drawn for the hint to sit beneath.
            StandingsTooltipSeamsFake.stubNoActiveView();

            assertThat(tooltip.resolveExpandedDetailName(sectorMock, systemMock))
                .isEmpty();
        }
    }

    @Nested
    class CreateFactionAccountResolver {

        @Test
        void createFactionAccountResolverListsAFactionAsItsLineAlone() {
            // The shared default, and what the glance box relies on: a box with nothing further to say
            // overrides nothing and every faction it lists reads as its line alone.
            assertThat(tooltip
                    .createFactionAccountResolver(systemMock, ANY_PASS)
                    .resolveAccountEntries(new WeighedFactionStanding("hegemony", 900)))
                .isEmpty();
        }
    }

    @Nested
    class BuildBodySections {

        @Test
        void buildBodySectionsResolvesTheFactionsWithTheAccountTheBoxAsksFor() {
            // The one thing a box adds to the shared resolution has to reach it: asked for and then
            // dropped, every box would draw the glance and the detail mode would show nothing new.
            new AccountingStandingsTooltip(claimBreakdownReaderFake)
                .buildBodySections(sectorMock, systemMock);

            StandingsTooltipSeamsFake.verifyGroupsResolvedWithTheBoxsAccounts(
                AccountingStandingsTooltip.FACTION_ACCOUNTS);
        }

        @Test
        void buildBodySectionsShowsNothingWhenNoViewIsPainting() {
            // The tab is switched away from the political map, so there is no grouping to rank under.
            // An empty body is what stops the box being drawn at all, rather than one echoing the
            // system name the cursor already sits on.
            StandingsTooltipSeamsFake.stubNoActiveView();

            assertThat(tooltip.buildBodySections(sectorMock, systemMock))
                .isEmpty();
        }

        @Test
        void buildBodySectionsRanksTheSystemUnderTheActiveViewsOwnGrouping() {
            // What keeps a box honest: it ranks through the same grouping the map painted its fills by,
            // so the two can never disagree about who holds the system. The pass is built from that
            // grouping and the groups named through the pass's own, so the one the view answered with
            // is the one that reaches both.
            tooltip.buildBodySections(sectorMock, systemMock);

            StandingsTooltipSeamsFake
                .verifyPassReadUnderTheViewsGrouping();
            StandingsTooltipSeamsFake
                .verifyGroupsResolvedUnderTheViewsGrouping(sectorMock);
        }

        @Test
        void buildBodySectionsNamesTheStrongestGroupAsHoldingTheSystemAndTheRestAsContestingIt() {
            // The headings are what turn a ranked list into an answer: the map fills the system in
            // the leader's colour, so the box says outright that the leader holds it and the others are
            // merely present, rather than leaving that to be read off the row order.
            StandingsTooltipSeamsFake.stubGroupEntries(
                createLeadingGroupEntry(),
                createRivalGroupEntry());

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Contested by:",
                    "Persean League");
        }

        @Test
        void buildBodySectionsListsAGroupStandingWithTheLeaderUnderItsOwnHeading() {
            // The block the whole axis exists for: an ally holding markets beside the leader is not
            // fighting it for the system, and filed under the contested heading the box would say
            // two allies were at war over a system they jointly hold.
            allianceSet = buildAllianceOf(LEADER_BLOC, ALLY_BLOC);

            stubThreeGroupsRanked();

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Allied with the dominant faction:",
                    "Tri-Tachyon",
                    "Contested by:",
                    "Persean League");
        }

        @Test
        void buildBodySectionsKeepsAGroupAlliedWithARivalUnderTheContestedHeading() {
            // Only the leader's own allies are lifted out. Two rivals standing together and not with
            // the leader are both fighting it for the system, which is the relation the box states.
            allianceSet = buildAllianceOf(RIVAL_BLOC, ALLY_BLOC);

            stubThreeGroupsRanked();

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Persean League");
        }

        @Test
        void buildBodySectionsOmitsTheAlliedHeadingWithNothingGroupingFactions() {
            // The install without the mod that supplies alliances, where no two groups ever stand
            // together: every group below the leader contests the system exactly as it did before
            // the block existed, and the heading is dropped rather than left standing over nothing.
            stubThreeGroupsRanked();

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Persean League");
        }

        @Test
        void buildBodySectionsListsTheLeaderOnceWhereItStandsInAnAlliance() {
            // A leader in an alliance is not its own ally: routed on the bloc alone it would be
            // lifted into the allied block as well and read as two holders of one system.
            allianceSet = buildAllianceOf(LEADER_BLOC, ALLY_BLOC);

            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(createGroupStanding(LEADER_BLOC)),
                List.of(createLeadingGroupEntry()));

            assertThat(readBodyLabelTexts())
                .containsExactly("Dominated by:", "Rebel Pact");
        }

        @Test
        void buildBodySectionsRoutesEachHoverAgainstTheAllianceSetAsItStandsThen() {
            // Why the box holds the means of sampling a grouping rather than a grouping: it lives for
            // the whole session while alliances form and dissolve inside it, so one taken at
            // construction would go on filing a group under the alliance it left an hour ago. Posed
            // as the alliance dissolving between two hovers of the one system, which is the moment a
            // held grouping would answer for a sector that had moved on.
            allianceSet = buildAllianceOf(LEADER_BLOC, ALLY_BLOC);

            stubThreeGroupsRanked();

            assertThat(readBodyLabelTexts())
                .contains("Allied with the dominant faction:");

            allianceSet = HolderGrouping.identity();

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Persean League");
        }

        @Test
        void buildBodySectionsKeepsAContestingGroupsOwnCrestAndScore() {
            // A contesting group is a full standing, not a footnote to the leader's: it keeps the crest
            // and the number the map ranked it by, so the player can see how close the contest is.
            var rivalHeaderRow = 3;

            StandingsTooltipSeamsFake.stubGroupEntries(
                createLeadingGroupEntry(),
                createRivalGroupEntry());

            var rivalHeader = readTableRow(readBodyRows(), rivalHeaderRow);

            assertThat(readLabelRun(rivalHeader, MARK_RUN))
                .isEqualTo(new ImageSpan(RIVAL_CREST));
            assertThat(rivalHeader.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan(RIVAL_SCORE, HIGHLIGHT)));
        }

        @Test
        void buildBodySectionsOmitsContestedWhenOneGroupHoldsTheSystemAlone() {
            // An uncontested system has to read as uncontested, and a heading standing over no groups
            // would read as a contest whose challengers failed to resolve.
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());

            assertThat(readBodyLabelTexts())
                .containsExactly("Dominated by:", "Rebel Pact");
        }

        @Test
        void buildBodySectionsHoldsEachHeadingWithTheGroupsItNames() {
            // Each heading is a block with its own groups, so the box parts one block from the next and
            // nothing inside a block - a heading parted from its own entries would read as belonging to
            // the block above it.
            var dominatedSection = 0;
            var contestedSection = 1;

            StandingsTooltipSeamsFake.stubGroupEntries(
                createLeadingGroupEntry(),
                createRivalGroupEntry());

            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(2);
            assertThat(readLabelTexts(sections.get(dominatedSection).readRowsInOrder()))
                .containsExactly("Dominated by:", "Rebel Pact");
            assertThat(readLabelTexts(sections.get(contestedSection).readRowsInOrder()))
                .containsExactly("Contested by:", "Persean League");
        }

        @Test
        void buildBodySectionsDrawsHeadingsAtTheContentEdgeInGold() {
            // What the review found here: a heading laid inside the crest gutter starts where the group
            // labels below it start and so reads as indented under nothing, and drawn in their own
            // bright it is told apart from them only by lacking a crest.
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());

            var heading = readTableRow(readBodyRows(), DOMINATED_HEADING_ROW);

            assertThat(readLabelRun(heading, LABEL_RUN))
                .isEqualTo(new TextSpan("Dominated by:", HIGHLIGHT));

            assertThat(heading.labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(heading.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(heading.labelledRow().trailingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void buildBodySectionsNamesWhatTheSystemIsBeforeWhoHoldsIt() {
            // What the system is first, then the contest over it, so the standings read as a contest
            // over a known system rather than as the whole of what the box has to say.
            StandingsTooltipSeamsFake.stubStatusRow("Decivilised");
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Decivilised",
                    "Dominated by:",
                    "Rebel Pact");
        }

        @Test
        void buildBodySectionsFallsBackToTheSystemStatusWhenNothingRanks() {
            // A system nobody holds is not nothing: the status line says why it holds no standing, so
            // the hover reads as landing on a real but uninhabited system. It is a block of its own,
            // since what the system is answers a different question from who contests it.
            var statusRow = StandingsTooltipSeamsFake.stubStatusRow("Unpopulated");
            var sections = tooltip.buildBodySections(sectorMock, systemMock);

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).readRowsInOrder())
                .containsExactly(statusRow);
        }

        @Test
        void buildBodySectionsJudgesTheSystemEmptyUnderTheRankingsOwnReveal() {
            // The status has to admit exactly the colonies the standings were ranked through: judged
            // under the narrower filter, a system revealed only by the dev knob would be called
            // unpopulated directly above the rows scoring the faction holding it.
            // Built through the constructor rather than the static entry: the seams fixture stands
            // in for this class's statics, so a pass assembled through one of them here would be
            // answered by the stand-in rather than built.
            StandingsTooltipSeamsFake.stubPass(new DominancePass(
                ANY_RULES,
                new HolderPass(
                    HolderGrouping.identity(),
                    UNDER_THE_REVEAL,
                    new SystemColoniesIndex(null))));

            tooltip.buildBodySections(sectorMock, systemMock);

            StandingsTooltipSeamsFake
                .verifyStatusJudgedUnderVisibility(UNDER_THE_REVEAL);
        }

        @Test
        void buildBodySectionsShowsNothingWhenNothingRanksAndTheSystemHasNoStatusEither() {
            // Nothing ranked and nothing to say about the system, so the body stays empty and no box is
            // drawn - the one case where a hover over a real system shows nothing at all.
            assertThat(tooltip.buildBodySections(sectorMock, systemMock))
                .isEmpty();
        }
    }

    // The leading group as the resolver hands it over: a bloc carrying its crest and summed score.
    private static CellTooltipEntry createLeadingGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(BLOC_MARK, "Rebel Pact", BLOC_SCORE));
    }

    // A second, lower-ranked group, named and scored apart from the leader so a case about which block
    // a group lands in cannot pass by reading the leader's line twice.
    private static CellTooltipEntry createRivalGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(RIVAL_MARK, "Persean League", RIVAL_SCORE));
    }

    // A third group between the two, named and scored apart from both, so a case about the block an
    // alliance routes a group into cannot pass by reading either of the other lines.
    private static CellTooltipEntry createAlliedGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(ALLY_MARK, "Tri-Tachyon", ALLY_SCORE));
    }

    // One ranked group as the box places it: the bloc it is. What it is weighed at and made up of are
    // the naming's business, which is stood in for, so which block it falls in turns on the bloc alone.
    private static GroupStanding createGroupStanding(String blocId) {
        return new GroupStanding(blocId, ANY_SCORE, List.of());
    }

    // The three groups every routing case is posed over, ranked leader first: an alliance set states
    // which of them stand together, so the ranking itself is the same in all of them.
    private static void stubThreeGroupsRanked() {
        StandingsTooltipSeamsFake.stubRankedGroups(
            List.of(
                createGroupStanding(LEADER_BLOC),
                createGroupStanding(ALLY_BLOC),
                createGroupStanding(RIVAL_BLOC)),
            List.of(
                createLeadingGroupEntry(),
                createAlliedGroupEntry(),
                createRivalGroupEntry()));
    }

    private static List<String> readLabelTexts(List<TooltipRow> rows) {
        return rows
            .stream()
            .map(CellTooltipRowReads::readOpeningWords)
            .toList();
    }

    private List<String> readBodyLabelTexts() {
        return readLabelTexts(readBodyRows());
    }

    private List<TooltipRow> readBodyRows() {
        return TooltipSection.readRowsInOrder(tooltip.buildBodySections(sectorMock, systemMock));
    }

    /**
     * A box that lists each group as the line naming it and nothing beneath - the shared shape with
     * whatever a real box goes on to say stripped out, which is exactly the shared default. So a case
     * above is about the shape and never about one box's answer, and a box that quietly stopped
     * honouring the default would fail here rather than in its own suite.
     */
    private static final class ListingStandingsTooltip extends SystemStandingsTooltip {

        private ListingStandingsTooltip(
                ClaimBreakdownReader claimBreakdownReader,
                HolderGroupingSource holderGroupingSource) {

            super(claimBreakdownReader, holderGroupingSource);
        }
    }

    /**
     * A box that does have something to hang beneath the factions it lists - the shared shape's other
     * side, and the only way to tell a box's own account reaching the resolution apart from the
     * default reaching it. What the account says is never read; that it is this box's is the point.
     */
    private static final class AccountingStandingsTooltip extends SystemStandingsTooltip {

        // Answers something rather than nothing, so this box's account cannot be mistaken for the
        // default reaching the resolution by another route. What it says is never read.
        private static final FactionAccountResolver FACTION_ACCOUNTS = standing -> List.of(
            CellTooltipEntry.createEntry(
                CellTooltipEntryLine.createLine(null, standing.factionId(), "1")));

        private AccountingStandingsTooltip(ClaimBreakdownReader claimBreakdownReader) {
            super(claimBreakdownReader, HolderGrouping::identity);
        }

        @Override
        protected FactionAccountResolver createFactionAccountResolver(
                StarSystemAPI system,
                DominancePass pass) {

            return FACTION_ACCOUNTS;
        }
    }
}
