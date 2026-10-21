package kmu.maplayers.ownermap.owners.holders.alliances;

import kmlib.starsector.factions.alliances.FactionAlliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.buildAllianceOf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link BlocAffiliation}'s one predicate: two distinct blocs stand together exactly when they
 * are factions of one alliance, and with no alliances no two blocs ever do. These are the answers
 * every contest judgement reads, so they are fixed here on hand-built memberships free of any live
 * alliance source.
 */
final class BlocAffiliationTests {

    @Nested
    class Constructor {

        @Test
        void rejectsNullAlliances() {
            // An affiliation with no memberships behind it would fault on the first pair it was
            // asked about rather than at the binding point that failed to supply them; the absence
            // of alliances is a value of its own - FactionAlliances.NONE - never a null.
            assertThatThrownBy(() -> new BlocAffiliation(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("alliances");
        }
    }

    @Nested
    class AreOwnersAllied {

        @Test
        void isTrueForTwoFactionsOfOneAlliance() {
            // Asserted in both argument orders: standing together has no direction, so neither may
            // the predicate.
            assertThat(buildAlliedAffiliation().areOwnersAllied("hegemony", "astral_armada"))
                .isTrue();
            assertThat(buildAlliedAffiliation().areOwnersAllied("astral_armada", "hegemony"))
                .isTrue();
        }

        @Test
        void isFalseForAFactionOutsideTheAlliance() {

            assertThat(buildAlliedAffiliation().areOwnersAllied("hegemony", "tritachyon"))
                .isFalse();
        }

        @Test
        void isFalseForABlocAgainstItself() {
            // One bloc named twice is one side of a contest, not two standing together; whether a
            // bloc is the painter is asked at the judging site as a question of its own.
            assertThat(buildAlliedAffiliation().areOwnersAllied("hegemony", "hegemony"))
                .isFalse();
        }

        @Test
        void isFalseForABlocInNoAlliance() {
            // A faction in no alliance stands with nothing - neither with an alliance member nor
            // with another faction standing alone.
            assertThat(buildAlliedAffiliation().areOwnersAllied("pirates", "hegemony"))
                .isFalse();
            assertThat(buildAlliedAffiliation().areOwnersAllied("pirates", "luddic_church"))
                .isFalse();
        }

        @Test
        void isFalseForABlocWithNoId() {

            assertThat(buildAlliedAffiliation().areOwnersAllied(null, "hegemony"))
                .isFalse();
        }

        @Test
        void isFalseForTwoGroupBlocIds() {
            // A layer painting groups: the pass's bloc IDs are alliance IDs, which name no faction, so
            // no two distinct blocs there ever stand together - the rule stays uniform with no
            // per-layer branch.
            var alliances = new FactionAlliances(Map.of(
                "hegemony", "group-1",
                "tritachyon", "group-2"));

            assertThat(new BlocAffiliation(alliances).areOwnersAllied("group-1", "group-2"))
                .isFalse();
        }

        @Test
        void isFalseForEveryDistinctPairWithNoAlliances() {

            assertThat(new BlocAffiliation(FactionAlliances.NONE).areOwnersAllied("hegemony", "astral_armada"))
                .isFalse();
        }
    }

    // Two allied factions with tritachyon left an outsider, so a case can probe the allied, the
    // rival, and the unknown path off one instance.
    private static BlocAffiliation buildAlliedAffiliation() {
        return new BlocAffiliation(buildAllianceOf("hegemony", "astral_armada"));
    }
}
