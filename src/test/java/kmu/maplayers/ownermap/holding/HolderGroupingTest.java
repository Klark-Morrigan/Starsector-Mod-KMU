package kmu.maplayers.ownermap.holding;

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
 * faction its own bloc, and a grouping holding a group folds members into the bloc while
 * outsiders stay themselves. These are the answers the regroup step and the render
 * rules read, so they are fixed here on hand-built maps free of any live group source.
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
        void treatsNoBlocAsAGroup() {

            assertThat(HolderGrouping.identity().isGroupedBloc("hegemony"))
                .isFalse();
            assertThat(HolderGrouping.identity().resolveGroupName("hegemony"))
                .isNull();
        }
    }

    @Nested
    class ResolveBlocId {

        @Test
        void mapsAnAlliedFactionToItsBloc() {

            assertThat(buildSingleGroupGrouping().resolveBlocId("hegemony"))
                .isEqualTo("group-1");
        }

        @Test
        void leavesAnOutsiderAsItsOwnBloc() {

            assertThat(buildSingleGroupGrouping().resolveBlocId("tritachyon"))
                .isEqualTo("tritachyon");
        }
    }

    @Nested
    class UnnamedIds {

        // A faction the game itself would always have named, and a mod may not. Every lookup here
        // reads an immutable map, which faults on a null key rather than reporting it absent - even
        // the identity grouping's empty ones - so an owner with no ID would otherwise take down
        // whatever walk reached it: a band's count, a footprint regroup, or a render rule asking
        // whether its bloc is a group.
        @Test
        void resolveBlocIdNamesNoBlocForAFactionWithNoId() {

            assertThat(buildSingleGroupGrouping().resolveBlocId(null))
                .isNull();
            assertThat(HolderGrouping.identity().resolveBlocId(" "))
                .isNull();
        }

        @Test
        void resolveColourFactionIdNamesNoPaletteForABlocWithNoId() {

            assertThat(buildSingleGroupGrouping().resolveColourFactionId(null))
                .isNull();
            assertThat(HolderGrouping.identity().resolveColourFactionId(" "))
                .isNull();
        }

        @Test
        void resolveGroupNameReadsABlocWithNoIdAsNoGroup() {

            assertThat(buildSingleGroupGrouping().resolveGroupName(null))
                .isNull();
            assertThat(buildSingleGroupGrouping().isGroupedBloc(null))
                .isFalse();
        }

        @Test
        void resolveMemberFactionIdsNamesNoMemberForABlocWithNoId() {
            // The lone-faction fallback must not fire here: a bloc with no ID standing in as its own
            // sole member would hand a rule read over membership a member named nothing at all.
            assertThat(buildSingleGroupGrouping().resolveMemberFactionIds(null))
                .isEmpty();
            assertThat(HolderGrouping.identity().resolveMemberFactionIds(" "))
                .isEmpty();
        }
    }

    @Nested
    class ResolveColourFactionId {

        @Test
        void namesTheGroupsColourMember() {

            assertThat(buildSingleGroupGrouping().resolveColourFactionId("group-1"))
                .isEqualTo("hegemony");
        }

        @Test
        void coloursAFactionBlocAsItself() {

            assertThat(buildSingleGroupGrouping().resolveColourFactionId("tritachyon"))
                .isEqualTo("tritachyon");
        }
    }

    @Nested
    class ResolveGroupName {

        @Test
        void carriesTheGroupName() {

            assertThat(buildSingleGroupGrouping().resolveGroupName("group-1"))
                .isEqualTo("Allied Powers");
        }

        @Test
        void returnsNullForAFactionBloc() {

            assertThat(buildSingleGroupGrouping().resolveGroupName("tritachyon"))
                .isNull();
        }
    }

    @Nested
    class IsGroupedBloc {

        @Test
        void isTrueForAGroupedBloc() {

            assertThat(buildSingleGroupGrouping().isGroupedBloc("group-1"))
                .isTrue();
        }

        @Test
        void isFalseForAFactionBloc() {

            assertThat(buildSingleGroupGrouping().isGroupedBloc("tritachyon"))
                .isFalse();
        }
    }

    @Nested
    class HasAnyGroupedBloc {

        @Test
        void isTrueWhenTheGroupingHoldsAGroup() {

            assertThat(buildSingleGroupGrouping().hasAnyGroupedBloc())
                .isTrue();
        }

        @Test
        void isFalseForTheIdentityGrouping() {
            // Nothing is grouped, so every bloc is a lone faction - the state a render rule that sets
            // groups against a backdrop must tell from "grouped, and these are the outsiders".
            assertThat(HolderGrouping.identity().hasAnyGroupedBloc())
                .isFalse();
        }

        @Test
        void isFalseWhenFactionsAreFoldedButNoBlocIsNamed() {
            // The group-name map alone answers this, matching isGroupedBloc. A grouping carrying a
            // fold with no named bloc has no group to read, whatever the other two maps hold.
            var grouping = new HolderGrouping(
                    Map.of("hegemony", "group-1"),
                    Map.of("group-1", "hegemony"),
                    Map.of());

            assertThat(grouping.hasAnyGroupedBloc())
                .isFalse();
        }
    }

    @Nested
    class RegroupByBloc {

        @Test
        void collapsesSameBlocFactionsThroughTheMerge() {
            // Two factions the grouping folds into one bloc merge their values into a single entry, so
            // a group's members rank as one summed unit.
            var grouping = new HolderGrouping(
                Map.of("hegemony", "group-1", "tritachyon", "group-1"),
                Map.of("group-1", "hegemony"),
                Map.of("group-1", "Allied Powers"));

            var valueByFactionId = new LinkedHashMap<String, Integer>();

            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(grouping.regroupByBloc(valueByFactionId, 0, Integer::sum))
                .containsExactly(entry("group-1", 5));
        }

        @Test
        void keepsFactionsInDistinctBlocsSeparate() {
            // Only the mapped faction folds into its bloc; an unmapped faction stays its own bloc, so
            // the two never merge.
            var grouping = new HolderGrouping(
                Map.of("hegemony", "group-1"),
                Map.of("group-1", "hegemony"),
                Map.of("group-1", "Allied Powers"));

            var valueByFactionId = new LinkedHashMap<String, Integer>();

            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(grouping.regroupByBloc(valueByFactionId, 0, Integer::sum))
                .containsOnly(entry("group-1", 2), entry("tritachyon", 3));
        }

        @Test
        void leavesEachFactionsValueUnchangedUnderTheIdentityGrouping() {
            // Under identity every faction is its own bloc, so each value merges into the fold's
            // identity alone and comes out unchanged - the no-op an ungrouped view relies on.
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
            // paint, so a group's two members are one bloc there rather than two.
            var grouping = new HolderGrouping(
                Map.of("hegemony", "group-1", "tritachyon", "group-1"),
                Map.of("group-1", "hegemony"),
                Map.of("group-1", "Allied Powers"));

            assertThat(grouping.collectBlocIds(List.of("hegemony", "tritachyon")))
                .containsExactly("group-1");
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

    @Nested
    class ResolveMemberFactionIds {

        @Test
        void namesEveryFactionFoldedIntoAGroupedBloc() {

            assertThat(buildSingleGroupGrouping().resolveMemberFactionIds("group-1"))
                .containsExactlyInAnyOrder("hegemony", "astral_armada");
        }

        @Test
        void namesOnlyTheFactionsFoldedIntoTheBlocAskedAbout() {
            // Two groups at once, which is the only shape that pins the read as a filter rather
            // than as a walk: with one group in the grouping, every folded faction is that bloc's
            // and a read ignoring which bloc it was asked about comes back correct all the same.
            var grouping = new HolderGrouping(
                Map.of(
                    "hegemony", "group-1",
                    "astral_armada", "group-1",
                    "tritachyon", "group-2",
                    "luddic_church", "group-2"),
                Map.of("group-1", "hegemony", "group-2", "tritachyon"),
                Map.of("group-1", "Allied Powers", "group-2", "Free Traders"));

            assertThat(grouping.resolveMemberFactionIds("group-2"))
                .containsExactlyInAnyOrder("tritachyon", "luddic_church");
        }

        @Test
        void namesALoneFactionBlocAsItsOwnSoleMember() {
            // Nothing folds into an outsider's bloc, and its ID is that faction's own - the
            // membership of one every read under no group comes back with.
            assertThat(buildSingleGroupGrouping().resolveMemberFactionIds("tritachyon"))
                .containsExactly("tritachyon");
        }

        @Test
        void namesEveryBlocAsOneFactionUnderTheIdentityGrouping() {

            assertThat(HolderGrouping.identity().resolveMemberFactionIds("hegemony"))
                .containsExactly("hegemony");
        }
    }

    // Two allied factions folded into one bloc whose colour faction and leading
    // member is hegemony, with tritachyon left an outsider mapped to itself, so a
    // test can probe both the grouped and the unowned path off one instance.
    private static HolderGrouping buildSingleGroupGrouping() {
        return new HolderGrouping(
            Map.of("hegemony", "group-1", "astral_armada", "group-1"),
            Map.of("group-1", "hegemony"),
            Map.of("group-1", "Allied Powers"));
    }
}
