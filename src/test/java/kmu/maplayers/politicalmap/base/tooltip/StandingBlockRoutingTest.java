package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.BlocFriendliness;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture;
import kmu.maplayers.politicalmap.base.dominance.StandingFraction;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where each of a hovered system's ranked groups is listed: the blocs out of the contest set
 * aside first, the strongest of what remains holding the system, the rest placed either side of it
 * by alliance, and what alliance left standing against it sorted again by disposition.
 *
 * <p>Posed over hand-built standings under a bar and a disposition table stated here, since which
 * blocs a live box bars is {@link kmu.maplayers.politicalmap.base.dominance.BlocCandidacy}'s answer
 * and how warm two factions are is the sector's - what is pinned here is the order the axes are
 * applied in and what each of them takes.
 */
class StandingBlockRoutingTest {

    private static final String HOLDER_BLOC = "hegemony";
    private static final String ALLY_BLOC = "tritachyon";
    private static final String RIVAL_BLOC = "persean_league";

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

    // What bars a bloc, in the shape the routing takes it: a predicate over bloc ids.
    private static final Predicate<String> IS_POLITICAL_BLOC = blocId ->
        !PLACEHOLDER_BLOC.equals(blocId) && !OTHER_PLACEHOLDER_BLOC.equals(blocId);

    // What a stood-up group is weighed at. The routing reads a group's bloc and its members alone,
    // so a score only ever states the order the groups arrived in and never reaches an assertion.
    private static final int ANY_SCORE = 0;

    @Nested
    class RouteRankedStandings {

        @Test
        void routeRankedStandingsNamesTheStrongestBlocInTheRunningAsHoldingTheSystem() {

            assertThat(routeWithoutAlliances(HOLDER_BLOC, RIVAL_BLOC)
                    .selectStandingsIn(StandingBlock.HOLDER))
                .containsExactly(routeWhole(HOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsLeavesEveryOtherBlocInTheRunningContestingTheSystem() {

            assertThat(readListedBlocs(
                    routeWithoutAlliances(HOLDER_BLOC, RIVAL_BLOC),
                    StandingBlock.CONTESTED))
                .containsExactly(RIVAL_BLOC);
        }

        @Test
        void routeRankedStandingsLiftsTheHoldersOwnAllyOutOfTheContestedBlock() {
            // The split below the holder is the shared one, so a group is filed here by the rule the
            // band beneath the cell lays its runs at contested length by.
            var routing = StandingBlockRouting.routeRankedStandings(
                createRankedStandings(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC),
                buildRules(
                    HolderGroupingFixture.buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                    HolderGrouping.identity(),
                    List.of()));

            assertThat(routing.selectStandingsIn(StandingBlock.ALLIED))
                .containsExactly(routeWhole(ALLY_BLOC));

            assertThat(readListedBlocs(routing, StandingBlock.CONTESTED))
                .containsExactly(RIVAL_BLOC);
        }

        @Test
        void routeRankedStandingsSetsABlocOutOfTheRunningAsideWhateverItOutranked() {
            // The bar is the outer axis: a placeholder owner ranking above everybody is still no
            // contender, so it lands in its own block rather than at the head of the contest.
            assertThat(routeWithoutAlliances(PLACEHOLDER_BLOC, HOLDER_BLOC)
                    .selectStandingsIn(StandingBlock.NON_POLITICAL))
                .containsExactly(routeWhole(PLACEHOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsNamesTheStrongestContenderAsHoldingASystemAPlaceholderOutranks() {
            // The reason the bar is taken before the holder is picked rather than after: dropping the
            // top-ranked group would have dropped the placeholder and left the real holder in the
            // pool the blocks below it are drawn from.
            assertThat(routeWithoutAlliances(PLACEHOLDER_BLOC, HOLDER_BLOC, RIVAL_BLOC)
                    .selectStandingsIn(StandingBlock.HOLDER))
                .containsExactly(routeWhole(HOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsLeavesNobodyHoldingASystemOnlyAPlaceholderStandsIn() {
            // The one place the box and the fill part company: the map still paints, borders and
            // labels the cell for the placeholder, while the box heads no block with it - and an
            // empty holder block is what drops that heading rather than leaving it over nothing.
            var routing = routeWithoutAlliances(PLACEHOLDER_BLOC);

            assertThat(routing.selectStandingsIn(StandingBlock.HOLDER))
                .isEmpty();

            assertThat(routing.selectStandingsIn(StandingBlock.NON_POLITICAL))
                .containsExactly(routeWhole(PLACEHOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsKeepsABlocOutOfTheRunningOutOfTheAlliedBlock() {
            // Standing with the holder is a question about the contest, which a barred bloc is
            // outside of - so no alliance set can lift it back in one axis further down.
            var routing = StandingBlockRouting.routeRankedStandings(
                createRankedStandings(HOLDER_BLOC, PLACEHOLDER_BLOC),
                buildRules(
                    HolderGroupingFixture.buildAllianceOf(HOLDER_BLOC, PLACEHOLDER_BLOC),
                    HolderGrouping.identity(),
                    List.of()));

            assertThat(routing.selectStandingsIn(StandingBlock.ALLIED))
                .isEmpty();

            assertThat(routing.selectStandingsIn(StandingBlock.NON_POLITICAL))
                .containsExactly(routeWhole(PLACEHOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsPlacesEveryRankedGroupInExactlyOneBlock() {
            // The invariant the axes have to leave intact between them: every ranked group is
            // placed, and only a bloc its members disagree about is placed twice. A group taken by
            // no block disappears from a box that ranked it, and one taken by two that nothing
            // split is listed under two headings contradicting each other - neither of which the
            // block set being closed says anything about. Posed with nothing to split, so what is
            // read here is the partition the other axes leave.
            var routing = StandingBlockRouting.routeRankedStandings(
                createRankedStandings(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC, PLACEHOLDER_BLOC),
                buildRules(
                    HolderGroupingFixture.buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                    HolderGrouping.identity(),
                    List.of()));

            // Read as the blocs listed rather than as the rows listing them: what a block qualifies
            // its heading with is the routing's own answer and pinned by the cases about each block,
            // while what this one is about is that no bloc is lost between them or listed twice.
            assertThat(Arrays
                    .stream(StandingBlock.values())
                    .flatMap(block -> routing.selectStandingsIn(block).stream())
                    .map(routed -> routed.standing().blocId())
                    .toList())
                .containsExactlyInAnyOrder(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC, PLACEHOLDER_BLOC);
        }

        @Test
        void routeRankedStandingsKeepsTheOrderTheGroupsRankedInInsideABlock() {
            // The groups arrive strongest first and a block reads in that order rather than in one
            // the partition invented, so the box lists them exactly as the fills rank them.
            assertThat(routeWithoutAlliances(
                    HOLDER_BLOC,
                    PLACEHOLDER_BLOC,
                    OTHER_PLACEHOLDER_BLOC)
                    .selectStandingsIn(StandingBlock.NON_POLITICAL))
                .containsExactly(
                    routeWhole(PLACEHOLDER_BLOC),
                    routeWhole(OTHER_PLACEHOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsLiftsABlocOnGoodTermsWithTheHolderOutOfTheContestedBlock() {
            // The block the disposition axis exists for: a bloc the holder is on excellent terms
            // with has no quarrel over the system, and filed under the contested heading the box
            // would report a fight neither side is in.
            var routing = routeUnderDispositions(
                List.of(RIVAL_BLOC + ":" + HOLDER_BLOC),
                HOLDER_BLOC,
                RIVAL_BLOC);

            assertThat(readListedBlocs(routing, StandingBlock.FRIENDLY))
                .containsExactly(RIVAL_BLOC);

            assertThat(routing.selectStandingsIn(StandingBlock.CONTESTED))
                .isEmpty();
        }

        @Test
        void routeRankedStandingsLeavesAnIndifferentBlocContestingTheSystem() {
            // The scale's own zero is the cut: indifference is not goodwill, so a bloc at or below
            // neutral stays exactly where it was before the block existed.
            assertThat(readListedBlocs(
                    routeUnderDispositions(List.of(), HOLDER_BLOC, RIVAL_BLOC),
                    StandingBlock.CONTESTED))
                .containsExactly(RIVAL_BLOC);
        }

        @Test
        void routeRankedStandingsKeepsTheHoldersAllyAlliedWhateverItsDisposition() {
            // Alliance is the outer axis and disposition never re-sorts what it took, so an ally on
            // excellent terms gains nothing by it - and an ally gone sour loses nothing either.
            var routing = StandingBlockRouting.routeRankedStandings(
                createRankedStandings(HOLDER_BLOC, ALLY_BLOC),
                buildRules(
                    HolderGroupingFixture.buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                    HolderGrouping.identity(),
                    List.of(ALLY_BLOC + ":" + HOLDER_BLOC)));

            assertThat(routing.selectStandingsIn(StandingBlock.ALLIED))
                .containsExactly(routeWhole(ALLY_BLOC));

            assertThat(routing.selectStandingsIn(StandingBlock.FRIENDLY))
                .isEmpty();
        }

        @Test
        void routeRankedStandingsLiftsABlocOnGoodTermsWithEveryMemberOfAnAllianceHolder() {
            // The alliances view's own shape, which no other case here poses: the holder is the
            // alliance bloc the fills were painted for, so the membership the rival is measured
            // against is read out of the fold rather than being the holder's own id.
            var routing = StandingBlockRouting.routeRankedStandings(
                List.of(
                    new GroupStanding(
                        HolderGroupingFixture.ALLIANCE_BLOC_ID,
                        ANY_SCORE,
                        List.of(new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE))),
                    createGroupStanding(RIVAL_BLOC)),
                buildRules(
                    HolderGroupingFixture.buildAllianceOf(WARM_MEMBER, ABSENT_MEMBER),
                    HolderGroupingFixture.buildAllianceOf(WARM_MEMBER, ABSENT_MEMBER),
                    List.of(
                        RIVAL_BLOC + ":" + WARM_MEMBER,
                        RIVAL_BLOC + ":" + ABSENT_MEMBER)));

            assertThat(readListedBlocs(routing, StandingBlock.FRIENDLY))
                .containsExactly(RIVAL_BLOC);

            assertThat(routing.selectStandingsIn(StandingBlock.CONTESTED))
                .isEmpty();
        }

        @Test
        void routeRankedStandingsContestsABlocWhoseSourMemberHoldsNothingInTheSystem() {
            // Why the test is over the whole membership rather than over who happens to stand here:
            // read against the present member alone the bloc would come out friendly, and the same
            // two blocs would then read friendly over this system and contesting over the next on
            // nothing but which of them had a colony where.
            var routing = StandingBlockRouting.routeRankedStandings(
                List.of(
                    createGroupStanding(HOLDER_BLOC),
                    new GroupStanding(
                        HolderGroupingFixture.ALLIANCE_BLOC_ID,
                        ANY_SCORE,
                        List.of(new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE)))),
                buildRules(
                    HolderGrouping.identity(),
                    HolderGroupingFixture.buildAllianceOf(WARM_MEMBER, SOUR_MEMBER),
                    List.of(WARM_MEMBER + ":" + HOLDER_BLOC)));

            assertThat(routing.selectStandingsIn(StandingBlock.FRIENDLY))
                .isEmpty();

            assertThat(routing.selectStandingsIn(StandingBlock.CONTESTED))
                .hasSize(1);
        }

        @Test
        void routeRankedStandingsListsABlocThatIsNotOfOneMindUnderBothHeadings() {
            // A bloc half of which is warm and half sour is true of neither heading whole, so it is
            // listed under both rather than broken up into loose factions: the bloc stays one named
            // thing, and each of its rows holds only the members on that side.
            var routing = routeMixedBlocAgainstTheHolder();

            assertThat(readListedMembers(routing, StandingBlock.FRIENDLY))
                .containsExactly(WARM_MEMBER);

            assertThat(readListedMembers(routing, StandingBlock.CONTESTED))
                .containsExactly(SOUR_MEMBER);
        }

        @Test
        void routeRankedStandingsStatesHowMuchOfASplitBlocEachHeadingTook() {
            // What keeps neither heading overreaching. The fraction is over the bloc's whole roster -
            // three members, of which one is warm - and not over the two standing here, which is what
            // makes one alliance read the same over every system it holds.
            var routing = routeMixedBlocAgainstTheHolder();

            assertThat(readBlocFraction(routing, StandingBlock.FRIENDLY))
                .isEqualTo(new StandingFraction(1, 3));

            assertThat(readBlocFraction(routing, StandingBlock.CONTESTED))
                .isEqualTo(new StandingFraction(2, 3));
        }

        @Test
        void routeRankedStandingsListsOnlyThePresentMembersOfABlocThatIsNotOfOneMind() {
            // The two readings part exactly here: the fraction is over the whole membership, while
            // what is listed is who stands in the hovered system - the box having never listed
            // anybody else. So the rows and the fraction do not add up, and are not meant to.
            var routing = routeMixedBlocAgainstTheHolder();

            assertThat(Arrays
                    .stream(StandingBlock.values())
                    .flatMap(block -> readListedMembers(routing, block).stream())
                    .toList())
                .containsExactlyInAnyOrder(WARM_MEMBER, SOUR_MEMBER);
        }

        @Test
        void routeRankedStandingsSplitsNeitherTheHoldingBlocNorAnAlliedOne() {
            // Both are placed by membership rather than by relation, so a bloc a rival stands warm
            // toward half of is not thereby split: its members are listed under it because they are
            // its members, and nothing about relations is asserted of them.
            var holdingBloc = new GroupStanding(
                HolderGroupingFixture.ALLIANCE_BLOC_ID,
                ANY_SCORE,
                List.of(
                    new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE),
                    new WeighedFactionStanding(SOUR_MEMBER, ANY_SCORE)));

            var routing = StandingBlockRouting.routeRankedStandings(
                List.of(holdingBloc, createGroupStanding(ALLY_BLOC), createGroupStanding(RIVAL_BLOC)),
                buildRules(
                    HolderGroupingFixture.buildAllianceOf(
                        HolderGroupingFixture.ALLIANCE_BLOC_ID,
                        ALLY_BLOC),
                    HolderGroupingFixture.buildAllianceOf(WARM_MEMBER, SOUR_MEMBER),
                    List.of(RIVAL_BLOC + ":" + WARM_MEMBER)));

            assertThat(routing.selectStandingsIn(StandingBlock.HOLDER))
                .containsExactly(RoutedStanding.routeWhole(holdingBloc));

            assertThat(routing.selectStandingsIn(StandingBlock.ALLIED))
                .containsExactly(routeWhole(ALLY_BLOC));
        }

        @Test
        void routeRankedStandingsStatesBothFractionReadingsOverTwoAlliancesAtOdds() {
            // The one shape where a row's own reading and its members' are both live: an alliance of
            // three against an alliance of three. The bloc's rows count its own membership - one
            // friendly, two not - while each member's counts how much of the holder it quarrels with,
            // which is one of three apiece.
            var routing = routeTwoAlliancesAtOdds();

            assertThat(readBlocFraction(routing, StandingBlock.FRIENDLY))
                .isEqualTo(new StandingFraction(1, 3));

            assertThat(readBlocFraction(routing, StandingBlock.CONTESTED))
                .isEqualTo(new StandingFraction(2, 3));

            assertThat(readMemberFraction(routing, StandingBlock.FRIENDLY, RIVAL_MEMBER_D))
                .isEqualTo(new StandingFraction(0, 3));

            assertThat(readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_E))
                .isEqualTo(new StandingFraction(1, 3));

            assertThat(readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_F))
                .isEqualTo(new StandingFraction(1, 3));
        }

        @Test
        void routeRankedStandingsListsEachSideOfTwoAlliancesAtOddsUnderItsOwnHeading() {

            var routing = routeTwoAlliancesAtOdds();

            assertThat(readListedMembers(routing, StandingBlock.FRIENDLY))
                .containsExactly(RIVAL_MEMBER_D);

            assertThat(readListedMembers(routing, StandingBlock.CONTESTED))
                .containsExactly(RIVAL_MEMBER_E, RIVAL_MEMBER_F);
        }

        @Test
        void routeRankedStandingsCountsTheHoldersMembersOnALoneFactionStandingAgainstABloc() {
            // The reading a faction's row states rather than a bloc's: how much of the holder it
            // quarrels with. It is the only fraction a lone faction has to state, its own bloc being
            // one member that could count nothing but nought or the whole.
            var routing = StandingBlockRouting.routeRankedStandings(
                List.of(
                    new GroupStanding(
                        HOLDER_ALLIANCE_BLOC,
                        ANY_SCORE,
                        List.of(new WeighedFactionStanding(HOLDER_MEMBER_A, ANY_SCORE))),
                    new GroupStanding(
                        RIVAL_BLOC,
                        ANY_SCORE,
                        List.of(new WeighedFactionStanding(RIVAL_BLOC, ANY_SCORE)))),
                buildRules(
                    buildTwoAlliances(),
                    buildTwoAlliances(),
                    List.of(
                        RIVAL_BLOC + ":" + HOLDER_MEMBER_B,
                        RIVAL_BLOC + ":" + HOLDER_MEMBER_C)));

            assertThat(readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_BLOC))
                .isEqualTo(new StandingFraction(1, 3));

            assertThat(readBlocFraction(routing, StandingBlock.CONTESTED))
                .isEqualTo(new StandingFraction(1, 1));
        }

        @Test
        void routeRankedStandingsCountsEachMemberOfAnUnsplitBlocAgainstTheHolderSeparately() {
            // A bloc none of whose members is friendly is listed whole, and its row states nothing -
            // the heading took all of it. Its members still state their own counts, each quarrelling
            // with a different one of the holder's three, which is what shows a member's number is
            // not a part of the bloc's.
            var alliances = buildTwoAlliances();

            var routing = StandingBlockRouting.routeRankedStandings(
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
                            new WeighedFactionStanding(RIVAL_MEMBER_E, ANY_SCORE)))),
                buildRules(
                    alliances,
                    alliances,
                    List.of(
                        RIVAL_MEMBER_D + ":" + HOLDER_MEMBER_B,
                        RIVAL_MEMBER_D + ":" + HOLDER_MEMBER_C,
                        RIVAL_MEMBER_E + ":" + HOLDER_MEMBER_A,
                        RIVAL_MEMBER_E + ":" + HOLDER_MEMBER_C,
                        RIVAL_MEMBER_F + ":" + HOLDER_MEMBER_A)));

            assertThat(readBlocFraction(routing, StandingBlock.CONTESTED))
                .isEqualTo(new StandingFraction(3, 3));

            assertThat(readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_D))
                .isEqualTo(new StandingFraction(1, 3));

            assertThat(readMemberFraction(routing, StandingBlock.CONTESTED, RIVAL_MEMBER_E))
                .isEqualTo(new StandingFraction(1, 3));
        }
    }

    @Nested
    class HasAnyStanding {

        @Test
        void hasAnyStandingCountsAGroupInEveryBlockAlike() {
            // What the box asks to decide whether it has anything to list at all. A bloc out of the
            // running is still somebody standing in the system, so a system holding only the
            // placeholder is not an empty one.
            assertThat(routeWithoutAlliances(PLACEHOLDER_BLOC).hasAnyStanding())
                .isTrue();
        }

        @Test
        void hasAnyStandingAnswersNoForASystemTheRankingFoundNobodyIn() {

            assertThat(routeWithoutAlliances().hasAnyStanding())
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
            buildRules(
                alliances,
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

    // A bloc of two standing against the holder, one member warm toward it and the other not, with
    // both present in the system. The case both halves of the split rule are read off, so the two of
    // them are posed once rather than twice over inputs free to drift apart.
    private static StandingBlockRouting routeMixedBlocAgainstTheHolder() {

        return StandingBlockRouting.routeRankedStandings(
            List.of(
                createGroupStanding(HOLDER_BLOC),
                new GroupStanding(
                    HolderGroupingFixture.ALLIANCE_BLOC_ID,
                    ANY_SCORE,
                    List.of(
                        new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE),
                        new WeighedFactionStanding(SOUR_MEMBER, ANY_SCORE)))),
            buildRules(
                HolderGrouping.identity(),
                HolderGroupingFixture.buildAllianceOf(WARM_MEMBER, SOUR_MEMBER, ABSENT_MEMBER),
                List.of(WARM_MEMBER + ":" + HOLDER_BLOC)));
    }

    // The ranking as an install with nothing grouping factions and nobody on good terms produces it:
    // no two blocs stand together and none is warm toward another, which is the state every case not
    // about those two axes is posed in.
    private static StandingBlockRouting routeWithoutAlliances(String... rankedBlocIds) {
        return StandingBlockRouting.routeRankedStandings(
            createRankedStandings(rankedBlocIds),
            buildRules(HolderGrouping.identity(), HolderGrouping.identity(), List.of()));
    }

    // The same with a disposition table, for a case about who is warm toward whom rather than about
    // who is allied to whom.
    private static StandingBlockRouting routeUnderDispositions(
            List<String> aboveNeutralPairs,
            String... rankedBlocIds) {

        return StandingBlockRouting.routeRankedStandings(
            createRankedStandings(rankedBlocIds),
            buildRules(HolderGrouping.identity(), HolderGrouping.identity(), aboveNeutralPairs));
    }

    // The four rules a case poses: the bar, which every case here shares, and the three it varies -
    // the alliance set the allied block is lifted by, the fold a bloc's membership is read out of,
    // and which factions are above neutral with which, each pair written "<faction>:<other>".
    private static StandingBlockRules buildRules(
            HolderGrouping allianceSet,
            HolderGrouping membershipFold,
            List<String> aboveNeutralPairs) {

        return new StandingBlockRules(
            IS_POLITICAL_BLOC,
            new BlocAffiliation(allianceSet),
            // Read one way round, every rule here asking its pairs from the faction being sorted -
            // so a table stating what a rival thinks of the holder settles which side that rival
            // takes and both the counts stated on its row alike.
            new BlocFriendliness((factionId, otherFactionId) ->
                aboveNeutralPairs.contains(factionId + ":" + otherFactionId)),
            membershipFold::resolveMemberFactionIds);
    }

    private static List<GroupStanding> createRankedStandings(String... rankedBlocIds) {
        return Arrays
            .stream(rankedBlocIds)
            .map(StandingBlockRoutingTest::createGroupStanding)
            .toList();
    }

    // One ranked group as the routing places it: the bloc it is. What it is weighed at and made up
    // of are the naming's business, so which block it falls in turns on the bloc alone wherever the
    // case is not about a bloc's members disagreeing.
    private static GroupStanding createGroupStanding(String blocId) {
        return new GroupStanding(blocId, ANY_SCORE, List.<FactionStanding>of());
    }

    // That same group as a block placed by membership lists it: nothing qualifies the heading over
    // it, so its row states no fraction and neither does anything beneath it.
    private static RoutedStanding routeWhole(String blocId) {
        return RoutedStanding.routeWhole(createGroupStanding(blocId));
    }

}
