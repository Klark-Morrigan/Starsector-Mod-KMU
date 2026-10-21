package kmu.maplayers.ownermap.owners.holders.base;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Pins {@link HolderGrouping}'s lookups: the identity grouping leaves every
 * faction its own bloc, and a grouping holding a group folds members into the bloc while
 * outsiders stay themselves. These are the answers the regroup step and the render
 * rules read, so they are fixed here on hand-built maps free of any live group source.
 *
 * <p>A faction with no ID is posed against every lookup. The game itself always names its
 * factions and a mod may not, and each lookup reads an immutable map, which faults on a null key
 * rather than reporting it absent - even the identity grouping's empty ones. Unguarded, such an
 * owner would take down whatever walk reached it: a band's count, a footprint regroup, or a render
 * rule asking whether its bloc is a group.
 */
final class HolderGroupingTests {

    @Nested
    class Constructor {

        @Test
        void keepsItsLookupsWhenTheSourceMapsChangeAfterwards() {
            // A grouping is sampled once per rebuild and read for the whole pass, so a supplier
            // reusing its maps for the next sampling must not move the fold under a pass in flight.
            var blocIdByFactionId = new HashMap<>(Map.of("hegemony", "group-1"));
            var colourFactionIdByBlocId = new HashMap<>(Map.of("group-1", "hegemony"));
            var groupNameByBlocId = new HashMap<>(Map.of("group-1", "Allied Powers"));

            var grouping = new HolderGrouping(blocIdByFactionId, colourFactionIdByBlocId, groupNameByBlocId);

            blocIdByFactionId.put("tritachyon", "group-1");
            colourFactionIdByBlocId.put("group-1", "tritachyon");
            groupNameByBlocId.put("group-2", "Free Traders");

            assertThat(grouping.resolveBlocId("tritachyon"))
                .isEqualTo("tritachyon");
            assertThat(grouping.resolveColourFactionId("group-1"))
                .isEqualTo("hegemony");
            assertThat(grouping.isGroupedBloc("group-2"))
                .isFalse();
        }
    }

    @Nested
    class Identity {

        @Test
        void answersTheOneSharedGrouping() {
            // Every entry point taking no explicit grouping falls back to this, so it is one value
            // rather than a fresh one per ask.
            assertThat(HolderGrouping.identity())
                .isSameAs(HolderGrouping.identity());
        }

        @Test
        void groupsNoFactionAwayFromItself() {
            // The sparse maps are what make every lookup fall through to the faction itself, so
            // the identity grouping is all three of them empty.
            assertThat(HolderGrouping.identity())
                .isEqualTo(new HolderGrouping(Map.of(), Map.of(), Map.of()));
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

        @Test
        void mapsEveryFactionToItselfUnderTheIdentityGrouping() {

            assertThat(HolderGrouping.identity().resolveBlocId("hegemony"))
                .isEqualTo("hegemony");
        }

        @Test
        void namesNoBlocForAFactionWithNoId() {

            assertThat(buildSingleGroupGrouping().resolveBlocId(null))
                .isNull();
            assertThat(HolderGrouping.identity().resolveBlocId(" "))
                .isNull();
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

        @Test
        void coloursEveryBlocAsItselfUnderTheIdentityGrouping() {

            assertThat(HolderGrouping.identity().resolveColourFactionId("hegemony"))
                .isEqualTo("hegemony");
        }

        @Test
        void namesNoPaletteForABlocWithNoId() {

            assertThat(buildSingleGroupGrouping().resolveColourFactionId(null))
                .isNull();
            assertThat(HolderGrouping.identity().resolveColourFactionId(" "))
                .isNull();
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

        @Test
        void namesNoGroupUnderTheIdentityGrouping() {

            assertThat(HolderGrouping.identity().resolveGroupName("hegemony"))
                .isNull();
        }

        @Test
        void readsABlocWithNoIdAsNoGroup() {

            assertThat(buildSingleGroupGrouping().resolveGroupName(null))
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

        @Test
        void isFalseForEveryBlocUnderTheIdentityGrouping() {

            assertThat(HolderGrouping.identity().isGroupedBloc("hegemony"))
                .isFalse();
        }

        @Test
        void isFalseForABlocWithNoId() {

            assertThat(buildSingleGroupGrouping().isGroupedBloc(null))
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
        void collapsesSameBlocFactionsThroughTheMergeStartingFromTheIdentity() {
            // Two factions the grouping folds into one bloc merge their values into a single entry, so
            // a group's members rank as one unit. The merge concatenates from a marker identity, so
            // the entry shows the identity was taken once and the members merged in walk order.
            var valueByFactionId = new LinkedHashMap<String, String>();

            valueByFactionId.put("hegemony", "a");
            valueByFactionId.put("tritachyon", "b");

            assertThat(HolderGroupingFixture.buildGroupOf("hegemony", "tritachyon")
                    .regroupByBloc(valueByFactionId, "^", String::concat))
                .containsExactly(entry("group-1", "^ab"));
        }

        @Test
        void keepsFactionsInDistinctBlocsSeparate() {
            // Only the mapped faction folds into its bloc; an unmapped faction stays its own bloc, so
            // the two never merge.
            var valueByFactionId = new LinkedHashMap<String, Integer>();

            valueByFactionId.put("hegemony", 2);
            valueByFactionId.put("tritachyon", 3);

            assertThat(HolderGroupingFixture.buildGroupOf("hegemony")
                    .regroupByBloc(valueByFactionId, 0, Integer::sum))
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

        @Test
        void namesNoMemberForABlocWithNoId() {
            // The lone-faction fallback must not fire here: a bloc with no ID standing in as its own
            // sole member would hand a rule read over membership a member named nothing at all.
            assertThat(buildSingleGroupGrouping().resolveMemberFactionIds(null))
                .isEmpty();
            assertThat(HolderGrouping.identity().resolveMemberFactionIds(" "))
                .isEmpty();
        }
    }

    // Two allied factions folded into one bloc whose colour faction and leading member is the
    // Hegemony, with Tri-Tachyon left an outsider mapped to itself, so a case can probe both the
    // grouped and the lone-faction path off one instance.
    private static HolderGrouping buildSingleGroupGrouping() {
        return HolderGroupingFixture.buildGroupOf("hegemony", "astral_armada");
    }
}
