package kmu.starsector.nexerelin;

import kmu.maplayers.base.visibility.colonies.FactionAlliances;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the fold from plain alliance records to the memberships a visibility rule reads: two members
 * of one record stand together, a faction no record names stands alone, and the shapes a live
 * alliance manager can hand over - a record of one, a memberless record, a faction named twice -
 * resolve without leaving the fold partial. Built on hand-made records, so it is proven without a
 * live Nexerelin.
 *
 * <p>Read both ways round throughout. The fold keys on the faction and the question is asked of a
 * pair, so a mapping that had lost one direction would answer differently depending on which
 * colony's owner reached it first.
 */
class FactionAllianceFactoryTest {

    private static final String HEGEMONY = "hegemony";
    private static final String ASTRAL_ARMADA = "astral_armada";
    private static final String TRITACHYON = "tritachyon";
    private static final String LUDDIC_CHURCH = "luddic_church";

    @Nested
    class BuildFrom {

        @Test
        void reads_two_members_of_one_record_as_allied() {

            var alliances = FactionAllianceFactory.buildFrom(List.of(buildAlliedPowers()));

            assertThat(alliances.areFactionsAllied(HEGEMONY, ASTRAL_ARMADA))
                .isTrue();
            assertThat(alliances.areFactionsAllied(ASTRAL_ARMADA, HEGEMONY))
                .isTrue();
        }

        @Test
        void reads_a_faction_no_record_names_as_unallied() {

            var alliances = FactionAllianceFactory.buildFrom(List.of(buildAlliedPowers()));

            assertThat(alliances.areFactionsAllied(HEGEMONY, TRITACHYON))
                .isFalse();
            assertThat(alliances.areFactionsAllied(TRITACHYON, HEGEMONY))
                .isFalse();
        }

        @Test
        void reads_members_of_two_records_as_unallied() {

            var alliances = FactionAllianceFactory.buildFrom(List.of(
                buildAlliedPowers(),
                new AllianceRecord("alliance-2", "Rival Pact", List.of(LUDDIC_CHURCH))));

            assertThat(alliances.areFactionsAllied(HEGEMONY, LUDDIC_CHURCH))
                .isFalse();
        }

        @Test
        void reads_the_lone_member_of_a_record_of_one_as_unallied_with_anybody_else() {
            // An alliance whose partners have all left, which a live manager does hand over. It
            // folds like any other and leaves its last member with nobody to keep a secret with,
            // so every witness around it still speaks.
            var alliances = FactionAllianceFactory.buildFrom(List.of(
                new AllianceRecord("alliance-1", "Allied Powers", List.of(HEGEMONY))));

            assertThat(alliances.areFactionsAllied(HEGEMONY, ASTRAL_ARMADA))
                .isFalse();
            assertThat(alliances.areFactionsAllied(ASTRAL_ARMADA, HEGEMONY))
                .isFalse();
        }

        @Test
        void reads_nobody_as_allied_for_a_memberless_record() {

            var alliances = FactionAllianceFactory.buildFrom(List.of(
                new AllianceRecord("empty-alliance", "Empty Pact", List.of())));

            assertThat(alliances.areFactionsAllied(HEGEMONY, ASTRAL_ARMADA))
                .isFalse();
        }

        @Test
        void resolves_a_faction_named_by_two_records_to_the_later_one() {
            // Visited in list order, so the fold stays total and deterministic per input rather
            // than leaving the faction in whichever record was reached first.
            var alliances = FactionAllianceFactory.buildFrom(List.of(
                buildAlliedPowers(),
                new AllianceRecord(
                    "alliance-2", "Rival Pact", List.of(HEGEMONY, LUDDIC_CHURCH))));

            assertThat(alliances.areFactionsAllied(HEGEMONY, LUDDIC_CHURCH))
                .isTrue();
            assertThat(alliances.areFactionsAllied(HEGEMONY, ASTRAL_ARMADA))
                .isFalse();
        }

        @Test
        void reads_nobody_as_allied_for_no_records() {

            assertThat(FactionAllianceFactory.buildFrom(List.of()))
                .isEqualTo(FactionAlliances.NONE);
        }

        @Test
        void reads_nobody_as_allied_where_no_records_were_read_at_all() {

            assertThat(FactionAllianceFactory.buildFrom(null))
                .isEqualTo(FactionAlliances.NONE);
        }

        @Test
        void skips_a_record_the_game_never_named() {
            // The memberships are keyed on the alliance's own id, and one that is absent cannot be
            // compared against another - nor stored, the copy the result takes refusing it.
            var alliances = FactionAllianceFactory.buildFrom(List.of(
                new AllianceRecord(null, "Unnamed Pact", List.of(HEGEMONY, ASTRAL_ARMADA))));

            assertThat(alliances.areFactionsAllied(HEGEMONY, ASTRAL_ARMADA))
                .isFalse();
        }
    }

    // Two members, as a live alliance of two arrives: ranked by market size, which this fold has no
    // use for and must not come to depend on.
    private static AllianceRecord buildAlliedPowers() {

        return new AllianceRecord(
            "alliance-1", "Allied Powers", List.of(HEGEMONY, ASTRAL_ARMADA));
    }
}
