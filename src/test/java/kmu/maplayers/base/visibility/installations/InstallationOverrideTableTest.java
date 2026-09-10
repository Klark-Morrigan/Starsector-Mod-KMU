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
        void readsWhatTheFileStatesAboutATypeItNames() {

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
        void readsATypeTheFileNeverMentionsAsStatingNothing() {

            assertThat(InstallationOverrideTable.NONE.readOverrideOf(CRYOSLEEPER_TYPE))
                .isEqualTo(InstallationOverride.NONE);
        }

        @Test
        void readsAnEntityNamingNoTypeAsStatingNothing() {
            // An entity carrying no custom type at all is asked about like any other, and an
            // immutable table would refuse the question rather than answer it.
            assertThat(InstallationOverrideTable.NONE.readOverrideOf(null))
                .isEqualTo(InstallationOverride.NONE);
        }
    }

    @Nested
    class Construct {

        @Test
        void readsAnAbsentMapAsATableStatingNothing() {

            assertThat(new InstallationOverrideTable(null).overridesByEntityTypeId())
                .isEmpty();
        }

        @Test
        void keepsWhatItWasBuiltWithWhenTheSourceMapChangesLater() {

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
        void rejectsAnAttemptToChangeWhatTheFileStated() {
            // Read once and asked for every installation of every system a pass walks, so one
            // reader able to change it would be reclassifying the sector underneath the others.
            var table = new InstallationOverrideTable(Map.of(
                ARTILLERY_STATION_TYPE, InstallationOverride.NONE));

            assertThatThrownBy(() -> table.overridesByEntityTypeId().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
