package kmu.maplayers.base.visibility.installations;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what a hand-edited row is allowed to name a kind, and what happens to a row that names
 * something else.
 *
 * <p>The unmatched name is the case worth pinning. A row is written by hand, frequently by somebody
 * else's mod author, and a misspelling that resolved to a kind anyway would hand a whole entity
 * type a holder nobody wrote down - so it has to read as nothing named and leave the answer where
 * it started.
 */
final class InstallationKindTest {

    @Nested
    class FindKindNamed {

        @Test
        void findsTheKindARowNames() {

            assertThat(InstallationKind.findKindNamed("GARRISONED"))
                .contains(InstallationKind.GARRISONED);
        }

        @Test
        void findsAKindNamedInAnyCaseAndSpacing() {
            // The file is hand-edited; a row is not worth losing over the shift key.
            assertThat(InstallationKind.findKindNamed("  Derelict "))
                .contains(InstallationKind.DERELICT);
        }

        @Test
        void readsARowNamingNoKindAsNothingNamed() {
            // The ordinary state of a row that only means to settle admission.
            assertThat(InstallationKind.findKindNamed("   "))
                .isEmpty();
        }

        @Test
        void readsAnAbsentNameAsNothingNamed() {

            assertThat(InstallationKind.findKindNamed(null))
                .isEmpty();
        }

        @Test
        void readsANameNoKindAnswersToAsNothingNamed() {

            assertThat(InstallationKind.findKindNamed("ABANDONED"))
                .isEmpty();
        }
    }
}
