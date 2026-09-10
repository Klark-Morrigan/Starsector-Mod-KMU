package kmu.maplayers.base.visibility.installations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that a row built with a column left out reads back as one stating nothing about it, rather
 * than as one that throws when the question is finally put to it - the read happens per
 * installation of every system a pass walks, far from the file that was short a cell.
 */
final class InstallationOverrideTest {

    @Nested
    class Construct {

        @Test
        void reads_an_absent_admission_as_a_row_stating_none() {

            assertThat(new InstallationOverride(null, Optional.of(InstallationKind.HELD))
                .isAdmitted())
                .isEmpty();
        }

        @Test
        void reads_an_absent_kind_as_a_row_stating_none() {

            assertThat(new InstallationOverride(Optional.of(Boolean.TRUE), null).kind())
                .isEmpty();
        }
    }
}
