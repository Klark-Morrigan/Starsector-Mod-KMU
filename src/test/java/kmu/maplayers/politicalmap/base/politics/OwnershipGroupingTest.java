package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Pins {@link OwnershipGrouping}'s lookups: the identity grouping leaves every
 * faction its own bloc, and an alliance grouping folds members into the bloc while
 * outsiders stay themselves. These are the answers the regroup step and the render
 * rules read, so they are fixed here on hand-built maps free of any live alliance.
 */
class OwnershipGroupingTest {

    @Nested
    class Identity {

        @Test
        void mapsEveryFactionToItself() {
            assertThat(OwnershipGrouping.identity().resolveBlocId("hegemony"))
                    .isEqualTo("hegemony");
        }

        @Test
        void colorsEveryBlocAsItself() {
            assertThat(OwnershipGrouping.identity().resolveColorFactionId("hegemony"))
                    .isEqualTo("hegemony");
        }

        @Test
        void treatsNoBlocAsAnAlliance() {
            assertThat(OwnershipGrouping.identity().isAlliance("hegemony")).isFalse();
            assertThat(OwnershipGrouping.identity().resolveAllianceName("hegemony")).isNull();
        }
    }

    @Nested
    class ResolveBlocId {

        @Test
        void mapsAnAlliedFactionToItsBloc() {
            assertThat(allianceGrouping().resolveBlocId("hegemony")).isEqualTo("alliance-1");
        }

        @Test
        void leavesAnOutsiderAsItsOwnBloc() {
            assertThat(allianceGrouping().resolveBlocId("tritachyon")).isEqualTo("tritachyon");
        }
    }

    @Nested
    class ResolveColorFactionId {

        @Test
        void namesTheAlliancesDominantMember() {
            assertThat(allianceGrouping().resolveColorFactionId("alliance-1"))
                    .isEqualTo("hegemony");
        }

        @Test
        void colorsAFactionBlocAsItself() {
            assertThat(allianceGrouping().resolveColorFactionId("tritachyon"))
                    .isEqualTo("tritachyon");
        }
    }

    @Nested
    class ResolveAllianceName {

        @Test
        void carriesTheAllianceName() {
            assertThat(allianceGrouping().resolveAllianceName("alliance-1"))
                    .isEqualTo("Allied Powers");
        }

        @Test
        void returnsNullForAFactionBloc() {
            assertThat(allianceGrouping().resolveAllianceName("tritachyon")).isNull();
        }
    }

    @Nested
    class IsAlliance {

        @Test
        void isTrueForAnAllianceBloc() {
            assertThat(allianceGrouping().isAlliance("alliance-1")).isTrue();
        }

        @Test
        void isFalseForAFactionBloc() {
            assertThat(allianceGrouping().isAlliance("tritachyon")).isFalse();
        }
    }

    @Nested
    class RegroupByBloc {

        @Test
        void collapsesSameBlocFactionsThroughTheMerge() {
            // Two factions the grouping folds into one bloc merge their values into a single entry, so
            // an alliance's members rank as one summed unit.
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(grouping.regroupByBloc(valueByFactionId, 0, Integer::sum))
                    .containsExactly(entry("alliance-1", 5));
        }

        @Test
        void keepsFactionsInDistinctBlocsSeparate() {
            // Only the mapped faction folds into its bloc; an unmapped faction stays its own bloc, so
            // the two never merge.
            var grouping = new OwnershipGrouping(
                    Map.of("hegemony", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of("alliance-1", "Allied Powers"));
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(grouping.regroupByBloc(valueByFactionId, 0, Integer::sum))
                    .containsOnly(entry("alliance-1", 2), entry("tritachyon", 3));
        }

        @Test
        void leavesEachFactionsValueUnchangedUnderTheIdentityGrouping() {
            // Under identity every faction is its own bloc, so each value merges into the fold's
            // identity alone and comes out unchanged - the no-op the render's faction view relies on.
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(OwnershipGrouping.identity().regroupByBloc(valueByFactionId, 0, Integer::sum))
                    .containsOnly(entry("hegemony", 2), entry("tritachyon", 3));
        }

        @Test
        void preservesTheFirstSeenBlocOrderOfTheInput() {
            // The fold keeps each bloc in the order it first appears in the faction walk, not sorted,
            // so the picker's economy-walk ordering flows straight through the regroup.
            var valueByFactionId = new LinkedHashMap<String, Integer>();
            valueByFactionId.put("z-faction", 1);
            valueByFactionId.put("a-faction", 2);

            assertThat(OwnershipGrouping.identity().regroupByBloc(valueByFactionId, 0, Integer::sum))
                    .containsExactly(entry("z-faction", 1), entry("a-faction", 2));
        }
    }

    // Two allied factions folded into one bloc whose colour faction and dominant
    // member is hegemony, with tritachyon left an outsider mapped to itself, so a
    // test can probe both the grouped and the ungrouped path off one instance.
    private static OwnershipGrouping allianceGrouping() {
        return new OwnershipGrouping(
                Map.of("hegemony", "alliance-1", "astral_armada", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));
    }
}
