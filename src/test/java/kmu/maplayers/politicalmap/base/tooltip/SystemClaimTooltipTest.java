package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.systems.claims.ClaimBreakdownReader;
import kmlib.starsector.systems.claims.ContestAdmission;
import kmlib.starsector.systems.claims.FactionClaimStanding;
import kmlib.starsector.systems.claims.MarketClaimBreakdown;
import kmlib.starsector.systems.claims.PresenceOnlyClaimStanding;
import kmlib.starsector.systems.claims.SystemClaimBreakdown;
import kmlib.starsector.systems.claims.WeighedClaimStanding;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipLabelPlacement;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;
import kmlib.testfixtures.starsector.systems.claims.ClaimMarketFixture;

import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.tooltip.content.CellTooltipEntry;
import kmu.maplayers.base.tooltip.content.CellTooltipQualifier;
import kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel;
import kmu.maplayers.base.tooltip.layout.CellTooltipRowReads;
import kmu.maplayers.base.tooltip.layout.CellTooltipRows;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;
import kmu.maplayers.base.visibility.colonies.ColonyVisibility;
import kmu.maplayers.base.visibility.systems.MapVisibilityRules;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildPresenceOnlyStanding;
import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildStandingOnOneMarket;
import static kmlib.testfixtures.starsector.systems.claims.ClaimStandingFixture.buildUnknownPresenceOnlyStanding;

import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.GRAY;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.content.CellTooltipEntryReads.NO_NAME_STATED;
import static kmu.maplayers.base.tooltip.content.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.FACTIONS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.MARKET_STATS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.tooltip.detail.HoverTooltipDetailLevel.SYSTEM_COMPOSITION;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.LABEL_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MARKED_QUALIFIER_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.QUALIFIER_RUN;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readLabelTextRun;
import static kmu.maplayers.base.tooltip.layout.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.buildAllianceOf;
import static kmu.maplayers.politicalmap.base.tooltip.ListedClaimContestFixture.buildUnroutedContest;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubDispositionToward;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the shape a claim contest is read in: the claim is always stated, the claim holder's allies and
 * the factions merely on good terms with it are taken out of the contest into two blocks of their own,
 * the rivals who could have taken the system and the ones who never could are told apart into two
 * more, and a system held by decree says so on the claim line without losing the market standings
 * behind it.
 *
 * <p>Beneath every faction it names hangs that faction's account: the colonies its own standing was
 * read from, each opened into the terms its score was summed from. That is the one thing the line
 * above cannot say, since a standing is one colony's score and the faction may hold several - and it
 * is the whole of what this box adds to the shape it is built on. Where those colonies then hang, and
 * which of them leads, is the resolver's ({@link ClaimScoreRowResolverTest}).
 *
 * <p>The shape cases are posed at the shallowest level, which is the tree the box states before any
 * account is admitted; the account cases read the deepest. That the one composition reads at both
 * depths is asserted here once, over the cut the shared shape applies.
 *
 * <p>The banner heading a decreed box is the layer's rather than this box's, so it is pinned with the
 * heading itself ({@link PoliticalMapCellTooltipTest}); the marker asserted here is what the banner
 * does not answer - why this line outranks the higher-scoring one beneath it.
 *
 * <p>The breakdown itself is stood in for through the reader seam - it has its own suite in KMLib -
 * so what is left is the part this class alone decides: which lines are emitted, under which heading,
 * in what order, grouped into which blocks, and accounted for by which colonies.
 */
final class SystemClaimTooltipTest {

    private static final String SYSTEM_ID = "askonia";

    private static final String HEGEMONY = "hegemony";
    private static final String TRITACHYON = "tritachyon";
    private static final String PIRATES = "pirates";

    // A fifth faction, stubbed only by the case that needs all five blocks filled at once - every
    // other case here poses two of them and the three above cover it.
    private static final String LUDDIC_CHURCH = "luddic_church";

    private static final String HEGEMONY_CREST = "graphics/hegemony_crest.png";

    // The two lines a box over a populated system opens with, whatever the contest below them holds, by
    // their place in the flat run the box draws.
    private static final int CLAIM_HEADING_ROW = 0;
    private static final int CLAIM_ROW = 1;

    // Where the claim lands in a box whose system holds nobody: one line later, under the banner saying
    // so, which such a box always opens with.
    private static final int DECREED_CLAIM_ROW = 2;

    // The blocks a box with no status line holds, in draw order.
    private static final int CLAIM_SECTION = 0;
    private static final int SECOND_SECTION = 1;

    // What a block naming one faction comes to: its heading and the one line beneath it.
    private static final int HEADED_ONE_ENTRY_ROW_COUNT = 2;

    // Scores stand for market standings only, so any weights serve; four figures on the top one, so a
    // dropped thousands separator fails the assertion rather than passing unnoticed.
    private static final int TOP_SCORE = 1200;
    private static final int RIVAL_SCORE = 8;
    private static final int OUTSIDER_SCORE = 3;

    // Whether a faction may claim a system at all, which is what tells the two rival blocks apart. The
    // inner of the box's two axes: it divides whatever the relation to the claim holder has not
    // already placed, and inside the allied block it is said on the line instead of by a heading.
    private static final boolean IS_TERRITORIAL = true;
    private static final boolean IS_NON_TERRITORIAL = false;

    // Whether the player knows of the colony a standing rests on - the flag the box's projection
    // reads. Independent of what the mechanic made of that colony: a market held in the open is
    // weighed whether or not anybody has reached it, so a scored standing can rest on an
    // undiscovered colony, which is the shape posed here.
    private static final boolean IS_UNDISCOVERED_BY_PLAYER = false;

    // The size a market the account cases pose is built at. Those cases state a colony by its name
    // rather than by its number, so any size serves - the smaller one marking a market that is not
    // its faction's strongest.
    private static final int LESSER_MARKET_SIZE = 3;

    // A colony holding the system for a faction that holds no other there, which is what every
    // standing the account cases pose is built from - none of them is about the sibling term.
    private static final int NO_SIBLING_MARKETS = 0;

    // Where a market falls in the system's economy listing. No account case is about a tie, so every
    // market posed takes the head of the listing bar the second colony of the one faction holding two
    // the contest never weighed, whose account reads in listing order for want of any score to rank
    // by.
    private static final int FIRST_LISTED = 1;
    private static final int SECOND_LISTED = 2;

    // A system the contest itself settled - no decree over it - which is the state an account is
    // ordinarily resolved under and the one in which the strongest market is called out.
    private static final SystemClaimBreakdown CONTESTED_SYSTEM =
        new SystemClaimBreakdown(null, HEGEMONY, List.of());

    // That system as the box reads it: the scored contest paired with the colony rule its listing
    // was projected under. An account is handed the pair rather than the scored read alone, so a
    // case states the rule its account is resolved under here rather than through a settings seam -
    // which is the point of the pairing, an account reading the rule for itself being free to
    // withhold what the listing above it named.
    private static final ListedClaimContest CONTESTED_CONTEST =
        buildUnroutedContest(CONTESTED_SYSTEM, ColonyVisibility.BASE_FOG);

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();

    // The alliance set the box routes its blocks against, restated by the cases about an ally and left
    // ungrouped for the rest - which is both the state an install with nothing grouping factions is
    // permanently in and the state every case predating the allied block was written under.
    private HolderGrouping holderGrouping = HolderGrouping.identity();

    private final SystemClaimTooltip tooltip =
        new SystemClaimTooltip(claimBreakdownReaderFake, () -> holderGrouping);

    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    private MockedStatic<SystemStatusRow> statusRowMock;
    private MockedStatic<MapVisibilityRules> visibilityRulesMock;

    @BeforeEach
    void installColoursAndTheSystemStatusSeam() {
        CellTooltipPaletteFake.installPalette();

        // The status line has its own suite and would otherwise demand a live economy here, so it is
        // stood in as absent - a populated system - for every case but the one about it.
        statusRowMock = Mockito.mockStatic(SystemStatusRow.class);
        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any()))
            .thenReturn(Optional.empty());

        // The reveal is a live LunaLib read, unreachable from the test JVM; stood in as off, the
        // state every case but the reveal's own is posed under.
        visibilityRulesMock = Mockito.mockStatic(MapVisibilityRules.class);
        visibilityRulesMock
            .when(MapVisibilityRules::readFromLunaSettings)
            .thenReturn(MapVisibilityRules.BASE);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, HEGEMONY, "The Hegemony", HEGEMONY_CREST);
        stubFaction(sectorMock, TRITACHYON, "Tri-Tachyon", null);
        stubFaction(sectorMock, PIRATES, "Pirates", null);
        stubFaction(sectorMock, Factions.NEUTRAL, "Neutral", null);
    }

    @AfterEach
    void clearColoursAndTheSystemStatusSeam() {
        visibilityRulesMock.close();
        statusRowMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    @Nested
    class ComposeBody {

        @Test
        void composeBodyNamesTheClaimantWithItsCrestAndScoreUnderTheClaimHeading() {

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readLabelText(sections, CLAIM_HEADING_ROW))
                .isEqualTo("Claim:");

            var claimRow = readTableRow(sections, CLAIM_ROW);

            assertThat(readLabelRun(claimRow, MARK_RUN))
                .isEqualTo(new ImageSpan(HEGEMONY_CREST));
            assertThat(readLabelRun(claimRow, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(claimRow.labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void composeBodySortsTheRivalsIntoContestedAndNonTerritorialBlocks() {
            // The two kinds of presence answer different questions - who nearly took the system, and
            // who is merely there - so they are told apart by the heading they sit under rather than
            // by a note on a line.
            //
            // Posed with nothing grouping the factions, which is what an install without the mod that
            // supplies alliances is permanently in: the allied block is then empty for every system
            // and the box reads as these two blocks alone, exactly as it did before it had a third.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Non-territorial:",
                    "Pirates");
        }

        @Test
        void composeBodyListsATerritorialFactionTheContestNeverWeighedUnderContestedAtNought() {
            // A presence-only standing is a faction the mechanic reached nothing of - a concealed
            // base, or a station the economy does not list. Territorial, it is in the running by the
            // mechanic's own gate and scored nothing here, which is what the contested heading plus a
            // nought says exactly. The nought reads quiet: it is the contest's statement about a
            // faction it never weighed, not a figure that faction competed with and lost on.
            var presenceRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly("Claim:", "The Hegemony", "Contested by:", "Tri-Tachyon");
            assertThat(readTableRow(sections, presenceRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("0", GRAY)));
        }

        @Test
        void composeBodyListsANonTerritorialFactionTheContestNeverWeighedUnderNonTerritorial() {
            // The other half of the same routing: a faction barred from claiming is barred whether or
            // not the mechanic weighed anything for it, and that block is where the box says so.
            var presenceRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildPresenceOnlyStanding(PIRATES, IS_NON_TERRITORIAL))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly("Claim:", "The Hegemony", "Non-territorial:", "Pirates");
            assertThat(readTableRow(sections, presenceRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("0", GRAY)));
        }

        @Test
        void composeBodySortsBothKindsOfStandingByEligibilityRatherThanByKind() {
            // The block says how a faction stands to the claim, not what kind of record the contest
            // gave it - so two factions sharing an eligibility share a heading however differently
            // they were reached. Sorting by kind instead would file a pirate base's owner beside a
            // Remnant station's, which are ineligible and eligible respectively.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_NON_TERRITORIAL),
                    buildPresenceOnlyStanding(PIRATES, IS_NON_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Non-territorial:",
                    "Tri-Tachyon",
                    "Pirates");
        }

        @Test
        void composeBodyRoutesTheClaimHoldersAllyOutOfTheContestedBlock() {
            // The whole point of the third block. Vanilla scores each faction alone and knows nothing
            // of an alliance, so an ally competes for the system and loses it - left under
            // `Contested by:`, the box would show a faction fighting its own ally for a system the two
            // of them jointly hold. The score it lost by is untouched: the heading was the error, not
            // the number. An unallied faction in the same contest stays where it was, since it really
            // is contesting the claim.
            var alliedEntryRow = 3;

            holderGrouping = buildAllianceOf(HEGEMONY, TRITACHYON);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, IS_TERRITORIAL))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Allied with the claim holder:",
                    "Tri-Tachyon",
                    "Contested by:",
                    "Pirates");
            assertThat(readTableRow(sections, alliedEntryRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("8", HIGHLIGHT)));
        }

        @Test
        void composeBodyRoutesEachHoverAgainstTheAllianceSetAsItStandsThen() {
            // Why the box holds the means of sampling a grouping rather than a grouping: it lives for
            // the whole session while alliances form and dissolve inside it, so one taken at
            // construction would go on filing a faction under the alliance it left an hour ago. Posed
            // as the alliance dissolving between two hovers of the one system, which is the moment a
            // held grouping would answer for a sector that had moved on.
            holderGrouping = buildAllianceOf(HEGEMONY, TRITACHYON);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Allied with the claim holder:",
                    "Tri-Tachyon");

            holderGrouping = HolderGrouping.identity();

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon");
        }

        @Test
        void composeBodyQualifiesAnAllyThatCouldNeverHaveTakenTheSystem() {
            // The relation places a faction before its eligibility does, so an ineligible ally sits in
            // the allied block beside one that nearly took the system. That heading names neither
            // kind, so the line is where this one says which it is.
            var alliedEntryRow = 3;

            holderGrouping = buildAllianceOf(HEGEMONY, PIRATES);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, IS_NON_TERRITORIAL))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Allied with the claim holder:",
                    "Pirates");
            assertThat(readLabelRun(readTableRow(sections, alliedEntryRow), QUALIFIER_RUN))
                .isEqualTo(new TextSpan("non-territorial", HIGHLIGHT));
        }

        @Test
        void composeBodyLeavesTheQualifierOffTheBlockWhoseHeadingAlreadyStatesIt() {
            // The same faction unallied falls to `Non-territorial:`, whose heading is that very fact -
            // so its line is its name and nothing after it, one thing met once in a hover rather than
            // twice in the space of two rows.
            var nonTerritorialEntryRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, IS_NON_TERRITORIAL))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly("Claim:", "The Hegemony", "Non-territorial:", "Pirates");
            assertThat(readTableRow(sections, nonTerritorialEntryRow).labelRuns())
                .containsExactly(new TextSpan("Pirates", PLAYER_BRIGHT));
        }

        @Test
        void composeBodyRoutesAFactionOnGoodTermsWithTheClaimHolderOutOfTheContestedBlock() {
            // The fault the fourth block fixes, a step down the scale from the third: a faction the
            // sector puts on good terms with the claim holder has no quarrel with it over the system,
            // and `Contested by:` says it has. The score it lost by is untouched - the heading was the
            // error, not the number.
            var friendlyEntryRow = 3;

            stubDispositionToward(sectorMock, TRITACHYON, HEGEMONY, RepLevel.FAVORABLE);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Friendly with the claim holder:",
                    "Tri-Tachyon");
            assertThat(readTableRow(sections, friendlyEntryRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("8", HIGHLIGHT)));
        }

        @Test
        void composeBodyLeavesAFactionIndifferentTowardTheClaimHolderContesting() {
            // Where the block stops. `NEUTRAL` is the base game's own word for indifference and the
            // last level it declines to call goodwill, so a faction sitting exactly on it is a rival
            // like any other - which is what makes the threshold one the player can read off a
            // faction screen rather than a cut this box invented.
            stubDispositionToward(sectorMock, TRITACHYON, HEGEMONY, RepLevel.NEUTRAL);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon");
        }

        @Test
        void composeBodyKeepsTheClaimHoldersAllyAlliedHoweverWarmlyItIsDisposed() {
            // Alliance is the outer axis and disposition never re-sorts what it took, so an ally on
            // excellent terms with the holder gains nothing by it - the two blocks would otherwise
            // both be true of one faction, and which of them it drew in would be down to the order
            // they happen to be tested in.
            holderGrouping = buildAllianceOf(HEGEMONY, TRITACHYON);

            stubDispositionToward(sectorMock, TRITACHYON, HEGEMONY, RepLevel.FAVORABLE);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Allied with the claim holder:",
                    "Tri-Tachyon");
        }

        @Test
        void composeBodyQualifiesAFriendlyFactionThatCouldNeverHaveTakenTheSystem() {
            // The friendly heading names an eligibility no more than the allied one does, so the rule
            // that puts the word on an ineligible ally's line puts it on an ineligible friend's -
            // stated once over the pair rather than per block.
            var friendlyEntryRow = 3;

            stubDispositionToward(sectorMock, PIRATES, HEGEMONY, RepLevel.FAVORABLE);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, IS_NON_TERRITORIAL))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Friendly with the claim holder:",
                    "Pirates");
            assertThat(readLabelRun(readTableRow(sections, friendlyEntryRow), QUALIFIER_RUN))
                .isEqualTo(new TextSpan("non-territorial", HIGHLIGHT));
        }

        @Test
        void composeBodyLaysTheFiveBlocksDownFromTheClaimOutward() {
            // The whole chain in one system, in the order a reader meets it: who holds the place, who
            // stands with it by alliance, who stands with it in disposition, who stands against it,
            // and who could never have taken it. Every other case here poses one block at a time, so
            // a routing that drew the friendly block after the contested one - or filed a faction two
            // blocks from where it belongs - would pass all of them and fail this.
            holderGrouping = buildAllianceOf(HEGEMONY, TRITACHYON);

            stubFaction(sectorMock, LUDDIC_CHURCH, "The Luddic Church", null);
            stubDispositionToward(sectorMock, LUDDIC_CHURCH, HEGEMONY, RepLevel.FAVORABLE);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(LUDDIC_CHURCH, RIVAL_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(
                        Factions.NEUTRAL,
                        OUTSIDER_SCORE,
                        IS_NON_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Allied with the claim holder:",
                    "Tri-Tachyon",
                    "Friendly with the claim holder:",
                    "The Luddic Church",
                    "Contested by:",
                    "Pirates",
                    "Non-territorial:",
                    "Neutral");
        }

        @Test
        void composeBodyLeavesThePlaceholderOwnerUnderNonTerritorial() {
            // Why this box needs no block of its own for the placeholder owner every abandoned station
            // is handed to, where the standings box grew one. The claim mechanic never admits it, so
            // it arrives ineligible and the heading that says so is already the true statement about
            // it - and it is indifferent toward everyone, so the friendly block does not take it
            // either.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(
                        Factions.NEUTRAL,
                        OUTSIDER_SCORE,
                        IS_NON_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Non-territorial:",
                    "Neutral");
        }

        @Test
        void composeBodyCallsEveryTerritorialFactionARivalWhereNobodyHoldsTheSystem() {
            // There is nobody to be allied or friendly with, so neither block whose heading names a
            // holder draws over a system without one - even where the two factions present are allied
            // to each other and on excellent terms besides, which is the pair either comparison would
            // otherwise have matched on.
            holderGrouping = buildAllianceOf(HEGEMONY, TRITACHYON);

            stubDispositionToward(sectorMock, HEGEMONY, TRITACHYON, RepLevel.FAVORABLE);
            stubDispositionToward(sectorMock, TRITACHYON, HEGEMONY, RepLevel.FAVORABLE);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "None",
                    "Contested by:",
                    "The Hegemony",
                    "Tri-Tachyon");
        }

        @Test
        void composeBodyRoutesADecreedHoldersAllyIntoTheAlliedBlock() {
            // A decree settles who the holder is and nothing about how the blocks are routed, so the
            // decreed faction's ally sorts exactly as a winner's would - and the faction that would
            // have claimed by score is neither, so it goes on contesting.
            holderGrouping = buildAllianceOf(PIRATES, TRITACHYON);

            stubBreakdown(new SystemClaimBreakdown(
                PIRATES,
                PIRATES,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "Pirates",
                    "Allied with the claim holder:",
                    "Tri-Tachyon",
                    "Contested by:",
                    "The Hegemony");
        }

        @Test
        void composeBodyLeavesOutAFactionThePlayerHasFoundNoColonyOf() {
            // The known projection over the listing: a faction present only through colonies nobody
            // has found is named nowhere, since naming it would tell the player exactly what the fog
            // is keeping back - and the account beneath it would have nothing in it to boot.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildUnknownPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly("Claim:", "The Hegemony");
        }

        @Test
        void composeBodyListsARivalWeighedOnAColonyThePlayerHasNotFound() {
            // The scored kind survives the projection. The mechanic weighs colonies nobody has
            // reached and can hand one of them the system, so a rival dropped for the fog would
            // leave the contest reported as something other than what decided it - the account
            // beneath the name is what carries the withholding, redacting the colony rather than
            // the faction standing on it.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(
                        TRITACHYON,
                        RIVAL_SCORE,
                        IS_TERRITORIAL,
                        IS_UNDISCOVERED_BY_PLAYER))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly("Claim:", "The Hegemony", "Contested by:", "Tri-Tachyon");
        }

        @Test
        void composeBodyStatesTheScoreOfAHolderWeighedOnAColonyThePlayerHasNotFound() {
            // The claim line carries the number its standing reports, the standing now being listed.
            // What the blank value column means is the point: it says the claimant holds nothing the
            // contest weighed, which is the shape of a decree over a colony-less faction - so a
            // holder dropped for the fog would wear that shape while a colony of its own won the
            // system.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(
                    HEGEMONY,
                    TOP_SCORE,
                    IS_TERRITORIAL,
                    IS_UNDISCOVERED_BY_PLAYER))));

            var claimRow = readTableRow(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections(), CLAIM_ROW);

            assertThat(readLabelRun(claimRow, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void composeBodyListsAFactionThePlayerHasNotFoundUnderTheDevReveal() {
            // The reveal is the state a player has asked to be shown everything in, so the same
            // faction is listed in full - the withholding is about what they have found rather than
            // about the box.
            visibilityRulesMock
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(new MapVisibilityRules(UNDER_THE_REVEAL, false));

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildUnknownPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly("Claim:", "The Hegemony", "Contested by:", "Tri-Tachyon");
        }

        @Test
        void composeBodyShowsAQuietNoughtForADecreedClaimantTheContestNeverWeighed() {
            // A decree over a system its holder is present in through a concealed base alone: the
            // faction has a standing, so the claim line states the nought that standing reports
            // rather than the blank value column of a claimant holding nothing there at all.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildPresenceOnlyStanding(HEGEMONY, IS_TERRITORIAL))));

            var claimRow = readTableRow(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections(), CLAIM_ROW);

            assertThat(readLabelRun(claimRow, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("0", GRAY)));
        }

        @Test
        void composeBodyHoldsEachHeadingWithTheLinesItNames() {
            // Each block is a heading and its own entries, so the box parts one block from the next
            // and nothing inside a block - the shape the whole reading of the box rests on.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(sections)
                .hasSize(2);
            assertThat(sections.get(CLAIM_SECTION).readRowsInOrder())
                .hasSize(HEADED_ONE_ENTRY_ROW_COUNT);
            assertThat(sections.get(SECOND_SECTION).readRowsInOrder())
                .hasSize(HEADED_ONE_ENTRY_ROW_COUNT);
        }

        @Test
        void composeBodyKeepsRivalsInTheOrderTheContestRankedThem() {
            // The breakdown hands its standings over strongest first, which is the order a contest is
            // read in - a section that re-ordered or reversed them would put the nearest challenger
            // last while every other assertion in this suite still passed.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, true))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Pirates");
        }

        @Test
        void composeBodySetsHeadingsApartFromTheEntriesBeneathThem() {
            // The two faults the review found on this box: a heading laid inside the crest gutter reads
            // as indented under nothing, and claim lines drawn as nested rows encode a second tier this
            // box does not have - claims resolve per faction, so every line here is an entry.
            var contestedHeadingRow = 2;
            var contestedEntryRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readLabelRun(readTableRow(sections, contestedHeadingRow), LABEL_RUN))
                .isEqualTo(new TextSpan("Contested by:", HIGHLIGHT));
            assertThat(readTableRow(sections, contestedHeadingRow).labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(readTableRow(sections, contestedHeadingRow).labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);

            assertThat(readTableRow(sections, CLAIM_ROW).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
            assertThat(readTableRow(sections, contestedEntryRow).indent())
                .isCloseTo(NO_INDENT, within(TOLERANCE));
        }

        @Test
        void composeBodyOmitsContestedWhenTheClaimantIsTheOnlyTerritorialFaction() {
            // An uncontested claim has to read as uncontested, and a heading standing over no lines
            // would read as a contest whose rivals failed to resolve.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly("Claim:", "The Hegemony");
        }

        @Test
        void composeBodyMarksACoreClaimAndKeepsItsMarketScore() {
            // The decree is what took the system, so it is called out in the highlight colour on the
            // claim line itself - while the number beside it stays the faction's market standing,
            // which the decree does not erase.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            var claimRow = readTableRow(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections(), CLAIM_ROW);

            assertThat(readLabelRun(claimRow, MARKED_LABEL_RUN))
                .isEqualTo(new TextSpan("The Hegemony", PLAYER_BRIGHT));
            assertThat(readLabelRun(claimRow, MARKED_QUALIFIER_RUN))
                .isEqualTo(new TextSpan("(core)", HIGHLIGHT));
            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void composeBodyDropsTheDisplacedTopScorerIntoContested() {
            // The regression this guards: a decree must not collapse the box to one line. The faction
            // that would have claimed by score is simply not the claimant, so it reads as contesting -
            // which is what shows the player a core imposed over a stronger presence.
            stubBreakdown(new SystemClaimBreakdown(
                PIRATES,
                PIRATES,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true),
                    buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "Pirates",
                    "Contested by:",
                    "The Hegemony");

            var displacedScorerRow = 3;

            assertThat(readTableRow(sections, displacedScorerRow).labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(new TextSpan("1,200", HIGHLIGHT)));
        }

        @Test
        void composeBodyShowsNoScoreForACoreFactionHoldingNoMarketThere() {
            // A decree needs no colony behind it, so the claimant is named with the value column left
            // blank rather than with a nought it never scored.
            stubBreakdown(new SystemClaimBreakdown(
                HEGEMONY,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, true))));

            var claimRow = readTableRow(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections(), CLAIM_ROW);

            assertThat(claimRow.labelledRow().trailingRowSlot())
                .isEqualTo(new RowSlot.Text(TextSpan.createBlank(HIGHLIGHT)));
        }

        @Test
        void composeBodyStatesTheClaimAsNoneWhenNobodyCanTakeTheSystem() {
            // A faction present but barred from claiming leaves the system unclaimed, which the box has
            // to say outright - the claim heading over nothing would read as a failure to resolve one.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildStandingOnOneMarket(PIRATES, OUTSIDER_SCORE, false))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly(
                    "Claim:",
                    "None",
                    "Non-territorial:",
                    "Pirates");

            assertThat(readTableRow(sections, CLAIM_ROW).labelledRow().leadingRowSlot())
                .isEqualTo(RowSlot.EMPTY);
        }

        @Test
        void composeBodyOpensEveryClaimLineAtTheContentEdge() {
            // A crest rides in the label of the line carrying it, so the claim block listing only the
            // word for nobody opens flush under its own heading - level with the crested line in the
            // block below rather than a gutter's width apart from it.
            var nonTerritorialEntryRow = 3;

            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildStandingOnOneMarket(HEGEMONY, OUTSIDER_SCORE, false))));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly("Claim:", "None", "Non-territorial:", "The Hegemony");

            assertThat(readTableRow(sections, CLAIM_ROW).labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
            assertThat(readTableRow(sections, nonTerritorialEntryRow).labelPlacement())
                .isEqualTo(TooltipLabelPlacement.AT_CONTENT_EDGE);
        }

        @Test
        void composeBodyStatesTheClaimAsNoneForAPopulatedSystemNobodyHasTaken() {
            // Nobody holding a system that is nonetheless lived in is a finding rather than an absence,
            // and the only line that states it - so the block stands whether or not anything scored.
            stubBreakdown(SystemClaimBreakdown.NONE);

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly("Claim:", "None");
        }

        @Test
        void composeBodyDropsTheClaimBlockForAnUnclaimedSystemHoldingNobody() {
            // "None" beneath a banner already saying the system holds nobody answers the same absence
            // twice, so the block is dropped and the banner is left to say it once.
            stubSystemHoldingNobody();
            stubBreakdown(SystemClaimBreakdown.NONE);

            assertThat(readSectionOpeningWords(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly("Unpopulated");
        }

        @Test
        void composeBodyNamesTheDecreeHoldingASystemThatHoldsNobody() {
            // The half the banner does not answer: a decree over a system with nothing in it is a hold
            // the player can read nowhere else in the box, so it survives the drop above.
            stubSystemHoldingNobody();
            stubBreakdown(new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            assertThat(readSectionOpeningWords(sections))
                .containsExactly("Unpopulated", "Claim:", "The Hegemony");
            assertThat(readLabelRun(readTableRow(sections, DECREED_CLAIM_ROW), MARKED_QUALIFIER_RUN))
                .isEqualTo(new TextSpan("(core)", HIGHLIGHT));
        }

        @Test
        void composeBodyNamesTheSystemsStatusBeforeItsClaimAndInABlockOfItsOwn() {
            // A dead system names its state first, so the claim below reads as a hold over an empty
            // system rather than over a colony - and parted from it, since the two answer different
            // questions.
            var statusRow = stubSystemHoldingNobody();

            stubBreakdown(new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()));

            var sections = tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();
            var statusSection = 0;
            var claimSectionUnderTheStatus = 1;

            assertThat(sections.get(statusSection).readRowsInOrder())
                .containsExactly(statusRow);
            assertThat(readLabelTextRun(
                    sections.get(claimSectionUnderTheStatus).readRowsInOrder().get(0),
                    LABEL_RUN)
                    .text())
                .isEqualTo("Claim:");
        }

        @Test
        void composeBodyJudgesTheSystemEmptyUnderTheNormalRevealWhileItIsOff() {
            // An undiscovered colony must not count: suppressing the status line for one would make
            // the missing line itself the tell that something is hiding in the system.
            stubBreakdown(SystemClaimBreakdown.NONE);

            tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            // Matched on the rule the knowledge carries: the box pairs it with the sector's own
            // register where it draws, so the value handed over is never one a case could state.
            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(
                    any(),
                    argThat(knowledge -> BASE_FOG.equals(knowledge.rule()))));
        }

        @Test
        void composeBodyJudgesTheSystemEmptyUnderTheDevRevealWhileItIsOn() {
            // The reveal is read live off the same toggle the faction layer samples, so a player who
            // has turned it on is not told two different things by two layers about one system.
            visibilityRulesMock
                .when(MapVisibilityRules::readFromLunaSettings)
                .thenReturn(new MapVisibilityRules(UNDER_THE_REVEAL, false));

            stubBreakdown(SystemClaimBreakdown.NONE);

            tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections();

            statusRowMock.verify(
                () -> SystemStatusRow.resolveStatusRow(
                    any(),
                    argThat(knowledge -> UNDER_THE_REVEAL.equals(knowledge.rule()))));
        }

        @Test
        void composeBodyFallsBackToTheIdForAFactionTheSectorCannotResolve() {
            stubBreakdown(new SystemClaimBreakdown(
                null,
                "ghost_faction",
                List.of(buildStandingOnOneMarket("ghost_faction", TOP_SCORE, true))));

            var claimRow = readTableRow(tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections(), CLAIM_ROW);

            assertThat(readLabelTextRun(claimRow, LABEL_RUN).text())
                .isEqualTo("ghost_faction");
        }

        @Test
        void composeBodyReadsEveryBlockOnlyAsDeepAsTheLevelAsksFor() {
            // The level has to reach all five of this body's blocks rather than stopping at the box.
            // One composition, drawn as the factions alone where the player asked who claims the
            // system, and with the colonies behind them where they asked on what - so a body that
            // named a level of its own would draw the same thing at both and pass every other case
            // here.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(
                    tooltip.composeBody(sectorMock, systemMock, FACTIONS).blocks().readSections()))
                .containsExactly("Claim:", "The Hegemony", "Contested by:", "Tri-Tachyon");

            // One tier down the colonies appear and the terms behind them do not, which is what makes
            // this a cut over one tree rather than two bodies drawn from two reads.
            assertThat(readSectionOpeningWords(
                    tooltip.composeBody(sectorMock, systemMock, SYSTEM_COMPOSITION).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Standing Colony");
        }

        @Test
        void composeBodyAsksTheBoxForNoAccountAtAllWhereTheLevelShowsNoLineOfOne() {
            // The other half of the cut, and the half the drawn box cannot show: an account is
            // everything a listed faction is subordinated over, so the shallowest level draws not one
            // of its lines - and it is therefore never asked for. Cut after the fact, every faction
            // in every block would have its markets selected, ranked and worded first, over a hover
            // that asked only who claims the system.
            var accountingTooltip = new AccountingClaimContestTooltip(claimBreakdownReaderFake);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            accountingTooltip.composeBody(sectorMock, systemMock, FACTIONS);

            assertThat(accountingTooltip.readRequestedLevels())
                .isEmpty();
        }

        @Test
        void composeBodyAsksTheBoxForTheAccountAtTheLevelItWillBeReadTo() {
            // The level travels to the box rather than only gating the call, so an account carrying
            // tiers of its own stops where the cut would. Handed a fixed depth instead, the box would
            // work its deepest tiers out at every level that shows an account at all - and the cut
            // would trim the drawn box back to the same lines, so nothing on screen would say so.
            var accountingTooltip = new AccountingClaimContestTooltip(claimBreakdownReaderFake);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL))));

            accountingTooltip.composeBody(sectorMock, systemMock, SYSTEM_COMPOSITION);
            accountingTooltip.composeBody(sectorMock, systemMock, MARKET_STATS);

            assertThat(accountingTooltip.readRequestedLevels())
                .containsExactly(SYSTEM_COMPOSITION, MARKET_STATS);
        }

        @Test
        void composeBodyHangsEachFactionsColoniesBeneathItsOwnLine() {
            // Every block the box has takes the account, claimant and rival alike - and a colony reads
            // under the faction that holds it rather than under whichever line came before it.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(
                    tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Size",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Standing Colony",
                    "Size");
        }

        @Test
        void composeBodyHangsAnAlliedFactionsColoniesBeneathItsLineInTheAlliedBlock() {
            // The routing is the shared shape's and pinned there; what this case is about is that the
            // detail follows a faction into the block the relation put it in. An ally accounted for
            // only under `Contested by:` would be an account of a line the box no longer draws.
            holderGrouping = buildAllianceOf(HEGEMONY, TRITACHYON);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(
                    tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Size",
                    "Allied with the claim holder:",
                    "Tri-Tachyon",
                    "Standing Colony",
                    "Size");
        }

        @Test
        void composeBodyHangsAFriendlyFactionsColoniesBeneathItsLineInTheFriendlyBlock() {
            // The other relation block on the same terms: the detail follows a faction wherever the
            // relation put it, so a faction merely on good terms with the claim holder is accounted
            // for under the block it was drawn in rather than under `Contested by:`.
            stubDispositionToward(sectorMock, TRITACHYON, HEGEMONY, RepLevel.FAVORABLE);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            assertThat(readSectionOpeningWords(
                    tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Size",
                    "Friendly with the claim holder:",
                    "Tri-Tachyon",
                    "Standing Colony",
                    "Size");
        }

        @Test
        void composeBodyHangsTheColoniesOfAFactionTheContestNeverWeighedBeneathItsOwnLine() {
            // The whole point of the widening, read as the box draws it: the faction's line states a
            // nought and its colonies hang under that line rather than under the claimant's above.
            // They break down no further, nothing having been computed for them - which is what
            // parts such an account from the weighed one two lines above it.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    new PresenceOnlyClaimStanding(
                        TRITACHYON,
                        IS_TERRITORIAL,
                        List.of(buildConcealedMarket("Kanta's Den", FIRST_LISTED))))));

            assertThat(readSectionOpeningWords(
                    tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections()))
                .containsExactly(
                    "Claim:",
                    "The Hegemony",
                    "Standing Colony",
                    "Size",
                    "Contested by:",
                    "Tri-Tachyon",
                    "Kanta's Den");
        }

        @Test
        void composeBodyAccountsForNothingWhereADecreedClaimantHoldsNoColonyHere() {
            // A decree needs no colony behind it, so the claimant is named with nothing hung beneath
            // it rather than with a heading over an account it never earned.
            stubBreakdown(new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()));

            assertThat(readSectionOpeningWords(
                    tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections()))
                .containsExactly("Claim:", "The Hegemony");
        }

        @Test
        void composeBodyReadsTheSystemOnceHoweverManyFactionsTheContestHolds() {
            // The kinds and the last-seen remarks a listing carries are read from one walk of the
            // system, made for the box rather than for a line. Resolved where an account is built,
            // they would walk the system once for every faction listed - and the walk is the most
            // expensive thing a hover does.
            // The walk reaches the system's own entities only once it has an economy to tell a
            // listed market from an unlisted one, so the case stands one up holding nothing: what
            // it is about is how many times the system is read, not what the read finds.
            var economyMock = mock(EconomyAPI.class);

            when(economyMock.getMarkets(systemMock))
                .thenReturn(List.of());
            when(sectorMock.getEconomy())
                .thenReturn(economyMock);

            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(
                    buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                    buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL))));

            tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections();

            verify(systemMock, times(1)).getAllEntities();
        }

        @Test
        void composeBodyReadsTheColoniesOffTheWalkTheStatusRowWasJudgedFrom() {
            // The one walk the box makes has to answer everything below it. Opened again for the
            // account, the kinds and the dates would come off a second reading of the system - so
            // the banner could call a system empty while the lines beneath it named a colony that
            // arrived between the two.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL))));

            var statusRowColonies = ArgumentCaptor.forClass(Colonies.class);
            var statusRowKnowledge = ArgumentCaptor.forClass(ColonyKnowledge.class);
            var readingColonies = ArgumentCaptor.forClass(Colonies.class);
            var readingKnowledge = ArgumentCaptor.forClass(ColonyKnowledge.class);

            try (var readingMock = Mockito.mockStatic(
                    SystemColonyReading.class,
                    Mockito.CALLS_REAL_METHODS)) {

                tooltip.composeBody(sectorMock, systemMock, PATROL_DETAILS).blocks().readSections();

                statusRowMock.verify(() -> SystemStatusRow.resolveStatusRow(
                    statusRowColonies.capture(),
                    statusRowKnowledge.capture()));

                readingMock.verify(() -> SystemColonyReading.readColoniesIn(
                    any(),
                    any(),
                    readingColonies.capture(),
                    readingKnowledge.capture()));
            }
            assertThat(readingColonies.getValue())
                .isSameAs(statusRowColonies.getValue());
            assertThat(readingKnowledge.getValue())
                .isSameAs(statusRowKnowledge.getValue());
        }
    }

    @Nested
    class ResolveAccountEntries {

        @Test
        void resolveAccountEntriesAccountsForAFactionWithTheMarketsItHolds() {
            // The point of the deeper levels, and the one thing the faction's line cannot state: its
            // number is one market's score, so the markets it was read from are what its account
            // lists.
            var standing = new WeighedClaimStanding(
                HEGEMONY,
                IS_TERRITORIAL,
                buildMarket("Chicomoztoc", TOP_SCORE),
                List.of(buildMarket("Culann", LESSER_MARKET_SIZE)));

            assertThat(readLabelTexts(
                    tooltip.resolveAccountEntries(
                        CONTESTED_CONTEST,
                        standing,
                        SystemColonyReading.NONE,
                        PATROL_DETAILS)))
                .containsExactly("Chicomoztoc", "Culann");
        }

        @Test
        void resolveAccountEntriesBreaksEachMarketDownIntoItsTerms() {
            // A market's own line is a sum too, so the account goes one level further: the terms that
            // built its score hang beneath it rather than the number being left to be taken on trust.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_CONTEST,
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE,
                PATROL_DETAILS);

            assertThat(readLabelTexts(entries.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void resolveAccountEntriesBreaksAMarketIntoNothingWhereTheLevelStopsAtTheMarkets() {
            // The level reaches the account rather than only deciding whether to ask for one, so the
            // composition level names the markets and works out none of the arithmetic beneath them.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_CONTEST,
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE,
                SYSTEM_COMPOSITION);

            assertThat(readLabelTexts(entries))
                .containsExactly("Standing Colony");
            assertThat(entries.get(0).children())
                .isEmpty();
        }

        @Test
        void resolveAccountEntriesCallsOutTheMarketTheClaimantTookTheSystemWith() {
            // The box's half of the rule: it reads who took the system off the very contest it is
            // drawing, so the call-out lands on the one market in the whole box that won anything.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_CONTEST,
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE,
                PATROL_DETAILS);

            assertThat(entries.get(0).line().qualifier())
                .isEqualTo(CellTooltipQualifier.stateFinding("claim holder"));
        }

        @Test
        void resolveAccountEntriesCallsOutNoMarketOfAFactionThatTookNothing() {
            // A rival is represented by its own strongest market too, but that market took nothing -
            // called out, it would read as a second holder of a system that can only have one.
            var entries = tooltip.resolveAccountEntries(
                CONTESTED_CONTEST,
                buildStandingOnOneMarket(TRITACHYON, RIVAL_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE,
                PATROL_DETAILS);

            assertThat(entries.get(0).line().qualifier())
                .isNull();
        }

        @Test
        void resolveAccountEntriesAccountsForAFactionTheContestNeverWeighed() {
            // The case the widening exists for: such a faction's line is a nought and nothing else,
            // so its colonies are the whole of what the deeper levels have to add about it - and they
            // are what the player is looking at on the map.
            var standing = new PresenceOnlyClaimStanding(
                TRITACHYON,
                IS_TERRITORIAL,
                List.of(
                    buildConcealedMarket("Kanta's Den", FIRST_LISTED),
                    buildConcealedMarket("Chalcedon", SECOND_LISTED)));

            assertThat(readLabelTexts(
                    tooltip.resolveAccountEntries(
                        CONTESTED_CONTEST,
                        standing,
                        SystemColonyReading.NONE,
                        PATROL_DETAILS)))
                .containsExactly("Kanta's Den", "Chalcedon");
        }

        @Test
        void resolveAccountEntriesWithholdsUnderTheRuleTheContestWasProjectedUnder() {
            // The account draws under the rule that selected the listing above it, not under one it
            // reads for itself. Posed as the two disagreeing: the contest carries the reveal, while
            // the live settings seam answers with it off. The undiscovered market has to be listed -
            // read afresh here, the account would withhold the very colony the listing named, and
            // would be free to answer two factions of one box differently besides.
            var contest = buildUnroutedContest(CONTESTED_SYSTEM, UNDER_THE_REVEAL);

            var standing = new WeighedClaimStanding(
                HEGEMONY,
                IS_TERRITORIAL,
                buildMarket("Chicomoztoc", TOP_SCORE),
                List.of(buildUndiscoveredMarket("Culann", LESSER_MARKET_SIZE)));

            var entries = tooltip.resolveAccountEntries(
                contest,
                standing,
                SystemColonyReading.NONE,
                PATROL_DETAILS);

            // Read as the row being there rather than as the name it states: such a row stands for the
            // name instead of saying it, which is the resolver's own shape and pinned there.
            assertThat(readLabelTexts(entries))
                .containsExactly("Chicomoztoc", NO_NAME_STATED);
            assertThat(entries.get(1).line().hasRedactedName())
                .isTrue();
        }

        @Test
        void resolveAccountEntriesCallsOutNoMarketOfASystemHeldByDecree() {
            // A decree took the system before any market was weighed, so no market's score decided
            // anything and none is called out for it - the claimant's least of all.
            var entries = tooltip.resolveAccountEntries(
                buildUnroutedContest(
                    new SystemClaimBreakdown(HEGEMONY, HEGEMONY, List.of()),
                    ColonyVisibility.BASE_FOG),
                buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, IS_TERRITORIAL),
                SystemColonyReading.NONE,
                PATROL_DETAILS);

            assertThat(entries.get(0).line().qualifier())
                .isNull();
        }
    }

    @Nested
    class ResolveDeepestAccountLevel {

        @Test
        void resolveDeepestAccountLevelStopsAtTheMarketStats() {
            // The whole reason a box states its own depth. Vanilla settles a claim on a colony's size,
            // its garrison and how many colonies the faction holds beside it - no patrol enters the
            // arithmetic anywhere - so the account has no line at the level below and the cycle has to
            // collapse from here. Offered that level, the key would redraw the box unchanged.
            assertThat(tooltip.resolveDeepestAccountLevel())
                .isEqualTo(HoverTooltipDetailLevel.MARKET_STATS);
        }
    }

    @Nested
    class ResolveDeepestHeldLevelFor {

        @Test
        void resolveDeepestHeldLevelForOffersTheAccountBehindAScoredStanding() {
            // What the key at the foot of the box would reach: the colonies behind the faction, and
            // the terms behind a colony's score - the deeper tiers accounting for the very score the
            // shallowest states, and stopping where the mechanic's arithmetic does.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                HEGEMONY,
                List.of(buildStandingOnOneMarket(HEGEMONY, TOP_SCORE, true))));

            assertThat(tooltip.resolveDeepestHeldLevelFor(sectorMock, systemMock))
                .isEqualTo(HoverTooltipDetailLevel.MARKET_STATS);
        }

        @Test
        void resolveDeepestHeldLevelForOffersTheAccountBehindAPresenceTheContestNeverWeighed() {
            // Such a faction's colonies are exactly what the player can read nowhere else in the box,
            // its line stating a nought and nothing more - so the key has something to open even
            // where the mechanic weighed the whole system at nothing.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(tooltip.resolveDeepestHeldLevelFor(sectorMock, systemMock))
                .isEqualTo(HoverTooltipDetailLevel.MARKET_STATS);
        }

        @Test
        void resolveDeepestHeldLevelForOffersNothingWhereTheProjectionListsNobody() {
            // The deeper tiers account for the factions this box lists, and the fog has left it
            // listing none. Every level would state the same claim line, so the key would do nothing
            // the player could see - and a hint over it would advertise that it would.
            stubBreakdown(new SystemClaimBreakdown(
                null,
                null,
                List.of(buildUnknownPresenceOnlyStanding(TRITACHYON, IS_TERRITORIAL))));

            assertThat(tooltip.resolveDeepestHeldLevelFor(sectorMock, systemMock))
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    // Hands the tooltip the contest it is about, standing in for the market walk that would otherwise
    // have to be driven through a live economy to produce it.
    private void stubBreakdown(SystemClaimBreakdown breakdown) {
        claimBreakdownReaderFake.setBreakdown(SYSTEM_ID, breakdown);
    }

    // Puts the hovered system among the ones holding nobody, the state the status seam answers with a
    // banner. Returned so a case about where that banner sits can assert on the very row it stubbed.
    private TooltipRow.CentredRow stubSystemHoldingNobody() {

        var statusRow = CellTooltipRows.buildBannerRow(null, "Unpopulated");

        statusRowMock
            .when(() -> SystemStatusRow.resolveStatusRow(any(), any()))
            .thenReturn(Optional.of(statusRow));

        return statusRow;
    }

    // A colony held in concealment and found all the same - the shape a faction the contest never
    // weighed is present through, and the one the map draws in that faction's colours. Sized like
    // any other, the size going nowhere: nothing was computed for it.
    //
    // Concealment rather than an absence from the economy's listing, arbitrarily: the two suppress
    // scoring identically and no case here is about which of them did it.
    private static MarketClaimBreakdown buildConcealedMarket(String marketName, int listingPosition) {
        return ClaimMarketFixture
            .startMarket(marketName)
            .setListingPosition(listingPosition)
            .setAdmission(ContestAdmission.HIDDEN)
            .setMarketSize(LESSER_MARKET_SIZE)
            .setSiblingMarketCount(NO_SIBLING_MARKETS)
            .buildMarket();
    }

    // The same market on a colony nobody has discovered, listed after the one above it, for the case
    // about which rule decides whether it is drawn at all.
    private static MarketClaimBreakdown buildUndiscoveredMarket(String marketName, int marketSize) {
        return ClaimMarketFixture
            .startMarket(marketName)
            .setListingPosition(SECOND_LISTED)
            .setKnownToPlayer(IS_UNDISCOVERED_BY_PLAYER)
            .setMarketSize(marketSize)
            .setSiblingMarketCount(NO_SIBLING_MARKETS)
            .buildMarket();
    }

    // A market scoring its size alone, for a standing a case states by the markets under it rather
    // than by the arithmetic inside one. Every one of them heads the listing, since no case here is
    // about a tie or where the economy put anything.
    //
    // Marked with no glyph: whether a market line leads with one is the resolver's and pinned there,
    // and a mark on every line would run through the reading-order assertions these cases are
    // actually about.
    private static MarketClaimBreakdown buildMarket(String marketName, int marketSize) {
        return ClaimMarketFixture
            .startMarket(marketName)
            .setListingPosition(FIRST_LISTED)
            .setMarketSize(marketSize)
            .setSiblingMarketCount(NO_SIBLING_MARKETS)
            .buildMarket();
    }

    private static String readLabelText(List<TooltipSection> sections, int rowIndex) {
        return CellTooltipRowReads.readOpeningWords(
            TooltipSection.readRowsInOrder(sections).get(rowIndex));
    }

    // Reads one body line as the table row it is. A block's lines are typed on the row supertype, since
    // a centred line is a legal shape for one, but every line these cases assert on lays into the box's
    // columns - which is where the crest and value slots live.
    private static TooltipRow.TableRow readTableRow(List<TooltipSection> sections, int rowIndex) {
        return (TooltipRow.TableRow) TooltipSection
            .readRowsInOrder(sections)
            .get(rowIndex);
    }

    /**
     * A box on the claim shape that records every level it was asked to build an account at, and
     * hangs nothing.
     *
     * <p>Recording is the only way to see what the shape asked for: the cut trims the drawn box back
     * to the same lines whether the shape withheld the question or the box answered it and had the
     * answer dropped, so no assertion on what the box says can tell the two apart.
     */
    private static final class AccountingClaimContestTooltip extends SystemClaimContestTooltip {

        private final List<HoverTooltipDetailLevel> requestedLevels = new ArrayList<>();

        private AccountingClaimContestTooltip(ClaimBreakdownReader claimBreakdownReader) {
            super(claimBreakdownReader, HolderGrouping::identity);
        }

        @Override
        protected HoverTooltipDetailLevel resolveDeepestAccountLevel() {
            // The deepest the levels declare, so the shape is exercised over a box that fills the
            // cycle out - the real claim box's shallower bound is its own suite's subject.
            return HoverTooltipDetailLevel.PATROL_DETAILS;
        }

        @Override
        protected List<CellTooltipEntry> resolveAccountEntries(
                ListedClaimContest contest,
                FactionClaimStanding standing,
                SystemColonyReading colonyReading,
                HoverTooltipDetailLevel detailLevel) {

            requestedLevels.add(detailLevel);
            return List.of();
        }

        private List<HoverTooltipDetailLevel> readRequestedLevels() {
            return List.copyOf(requestedLevels);
        }
    }
}
