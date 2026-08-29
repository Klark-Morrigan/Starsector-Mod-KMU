package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bloc-level reading of a faction-level disposition: unanimous across both memberships or
 * not friendly at all. The faction-level answer is a hand-built pair list, so what is fixed here is
 * how the pairs are composed rather than where the game's own threshold falls.
 */
class BlocFriendlinessTest {

    @Nested
    class AreBlocsFriendly {

        @Test
        void isTrueWhereEveryPairAcrossTheTwoMembershipsIsAboveNeutral() {

            var friendliness = buildFriendlinessAboveNeutralOn(
                List.of(
                    "hegemony:tritachyon",
                    "hegemony:persean",
                    "luddic_church:tritachyon",
                    "luddic_church:persean"));

            assertThat(friendliness.areBlocsFriendly(
                    Set.of("hegemony", "luddic_church"),
                    Set.of("tritachyon", "persean")))
                .isTrue();
        }

        @Test
        void isFalseWhereNoPairIsAboveNeutral() {

            var friendliness = buildFriendlinessAboveNeutralOn(List.of());

            assertThat(friendliness.areBlocsFriendly(
                    Set.of("hegemony", "luddic_church"),
                    Set.of("tritachyon")))
                .isFalse();
        }

        @Test
        void isFalseWhereOnePairAmongManyIsSour() {
            // Unanimity, not a majority: three warm pairs do not carry a heading the fourth makes
            // false, which is the fault a block placed by disposition exists to fix.
            var friendliness = buildFriendlinessAboveNeutralOn(
                List.of("hegemony:tritachyon", "hegemony:persean", "luddic_church:tritachyon"));

            assertThat(friendliness.areBlocsFriendly(
                    Set.of("hegemony", "luddic_church"),
                    Set.of("tritachyon", "persean")))
                .isFalse();
        }

        @Test
        void readsOneFactionAgainstOneAsThatSinglePair() {
            // What the rule degenerates to wherever nothing groups factions, which is every layer
            // pinned to the identity grouping and every install without an alliance set.
            var friendliness = buildFriendlinessAboveNeutralOn(List.of("hegemony:tritachyon"));

            assertThat(friendliness.areBlocsFriendly(Set.of("hegemony"), Set.of("tritachyon")))
                .isTrue();
        }

        @Test
        void isFalseWhereAMemberStandingInNoSystemIsSour() {
            // Membership is read whole, so a member that holds nothing where the question is being
            // asked still sinks its bloc. The alternative reads the same two blocs friendly over one
            // system and contesting over the next, on nothing but which subset of each was present.
            var friendliness = buildFriendlinessAboveNeutralOn(List.of("hegemony:tritachyon"));

            assertThat(friendliness.areBlocsFriendly(
                    Set.of("hegemony", "luddic_church"),
                    Set.of("tritachyon")))
                .isFalse();
        }

        @Test
        void isFalseWhereEitherBlocIsMadeOfNobody() {
            // Friendliness is a positive claim, and walking no pairs at all would report the vacuous
            // truth - a bloc nothing is known about filed under a heading asserting something of it.
            var friendliness = buildFriendlinessAboveNeutralOn(List.of("hegemony:tritachyon"));

            assertThat(friendliness.areBlocsFriendly(Set.of(), Set.of("tritachyon")))
                .isFalse();
            assertThat(friendliness.areBlocsFriendly(Set.of("hegemony"), Set.of()))
                .isFalse();
        }
    }

    // A rule whose faction-level answer is above neutral for exactly the named pairs, each written
    // "<faction>:<other faction>" so a case states its whole disposition table in one line.
    private static BlocFriendliness buildFriendlinessAboveNeutralOn(List<String> aboveNeutralPairs) {

        return new BlocFriendliness(
            (factionId, otherFactionId) ->
                aboveNeutralPairs.contains(factionId + ":" + otherFactionId));
    }
}
