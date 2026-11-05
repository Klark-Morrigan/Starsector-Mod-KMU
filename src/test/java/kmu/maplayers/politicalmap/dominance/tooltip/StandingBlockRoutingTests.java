package kmu.maplayers.politicalmap.dominance.tooltip;

import kmlib.starsector.factions.alliances.FactionAlliances;
import kmlib.starsector.factions.relation.FactionSetFriendliness;

import kmu.maplayers.ownermap.owners.holders.alliances.BlocAffiliation;
import kmu.maplayers.ownermap.owners.holders.alliances.BlocRelations;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture;
import kmu.maplayers.politicalmap.dominance.standings.FactionStanding;
import kmu.maplayers.politicalmap.dominance.standings.GroupStanding;
import kmu.maplayers.politicalmap.dominance.standings.WeighedFactionStanding;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.buildAllianceOf;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where each of a hovered system's ranked groups is listed: the blocs out of the contest set
 * aside first, the bloc the fill was painted for holding the system, the rest placed either side of
 * it by alliance, and what alliance left standing against it sorted again by disposition - or, where
 * the fill was painted for no contender, everyone in the running listed as present.
 *
 * <p>Posed over hand-built standings under a bar and a disposition table stated here, since which
 * blocs a live box bars is {@link kmu.maplayers.politicalmap.dominance.BlocCandidacy}'s answer
 * and how warm two factions are is the sector's - what is pinned here is the order the axes are
 * applied in and what each of them takes.
 */
class StandingBlockRoutingTests {

    private static final String HOLDER_BLOC = "hegemony";
    private static final String ALLY_BLOC = "tritachyon";
    private static final String RIVAL_BLOC = "persean_league";
    private static final String OTHER_RIVAL_BLOC = "sindrian_diktat";

    // What the fill was painted for in a system nobody was weighed in.
    private static final String NO_PAINTED_BLOC = null;

    // The bloc the bar keeps out of the contest. Named for what it stands for in the sector - the
    // owner every abandoned station is handed to - so the cases read as the situation they are about.
    private static final String PLACEHOLDER_BLOC = "neutral";
    private static final String OTHER_PLACEHOLDER_BLOC = "derelict";

    // The factions a bloc is made of, for the cases about a bloc its members disagree on. The third
    // is in the alliance and holds nothing in the hovered system, which is where the whole-membership
    // test and the present-member listing part company.
    private static final String WARM_MEMBER = "luddic_church";
    private static final String SOUR_MEMBER = "pirates";
    private static final String ABSENT_MEMBER = "luddic_path";

    // The six factions of the worked case, three a side and lettered as the case is posed: A and E
    // are at odds and B and F are, so D is the rival alliance's one friendly member and E and F have
    // one quarrel each among the holder's three. Written as bare IDs because the routing resolves no
    // names - what the case is about is which quarrels fall where.
    private static final String HOLDER_MEMBER_A = "faction-a";
    private static final String HOLDER_MEMBER_B = "faction-b";
    private static final String HOLDER_MEMBER_C = "faction-c";
    private static final String RIVAL_MEMBER_D = "faction-d";
    private static final String RIVAL_MEMBER_E = "faction-e";
    private static final String RIVAL_MEMBER_F = "faction-f";

    private static final String HOLDER_ALLIANCE_BLOC = "holder-alliance";
    private static final String RIVAL_ALLIANCE_BLOC = "rival-alliance";

    // What bars a bloc, in the shape the routing takes it: a predicate over bloc IDs.
    private static final Predicate<String> IS_POLITICAL_BLOC = blocId ->
        !PLACEHOLDER_BLOC.equals(blocId) && !OTHER_PLACEHOLDER_BLOC.equals(blocId);

    // What a stood-up group is weighed at. The routing reads a group's bloc and its members alone,
    // so a score only ever states the order the groups arrived in and never reaches an assertion.
    private static final int ANY_SCORE = 0;

    @Nested
    class RouteRankedStandings {

        @Test
        void namesThePaintedBlocAsHoldingTheSystem() {

            // ARRANGE
            var expectedHolder = routeWhole(HOLDER_BLOC);

            // ACT
            var actualHolder = routeWithoutAlliances(HOLDER_BLOC, List.of(HOLDER_BLOC, RIVAL_BLOC))
                .selectStandingsIn(StandingBlock.HOLDER);

            // ASSERT
            assertThat(actualHolder)
                .containsExactly(expectedHolder);
        }

        // The tie the fix exists for: two blocs on equal combined weight rank by ID, while the fill
        // breaks that tie on the levels below it and can paint the one ranked second. The holder is
        // the bloc the fill was painted for, and everyone else keeps the order they ranked in.
        @Test
        void namesThePaintedBlocAsHoldingTheSystemWhereItRanksSecond() {

            // ARRANGE
            var expectedHolder = routeWhole(HOLDER_BLOC);
            var expectedContestedBlocIds = List.of(RIVAL_BLOC, OTHER_RIVAL_BLOC);

            // ACT
            var routing = routeWithoutAlliances(
                HOLDER_BLOC,
                List.of(RIVAL_BLOC, HOLDER_BLOC, OTHER_RIVAL_BLOC));
            var actualHolder = routing.selectStandingsIn(StandingBlock.HOLDER);
            var actualContestedBlocIds = readListedBlocs(routing, StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualHolder)
                .containsExactly(expectedHolder);
            assertThat(actualContestedBlocIds)
                .isEqualTo(expectedContestedBlocIds);
        }

        @Test
        void leavesEveryOtherBlocInTheRunningContestingTheSystem() {

            // ARRANGE
            var expectedContestedBlocIds = List.of(RIVAL_BLOC);

            // ACT
            var actualContestedBlocIds = readListedBlocs(
                routeWithoutAlliances(HOLDER_BLOC, List.of(HOLDER_BLOC, RIVAL_BLOC)),
                StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualContestedBlocIds)
                .isEqualTo(expectedContestedBlocIds);
        }

        // The split below the holder is the shared one, so a group is filed here by the rule the
        // band beneath the cell lays its runs at contested length by.
        @Test
        void liftsTheHoldersOwnAllyOutOfTheContestedBlock() {

            // ARRANGE
            var rankedStandings = createRankedStandings(List.of(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC));
            var rules = buildRules(
                buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                HolderGrouping.identity(),
                List.of());

            var expectedAllied = routeWhole(ALLY_BLOC);
            var expectedContestedBlocIds = List.of(RIVAL_BLOC);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, HOLDER_BLOC, rules);
            var actualAllied = routing.selectStandingsIn(StandingBlock.ALLIED);
            var actualContestedBlocIds = readListedBlocs(routing, StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualAllied)
                .containsExactly(expectedAllied);
            assertThat(actualContestedBlocIds)
                .isEqualTo(expectedContestedBlocIds);
        }

        // The bar is the outer axis: a placeholder owner ranking above everybody is still no
        // contender, so it lands in its own block rather than at the head of the contest.
        @Test
        void setsABlocOutOfTheRunningAsideWhateverItOutranked() {

            // ARRANGE
            var expectedNonPolitical = routeWhole(PLACEHOLDER_BLOC);

            // ACT
            var actualNonPolitical = routeWithoutAlliances(HOLDER_BLOC, List.of(PLACEHOLDER_BLOC, HOLDER_BLOC))
                .selectStandingsIn(StandingBlock.NON_POLITICAL);

            // ASSERT
            assertThat(actualNonPolitical)
                .containsExactly(expectedNonPolitical);
        }

        // The placeholder ranks first and the fill is painted for the bloc behind it, which is the
        // shape the fill's own bar produces: barred blocs are passed over while anybody else stands.
        @Test
        void namesThePaintedBlocAsHoldingASystemAPlaceholderOutranks() {

            // ARRANGE
            var expectedHolder = routeWhole(HOLDER_BLOC);

            // ACT
            var actualHolder = routeWithoutAlliances(
                    HOLDER_BLOC,
                    List.of(PLACEHOLDER_BLOC, HOLDER_BLOC, RIVAL_BLOC))
                .selectStandingsIn(StandingBlock.HOLDER);

            // ASSERT
            assertThat(actualHolder)
                .containsExactly(expectedHolder);
        }

        // The one place the box and the fill part company: the map still paints, borders and labels
        // the cell for the placeholder, while the box heads no block with it - and an empty holder
        // block is what drops that heading rather than leaving it over nothing.
        @Test
        void leavesNobodyHoldingASystemOnlyAPlaceholderStandsIn() {

            // ARRANGE
            var expectedNonPolitical = routeWhole(PLACEHOLDER_BLOC);

            // ACT
            var routing = routeWithoutAlliances(PLACEHOLDER_BLOC, List.of(PLACEHOLDER_BLOC));
            var actualHolder = routing.selectStandingsIn(StandingBlock.HOLDER);
            var actualNonPolitical = routing.selectStandingsIn(StandingBlock.NON_POLITICAL);

            // ASSERT
            assertThat(actualHolder)
                .isEmpty();
            assertThat(actualNonPolitical)
                .containsExactly(expectedNonPolitical);
        }

        // A fill painted for a barred bloc while others are present: the placeholder outweighed a
        // bloc present only through colonies the economy does not list. The box names nobody as
        // holding the system, so the bloc beside the placeholder is present and nothing more.
        @Test
        void listsTheContendersAsPresentWhereTheFillWasPaintedForABarredBloc() {

            // ARRANGE
            var expectedPresentBlocIds = List.of(HOLDER_BLOC, RIVAL_BLOC);
            var expectedNonPolitical = routeWhole(PLACEHOLDER_BLOC);

            // ACT
            var routing = routeWithoutAlliances(
                PLACEHOLDER_BLOC,
                List.of(PLACEHOLDER_BLOC, HOLDER_BLOC, RIVAL_BLOC));
            var actualHolder = routing.selectStandingsIn(StandingBlock.HOLDER);
            var actualPresentBlocIds = readListedBlocs(routing, StandingBlock.PRESENT);
            var actualContested = routing.selectStandingsIn(StandingBlock.CONTESTED);
            var actualNonPolitical = routing.selectStandingsIn(StandingBlock.NON_POLITICAL);

            // ASSERT
            assertThat(actualHolder)
                .isEmpty();
            assertThat(actualPresentBlocIds)
                .isEqualTo(expectedPresentBlocIds);
            assertThat(actualContested)
                .isEmpty();
            assertThat(actualNonPolitical)
                .containsExactly(expectedNonPolitical);
        }

        // A fill painted for nobody, where nobody was weighed: every bloc there holds only colonies
        // the economy does not list. Posed with an ally and a friendly bloc beside the first-ranked
        // one, because none of them has a holder to stand with - routed against one, they would be
        // filed as its ally, its friend and its rival.
        @Test
        void listsEveryContenderAsPresentAndNothingElseWhereTheFillWasPaintedForNobody() {

            // ARRANGE
            var rankedStandings = createRankedStandings(List.of(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC));
            var rules = buildRules(
                buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                HolderGrouping.identity(),
                List.of(RIVAL_BLOC + ":" + HOLDER_BLOC));

            var expectedPresentBlocIds = List.of(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, NO_PAINTED_BLOC, rules);
            var actualPresentBlocIds = readListedBlocs(routing, StandingBlock.PRESENT);
            var actualHolder = routing.selectStandingsIn(StandingBlock.HOLDER);
            var actualAllied = routing.selectStandingsIn(StandingBlock.ALLIED);
            var actualFriendly = routing.selectStandingsIn(StandingBlock.FRIENDLY);
            var actualContested = routing.selectStandingsIn(StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualPresentBlocIds)
                .isEqualTo(expectedPresentBlocIds);
            assertThat(actualHolder)
                .isEmpty();
            assertThat(actualAllied)
                .isEmpty();
            assertThat(actualFriendly)
                .isEmpty();
            assertThat(actualContested)
                .isEmpty();
        }

        // The block is for a system with no holder alone. Where somebody holds it, everyone else
        // stands with or against them instead.
        @Test
        void leavesThePresentBlockEmptyWhereSomebodyHoldsTheSystem() {

            // ACT
            var actualPresent = routeWithoutAlliances(HOLDER_BLOC, List.of(HOLDER_BLOC, RIVAL_BLOC))
                .selectStandingsIn(StandingBlock.PRESENT);

            // ASSERT
            assertThat(actualPresent)
                .isEmpty();
        }

        // Standing with the holder is a question about the contest, which a barred bloc is outside
        // of - so no alliance set can lift it back in one axis further down.
        @Test
        void keepsABlocOutOfTheRunningOutOfTheAlliedBlock() {

            // ARRANGE
            var rankedStandings = createRankedStandings(List.of(HOLDER_BLOC, PLACEHOLDER_BLOC));
            var rules = buildRules(
                buildAllianceOf(HOLDER_BLOC, PLACEHOLDER_BLOC),
                HolderGrouping.identity(),
                List.of());

            var expectedNonPolitical = routeWhole(PLACEHOLDER_BLOC);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, HOLDER_BLOC, rules);
            var actualAllied = routing.selectStandingsIn(StandingBlock.ALLIED);
            var actualNonPolitical = routing.selectStandingsIn(StandingBlock.NON_POLITICAL);

            // ASSERT
            assertThat(actualAllied)
                .isEmpty();
            assertThat(actualNonPolitical)
                .containsExactly(expectedNonPolitical);
        }

        // The invariant the axes have to leave intact between them: every ranked group is placed, and
        // only a bloc its members disagree about is placed twice. A group taken by no block
        // disappears from a box that ranked it, and one taken by two that nothing split is listed
        // under two headings contradicting each other - neither of which the block set being closed
        // says anything about. Posed with nothing to split, so what is read here is the partition
        // the other axes leave.
        @Test
        void placesEveryRankedGroupInExactlyOneBlock() {

            // ARRANGE
            var rankedStandings = createRankedStandings(List.of(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC, PLACEHOLDER_BLOC));
            var rules = buildRules(
                buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                HolderGrouping.identity(),
                List.of());

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, HOLDER_BLOC, rules);

            // Read as the blocs listed rather than as the rows listing them: what a block qualifies
            // its heading with is the routing's own answer and pinned by the cases about each block,
            // while what this one is about is that no bloc is lost between them or listed twice.
            var actualListedBlocIds = Arrays
                .stream(StandingBlock.values())
                .flatMap(block -> routing.selectStandingsIn(block).stream())
                .map(routed -> routed.standing().blocId())
                .toList();

            // ASSERT
            assertThat(actualListedBlocIds)
                .containsExactlyInAnyOrder(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC, PLACEHOLDER_BLOC);
        }

        // The groups arrive strongest first and a block reads in that order rather than in one the
        // partition invented, so the box lists them exactly as the fills rank them.
        @Test
        void keepsTheOrderTheGroupsRankedInInsideABlock() {

            // ARRANGE
            var expectedNonPolitical = List.of(
                routeWhole(PLACEHOLDER_BLOC),
                routeWhole(OTHER_PLACEHOLDER_BLOC));

            // ACT
            var actualNonPolitical = routeWithoutAlliances(
                    HOLDER_BLOC,
                    List.of(HOLDER_BLOC, PLACEHOLDER_BLOC, OTHER_PLACEHOLDER_BLOC))
                .selectStandingsIn(StandingBlock.NON_POLITICAL);

            // ASSERT
            assertThat(actualNonPolitical)
                .isEqualTo(expectedNonPolitical);
        }

        // The block the disposition axis exists for: a bloc the holder is on excellent terms with
        // has no quarrel over the system, and filed under the contested heading the box would report
        // a fight neither side is in.
        @Test
        void liftsABlocOnGoodTermsWithTheHolderOutOfTheContestedBlock() {

            // ARRANGE
            var expectedFriendlyBlocIds = List.of(RIVAL_BLOC);

            // ACT
            var routing = routeUnderDispositions(
                List.of(RIVAL_BLOC + ":" + HOLDER_BLOC),
                HOLDER_BLOC,
                List.of(HOLDER_BLOC, RIVAL_BLOC));
            var actualFriendlyBlocIds = readListedBlocs(routing, StandingBlock.FRIENDLY);
            var actualContested = routing.selectStandingsIn(StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualFriendlyBlocIds)
                .isEqualTo(expectedFriendlyBlocIds);
            assertThat(actualContested)
                .isEmpty();
        }

        // The scale's own zero is the cut: indifference is not goodwill, so a bloc at or below
        // neutral stays exactly where it was before the block existed.
        @Test
        void leavesAnIndifferentBlocContestingTheSystem() {

            // ARRANGE
            var expectedContestedBlocIds = List.of(RIVAL_BLOC);

            // ACT
            var actualContestedBlocIds = readListedBlocs(
                routeUnderDispositions(List.of(), HOLDER_BLOC, List.of(HOLDER_BLOC, RIVAL_BLOC)),
                StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualContestedBlocIds)
                .isEqualTo(expectedContestedBlocIds);
        }

        // Alliance is the outer axis and disposition never re-sorts what it took, so an ally on
        // excellent terms gains nothing by it - and an ally gone sour loses nothing either.
        @Test
        void keepsTheHoldersAllyAlliedWhateverItsDisposition() {

            // ARRANGE
            var rankedStandings = createRankedStandings(List.of(HOLDER_BLOC, ALLY_BLOC));
            var rules = buildRules(
                buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                HolderGrouping.identity(),
                List.of(ALLY_BLOC + ":" + HOLDER_BLOC));

            var expectedAllied = routeWhole(ALLY_BLOC);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, HOLDER_BLOC, rules);
            var actualAllied = routing.selectStandingsIn(StandingBlock.ALLIED);
            var actualFriendly = routing.selectStandingsIn(StandingBlock.FRIENDLY);

            // ASSERT
            assertThat(actualAllied)
                .containsExactly(expectedAllied);
            assertThat(actualFriendly)
                .isEmpty();
        }

        // The alliances view's own shape, which no other case here poses: the holder is the alliance
        // bloc the fills were painted for, so the membership the rival is measured against is read
        // out of the fold rather than being the holder's own ID.
        @Test
        void liftsABlocOnGoodTermsWithEveryMemberOfAnAllianceHolder() {

            // ARRANGE
            var rankedStandings = List.of(
                new GroupStanding(
                    HolderGroupingFixture.GROUP_BLOC_ID,
                    ANY_SCORE,
                    List.of(new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE))),
                createGroupStanding(RIVAL_BLOC));
            var rules = buildRules(
                buildAllianceOf(WARM_MEMBER, ABSENT_MEMBER),
                HolderGroupingFixture.buildGroupOf(WARM_MEMBER, ABSENT_MEMBER),
                List.of(
                    RIVAL_BLOC + ":" + WARM_MEMBER,
                    RIVAL_BLOC + ":" + ABSENT_MEMBER));

            var expectedFriendlyBlocIds = List.of(RIVAL_BLOC);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(
                rankedStandings,
                HolderGroupingFixture.GROUP_BLOC_ID,
                rules);
            var actualFriendlyBlocIds = readListedBlocs(routing, StandingBlock.FRIENDLY);
            var actualContested = routing.selectStandingsIn(StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualFriendlyBlocIds)
                .isEqualTo(expectedFriendlyBlocIds);
            assertThat(actualContested)
                .isEmpty();
        }

        // Why the test is over the whole membership rather than over who happens to stand here: read
        // against the present member alone the bloc would come out friendly, and the same two blocs
        // would then read friendly over this system and contesting over the next on nothing but which
        // of them had a colony where.
        @Test
        void contestsABlocWhoseSourMemberHoldsNothingInTheSystem() {

            // ARRANGE
            var rankedStandings = List.of(
                createGroupStanding(HOLDER_BLOC),
                new GroupStanding(
                    HolderGroupingFixture.GROUP_BLOC_ID,
                    ANY_SCORE,
                    List.of(new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE))));
            var rules = buildRules(
                FactionAlliances.NONE,
                HolderGroupingFixture.buildGroupOf(WARM_MEMBER, SOUR_MEMBER),
                List.of(WARM_MEMBER + ":" + HOLDER_BLOC));

            var expectedContestedBlocIds = List.of(HolderGroupingFixture.GROUP_BLOC_ID);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, HOLDER_BLOC, rules);
            var actualFriendly = routing.selectStandingsIn(StandingBlock.FRIENDLY);
            var actualContestedBlocIds = readListedBlocs(routing, StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualFriendly)
                .isEmpty();
            assertThat(actualContestedBlocIds)
                .isEqualTo(expectedContestedBlocIds);
        }

        // A bloc half of which is warm and half sour is true of neither heading whole, so it is
        // listed under both rather than broken up into loose factions: the bloc stays one named
        // thing, and each of its rows holds only the members on that side.
        @Test
        void listsABlocThatIsNotOfOneMindUnderBothHeadings() {

            // ARRANGE
            var expectedFriendlyMembers = List.of(WARM_MEMBER);
            var expectedContestedMembers = List.of(SOUR_MEMBER);

            // ACT
            var routing = routeMixedBlocAgainstTheHolder();
            var actualFriendlyMembers = readListedMembers(routing, StandingBlock.FRIENDLY);
            var actualContestedMembers = readListedMembers(routing, StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualFriendlyMembers)
                .isEqualTo(expectedFriendlyMembers);
            assertThat(actualContestedMembers)
                .isEqualTo(expectedContestedMembers);
        }

        // What keeps neither heading overreaching. The fraction is over the bloc's whole roster -
        // three members, of which one is warm - and not over the two standing here, which is what
        // makes one alliance read the same over every system it holds.
        @Test
        void statesHowMuchOfASplitBlocEachHeadingTook() {

            // ARRANGE
            var expectedFriendlyFraction = new StandingFraction(1, 3);
            var expectedContestedFraction = new StandingFraction(2, 3);

            // ACT
            var routing = routeMixedBlocAgainstTheHolder();
            var actualFriendlyFraction = readBlocFraction(routing, StandingBlock.FRIENDLY);
            var actualContestedFraction = readBlocFraction(routing, StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualFriendlyFraction)
                .isEqualTo(expectedFriendlyFraction);
            assertThat(actualContestedFraction)
                .isEqualTo(expectedContestedFraction);
        }

        // The two readings part exactly here: the fraction is over the whole membership, while what
        // is listed is who stands in the hovered system - the box having never listed anybody else.
        // So the rows and the fraction do not add up, and are not meant to. The holder is listed
        // too, standing through its one member.
        @Test
        void listsOnlyThePresentMembersOfABlocThatIsNotOfOneMind() {

            // ACT
            var routing = routeMixedBlocAgainstTheHolder();
            var actualListedMembers = Arrays
                .stream(StandingBlock.values())
                .flatMap(block -> readListedMembers(routing, block).stream())
                .toList();

            // ASSERT
            assertThat(actualListedMembers)
                .containsExactlyInAnyOrder(HOLDER_BLOC, WARM_MEMBER, SOUR_MEMBER);
        }

        // Both are placed by membership rather than by relation, so a bloc a rival stands warm
        // toward half of is not thereby split: its members are listed under it because they are its
        // members, and nothing about relations is asserted of them.
        @Test
        void splitsNeitherTheHoldingBlocNorAnAlliedOne() {

            // ARRANGE
            var holdingBloc = new GroupStanding(
                HolderGroupingFixture.GROUP_BLOC_ID,
                ANY_SCORE,
                List.of(
                    new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE),
                    new WeighedFactionStanding(SOUR_MEMBER, ANY_SCORE)));

            var rankedStandings = List.of(
                holdingBloc,
                createGroupStanding(ALLY_BLOC),
                createGroupStanding(RIVAL_BLOC));
            var rules = buildRules(
                buildAllianceOf(
                    HolderGroupingFixture.GROUP_BLOC_ID,
                    ALLY_BLOC),
                HolderGroupingFixture.buildGroupOf(WARM_MEMBER, SOUR_MEMBER),
                List.of(RIVAL_BLOC + ":" + WARM_MEMBER));

            var expectedHolder = RoutedStanding.routeWhole(holdingBloc);
            var expectedAllied = routeWhole(ALLY_BLOC);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(
                rankedStandings,
                HolderGroupingFixture.GROUP_BLOC_ID,
                rules);
            var actualHolder = routing.selectStandingsIn(StandingBlock.HOLDER);
            var actualAllied = routing.selectStandingsIn(StandingBlock.ALLIED);

            // ASSERT
            assertThat(actualHolder)
                .containsExactly(expectedHolder);
            assertThat(actualAllied)
                .containsExactly(expectedAllied);
        }

        // The one shape where a row's own reading and its members' are both live: an alliance of
        // three against an alliance of three. The bloc's rows count its own membership - one
        // friendly, two not - while each member's counts how much of the holder it quarrels with,
        // which is one of three apiece.
        @Test
        void statesBothFractionReadingsOverTwoAlliancesAtOdds() {

            // ARRANGE
            var expectedFriendlyBlocFraction = new StandingFraction(1, 3);
            var expectedContestedBlocFraction = new StandingFraction(2, 3);
            var expectedFriendlyMemberFraction = new StandingFraction(0, 3);
            var expectedContestedMemberFraction = new StandingFraction(1, 3);

            // ACT
            var routing = routeTwoAlliancesAtOdds();
            var actualFriendlyBlocFraction = readBlocFraction(routing, StandingBlock.FRIENDLY);
            var actualContestedBlocFraction = readBlocFraction(routing, StandingBlock.CONTESTED);
            var actualFriendlyFractionOfD = readMemberFraction(routing, StandingBlock.FRIENDLY, RIVAL_MEMBER_D);
            var actualContestedFractionOfE = readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_E);
            var actualContestedFractionOfF = readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_F);

            // ASSERT
            assertThat(actualFriendlyBlocFraction)
                .isEqualTo(expectedFriendlyBlocFraction);
            assertThat(actualContestedBlocFraction)
                .isEqualTo(expectedContestedBlocFraction);
            assertThat(actualFriendlyFractionOfD)
                .isEqualTo(expectedFriendlyMemberFraction);
            assertThat(actualContestedFractionOfE)
                .isEqualTo(expectedContestedMemberFraction);
            assertThat(actualContestedFractionOfF)
                .isEqualTo(expectedContestedMemberFraction);
        }

        @Test
        void listsEachSideOfTwoAlliancesAtOddsUnderItsOwnHeading() {

            // ARRANGE
            var expectedFriendlyMembers = List.of(RIVAL_MEMBER_D);
            var expectedContestedMembers = List.of(RIVAL_MEMBER_E, RIVAL_MEMBER_F);

            // ACT
            var routing = routeTwoAlliancesAtOdds();
            var actualFriendlyMembers = readListedMembers(routing, StandingBlock.FRIENDLY);
            var actualContestedMembers = readListedMembers(routing, StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualFriendlyMembers)
                .isEqualTo(expectedFriendlyMembers);
            assertThat(actualContestedMembers)
                .isEqualTo(expectedContestedMembers);
        }

        // The reading a faction's row states rather than a bloc's: how much of the holder it
        // quarrels with. It is the only fraction a lone faction has to state, its own bloc being one
        // member that could count nothing but nought or the whole.
        @Test
        void countsTheHoldersMembersOnALoneFactionStandingAgainstABloc() {

            // ARRANGE
            var rankedStandings = List.of(
                new GroupStanding(
                    HOLDER_ALLIANCE_BLOC,
                    ANY_SCORE,
                    List.of(new WeighedFactionStanding(HOLDER_MEMBER_A, ANY_SCORE))),
                new GroupStanding(
                    RIVAL_BLOC,
                    ANY_SCORE,
                    List.of(new WeighedFactionStanding(RIVAL_BLOC, ANY_SCORE))));
            var rules = buildRules(
                buildTwoAllianceMemberships(),
                buildTwoAlliances(),
                List.of(
                    RIVAL_BLOC + ":" + HOLDER_MEMBER_B,
                    RIVAL_BLOC + ":" + HOLDER_MEMBER_C));

            var expectedMemberFraction = new StandingFraction(1, 3);
            var expectedBlocFraction = new StandingFraction(1, 1);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, HOLDER_ALLIANCE_BLOC, rules);
            var actualMemberFraction = readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_BLOC);
            var actualBlocFraction = readBlocFraction(routing, StandingBlock.CONTESTED);

            // ASSERT
            assertThat(actualMemberFraction)
                .isEqualTo(expectedMemberFraction);
            assertThat(actualBlocFraction)
                .isEqualTo(expectedBlocFraction);
        }

        // A bloc none of whose members is friendly is listed whole, and its row states nothing - the
        // heading took all of it. Its members still state their own counts, each quarrelling with a
        // different one of the holder's three, which is what shows a member's number is not a part
        // of the bloc's.
        @Test
        void countsEachMemberOfAnUnsplitBlocAgainstTheHolderSeparately() {

            // ARRANGE
            var rankedStandings = List.of(
                new GroupStanding(
                    HOLDER_ALLIANCE_BLOC,
                    ANY_SCORE,
                    List.of(new WeighedFactionStanding(HOLDER_MEMBER_A, ANY_SCORE))),
                new GroupStanding(
                    RIVAL_ALLIANCE_BLOC,
                    ANY_SCORE,
                    List.of(
                        new WeighedFactionStanding(RIVAL_MEMBER_D, ANY_SCORE),
                        new WeighedFactionStanding(RIVAL_MEMBER_E, ANY_SCORE))));
            var rules = buildRules(
                buildTwoAllianceMemberships(),
                buildTwoAlliances(),
                List.of(
                    RIVAL_MEMBER_D + ":" + HOLDER_MEMBER_B,
                    RIVAL_MEMBER_D + ":" + HOLDER_MEMBER_C,
                    RIVAL_MEMBER_E + ":" + HOLDER_MEMBER_A,
                    RIVAL_MEMBER_E + ":" + HOLDER_MEMBER_C,
                    RIVAL_MEMBER_F + ":" + HOLDER_MEMBER_A));

            var expectedBlocFraction = new StandingFraction(3, 3);
            var expectedMemberFraction = new StandingFraction(1, 3);

            // ACT
            var routing = StandingBlockRouting.routeRankedStandings(rankedStandings, HOLDER_ALLIANCE_BLOC, rules);
            var actualBlocFraction = readBlocFraction(routing, StandingBlock.CONTESTED);
            var actualFractionOfD = readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_D);
            var actualFractionOfE = readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_E);

            // ASSERT
            assertThat(actualBlocFraction)
                .isEqualTo(expectedBlocFraction);
            assertThat(actualFractionOfD)
                .isEqualTo(expectedMemberFraction);
            assertThat(actualFractionOfE)
                .isEqualTo(expectedMemberFraction);
        }
    }

    @Nested
    class HasAnyStanding {

        // What the box asks to decide whether it has anything to list at all. A bloc out of the
        // running is still somebody standing in the system, so a system holding only the placeholder
        // is not an empty one.
        @Test
        void countsAGroupInEveryBlockAlike() {

            // ACT
            var actualHasAnyStanding = routeWithoutAlliances(PLACEHOLDER_BLOC, List.of(PLACEHOLDER_BLOC))
                .hasAnyStanding();

            // ASSERT
            assertThat(actualHasAnyStanding)
                .isTrue();
        }

        @Test
        void answersNoForASystemTheRankingFoundNobodyIn() {

            // ACT
            var actualHasAnyStanding = routeWithoutAlliances(NO_PAINTED_BLOC, List.of())
                .hasAnyStanding();

            // ASSERT
            assertThat(actualHasAnyStanding)
                .isFalse();
        }
    }

    // The blocs one block lists, in the order it placed them. What most cases read, since which block
    // a bloc lands in is what the axes decide and what it counts is a separate question - asserted on
    // whole routed values, a placement case would break on any change to either.
    private static List<String> readListedBlocs(StandingBlockRouting routing, StandingBlock block) {

        return routing
            .selectStandingsIn(block)
            .stream()
            .map(routed -> routed.standing().blocId())
            .toList();
    }

    // The factions one block lists beneath the bloc it placed there, in the order it placed them.
    // Every case about a split reads this rather than whole routed values, since what is being asked
    // is which members went where and not how a group is stood up.
    private static List<String> readListedMembers(
            StandingBlockRouting routing,
            StandingBlock block) {

        return routing
            .selectStandingsIn(block)
            .stream()
            .flatMap(routed -> routed.standing().members().stream())
            .map(FactionStanding::factionId)
            .toList();
    }

    // How far one block's heading reached over the single bloc it placed there.
    private static StandingFraction readBlocFraction(
            StandingBlockRouting routing,
            StandingBlock block) {

        return routing
            .selectStandingsIn(block)
            .get(0)
            .fraction();
    }

    // And how far it reached over one of that bloc's members, which counts the other membership.
    private static StandingFraction readMemberFraction(
            StandingBlockRouting routing,
            StandingBlock block,
            String factionId) {

        return routing
            .selectStandingsIn(block)
            .get(0)
            .readFractionFor(factionId);
    }

    // Two alliances of three standing against each other, with A and E at odds and B and F at odds.
    // The worked case: the rival's three members are all present and split one to two, so a row's own
    // fraction and its members' are both live over one bloc.
    private static StandingBlockRouting routeTwoAlliancesAtOdds() {

        var alliances = buildTwoAlliances();

        return StandingBlockRouting.routeRankedStandings(
            List.of(
                new GroupStanding(
                    HOLDER_ALLIANCE_BLOC,
                    ANY_SCORE,
                    List.of(new WeighedFactionStanding(HOLDER_MEMBER_A, ANY_SCORE))),
                new GroupStanding(
                    RIVAL_ALLIANCE_BLOC,
                    ANY_SCORE,
                    List.of(
                        new WeighedFactionStanding(RIVAL_MEMBER_D, ANY_SCORE),
                        new WeighedFactionStanding(RIVAL_MEMBER_E, ANY_SCORE),
                        new WeighedFactionStanding(RIVAL_MEMBER_F, ANY_SCORE)))),
            HOLDER_ALLIANCE_BLOC,
            buildRules(
                buildTwoAllianceMemberships(),
                alliances,
                List.of(
                    RIVAL_MEMBER_D + ":" + HOLDER_MEMBER_A,
                    RIVAL_MEMBER_D + ":" + HOLDER_MEMBER_B,
                    RIVAL_MEMBER_D + ":" + HOLDER_MEMBER_C,
                    RIVAL_MEMBER_E + ":" + HOLDER_MEMBER_B,
                    RIVAL_MEMBER_E + ":" + HOLDER_MEMBER_C,
                    RIVAL_MEMBER_F + ":" + HOLDER_MEMBER_A,
                    RIVAL_MEMBER_F + ":" + HOLDER_MEMBER_C)));
    }

    // The fold behind that case: two blocs of three, which the shared fixture cannot pose - it builds
    // one alliance and leaves everyone else standing alone.
    private static HolderGrouping buildTwoAlliances() {

        return new HolderGrouping(
            Map.of(
                HOLDER_MEMBER_A, HOLDER_ALLIANCE_BLOC,
                HOLDER_MEMBER_B, HOLDER_ALLIANCE_BLOC,
                HOLDER_MEMBER_C, HOLDER_ALLIANCE_BLOC,
                RIVAL_MEMBER_D, RIVAL_ALLIANCE_BLOC,
                RIVAL_MEMBER_E, RIVAL_ALLIANCE_BLOC,
                RIVAL_MEMBER_F, RIVAL_ALLIANCE_BLOC),
            Map.of(
                HOLDER_ALLIANCE_BLOC, HOLDER_MEMBER_A,
                RIVAL_ALLIANCE_BLOC, RIVAL_MEMBER_D),
            Map.of(
                HOLDER_ALLIANCE_BLOC, "First Alliance",
                RIVAL_ALLIANCE_BLOC, "Second Alliance"));
    }

    // The same two alliances as who stands with whom, which is what the allied block is lifted by.
    private static FactionAlliances buildTwoAllianceMemberships() {

        return new FactionAlliances(Map.of(
            HOLDER_MEMBER_A, HOLDER_ALLIANCE_BLOC,
            HOLDER_MEMBER_B, HOLDER_ALLIANCE_BLOC,
            HOLDER_MEMBER_C, HOLDER_ALLIANCE_BLOC,
            RIVAL_MEMBER_D, RIVAL_ALLIANCE_BLOC,
            RIVAL_MEMBER_E, RIVAL_ALLIANCE_BLOC,
            RIVAL_MEMBER_F, RIVAL_ALLIANCE_BLOC));
    }

    // A bloc of two standing against the holder, one member warm toward it and the other not, with
    // both present in the system. The case both halves of the split rule are read off, so the two of
    // them are posed once rather than twice over inputs free to drift apart.
    private static StandingBlockRouting routeMixedBlocAgainstTheHolder() {

        return StandingBlockRouting.routeRankedStandings(
            List.of(
                createGroupStanding(HOLDER_BLOC),
                new GroupStanding(
                    HolderGroupingFixture.GROUP_BLOC_ID,
                    ANY_SCORE,
                    List.of(
                        new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE),
                        new WeighedFactionStanding(SOUR_MEMBER, ANY_SCORE)))),
            HOLDER_BLOC,
            buildRules(
                FactionAlliances.NONE,
                HolderGroupingFixture.buildGroupOf(WARM_MEMBER, SOUR_MEMBER, ABSENT_MEMBER),
                List.of(WARM_MEMBER + ":" + HOLDER_BLOC)));
    }

    // The ranking as an install with nothing grouping factions and nobody on good terms produces it:
    // no two blocs stand together and none is warm toward another, which is the state every case not
    // about those two axes is posed in.
    private static StandingBlockRouting routeWithoutAlliances(
            String paintedBlocId,
            List<String> rankedBlocIds) {

        return StandingBlockRouting.routeRankedStandings(
            createRankedStandings(rankedBlocIds),
            paintedBlocId,
            buildRules(FactionAlliances.NONE, HolderGrouping.identity(), List.of()));
    }

    // The same with a disposition table, for a case about who is warm toward whom rather than about
    // who is allied to whom.
    private static StandingBlockRouting routeUnderDispositions(
            List<String> aboveNeutralPairs,
            String paintedBlocId,
            List<String> rankedBlocIds) {

        return StandingBlockRouting.routeRankedStandings(
            createRankedStandings(rankedBlocIds),
            paintedBlocId,
            buildRules(FactionAlliances.NONE, HolderGrouping.identity(), aboveNeutralPairs));
    }

    // The four rules a case poses: the bar, which every case here shares, and the three it varies -
    // the alliance set the allied block is lifted by, the fold a bloc's membership is read out of,
    // and which factions are above neutral with which, each pair written "<faction>:<other>".
    private static StandingBlockRules buildRules(
            FactionAlliances allianceSet,
            HolderGrouping membershipFold,
            List<String> aboveNeutralPairs) {

        // Read one way round, every rule here asking its pairs from the faction being sorted - so a
        // table stating what a rival thinks of the holder settles which side that rival takes and
        // both the counts stated on its row alike.
        var friendliness = new FactionSetFriendliness((factionId, otherFactionId) ->
            aboveNeutralPairs.contains(factionId + ":" + otherFactionId));

        return new StandingBlockRules(
            IS_POLITICAL_BLOC,
            new BlocRelations(new BlocAffiliation(allianceSet), friendliness),
            membershipFold::resolveMemberFactionIds);
    }

    private static List<GroupStanding> createRankedStandings(List<String> rankedBlocIds) {

        return rankedBlocIds
            .stream()
            .map(StandingBlockRoutingTests::createGroupStanding)
            .toList();
    }

    // One ranked group as the routing places it: the bloc it is, standing through a lone member of
    // the same ID. What it is weighed at and made up of are the naming's business, so which block it
    // falls in turns on the bloc alone wherever the case is not about a bloc's members disagreeing.
    private static GroupStanding createGroupStanding(String blocId) {
        return new GroupStanding(blocId, ANY_SCORE, List.of(new WeighedFactionStanding(blocId, ANY_SCORE)));
    }

    // That same group as a block placed by membership lists it: nothing qualifies the heading over
    // it, so its row states no fraction and neither does anything beneath it.
    private static RoutedStanding routeWhole(String blocId) {
        return RoutedStanding.routeWhole(createGroupStanding(blocId));
    }
}
