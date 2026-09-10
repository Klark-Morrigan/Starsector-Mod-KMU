package kmu.mods.nexerelin.alliances;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link AllianceGroupingFactory}'s fold from plain alliance records to an
 * {@link HolderGrouping}: members share their alliance's bloc, the bloc colours off
 * the sorted-first member and carries the name, and outsiders, empty records, and
 * overlapping membership all resolve deterministically. Built on hand-made records so
 * the factory is proven without a live Nexerelin.
 */
class AllianceGroupingFactoryTest {

    @Nested
    class BuildFrom {

        @Test
        void mapsEveryMemberToItsBloc() {

            var grouping = AllianceGroupingFactory.buildFrom(List.of(buildAlliedPowers()));

            assertThat(grouping.resolveBlocId("hegemony"))
                .isEqualTo("alliance-1");
            assertThat(grouping.resolveBlocId("astral_armada"))
                .isEqualTo("alliance-1");
        }

        @Test
        void coloursTheBlocOffTheSortedFirstMember() {

            var grouping = AllianceGroupingFactory.buildFrom(List.of(buildAlliedPowers()));

            assertThat(grouping.resolveColourFactionId("alliance-1"))
                .isEqualTo("hegemony");
        }

        @Test
        void carriesTheAllianceName() {

            var grouping = AllianceGroupingFactory.buildFrom(List.of(buildAlliedPowers()));

            assertThat(grouping.resolveAllianceName("alliance-1"))
                .isEqualTo("Allied Powers");
            assertThat(grouping.isAlliance("alliance-1"))
                .isTrue();
        }

        @Test
        void leavesAFactionInNoRecordAsItsOwnBloc() {

            var grouping = AllianceGroupingFactory.buildFrom(List.of(buildAlliedPowers()));

            assertThat(grouping.resolveBlocId("tritachyon"))
                .isEqualTo("tritachyon");
            assertThat(grouping.isAlliance("tritachyon"))
                .isFalse();
        }

        @Test
        void yieldsTheIdentityGroupingForNoRecords() {

            var grouping = AllianceGroupingFactory.buildFrom(List.of());

            assertThat(grouping.resolveBlocId("hegemony"))
                .isEqualTo("hegemony");
            assertThat(grouping.isAlliance("hegemony"))
                .isFalse();
        }

        @Test
        void ignoresAMemberlessRecord() {

            var grouping = AllianceGroupingFactory.buildFrom(List.of(
                new AllianceRecord("empty-alliance", "Empty Pact", List.of())));

            // No member folds into it and no colour faction is picked, so the bloc id is
            // not an alliance and colours as itself - the record left no trace.
            assertThat(grouping.isAlliance("empty-alliance"))
                .isFalse();
            assertThat(grouping.resolveColourFactionId("empty-alliance"))
                .isEqualTo("empty-alliance");
        }

        @Test
        void resolvesAnOverlappingMemberToTheLaterRecord() {
            // hegemony is named by both records; the fold visits them in list order, so
            // the second record wins and the mapping stays total and deterministic.
            var grouping = AllianceGroupingFactory.buildFrom(List.of(
                buildAlliedPowers(),
                new AllianceRecord(
                    "alliance-2",
                    "Rival Bloc",
                    List.of("hegemony", "luddic_church"))));

            assertThat(grouping.resolveBlocId("hegemony"))
                .isEqualTo("alliance-2");
            assertThat(grouping.resolveBlocId("astral_armada"))
                .isEqualTo("alliance-1");
        }
    }

    // Two members ranked hegemony-first, so the bloc colours off hegemony; reused across
    // tests probing both the grouped members and an outsider left to itself.
    private static AllianceRecord buildAlliedPowers() {

        return new AllianceRecord(
            "alliance-1",
            "Allied Powers",
            List.of("hegemony", "astral_armada"));
    }
}
