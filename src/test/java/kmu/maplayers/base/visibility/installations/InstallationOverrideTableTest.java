package kmu.maplayers.base.visibility.installations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins what the table answers about a type it was never told about, and that what it was told
 * cannot change underneath the pass reading it.
 *
 * <p>The unmentioned type is the ordinary case rather than the edge one: the file names the handful
 * of entity types anybody has researched, and every other station in the sector arrives here
 * unmentioned. It has to read as a row stating nothing - which leaves each question to the facts -
 * rather than as an absence a caller has to test for.
 */
final class InstallationOverrideTableTest {

    private static final String ARTILLERY_STATION_TYPE = "IndEvo_ArtilleryStation";
    private static final String CRYOSLEEPER_TYPE = "derelict_cryosleeper";

    @Nested
    class ReadOverrideOf {

        @Test
        void reads_what_the_file_states_about_a_type_it_names() {

            var table = new InstallationOverrideTable(Map.of(
                ARTILLERY_STATION_TYPE,
                new InstallationOverride(
                    Optional.of(Boolean.TRUE),
                    Optional.of(InstallationKind.HELD))));

            assertThat(table.readOverrideOf(ARTILLERY_STATION_TYPE))
                .isEqualTo(new InstallationOverride(
                    Optional.of(Boolean.TRUE),
                    Optional.of(InstallationKind.HELD)));
        }

        @Test
        void reads_a_type_the_file_never_mentions_as_stating_nothing() {

            assertThat(InstallationOverrideTable.NONE.readOverrideOf(CRYOSLEEPER_TYPE))
                .isEqualTo(InstallationOverride.NONE);
        }

        @Test
        void reads_an_entity_naming_no_type_as_stating_nothing() {
            // An entity carrying no custom type at all is asked about like any other, and an
            // immutable table would refuse the question rather than answer it.
            assertThat(InstallationOverrideTable.NONE.readOverrideOf(null))
                .isEqualTo(InstallationOverride.NONE);
        }
    }

    @Nested
    class Construct {

        @Test
        void reads_an_absent_map_as_a_table_stating_nothing() {

            assertThat(new InstallationOverrideTable(null).overridesByEntityTypeId())
                .isEmpty();
        }

        @Test
        void keeps_what_it_was_built_with_when_the_source_map_changes_later() {

            var overridesByEntityTypeId = new HashMap<String, InstallationOverride>();
            overridesByEntityTypeId.put(
                ARTILLERY_STATION_TYPE,
                new InstallationOverride(Optional.of(Boolean.TRUE), Optional.empty()));

            var table = new InstallationOverrideTable(overridesByEntityTypeId);

            overridesByEntityTypeId.clear();

            assertThat(table.readOverrideOf(ARTILLERY_STATION_TYPE).isAdmitted())
                .contains(Boolean.TRUE);
        }

        @Test
        void rejects_an_attempt_to_change_what_the_file_stated() {
            // Read once and asked for every installation of every system a pass walks, so one
            // reader able to change it would be reclassifying the sector underneath the others.
            var table = new InstallationOverrideTable(Map.of(
                ARTILLERY_STATION_TYPE, InstallationOverride.NONE));

            assertThatThrownBy(() -> table.overridesByEntityTypeId().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
