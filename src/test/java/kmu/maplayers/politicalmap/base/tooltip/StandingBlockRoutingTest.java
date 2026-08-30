package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.BlocFriendliness;
import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
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

            assertThat(routeWithoutAlliances(HOLDER_BLOC, RIVAL_BLOC)
                    .selectStandingsIn(StandingBlock.CONTESTED))
                .containsExactly(routeWhole(RIVAL_BLOC));
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

            assertThat(routing.selectStandingsIn(StandingBlock.CONTESTED))
                .containsExactly(routeWhole(RIVAL_BLOC));
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
            // The invariant the axes have to leave intact between them: the blocks partition the
            // ranking. A group taken by two of them is listed twice under two headings that
            // contradict each other, and one taken by none disappears from a box that ranked it -
            // neither of which the block set being closed says anything about.
            var routing = StandingBlockRouting.routeRankedStandings(
                createRankedStandings(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC, PLACEHOLDER_BLOC),
                buildRules(
                    HolderGroupingFixture.buildAllianceOf(HOLDER_BLOC, ALLY_BLOC),
                    HolderGrouping.identity(),
                    List.of()));

            assertThat(Arrays
                    .stream(StandingBlock.values())
                    .flatMap(block -> routing.selectStandingsIn(block).stream())
                    .toList())
                .containsExactlyInAnyOrder(
                    routeWhole(HOLDER_BLOC),
                    routeWhole(ALLY_BLOC),
                    routeWhole(RIVAL_BLOC),
                    routeWhole(PLACEHOLDER_BLOC));
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

            assertThat(routing.selectStandingsIn(StandingBlock.FRIENDLY))
                .containsExactly(routeWhole(RIVAL_BLOC));

            assertThat(routing.selectStandingsIn(StandingBlock.CONTESTED))
                .isEmpty();
        }

        @Test
        void routeRankedStandingsLeavesAnIndifferentBlocContestingTheSystem() {
            // The scale's own zero is the cut: indifference is not goodwill, so a bloc at or below
            // neutral stays exactly where it was before the block existed.
            assertThat(routeUnderDispositions(List.of(), HOLDER_BLOC, RIVAL_BLOC)
                    .selectStandingsIn(StandingBlock.CONTESTED))
                .containsExactly(routeWhole(RIVAL_BLOC));
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
        void routeRankedStandingsListsTheMembersOfABlocThatIsNotOfOneMindApart() {
            // A bloc half of which is warm and half sour cannot be listed whole under either
            // heading, so each member present takes a row of its own in the block its own
            // disposition puts it in - each still stating the bloc it came out of, so nothing about
            // the grouping is hidden.
            var routing = routeMixedBlocAgainstTheHolder();

            assertThat(routing.selectStandingsIn(StandingBlock.FRIENDLY))
                .containsExactly(RoutedStanding.dissolveFrom(
                    new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE),
                    HolderGroupingFixture.ALLIANCE_BLOC_ID));

            assertThat(routing.selectStandingsIn(StandingBlock.CONTESTED))
                .containsExactly(RoutedStanding.dissolveFrom(
                    new WeighedFactionStanding(SOUR_MEMBER, ANY_SCORE),
                    HolderGroupingFixture.ALLIANCE_BLOC_ID));
        }

        @Test
        void routeRankedStandingsListsOnlyThePresentMembersOfABlocThatIsNotOfOneMind() {
            // The two readings part exactly here: the test is over the whole membership, while what
            // is listed is who stands in the hovered system - the box having never listed anybody
            // else.
            var routing = routeMixedBlocAgainstTheHolder();

            assertThat(Arrays
                    .stream(StandingBlock.values())
                    .flatMap(block -> routing.selectStandingsIn(block).stream())
                    .filter(RoutedStanding::isDissolvedFromAlliance)
                    .map(routed -> routed.standing().blocId())
                    .toList())
                .containsExactlyInAnyOrder(WARM_MEMBER, SOUR_MEMBER);
        }

        @Test
        void routeRankedStandingsNeverListsTheHoldersOwnMembersApart() {
            // The holder block is placed by membership rather than by relation, so a holding bloc
            // whose members disagree about a rival is not thereby broken up: its members are listed
            // under it because they are its members and nothing about relations is asserted of them.
            var routing = StandingBlockRouting.routeRankedStandings(
                List.of(new GroupStanding(
                    HolderGroupingFixture.ALLIANCE_BLOC_ID,
                    ANY_SCORE,
                    List.of(
                        new WeighedFactionStanding(WARM_MEMBER, ANY_SCORE),
                        new WeighedFactionStanding(SOUR_MEMBER, ANY_SCORE)))),
                buildRules(
                    HolderGrouping.identity(),
                    HolderGroupingFixture.buildAllianceOf(WARM_MEMBER, SOUR_MEMBER),
                    List.of(WARM_MEMBER + ":" + WARM_MEMBER)));

            assertThat(routing.selectStandingsIn(StandingBlock.HOLDER))
                .hasSize(1)
                .allMatch(routed -> !routed.isDissolvedFromAlliance());
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

    // A bloc of two standing against the holder, one member warm toward it and the other not, with
    // both present in the system. The case both dissolution rules are read off, so the two of them
    // are posed once rather than twice over inputs free to drift apart.
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

    // That same group as a block lists it where nothing broke its bloc up, which is every block but
    // the two disposition sorts and most rows of those.
    private static RoutedStanding routeWhole(String blocId) {
        return RoutedStanding.routeWhole(createGroupStanding(blocId));
    }
}
