package kmu.maplayers.politicalmap.dominance.standings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what a group standing accepts and what it says about its members: it refuses to stand for no
 * members, and it counts as weighed where any one member was.
 */
final class GroupStandingTests {

    @Nested
    class Construct {

        @Test
        void refusesAGroupWithNoMembers() {
            // A reader of a lone-faction group reads its first member, which a memberless group lacks.
            assertThatThrownBy(() -> new GroupStanding("hegemony", 0, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class HasWeighedMember {

        @Test
        void answersTrueWhereOneMemberWasWeighed() {

            var standing = GroupStanding.sumOverMembers("pact", List.of(
                new PresenceOnlyFactionStanding("astral_armada"),
                new WeighedFactionStanding("hegemony", 4)));

            assertThat(standing.hasWeighedMember())
                .isTrue();
        }

        @Test
        void answersFalseWhereNoMemberWasWeighed() {

            var standing = GroupStanding.sumOverMembers("pact", List.of(
                new PresenceOnlyFactionStanding("astral_armada")));

            assertThat(standing.hasWeighedMember())
                .isFalse();
        }
    }
}
