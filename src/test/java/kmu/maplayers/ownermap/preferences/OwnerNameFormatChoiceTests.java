package kmu.maplayers.ownermap.preferences;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link OwnerNameFormatChoice}: the keys are the frozen literals every existing save reads
 * back through, and only the two named forms draw a label.
 */
final class OwnerNameFormatChoiceTests {

    @Nested
    class PersistenceKey {

        @Test
        void returnsTheFrozenSaveStableKey() {
            // Pinned as literals: renaming one resets every save that stored that format back to
            // the default, so a change must break this test first.
            assertThat(OwnerNameFormatChoice.FULL.persistenceKey())
                .isEqualTo("full");
            assertThat(OwnerNameFormatChoice.SHORT.persistenceKey())
                .isEqualTo("short");
            assertThat(OwnerNameFormatChoice.NONE.persistenceKey())
                .isEqualTo("none");
        }
    }

    @Nested
    class AreNamesDrawn {

        @Test
        void isTrueForBothNamedForms() {

            assertThat(OwnerNameFormatChoice.FULL.areNamesDrawn())
                .isTrue();
            assertThat(OwnerNameFormatChoice.SHORT.areNamesDrawn())
                .isTrue();
        }

        @Test
        void isFalseForNone() {
            // The gate the label passes read: None must skip the whole label build, not merely
            // resolve to an empty string.
            assertThat(OwnerNameFormatChoice.NONE.areNamesDrawn())
                .isFalse();
        }
    }
}
