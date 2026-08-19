package kmu.maplayers.politicalmap.base.dominance;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Pins {@link HolderGrouping}'s lookups: the identity grouping leaves every
 * faction its own bloc, and an alliance grouping folds members into the bloc while
 * outsiders stay themselves. These are the answers the regroup step and the render
 * rules read, so they are fixed here on hand-built maps free of any live alliance.
 */
class HolderGroupingTest {

    @Nested
    class Identity {

        @Test
        void mapsEveryFactionToItself() {
            assertThat(HolderGrouping.identity().resolveBlocId("hegemony"))
                .isEqualTo("hegemony");
        }

        @Test
        void coloursEveryBlocAsItself() {
            assertThat(HolderGrouping.identity().resolveColourFactionId("hegemony"))
                .isEqualTo("hegemony");
        }

        @Test
        void treatsNoBlocAsAnAlliance() {

            assertThat(HolderGrouping.identity().isAlliance("hegemony"))
                .isFalse();
            assertThat(HolderGrouping.identity().resolveAllianceName("hegemony"))
                .isNull();
        }
    }

    @Nested
    class ResolveBlocId {

        @Test
        void mapsAnAlliedFactionToItsBloc() {
            assertThat(buildAllianceGrouping().resolveBlocId("hegemony"))
                .isEqualTo("alliance-1");
        }

        @Test
        void leavesAnOutsiderAsItsOwnBloc() {
            assertThat(buildAllianceGrouping().resolveBlocId("tritachyon"))
                .isEqualTo("tritachyon");
        }
    }

    @Nested
    class UnnamedIds {

        // A faction the game itself would always have named, and a mod may not. Every lookup here
        // reads an immutable map, which faults on a null key rather than reporting it absent - even
        // the identity grouping's empty ones - so an owner with no id would otherwise take down
        // whatever walk reached it: a band's count, the dominance regroup, or a render rule asking
        // whether its bloc is an alliance.
        @Test
        void resolveBlocIdNamesNoBlocForAFactionWithNoId() {

            assertThat(buildAllianceGrouping().resolveBlocId(null))
                .isNull();
            assertThat(HolderGrouping.identity().resolveBlocId(" "))
                .isNull();
        }

        @Test
        void resolveColourFactionIdNamesNoPaletteForABlocWithNoId() {

            assertThat(buildAllianceGrouping().resolveColourFactionId(null))
                .isNull();
            assertThat(HolderGrouping.identity().resolveColourFactionId(" "))
                .isNull();
        }

        @Test
        void resolveAllianceNameReadsABlocWithNoIdAsNoAlliance() {
            
            assertThat(buildAllianceGrouping().resolveAllianceName(null))
                .isNull();
            assertThat(buildAllianceGrouping().isAlliance(null))
                .isFalse();
        }
    }

    @Nested
    class ResolveColourFactionId {

        @Test
        void namesTheAlliancesDominantMember() {
            assertThat(buildAllianceGrouping().resolveColourFactionId("alliance-1"))
                .isEqualTo("hegemony");
        }

        @Test
        void coloursAFactionBlocAsItself() {
            assertThat(buildAllianceGrouping().resolveColourFactionId("tritachyon"))
                .isEqualTo("tritachyon");
        }
    }

    @Nested
    class ResolveAllianceName {

        @Test
        void carriesTheAllianceName() {
            assertThat(buildAllianceGrouping().resolveAllianceName("alliance-1"))
                .isEqualTo("Allied Powers");
        }

        @Test
        void returnsNullForAFactionBloc() {
            assertThat(buildAllianceGrouping().resolveAllianceName("tritachyon"))
                .isNull();
        }
    }

    @Nested
    class IsAlliance {

        @Test
        void isTrueForAnAllianceBloc() {
            assertThat(buildAllianceGrouping().isAlliance("alliance-1"))
                .isTrue();
        }

        @Test
        void isFalseForAFactionBloc() {
            assertThat(buildAllianceGrouping().isAlliance("tritachyon"))
                .isFalse();
        }
    }

    @Nested
    class HasAnyAlliance {

        @Test
        void isTrueWhenTheGroupingHoldsAnAlliance() {
            assertThat(buildAllianceGrouping().hasAnyAlliance())
                .isTrue();
        }

        @Test
        void isFalseForTheIdentityGrouping() {
            // Nothing is grouped, so every bloc is a lone faction - the state a render rule that sets
            // alliances against a backdrop must tell from "grouped, and these are the outsiders".
            assertThat(HolderGrouping.identity().hasAnyAlliance())
                .isFalse();
        }

        @Test
        void isFalseWhenFactionsAreFoldedButNoBlocIsNamed() {
            // The alliance-name map alone answers this, matching isAlliance. A grouping carrying a
            // fold with no named bloc has no alliance to read, whatever the other two maps hold.
            var grouping = new HolderGrouping(
                    Map.of("hegemony", "alliance-1"),
                    Map.of("alliance-1", "hegemony"),
                    Map.of());

            assertThat(grouping.hasAnyAlliance())
                .isFalse();
        }
    }

    @Nested
    class RegroupByBloc {

        @Test
        void collapsesSameBlocFactionsThroughTheMerge() {
            // Two factions the grouping folds into one bloc merge their values into a single entry, so
            // an alliance's members rank as one summed unit.
            var grouping = new HolderGrouping(
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
            var grouping = new HolderGrouping(
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

            assertThat(HolderGrouping.identity().regroupByBloc(valueByFactionId, 0, Integer::sum))
                .containsOnly(entry("hegemony", 2), entry("tritachyon", 3));
        }

        @Test
        void leavesOutAFactionItCanNameNoBlocFor() {
            // The one thing the fold decides that the lookup cannot. A nameless key would travel on
            // as a bloc, and two such owners would merge into one entry naming neither of them -
            // one run, one fill, one row. The named factions beside it fold as they always do.
            var valueByFactionId = new LinkedHashMap<String, Integer>();

            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put(null, 5);
            valueByFactionId.put("tritachyon", 3);

            assertThat(HolderGrouping.identity().regroupByBloc(valueByFactionId, 0, Integer::sum))
                .containsOnly(entry("hegemony", 2), entry("tritachyon", 3));
        }

        @Test
        void preservesTheFirstSeenBlocOrderOfTheInput() {
            // The fold keeps each bloc in the order it first appears in the faction walk, not sorted,
            // so the picker's economy-walk ordering flows straight through the regroup.
            var valueByFactionId = new LinkedHashMap<String, Integer>();

            valueByFactionId.put("z-faction", 1);
            valueByFactionId.put("a-faction", 2);

            assertThat(HolderGrouping.identity().regroupByBloc(valueByFactionId, 0, Integer::sum))
                .containsExactly(entry("z-faction", 1), entry("a-faction", 2));
        }
    }

    @Nested
    class CollectBlocIds {

        @Test
        void namesABlocOnceHoweverManyOfItsMembersAreThere() {
            // The id-level fold's own case: a caller asking who is present wants the blocs it would
            // paint, so an alliance's two members are one bloc there rather than two.
            var grouping = new HolderGrouping(
                Map.of("hegemony", "alliance-1", "tritachyon", "alliance-1"),
                Map.of("alliance-1", "hegemony"),
                Map.of("alliance-1", "Allied Powers"));

            assertThat(grouping.collectBlocIds(List.of("hegemony", "tritachyon")))
                .containsExactly("alliance-1");
        }

        @Test
        void keepsFactionsInDistinctBlocsSeparate() {
            assertThat(HolderGrouping.identity().collectBlocIds(List.of("hegemony", "tritachyon")))
                .containsExactly("hegemony", "tritachyon");
        }

        @Test
        void leavesOutAFactionItCanNameNoBlocFor() {
            // The same rule the value fold applies, stated once for both: a nameless key would
            // travel on as a bloc that nothing downstream could name, colour, or rank.
            var factionIds = new ArrayList<String>();

            factionIds.add("hegemony");
            factionIds.add(null);

            assertThat(HolderGrouping.identity().collectBlocIds(factionIds))
                .containsExactly("hegemony");
        }

        @Test
        void preservesTheFirstSeenBlocOrderOfTheInput() {
            // Ordered by the walk that read the factions, not sorted, so a caller that had an order
            // worth keeping keeps it.
            assertThat(HolderGrouping.identity().collectBlocIds(List.of("z-faction", "a-faction")))
                .containsExactly("z-faction", "a-faction");
        }
    }

    // Two allied factions folded into one bloc whose colour faction and dominant
    // member is hegemony, with tritachyon left an outsider mapped to itself, so a
    // test can probe both the grouped and the unowned path off one instance.
    private static HolderGrouping buildAllianceGrouping() {
        return new HolderGrouping(
            Map.of("hegemony", "alliance-1", "astral_armada", "alliance-1"),
            Map.of("alliance-1", "hegemony"),
            Map.of("alliance-1", "Allied Powers"));
    }
}
