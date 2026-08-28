package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link BlocAffiliation}'s one predicate: two distinct blocs stand together exactly when the
 * alliance set folds them into one bloc, and under {@link BlocAffiliation#NONE} no two blocs ever
 * do. These are the answers every contest judgement reads, so they are fixed here on hand-built
 * groupings free of any live alliance.
 */
class BlocAffiliationTest {

    @Nested
    class AreBlocsAllied {

        @Test
        void isTrueForTwoFactionsFoldedIntoOneAlliance() {

            assertThat(buildAlliedAffiliation().areBlocsAllied("hegemony", "astral_armada"))
                .isTrue();
        }

        @Test
        void isFalseForAFactionOutsideTheAlliance() {

            assertThat(buildAlliedAffiliation().areBlocsAllied("hegemony", "tritachyon"))
                .isFalse();
        }

        @Test
        void isFalseForABlocAgainstItself() {
            // One bloc named twice is one side of a contest, not two standing together; whether a
            // bloc is the painter is asked at the judging site as a question of its own.
            assertThat(buildAlliedAffiliation().areBlocsAllied("hegemony", "hegemony"))
                .isFalse();
        }

        @Test
        void isFalseForABlocTheAllianceSetNeverNamed() {
            // An unknown id falls through to itself, so it stands with nothing - neither with an
            // alliance member nor with another unknown.
            assertThat(buildAlliedAffiliation().areBlocsAllied("pirates", "hegemony"))
                .isFalse();
            assertThat(buildAlliedAffiliation().areBlocsAllied("pirates", "luddic_church"))
                .isFalse();
        }

        @Test
        void isFalseForABlocWithNoId() {
            // A bloc with no id resolves to no bloc, and two such ids must not read as standing
            // together merely because both resolved to nothing.
            assertThat(buildAlliedAffiliation().areBlocsAllied(null, "hegemony"))
                .isFalse();
            assertThat(buildAlliedAffiliation().areBlocsAllied(" ", "  "))
                .isFalse();
        }

        @Test
        void isFalseForTwoAllianceBlocIds() {
            // The alliances-layer case: the pass's bloc ids are alliance ids the alliance set names
            // no faction for, so each falls through to itself and no two distinct blocs there ever
            // stand together - the rule stays uniform with no per-layer branch.
            var grouping = new HolderGrouping(
                Map.of(
                    "hegemony",
                    "alliance-1",
                    "astral_armada",
                    "alliance-1",
                    "tritachyon",
                    "alliance-2",
                    "persean_league",
                    "alliance-2"),
                Map.of("alliance-1", "hegemony", "alliance-2", "tritachyon"),
                Map.of("alliance-1", "Allied Powers", "alliance-2", "Rival Powers"));

            assertThat(new BlocAffiliation(grouping).areBlocsAllied("alliance-1", "alliance-2"))
                .isFalse();
        }

        @Test
        void isFalseForEveryDistinctPairUnderNone() {
            // The Nex-free install's affiliation: the identity grouping read as an alliance set
            // folds nothing, so factions allied in some other grouping stand apart under it.
            assertThat(BlocAffiliation.NONE.areBlocsAllied("hegemony", "astral_armada"))
                .isFalse();
        }
    }

    // Two allied factions folded into one bloc with tritachyon left an outsider, so a case can
    // probe the allied, the rival, and the unknown path off one instance.
    private static BlocAffiliation buildAlliedAffiliation() {
        return new BlocAffiliation(HolderGroupingFixture.buildAllianceOf("hegemony", "astral_armada"));
    }
}
