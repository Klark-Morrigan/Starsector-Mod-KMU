package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the bloc-level reading of a faction-level disposition: unanimous across both memberships or
 * not friendly at all, how much of a membership falls short where it is not, and how much of the far
 * bloc one faction falls short of. The faction-level answer is a hand-built pair list, so what is
 * fixed here is how the pairs are composed rather than where the game's own threshold falls.
 *
 * <p>That list is read one way round on purpose. Every answer here takes its pairs from the faction
 * named first, which is what stops a faction being sorted by one reading and counted by another, so
 * the cases state a warm pair in one direction and leave the other cold.
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

    @Nested
    class CountMembersAtOddsWith {

        @Test
        void countsNoneWhereEveryMemberIsFriendlyWithTheWholeOtherBloc() {

            var friendliness = buildFriendlinessAboveNeutralOn(
                List.of("hegemony:tritachyon", "luddic_church:tritachyon"));

            assertThat(friendliness.countMembersAtOddsWith(
                    Set.of("hegemony", "luddic_church"),
                    Set.of("tritachyon")))
                .isZero();
        }

        @Test
        void countsAMemberOnceHoweverManyOfTheOtherBlocItQuarrelsWith() {
            // Counted per member and not per pair: what a row states is how much of its membership
            // the heading is false of, so one faction at odds with both of the other bloc is still
            // one member of two.
            var friendliness = buildFriendlinessAboveNeutralOn(
                List.of("hegemony:tritachyon", "hegemony:persean"));

            assertThat(friendliness.countMembersAtOddsWith(
                    Set.of("hegemony", "luddic_church"),
                    Set.of("tritachyon", "persean")))
                .isOne();
        }

        @Test
        void countsEveryMemberWhereNoneIsFriendly() {

            var friendliness = buildFriendlinessAboveNeutralOn(List.of());

            assertThat(friendliness.countMembersAtOddsWith(
                    Set.of("hegemony", "luddic_church"),
                    Set.of("tritachyon")))
                .isEqualTo(2);
        }

        @Test
        void countsEveryMemberWhereTheOtherBlocIsMadeOfNobody() {
            // The same positive claim the yes-or-no is: nobody is friendly with nobody, which is what
            // leaves that answer needing no guard of its own for this side.
            var friendliness = buildFriendlinessAboveNeutralOn(List.of("hegemony:tritachyon"));

            assertThat(friendliness.countMembersAtOddsWith(Set.of("hegemony"), Set.of()))
                .isOne();
        }
    }

    @Nested
    class CountFactionsAtOddsWith {

        @Test
        void countsEveryOneOfTheOtherBlocOneFactionFallsShortOf() {
            // The far side of the same walk: a faction's own row states how much of the holder it
            // quarrels with, where a bloc's states how much of itself falls short of the whole.
            var friendliness = buildFriendlinessAboveNeutralOn(List.of("hegemony:tritachyon"));

            assertThat(friendliness.countFactionsAtOddsWith(
                    "hegemony",
                    Set.of("tritachyon", "persean", "luddic_church")))
                .isEqualTo(2);
        }

        @Test
        void countsNoneWhereTheFactionIsFriendlyWithAllOfThem() {

            var friendliness = buildFriendlinessAboveNeutralOn(
                List.of("hegemony:tritachyon", "hegemony:persean"));

            assertThat(friendliness.countFactionsAtOddsWith(
                    "hegemony",
                    Set.of("tritachyon", "persean")))
                .isZero();
        }

        @Test
        void readsThePairFromTheFactionItIsCountingFor() {
            // The direction every rule here shares. A table warm one way only leaves this counting
            // the faction's own view, so a faction sorted friendly cannot then be counted at odds -
            // which asking the pair from the other bloc's end would allow.
            var friendliness = buildFriendlinessAboveNeutralOn(List.of("hegemony:tritachyon"));

            assertThat(friendliness.countFactionsAtOddsWith("hegemony", Set.of("tritachyon")))
                .isZero();
            assertThat(friendliness.countFactionsAtOddsWith("tritachyon", Set.of("hegemony")))
                .isOne();
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
