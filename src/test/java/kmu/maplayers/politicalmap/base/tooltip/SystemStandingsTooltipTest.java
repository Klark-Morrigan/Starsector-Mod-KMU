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
import kmu.maplayers.base.tooltip.HoverTooltipDetailLevel;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingSource;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readRowOpeningWords;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readTableRow;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.SYSTEM_COMPOSITION;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.buildAllianceOf;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.ANY_PASS;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.ANY_RULES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shape every box built on a hovered system's standings takes, whichever of them drew it:
 * ranked under the view that painted the fills, what the system is stated above the contest, and the
 * groups laid out under the blocks they route into.
 *
 * <p>Asserted over the shape rather than over the box built on it, because the point is not that the
 * shallow reading and the deep one agree today but that agreeing is not the box's decision to make: a
 * case here that passed at one depth and failed at another would be describing two contests over one
 * system.
 *
 * <p>Driven through a box that lists whatever it is handed, so no case depends on what a real box goes
 * on to say. What the real box does decide belongs to its own suite
 * ({@link SystemDominationTooltipTest}), and the decree heading the box to
 * {@link PoliticalMapCellTooltipTest}.
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
    class HasDeeperDetailFor {

        @Test
        void hasDeeperDetailForOffersTheAccountBehindTheScoresRanked() {
            // What the key at the foot of the box would reach. Answered for the whole cycle at once
            // because it is the one thing its levels agree on - the deeper tiers account for the very
            // scores the shallowest ranks by, so every press reaches the same account.
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());

            assertThat(tooltip.hasDeeperDetailFor(sectorMock, systemMock))
                .isTrue();
        }

        @Test
        void hasDeeperDetailForOffersTheAccountBehindAStandingOverACollapsedSystem() {
            // The status line and the standings answer different questions of the one pass: a system
            // whose colonies have all collapsed is headed Decivilised and still ranks whoever holds
            // them, and those colonies are exactly what a deeper level opens up. Judged off the
            // line instead, such a system - the only kind whose whole account is the collapse - is
            // the one where the detail is withheld.
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());
            StandingsTooltipSeamsFake.stubStatusRow("Decivilised");

            assertThat(tooltip.hasDeeperDetailFor(sectorMock, systemMock))
                .isTrue();
        }

        @Test
        void hasDeeperDetailForOffersNothingForASystemRankingNobody() {
            // Nobody ranks, so there is no score for a deeper tier to account for and every level
            // would state the same banner - a key press the player could not see the result of. The
            // hint goes with it rather than advertising one.
            assertThat(tooltip.hasDeeperDetailFor(sectorMock, systemMock))
                .isFalse();
        }

        @Test
        void hasDeeperDetailForOffersNothingWhileNoViewIsPainting() {
            // The tab is switched away, so there is no pass to judge the system under and no body being
            // drawn for the hint to sit beneath.
            StandingsTooltipSeamsFake.stubNoActiveView();

            assertThat(tooltip.hasDeeperDetailFor(sectorMock, systemMock))
                .isFalse();
        }
    }

    @Nested
    class ComposeBody {

        @Test
        void composeBodyResolvesTheFactionsWithTheAccountTheBoxAsksFor() {
            // The one thing a box adds to the shared resolution has to reach it: asked for and then
            // dropped, every box would draw the glance and the detail mode would show nothing new.
            new AccountingStandingsTooltip(claimBreakdownReaderFake)
                .composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections();

            StandingsTooltipSeamsFake.verifyGroupsResolvedWithTheBoxsAccounts(
                AccountingStandingsTooltip.FACTION_ACCOUNTS);
        }

        @Test
        void composeBodyAsksTheBoxForNoAccountAtAllWhereTheLevelAdmitsNoLineOfOne() {
            // The cut alone would draw the same box, and that is the fault: an account is everything a
            // listed faction is subordinated over, so the shallowest level draws not one of its lines
            // - while the read behind it is the most expensive thing a hover makes. Asked for and then
            // cut, the level that shows the least would cost the most.
            var accountingTooltip = new AccountingStandingsTooltip(claimBreakdownReaderFake);

            accountingTooltip.composeBody(sectorMock, systemMock, FACTIONS);

            assertThat(accountingTooltip.readRequestedLevels())
                .isEmpty();
        }

        @Test
        void composeBodyAsksTheBoxForTheAccountAtTheLevelItWillBeReadTo() {
            // The level travels to the box rather than only gating the call, so an account carrying
            // tiers of its own stops where the cut would. Handed a fixed depth instead, the box would
            // work its deepest tiers out over every level that admits any account at all.
            var accountingTooltip = new AccountingStandingsTooltip(claimBreakdownReaderFake);

            accountingTooltip.composeBody(sectorMock, systemMock, SYSTEM_COMPOSITION);
            accountingTooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS);

            assertThat(accountingTooltip.readRequestedLevels())
                .containsExactly(SYSTEM_COMPOSITION, PATROL_DETAILS);
        }

        @Test
        void composeBodyReadsItsBlocksOnlyAsDeepAsTheLevelAsksFor() {
            // The level has to reach the blocks rather than stopping at the box, which is the whole of
            // what a cut is: one listing, drawn as the group alone where the player asked who holds the
            // system and with the account beneath it where they asked what on. A body that named a
            // level of its own would draw the same thing at both and pass every other case here.
            StandingsTooltipSeamsFake.stubGroupEntries(createAccountedLeadingGroupEntry());

            assertThat(readBodyLabelTextsAt(FACTIONS))
                .containsExactly("Dominated by:", "Rebel Pact");
            assertThat(readBodyLabelTextsAt(SYSTEM_COMPOSITION))
                .containsExactly("Dominated by:", "Rebel Pact", "Chicomoztoc");
        }

        @Test
        void composeBodyShowsNothingWhenNoViewIsPainting() {
            // The tab is switched away from the political map, so there is no grouping to rank under.
            // An empty body is what stops the box being drawn at all, rather than one echoing the
            // system name the cursor already sits on.
            StandingsTooltipSeamsFake.stubNoActiveView();

            assertThat(tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections())
                .isEmpty();
        }

        @Test
        void composeBodyRanksTheSystemUnderTheActiveViewsOwnGrouping() {
            // What keeps a box honest: it ranks through the same grouping the map painted its fills by,
            // so the two can never disagree about who holds the system. The pass is built from that
            // grouping and the groups named through the pass's own, so the one the view answered with
            // is the one that reaches both.
            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections();

            StandingsTooltipSeamsFake
                .verifyPassReadUnderTheViewsGrouping();
            StandingsTooltipSeamsFake
                .verifyGroupsResolvedUnderTheViewsGrouping(sectorMock);
        }

        @Test
        void composeBodyNamesTheStrongestGroupAsHoldingTheSystemAndTheRestAsContestingIt() {
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
        void composeBodyListsAGroupStandingWithTheLeaderUnderItsOwnHeading() {
            // The block the whole axis exists for: an ally holding markets beside the leader is not
            // fighting it for the system, and filed under the contested heading the box would say
            // two allies were at war over a system they jointly hold.
            allianceSet = buildAllianceOf(LEADER_BLOC, ALLY_BLOC);

            stubThreeGroupsRanked();

            assertThat(readBodyLabelTexts())
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Allied with the system holder:",
                    "Tri-Tachyon",
                    "Contested by:",
                    "Persean League");
        }

        @Test
        void composeBodyKeepsAGroupAlliedWithARivalUnderTheContestedHeading() {
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
        void composeBodyOmitsTheAlliedHeadingWithNothingGroupingFactions() {
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
        void composeBodyListsTheLeaderOnceWhereItStandsInAnAlliance() {
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
        void composeBodyRoutesEachHoverAgainstTheAllianceSetAsItStandsThen() {
            // Why the box holds the means of sampling a grouping rather than a grouping: it lives for
            // the whole session while alliances form and dissolve inside it, so one taken at
            // construction would go on filing a group under the alliance it left an hour ago. Posed
            // as the alliance dissolving between two hovers of the one system, which is the moment a
            // held grouping would answer for a sector that had moved on.
            allianceSet = buildAllianceOf(LEADER_BLOC, ALLY_BLOC);

            stubThreeGroupsRanked();

            assertThat(readBodyLabelTexts())
                .contains("Allied with the system holder:");

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
        void composeBodyKeepsAContestingGroupsOwnCrestAndScore() {
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
        void composeBodyOmitsContestedWhenOneGroupHoldsTheSystemAlone() {
            // An uncontested system has to read as uncontested, and a heading standing over no groups
            // would read as a contest whose challengers failed to resolve.
            StandingsTooltipSeamsFake.stubGroupEntries(createLeadingGroupEntry());

            assertThat(readBodyLabelTexts())
                .containsExactly("Dominated by:", "Rebel Pact");
        }

        @Test
        void composeBodyHoldsEachHeadingWithTheGroupsItNames() {
            // Each heading is a block with its own groups, so the box parts one block from the next and
            // nothing inside a block - a heading parted from its own entries would read as belonging to
            // the block above it.
            var dominatedSection = 0;
            var contestedSection = 1;

            StandingsTooltipSeamsFake.stubGroupEntries(
                createLeadingGroupEntry(),
                createRivalGroupEntry());

            var sections = tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections();

            assertThat(sections)
                .hasSize(2);
            assertThat(readRowOpeningWords(sections.get(dominatedSection).readRowsInOrder()))
                .containsExactly("Dominated by:", "Rebel Pact");
            assertThat(readRowOpeningWords(sections.get(contestedSection).readRowsInOrder()))
                .containsExactly("Contested by:", "Persean League");
        }

        @Test
        void composeBodyDrawsHeadingsAtTheContentEdgeInGold() {
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
        void composeBodyNamesWhatTheSystemIsBeforeWhoHoldsIt() {
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
        void composeBodyFallsBackToTheSystemStatusWhenNothingRanks() {
            // A system nobody holds is not nothing: the status line says why it holds no standing, so
            // the hover reads as landing on a real but uninhabited system. It is a block of its own,
            // since what the system is answers a different question from who contests it.
            var statusRow = StandingsTooltipSeamsFake.stubStatusRow("Unpopulated");
            var sections = tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections();

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).readRowsInOrder())
                .containsExactly(statusRow);
        }

        @Test
        void composeBodyJudgesTheSystemEmptyUnderTheRankingsOwnReveal() {
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

            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections();

            StandingsTooltipSeamsFake
                .verifyStatusJudgedUnderVisibility(UNDER_THE_REVEAL);
        }

        @Test
        void composeBodyShowsNothingWhenNothingRanksAndTheSystemHasNoStatusEither() {
            // Nothing ranked and nothing to say about the system, so the body stays empty and no box is
            // drawn - the one case where a hover over a real system shows nothing at all.
            assertThat(tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections())
                .isEmpty();
        }
    }

    // The leading group as the resolver hands it over: a bloc carrying its crest and summed score.
    private static CellTooltipEntry createLeadingGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(BLOC_MARK, "Rebel Pact", BLOC_SCORE));
    }

    // The leading group with an account hanging beneath it - the shape a box that has something to say
    // about a group hands over. The one entry a cut can be read off: what it carries is subordinated,
    // so the shallowest level lists the group alone and the next one down opens it.
    private static CellTooltipEntry createAccountedLeadingGroupEntry() {
        return createLeadingGroupEntry()
            .nesting(List.of(CellTooltipEntry.createEntry(
                CellTooltipEntryLine.createLine(null, "Chicomoztoc", BLOC_SCORE))));
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

    private List<String> readBodyLabelTexts() {
        return readBodyLabelTextsAt(PATROL_DETAILS);
    }

    // The body as its lines read, at the depth asked for. Every case but the one about the cut wants
    // the whole tree, so they go through the reading above rather than each naming a level it has no
    // opinion about.
    private List<String> readBodyLabelTextsAt(HoverTooltipDetailLevel detailLevel) {
        return readRowOpeningWords(TooltipSection.readRowsInOrder(
            tooltip.composeBody(sectorMock, systemMock, detailLevel).blocks().readSections()));
    }

    private List<TooltipRow> readBodyRows() {
        return TooltipSection.readRowsInOrder(
            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections());
    }

    /**
     * A box that lists each group as the line naming it and nothing beneath - the shared shape with
     * whatever a real box goes on to say stripped out. So a case above is about the shape and never
     * about one box's answer.
     *
     * <p>It answers {@link FactionAccountResolver#NO_ACCOUNT} outright rather than inheriting it: the
     * shape leaves the account to the box, so hanging nothing is a stand-in's own answer here and not
     * a default any real box could fall back on.
     */
    private static final class ListingStandingsTooltip extends SystemStandingsTooltip {

        private ListingStandingsTooltip(
                ClaimBreakdownReader claimBreakdownReader,
                HolderGroupingSource holderGroupingSource) {

            super(claimBreakdownReader, holderGroupingSource);
        }

        @Override
        protected FactionAccountResolver createFactionAccountResolver(
                StarSystemAPI system,
                DominancePass pass,
                HoverTooltipDetailLevel detailLevel) {

            return FactionAccountResolver.NO_ACCOUNT;
        }
    }

    /**
     * A box that does have something to hang beneath the factions it lists, and that records every
     * level it was asked to build one at - the shared shape's other side, and the only way to tell a
     * box's own account reaching the resolution apart from an empty one reaching it. What the account
     * says is never read; that it is this box's, and that it was asked for at all, are the points.
     */
    private static final class AccountingStandingsTooltip extends SystemStandingsTooltip {

        // Answers something rather than nothing, so this box's account cannot be mistaken for an empty
        // one reaching the resolution by another route. What it says is never read.
        private static final FactionAccountResolver FACTION_ACCOUNTS = standing -> List.of(
            CellTooltipEntry.createEntry(
                CellTooltipEntryLine.createLine(null, standing.factionId(), "1")));

        // The levels the shape asked this box for an account at, in the order it asked. Recorded
        // rather than inferred from what the box went on to draw: an account built and then cut
        // leaves a body identical to one never asked for, which is the whole distinction the cases
        // reading this are about.
        private final List<HoverTooltipDetailLevel> requestedLevels = new ArrayList<>();

        private AccountingStandingsTooltip(ClaimBreakdownReader claimBreakdownReader) {
            super(claimBreakdownReader, HolderGrouping::identity);
        }

        @Override
        protected FactionAccountResolver createFactionAccountResolver(
                StarSystemAPI system,
                DominancePass pass,
                HoverTooltipDetailLevel detailLevel) {

            requestedLevels.add(detailLevel);
            return FACTION_ACCOUNTS;
        }

        private List<HoverTooltipDetailLevel> readRequestedLevels() {
            return List.copyOf(requestedLevels);
        }
    }
}
