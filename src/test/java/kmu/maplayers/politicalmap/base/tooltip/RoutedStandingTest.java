package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.GroupStanding;
import kmu.maplayers.politicalmap.base.dominance.StandingFraction;
import kmu.maplayers.politicalmap.base.dominance.WeighedFactionStanding;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a group carries once a block has placed it: the fractions its row and its members state,
 * and what a row under a heading true of the whole of it carries where they would be.
 */
class RoutedStandingTest {

    private static final String BLOC_ID = "alliance-1";
    private static final String MEMBER_FACTION_ID = "hegemony";

    // What the group is weighed at, which nothing here reads - these cases are about the fractions
    // laid over a group rather than about the group itself.
    private static final int ANY_SCORE = 0;

    private static final GroupStanding ANY_STANDING = new GroupStanding(
        BLOC_ID,
        ANY_SCORE,
        List.of(new WeighedFactionStanding(MEMBER_FACTION_ID, ANY_SCORE)));

    @Nested
    class ReadFractionFor {

        @Test
        void readFractionForAnswersTheFractionWorkedOutForThatMember() {

            var routedStanding = new RoutedStanding(
                ANY_STANDING,
                new StandingFraction(1, 3),
                Map.of(MEMBER_FACTION_ID, new StandingFraction(2, 4)));

            assertThat(routedStanding.readFractionFor(MEMBER_FACTION_ID))
                .isEqualTo(new StandingFraction(2, 4));
        }

        @Test
        void readFractionForStatesNothingForAFactionNoneWasWorkedOutFor() {
            // Answered for every faction rather than only for those a block counted, so a resolver
            // laying rows out asks one question of every row instead of judging an absence itself.
            assertThat(RoutedStanding.routeWhole(ANY_STANDING).readFractionFor(MEMBER_FACTION_ID))
                .isEqualTo(StandingFraction.NOTHING_TO_STATE);
        }
    }

    @Nested
    class RouteWhole {

        @Test
        void routeWholeStatesNoFractionOnTheRowEither() {
            // What every block placed by membership hands over: the heading is true of the whole
            // group, so there is nothing for the row to qualify.
            assertThat(RoutedStanding.routeWhole(ANY_STANDING).fraction())
                .isEqualTo(StandingFraction.NOTHING_TO_STATE);
        }
    }

    @Nested
    class RoutedStandingConstruction {

        @Test
        void rejectsAGroupThatIsNotThere() {

            assertThatThrownBy(() ->
                    new RoutedStanding(null, StandingFraction.NOTHING_TO_STATE, Map.of()))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void rejectsAnAbsentFractionRatherThanReadingItAsNoneStated() {
            // The absence has a value of its own, so a null is a caller that has not decided rather
            // than one stating there is nothing to say.
            assertThatThrownBy(() -> new RoutedStanding(ANY_STANDING, null, Map.of()))
                .isInstanceOf(NullPointerException.class);
        }

        @Test
        void holdsTheMemberFractionsApartFromTheMapItWasHanded() {
            // A block fills its counts in as it walks, so a routed standing that kept the caller's own
            // map would go on changing after the block that placed it moved on.
            var memberFractions = new HashMap<String, StandingFraction>();

            memberFractions.put(MEMBER_FACTION_ID, new StandingFraction(1, 3));

            var routedStanding = new RoutedStanding(
                ANY_STANDING,
                StandingFraction.NOTHING_TO_STATE,
                memberFractions);

            memberFractions.clear();

            assertThat(routedStanding.readFractionFor(MEMBER_FACTION_ID))
                .isEqualTo(new StandingFraction(1, 3));
        }
    }
}
