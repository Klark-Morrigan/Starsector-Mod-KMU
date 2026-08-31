package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.entities.EntityNameplate;
import kmlib.starsector.ui.text.ImageSpan;
import kmlib.starsector.ui.text.TextSpan;
import kmlib.starsector.ui.widgets.RowSlot;
import kmlib.starsector.ui.widgets.tooltip.TooltipRow;
import kmlib.starsector.ui.widgets.tooltip.TooltipSection;
import kmlib.testfixtures.starsector.systems.claims.ClaimBreakdownReaderFake;

import kmu.maplayers.SectorScenarioFixtures;
import kmu.maplayers.base.tooltip.CellTooltipEntry;
import kmu.maplayers.base.tooltip.CellTooltipEntryLine;
import kmu.maplayers.base.tooltip.CellTooltipMark;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonySightings;
import kmu.maplayers.politicalmap.base.dominance.BaseSizeFactor;
import kmu.maplayers.politicalmap.base.dominance.DominancePass;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.KnownMarketFootprints;
import kmu.maplayers.politicalmap.base.dominance.MarketWeightBreakdown;
import kmu.maplayers.politicalmap.base.dominance.PresenceOnlyFactionStanding;
import kmu.maplayers.politicalmap.base.dominance.UnweighedColony;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;
import kmu.maplayers.politicalmap.base.dominance.weighting.BaseSizeWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;
import kmu.maplayers.politicalmap.base.dominance.weighting.PatrolWeighting;
import kmu.maplayers.politicalmap.base.dominance.weighting.StationWeighting;
import kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures;
import kmu.settings.HiddenMarketScalingChoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.base.tooltip.CellTooltipEntryReads.readLabelTexts;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.HIGHLIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.PLAYER_BRIGHT;
import static kmu.maplayers.base.tooltip.CellTooltipPaletteFake.TEXT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARKED_LABEL_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MARK_RUN;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.MEMBER_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.NO_INDENT;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.TOLERANCE;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readLabelRun;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readSectionOpeningWords;
import static kmu.maplayers.base.tooltip.CellTooltipRowReads.readTableRow;
import static kmu.maplayers.base.tooltip.HoverTooltipDetailLevel.PATROL_DETAILS;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibility.BASE_FOG;
import static kmu.maplayers.base.visibility.colonies.ColonyVisibilityFixtures.UNDER_THE_REVEAL;
import static kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture.buildAllianceOf;
import static kmu.maplayers.politicalmap.base.politics.SectorPoliticsFixtures.FULL_STABILITY;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubDispositionToward;
import static kmu.maplayers.politicalmap.base.tooltip.SectorFactionsFake.stubFaction;
import static kmu.maplayers.politicalmap.base.tooltip.StandingsTooltipSeamsFake.VIEW_GROUPING;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what the faction and alliance views' hover box says about a system: the groups holding it, laid
 * out flat or over their member factions as the resolver handed them over, and beneath every faction
 * named, the colonies it holds the system with opened down to the factors each weight was summed from.
 *
 * <p>Two facts this box alone decides. A group is listed as the resolver shaped it, which is the shared
 * default rather than an answer of this box's own - a group made up of nothing reads as one flat line,
 * one carrying members reads over them indented. And a faction is accounted for by <em>its own</em>
 * colonies, the one thing a bloc's line above cannot say, since a bloc's score is the sum over its
 * members' - read once for the whole box rather than once per faction.
 *
 * <p>A third holds for the colonies the economy does not list: they are read apart from the weighed
 * ones and paired to their faction the same way, so they reach the account without ever reaching the
 * pass. That they weigh nothing is {@link MarketWeightRowResolverTest}'s; that they are the faction's
 * own is here.
 *
 * <p>How much of that account a given detail level admits is not this box's decision at all - the
 * account is composed whole and cut where the listing is laid out, which is pinned once over the
 * shared shape ({@link SystemStandingsTooltipTest}). So is the ranking, the status line, the headings
 * and which of them a group falls under; the decree heading the box belongs to every one of the
 * layer's boxes and is pinned with the heading itself ({@link PoliticalMapCellTooltipTest}). Where a
 * colony then hangs is the shared resolution's ({@link StandingRowResolverTest}).
 *
 * <p>The non-political block is the exception among the blocks and is pinned here, because it is the
 * one whose routing turns on which faction a bloc actually is: the shape suite poses its groups as
 * bloc ids that stand for nobody in particular, which is right for every block placed by rank or by
 * alliance and cannot state this one at all.
 */
final class SystemDominationTooltipTest {

    private static final String SYSTEM_ID = "askonia";
    private static final String CORE_FACTION = "hegemony";
    private static final String ALLY_FACTION = "tritachyon";

    // The two the chain's remaining relation blocks are posed with: one the sector puts on good terms
    // with the holder, and one it says nothing about, which is indifference and so a rival.
    private static final String FRIENDLY_FACTION = "luddic_church";
    private static final String RIVAL_FACTION = "persean_league";

    private static final String BLOC_CREST = "graphics/rebel_pact_crest.png";
    private static final CellTooltipMark BLOC_MARK =
        CellTooltipMark.resolveMarkAsAuthored(BLOC_CREST);

    private static final String MEMBER_CREST = "graphics/hegemony_crest.png";
    private static final CellTooltipMark MEMBER_MARK =
        CellTooltipMark.resolveMarkAsAuthored(MEMBER_CREST);

    // The lines a box with no status and no decree lays out, in draw order: the heading naming who
    // holds the system, that group's own header, then its members beneath.
    private static final int GROUP_HEADER_ROW = 1;
    private static final int FIRST_MEMBER_ROW = 2;
    private static final int SECOND_MEMBER_ROW = 3;

    // The scores reach this box already worded by the resolver, so they stand in as the text they draw
    // as - what the box does with them is carry them into the value column.
    private static final String BLOC_SCORE = "1,200";
    private static final String ALLY_SCORE = "800";
    private static final String PLACEHOLDER_SCORE = "50";

    // What a stood-up group is weighed at, forwarded to the (stood-in) naming: the case using it is
    // about which block a group falls in, which is read off its bloc alone.
    private static final int ANY_SCORE = 0;
    private static final String MEMBER_SCORE = "900";
    private static final String OTHER_MEMBER_SCORE = "300";

    // Where a colony sits and whether it is concealed, neither of which any case here turns on.
    // Named so the pair of booleans a colony's parts open with can be read rather than counted off
    // against the record's own order.
    private static final boolean VISIBLE_COLONY = false;
    private static final boolean PLANET_COLONY = false;

    // The one colony the economy does not list, marked with no glyph: every case about the account is
    // about which faction a colony is listed under rather than about what its line leads with.
    private static final UnweighedColony UNLISTED_COLONY = new UnweighedColony(
        "galatia_academy",
        ColonyKind.COLONY,
        VISIBLE_COLONY,
        EntityNameplate.createUnmarkedNameplate("Galatia Academy"));

    // Stability is left unweighed throughout, so a colony breaks down into the one factor each case is
    // about rather than into a stability line every assertion would have to step over.
    private static final DominanceRules ANY_RULES = new DominanceRules(
        false,
        new BaseSizeWeighting(1.0, HiddenMarketScalingChoice.NORMAL, 2.5, 1.0),
        new StationWeighting(false, 3.0, 0.5, 0.5),
        new PatrolWeighting(false, 0.25, 0.5, 1.0, 0.5));

    // Opened over no sector, so every pass here answers the empty colony set: what these cases are
    // about is which set the box hands on and under which knobs, not what a walk would have found.
    private static final DominancePass ANY_PASS =
        DominancePass.over(null, ANY_RULES, BASE_FOG, VIEW_GROUPING);

    // The same pass with the dev reveal on, so a case can tell a read that carries the pass's own
    // reveal from one that hardcodes the ordinary answer - which every other case would agree with.
    private static final DominancePass REVEALED_PASS =
        DominancePass.over(null, ANY_RULES, UNDER_THE_REVEAL, VIEW_GROUPING);

    // A pass over a sector that has actually observed something, so its register is a real read of
    // that sector's memory rather than the empty one. The distinction is the whole of what the case
    // using it can see: a sector with nothing recorded answers the same empty register a read that
    // opened one for itself would find, so only a written register tells the pass's own from any
    // other.
    //
    // Built here rather than in the case, because the seam fixture stands a static in front of
    // DominancePass for the duration of each one.
    private static final DominancePass OBSERVED_PASS = buildPassOverAnObservedSector();

    // Two factions of one bloc, so a case can tell "the faction's own colonies" from "the bloc's".
    private static final WeighedFactionStanding LEAD_MEMBER =
        new WeighedFactionStanding("hegemony", 6000);
    private static final WeighedFactionStanding OTHER_MEMBER =
        new WeighedFactionStanding("tritachyon", 3000);

    private final ClaimBreakdownReaderFake claimBreakdownReaderFake = new ClaimBreakdownReaderFake();

    // The alliance set the box routes its blocks against, restated by the case about an ally and left
    // ungrouped for the rest - the state an install with nothing grouping factions is in.
    private HolderGrouping allianceSet = HolderGrouping.identity();

    private final SystemDominationTooltip tooltip =
        new SystemDominationTooltip(claimBreakdownReaderFake, () -> allianceSet);

    private final SectorAPI sectorMock = mock(SectorAPI.class);
    private final StarSystemAPI systemMock = mock(StarSystemAPI.class);

    // The economy read behind the account, which no unit test can drive live. CALLS_REAL_METHODS keeps
    // the weight arithmetic beside it live, since the numbers on a factor line are read through the
    // very same class.
    private MockedStatic<KnownMarketFootprints> footprintsMock;

    @BeforeEach
    void installColoursAndTheRankingSeams() {

        CellTooltipPaletteFake.installPalette();
        StandingsTooltipSeamsFake.installSeams(ANY_PASS);

        footprintsMock = Mockito.mockStatic(KnownMarketFootprints.class, Mockito.CALLS_REAL_METHODS);

        when(systemMock.getId())
            .thenReturn(SYSTEM_ID);

        stubFaction(sectorMock, CORE_FACTION, "The Hegemony", MEMBER_CREST);
    }

    @AfterEach
    void clearColoursAndTheRankingSeams() {
        footprintsMock.close();
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

        @Test
        void buildBodySectionsListsThePlaceholderOwnerApartFromWhoHoldsTheSystem() {
            // Vanilla hands every abandoned station and collapsed colony to the neutral placeholder,
            // which takes a footprint like anybody else. Left among the contenders it would head the
            // box over a system a real faction runs, so it is set aside before a holder is picked -
            // listed with whatever it scored, and never named as holding the place.
            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(
                    new GroupStanding(CORE_FACTION, ANY_SCORE, List.of()),
                    new GroupStanding(Factions.NEUTRAL, ANY_SCORE, List.of())),
                List.of(createLoneGroupEntry(), createPlaceholderGroupEntry()));

            assertThat(readSectionOpeningWords(
                    tooltip.buildBodySections(sectorMock, systemMock, PATROL_DETAILS)))
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Non-political:",
                    "Neutral");
        }

        @Test
        void buildBodySectionsLaysTheFiveBlocksDownFromTheHolderOutward() {
            // The whole chain in one system, in the order a reader meets it: who holds the place,
            // who stands with it by alliance, who stands with it in disposition, who stands against
            // it, and who was never in the running. Posed here rather than with the shared shape
            // because two of the five turn on which faction a bloc actually is - the placeholder
            // owner, and a bloc the sector's own relations put on good terms with the holder.
            allianceSet = buildAllianceOf(CORE_FACTION, ALLY_FACTION);

            stubFaction(sectorMock, FRIENDLY_FACTION, "The Luddic Church", MEMBER_CREST);
            stubDispositionToward(sectorMock, FRIENDLY_FACTION, CORE_FACTION, RepLevel.FAVORABLE);

            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(
                    new GroupStanding(CORE_FACTION, ANY_SCORE, List.of()),
                    new GroupStanding(ALLY_FACTION, ANY_SCORE, List.of()),
                    new GroupStanding(FRIENDLY_FACTION, ANY_SCORE, List.of()),
                    new GroupStanding(RIVAL_FACTION, ANY_SCORE, List.of()),
                    new GroupStanding(Factions.NEUTRAL, ANY_SCORE, List.of())),
                List.of(
                    createLoneGroupEntry(),
                    createAlliedGroupEntry(),
                    createNamedGroupEntry("The Luddic Church"),
                    createNamedGroupEntry("Persean League"),
                    createPlaceholderGroupEntry()));

            assertThat(readSectionOpeningWords(
                    tooltip.buildBodySections(sectorMock, systemMock, PATROL_DETAILS)))
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Allied with the system holder:",
                    "Tri-Tachyon",
                    "Friendly with the system holder:",
                    "The Luddic Church",
                    "Contested by:",
                    "Persean League",
                    "Non-political:",
                    "Neutral");
        }

        @Test
        void buildBodySectionsListsEachGroupOnceWhereNoBlocCanBeOfTwoMinds() {
            // Under a grouping that makes every bloc a singleton there is nothing for a bloc's members
            // to disagree about, so none folds into two headings and none is stood up anew: the box
            // names exactly the groups the ranking handed it, once each. What a fold looks like when
            // one does happen is the routing's to pin.
            allianceSet = HolderGrouping.identity();

            stubFaction(sectorMock, FRIENDLY_FACTION, "The Luddic Church", MEMBER_CREST);
            stubDispositionToward(sectorMock, FRIENDLY_FACTION, CORE_FACTION, RepLevel.FAVORABLE);

            StandingsTooltipSeamsFake.stubRankedGroups(
                List.of(
                    new GroupStanding(CORE_FACTION, ANY_SCORE, List.of()),
                    new GroupStanding(FRIENDLY_FACTION, ANY_SCORE, List.of()),
                    new GroupStanding(RIVAL_FACTION, ANY_SCORE, List.of())),
                List.of(
                    createLoneGroupEntry(),
                    createNamedGroupEntry("The Luddic Church"),
                    createNamedGroupEntry("Persean League")));

            assertThat(readSectionOpeningWords(
                    tooltip.buildBodySections(sectorMock, systemMock, PATROL_DETAILS)))
                .containsExactly(
                    "Dominated by:",
                    "Rebel Pact",
                    "Friendly with the system holder:",
                    "The Luddic Church",
                    "Contested by:",
                    "Persean League");
        }

        @Test
        void buildBodySectionsFallsBackToTheSystemStatusWhenNothingRanks() {
            // An empty system says the same thing at every depth: there is no more detail to be had
            // about a system nobody holds.
            var statusRow = StandingsTooltipSeamsFake.stubStatusRow("Unpopulated");

            StandingsTooltipSeamsFake.stubGroupEntries();
            stubBreakdowns(Map.of());

            var sections = tooltip.buildBodySections(sectorMock, systemMock, PATROL_DETAILS);

            assertThat(sections)
                .hasSize(1);
            assertThat(sections.get(0).readRowsInOrder())
                .containsExactly(statusRow);
        }
    }

    @Nested
    class CreateFactionAccountResolver {

        @Test
        void createFactionAccountResolverAccountsForAFactionWithTheColoniesItHolds() {
            // The point of the deeper levels, and the one thing a bloc's line cannot state: a faction's
            // score is the sum over the colonies it holds here, so those are what its account lists -
            // and a sibling's colonies are the sibling's account, never this one's.
            stubBreakdowns(Map.of(
                "hegemony", List.of(buildBreakdown("Culann", 3.0)),
                "tritachyon", List.of(buildBreakdown("Eventide", 5.0))));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS);

            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(LEAD_MEMBER)))
                .containsExactly("Culann");
            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(OTHER_MEMBER)))
                .containsExactly("Eventide");
        }

        @Test
        void createFactionAccountResolverBreaksEachColonyDownIntoItsFactors() {
            // A colony's own line is a sum too, so the account goes one level further: the factors that
            // moved its weight hang beneath it rather than the number being left to be taken on trust.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            var colonyEntries = tooltip
                .createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS)
                .resolveAccountEntries(LEAD_MEMBER);

            assertThat(readLabelTexts(colonyEntries.get(0).children()))
                .containsExactly("Size");
        }

        @Test
        void createFactionAccountResolverAccountsForNothingWhereAFactionHoldsNoColonyHere() {
            // Nothing to account for reads as the faction listed by its line alone, which is exactly
            // what an empty answer means to the shape above - not a heading over an empty account.
            stubBreakdowns(Map.of("tritachyon", List.of(buildBreakdown("Eventide", 5.0))));

            assertThat(tooltip
                    .createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS)
                    .resolveAccountEntries(LEAD_MEMBER))
                .isEmpty();
        }

        @Test
        void createFactionAccountResolverAccountsForTheColoniesTheEconomyDoesNotList() {
            // The one colony no score above accounts for: vanilla builds it and never registers it,
            // so the weighed read cannot see it and the player is left looking at a station in a
            // faction's colours that the box says nothing about.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Culann", 3.0))));
            stubUnweighedColonies(Map.of(
                "hegemony",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS);

            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(LEAD_MEMBER)))
                .containsExactly("Culann", "Galatia Academy");
        }

        @Test
        void createFactionAccountResolverKeepsAnUnlistedColonyToTheFactionHoldingIt() {
            // The two reads are keyed the same way and paired the same way, so an unlisted colony
            // can no more be listed under a sibling's name than a weighed one can.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of(
                "tritachyon",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS);

            assertThat(accountResolver.resolveAccountEntries(LEAD_MEMBER))
                .isEmpty();
            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(OTHER_MEMBER)))
                .containsExactly("Galatia Academy");
        }

        @Test
        void createFactionAccountResolverAccountsForAFactionPresentThroughUnlistedColoniesAlone() {
            // A faction the weighing never reached: its every colony here is one the economy does
            // not list, so its whole account is the unlisted read. The colonies were always carried
            // - what they lacked was a line to hang from, which the ranking now gives them.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of(
                "tritachyon",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS);

            assertThat(readLabelTexts(accountResolver.resolveAccountEntries(
                    new PresenceOnlyFactionStanding("tritachyon"))))
                .containsExactly("Galatia Academy");
        }

        @Test
        void createFactionAccountResolverReadsTheColoniesUnderTheRankingsOwnPass() {
            // The parts have to be read under the rule and reveal the scores above them were ranked
            // through, or the box would explain a number with arithmetic that did not produce it.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            tooltip.createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS);

            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    Colonies.NONE,
                    ANY_RULES,
                    ANY_PASS.colonyKnowledge()));
        }

        @Test
        void createFactionAccountResolverReadsTheWeighedColoniesOnceForTheWholeBox() {
            // Every faction's colonies come out of one read of the pass's colony set: read per
            // faction, two of them could be explained from different selections over it, and the
            // walk behind that set is the most expensive thing a hover does.
            stubBreakdowns(Map.of("hegemony", List.of(buildBreakdown("Jangala", 6.0))));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS);

            accountResolver.resolveAccountEntries(LEAD_MEMBER);
            accountResolver.resolveAccountEntries(OTHER_MEMBER);

            // Counted on the real arguments rather than on matchers, since registering the stub is
            // itself an invocation and an any()-matched count would take it for a second read.
            footprintsMock.verify(
                () -> KnownMarketFootprints.readBreakdownByFaction(
                    Colonies.NONE,
                    ANY_RULES,
                    ANY_PASS.colonyKnowledge()),
                Mockito.times(1));
        }

        @Test
        void createFactionAccountResolverReadsTheUnlistedColoniesUnderTheRankingsOwnReveal() {
            // The reveal decides what the box may name, and a colony the pass could not weigh is no
            // exception. Read at a reveal of its own it would withhold an undiscovered station while
            // naming the weighed colonies beside it - or name one the player has not found.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of());

            tooltip.createFactionAccountResolver(systemMock, REVEALED_PASS, PATROL_DETAILS);

            footprintsMock.verify(
                () -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                    Colonies.NONE,
                    REVEALED_PASS.colonyKnowledge()));
        }

        @Test
        void createFactionAccountResolverReadsTheUnlistedColoniesOnceForTheWholeBox() {
            // The unlisted colonies are a second selection over the same set, made once per paint
            // rather than once per faction listed - the same rule the weighed read is held to, so
            // neither can end up describing a system the other did not.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of(
                "hegemony",
                List.of(UNLISTED_COLONY)));

            var accountResolver =
                tooltip.createFactionAccountResolver(systemMock, ANY_PASS, PATROL_DETAILS);

            accountResolver.resolveAccountEntries(LEAD_MEMBER);
            accountResolver.resolveAccountEntries(OTHER_MEMBER);

            // Counted on the real arguments rather than on matchers, since registering the stub is
            // itself an invocation and an any()-matched count would take it for a second read.
            footprintsMock.verify(
                () -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                    Colonies.NONE,
                    ANY_PASS.colonyKnowledge()),
                Mockito.times(1));
        }

        @Test
        void createFactionAccountResolverDatesTheColoniesAgainstThePassesOwnRegister() {
            // The whole reason the register is handed down rather than opened here: the dates
            // stated have to come off the very observations the pass resolved its projection
            // against. Read from the sector afresh, a box could date a colony against a register
            // the fills beside it were never drawn under - and nothing on screen would say which.
            stubBreakdowns(Map.of());
            stubUnweighedColonies(Map.of());

            // The case is worth nothing unless the pass carries a register that is not the empty
            // one, that being what a read opening its own would also find - so the premise is
            // asserted rather than assumed.
            assertThat(OBSERVED_PASS.colonyKnowledge().sightings())
                .isNotSameAs(ColonySightings.NONE);

            var sightings = ArgumentCaptor.forClass(ColonySightings.class);

            try (var notesMock = Mockito.mockStatic(ColonyObservationNotes.class)) {

                tooltip.createFactionAccountResolver(systemMock, OBSERVED_PASS, PATROL_DETAILS);

                notesMock.verify(() -> ColonyObservationNotes.readNotesFor(
                    any(),
                    any(),
                    any(),
                    sightings.capture()));
            }
            assertThat(sightings.getValue())
                .isSameAs(OBSERVED_PASS.colonyKnowledge().sightings());
        }
    }

    // The body read top to bottom as the lines a player sees, which is the shape these cases are about.
    private List<TooltipRow> readBodyRows() {
        return TooltipSection.readRowsInOrder(
            tooltip.buildBodySections(sectorMock, systemMock, PATROL_DETAILS));
    }

    // Stands the economy read in as the colonies each faction holds in the system, so no case needs a
    // live economy to produce parts for the box to open up.
    private void stubBreakdowns(Map<String, List<MarketWeightBreakdown>> breakdownsByFactionId) {
        footprintsMock
            .when(() -> KnownMarketFootprints.readBreakdownByFaction(
                any(),
                any(),
                any()))
            .thenReturn(breakdownsByFactionId);
    }

    // Stands in the second read, the colonies present that the economy does not list. Stubbed apart
    // from the weighed ones because that is how the box reads them: two selections over the one set,
    // so nothing an unlisted colony says can reach the pass.
    private void stubUnweighedColonies(Map<String, List<UnweighedColony>> coloniesByFactionId) {
        footprintsMock
            .when(() -> KnownMarketFootprints.readUnweighedColoniesByFaction(
                any(),
                any()))
            .thenReturn(coloniesByFactionId);
    }

    // One group as the resolver hands it over: a bloc carrying its crest and summed score, gathering
    // the factions in it as its peers, since a bloc and its members answer who holds the system at two
    // granularities rather than one accounting for the other. Whether a group gathers anything is the
    // resolver's decision, so a case here states it by handing over the members or none. A member
    // faction breaks down no further, so each is entered as an entry carrying nothing.
    private static CellTooltipEntry createGroupEntry(CellTooltipEntryLine... memberLines) {
        return CellTooltipEntry
            .createEntry(CellTooltipEntryLine.createLine(BLOC_MARK, "Rebel Pact", BLOC_SCORE))
            .grouping(Arrays
                .stream(memberLines)
                .map(CellTooltipEntry::createEntry)
                .toList());
    }

    // A group that breaks down no further - what a lone faction in the faction view resolves to.
    private static CellTooltipEntry createLoneGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(BLOC_MARK, "Rebel Pact", BLOC_SCORE));
    }

    // A second group named apart from the leader, for the case about the block an alliance routes it
    // into: its line is never read, only the heading it ends up under.
    private static CellTooltipEntry createAlliedGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(MEMBER_MARK, "Tri-Tachyon", ALLY_SCORE));
    }

    // Any further group, named apart from the rest so a case reading the box top to bottom tells the
    // blocks apart by the one line each of them holds.
    private static CellTooltipEntry createNamedGroupEntry(String name) {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(MEMBER_MARK, name, ALLY_SCORE));
    }

    // The placeholder owner as the resolver hands it over, named and scored apart from the leader so
    // the case about the block it lands in cannot pass by reading the leader's line twice.
    private static CellTooltipEntry createPlaceholderGroupEntry() {
        return CellTooltipEntry.createEntry(
            CellTooltipEntryLine.createLine(MEMBER_MARK, "Neutral", PLACEHOLDER_SCORE));
    }

    // One member faction beneath a bloc, told apart from its siblings by its score alone.
    private static CellTooltipEntryLine createMemberLine(String scoreText) {
        return CellTooltipEntryLine.createLine(MEMBER_MARK, "The Hegemony", scoreText);
    }

    // The sector behind OBSERVED_PASS: one derelict, met by the player, so a register entry really
    // lands and the read comes back as something other than the empty answer.
    private static DominancePass buildPassOverAnObservedSector() {

        // The staged pair is a derelict and a concealed base, which is exactly what the register
        // records - only a gated colony ever reaches it, and those are the two gated shapes.
        var sector = SectorScenarioFixtures.buildUnvisitedSectorHoldingGatedPair(SYSTEM_ID);

        SectorPoliticsFixtures.markSystemAsVisitedByPlayer(
            sector,
            SectorPoliticsFixtures.buildOnlySystem(sector));

        return DominancePass.over(sector, ANY_RULES, BASE_FOG, VIEW_GROUPING);
    }

    // One colony worth the given size points on its base size alone, so a case states a colony by the
    // one number it is ranked against its siblings by.
    private static MarketWeightBreakdown buildBreakdown(String marketName, double contribution) {
        return new MarketWeightBreakdown(
            marketName,
            EntityNameplate.createUnmarkedNameplate(marketName),
            VISIBLE_COLONY,
            PLANET_COLONY,
            FULL_STABILITY,
            new BaseSizeFactor(4, 4.0, contribution, 0.0),
            Optional.empty(),
            Optional.empty());
    }
}
