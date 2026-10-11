package kmu.maplayers.ownermap.owners.holders.alliances;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderGroupingFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link BlocAffiliation}'s one predicate: two distinct blocs stand together exactly when the
 * affiliation grouping folds them into one bloc, and over the identity grouping no two blocs ever
 * do. These are the answers every contest judgement reads, so they are fixed here on hand-built
 * groupings free of any live group source.
 */
final class BlocAffiliationTests {

    @Nested
    class Constructor {

        @Test
        void rejectsNullAffiliationGrouping() {
            // An affiliation with no grouping behind it would fault on the first pair it was
            // asked about rather than at the binding point that failed to supply one; the absence
            // of groups is a value of its own - HolderGrouping.identity() - never a null.
            assertThatThrownBy(() -> new BlocAffiliation(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("affiliationGrouping");
        }
    }

    @Nested
    class AreOwnersAllied {

        @Test
        void isTrueForTwoFactionsFoldedIntoOneGroup() {
            // Asserted in both argument orders: standing together has no direction, so neither may
            // the predicate.
            assertThat(buildAlliedAffiliation().areOwnersAllied("hegemony", "astral_armada"))
                .isTrue();
            assertThat(buildAlliedAffiliation().areOwnersAllied("astral_armada", "hegemony"))
                .isTrue();
        }

        @Test
        void isFalseForAFactionOutsideTheGroup() {

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
        void isFalseForABlocTheAffiliationNeverNamed() {
            // An unknown ID falls through to itself, so it stands with nothing - neither with a
            // group member nor with another unknown.
            assertThat(buildAlliedAffiliation().areOwnersAllied("pirates", "hegemony"))
                .isFalse();
            assertThat(buildAlliedAffiliation().areOwnersAllied("pirates", "luddic_church"))
                .isFalse();
        }

        @Test
        void isFalseForABlocWithNoId() {
            // A bloc with no ID resolves to no bloc, and two such IDs must not read as standing
            // together merely because both resolved to nothing.
            assertThat(buildAlliedAffiliation().areOwnersAllied(null, "hegemony"))
                .isFalse();
            assertThat(buildAlliedAffiliation().areOwnersAllied(" ", "  "))
                .isFalse();
        }

        @Test
        void isFalseForTwoGroupBlocIds() {
            // A layer painting groups: the pass's bloc IDs are group IDs the affiliation grouping
            // names no faction for, so each falls through to itself and no two distinct blocs there ever
            // stand together - the rule stays uniform with no per-layer branch.
            var grouping = new HolderGrouping(
                Map.of("hegemony", "group-1", "tritachyon", "group-2"),
                Map.of("group-1", "hegemony", "group-2", "tritachyon"),
                Map.of("group-1", "Allied Powers", "group-2", "Rival Powers"));

            assertThat(new BlocAffiliation(grouping).areOwnersAllied("group-1", "group-2"))
                .isFalse();
        }

        @Test
        void isFalseForEveryDistinctPairOverTheIdentityGrouping() {
            // The affiliation of an install with nothing grouping factions: the identity grouping read
            // as an affiliation folds nothing, so factions allied in some other grouping stand apart under it.
            assertThat(new BlocAffiliation(HolderGrouping.identity()).areOwnersAllied("hegemony", "astral_armada"))
                .isFalse();
        }
    }

    // Two allied factions folded into one bloc with tritachyon left an outsider, so a case can
    // probe the allied, the rival, and the unknown path off one instance.
    private static BlocAffiliation buildAlliedAffiliation() {
        return new BlocAffiliation(HolderGroupingFixture.buildGroupOf("hegemony", "astral_armada"));
    }
}
