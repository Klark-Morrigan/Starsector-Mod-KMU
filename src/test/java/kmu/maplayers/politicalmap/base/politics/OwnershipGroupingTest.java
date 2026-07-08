package kmu.maplayers.politicalmap.base.politics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
