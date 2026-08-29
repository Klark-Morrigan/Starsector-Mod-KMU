package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.BlocAffiliation;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.HolderGroupingFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where each of a hovered system's ranked groups is listed: the blocs out of the contest set
 * aside first, the strongest of what remains holding the system, and the rest placed either side of
 * it.
 *
 * <p>Posed over hand-built standings under a bar stated here, since which blocs a live box bars is
 * {@link kmu.maplayers.politicalmap.base.dominance.BlocCandidacy}'s answer rather than this rule's -
 * what is pinned here is that the bar is applied outside everything else.
 */
class StandingBlockRoutingTest {

    private static final String HOLDER_BLOC = "hegemony";
    private static final String ALLY_BLOC = "tritachyon";
    private static final String RIVAL_BLOC = "persean_league";

    // The bloc the bar keeps out of the contest. Named for what it stands for in the sector - the
    // owner every abandoned station is handed to - so the cases read as the situation they are about.
    private static final String PLACEHOLDER_BLOC = "neutral";
    private static final String OTHER_PLACEHOLDER_BLOC = "derelict";

    // What bars a bloc, in the shape the routing takes it: a predicate over bloc ids.
    private static final Predicate<String> IS_POLITICAL_BLOC = blocId ->
        !PLACEHOLDER_BLOC.equals(blocId) && !OTHER_PLACEHOLDER_BLOC.equals(blocId);

    // What a stood-up group is weighed at. The routing reads a group's bloc alone, so a score only
    // ever states the order the groups arrived in and never reaches an assertion.
    private static final int ANY_SCORE = 0;

    @Nested
    class RouteRankedStandings {

        @Test
        void routeRankedStandingsNamesTheStrongestBlocInTheRunningAsHoldingTheSystem() {

            assertThat(routeWithoutAlliances(HOLDER_BLOC, RIVAL_BLOC)
                    .selectStandingsIn(StandingBlock.HOLDER))
                .containsExactly(createGroupStanding(HOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsLeavesEveryOtherBlocInTheRunningContestingTheSystem() {

            assertThat(routeWithoutAlliances(HOLDER_BLOC, RIVAL_BLOC)
                    .selectStandingsIn(StandingBlock.CONTESTED))
                .containsExactly(createGroupStanding(RIVAL_BLOC));
        }

        @Test
        void routeRankedStandingsLiftsTheHoldersOwnAllyOutOfTheContestedBlock() {
            // The split below the holder is the shared one, so a group is filed here by the rule the
            // band beneath the cell lays its runs at contested length by.
            var routing = StandingBlockRouting.routeRankedStandings(
                createRankedStandings(HOLDER_BLOC, ALLY_BLOC, RIVAL_BLOC),
                IS_POLITICAL_BLOC,
                new BlocAffiliation(HolderGroupingFixture.buildAllianceOf(HOLDER_BLOC, ALLY_BLOC)));

            assertThat(routing.selectStandingsIn(StandingBlock.ALLIED))
                .containsExactly(createGroupStanding(ALLY_BLOC));

            assertThat(routing.selectStandingsIn(StandingBlock.CONTESTED))
                .containsExactly(createGroupStanding(RIVAL_BLOC));
        }

        @Test
        void routeRankedStandingsSetsABlocOutOfTheRunningAsideWhateverItOutranked() {
            // The bar is the outer axis: a placeholder owner ranking above everybody is still no
            // contender, so it lands in its own block rather than at the head of the contest.
            assertThat(routeWithoutAlliances(PLACEHOLDER_BLOC, HOLDER_BLOC)
                    .selectStandingsIn(StandingBlock.NON_POLITICAL))
                .containsExactly(createGroupStanding(PLACEHOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsNamesTheStrongestContenderAsHoldingASystemAPlaceholderOutranks() {
            // The reason the bar is taken before the holder is picked rather than after: dropping the
            // top-ranked group would have dropped the placeholder and left the real holder in the
            // pool the blocks below it are drawn from.
            assertThat(routeWithoutAlliances(PLACEHOLDER_BLOC, HOLDER_BLOC, RIVAL_BLOC)
                    .selectStandingsIn(StandingBlock.HOLDER))
                .containsExactly(createGroupStanding(HOLDER_BLOC));
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
                .containsExactly(createGroupStanding(PLACEHOLDER_BLOC));
        }

        @Test
        void routeRankedStandingsKeepsABlocOutOfTheRunningOutOfTheAlliedBlock() {
            // Standing with the holder is a question about the contest, which a barred bloc is
            // outside of - so no alliance set can lift it back in one axis further down.
            var routing = StandingBlockRouting.routeRankedStandings(
                createRankedStandings(HOLDER_BLOC, PLACEHOLDER_BLOC),
                IS_POLITICAL_BLOC,
                new BlocAffiliation(
                    HolderGroupingFixture.buildAllianceOf(HOLDER_BLOC, PLACEHOLDER_BLOC)));

            assertThat(routing.selectStandingsIn(StandingBlock.ALLIED))
                .isEmpty();

            assertThat(routing.selectStandingsIn(StandingBlock.NON_POLITICAL))
                .containsExactly(createGroupStanding(PLACEHOLDER_BLOC));
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
                    createGroupStanding(PLACEHOLDER_BLOC),
                    createGroupStanding(OTHER_PLACEHOLDER_BLOC));
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

    // The ranking as an install with nothing grouping factions produces it: no two blocs stand
    // together, which is the state every case not about the allied block is posed in.
    private static StandingBlockRouting routeWithoutAlliances(String... rankedBlocIds) {
        return StandingBlockRouting.routeRankedStandings(
            createRankedStandings(rankedBlocIds),
            IS_POLITICAL_BLOC,
            BlocAffiliation.NONE);
    }

    private static List<GroupStanding> createRankedStandings(String... rankedBlocIds) {
        return Arrays
            .stream(rankedBlocIds)
            .map(StandingBlockRoutingTest::createGroupStanding)
            .toList();
    }

    // One ranked group as the routing places it: the bloc it is. What it is weighed at and made up
    // of are the naming's business, so which block it falls in turns on the bloc alone.
    private static GroupStanding createGroupStanding(String blocId) {
        return new GroupStanding(blocId, ANY_SCORE, List.of());
    }
}
