package kmu.maplayers.ownermap.preferences;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FactionNameFormatChoice}: the keys are the frozen literals every existing save reads
 * back through, and only the two named forms draw a label.
 */
final class FactionNameFormatChoiceTest {

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyReturnsTheFrozenSaveStableKey() {
            // Pinned as literals: renaming one resets every save that stored that format back to
            // the default, so a change must break this test first.
            assertThat(FactionNameFormatChoice.FULL.persistenceKey()).isEqualTo("full");
            assertThat(FactionNameFormatChoice.SHORT.persistenceKey()).isEqualTo("short");
            assertThat(FactionNameFormatChoice.NONE.persistenceKey()).isEqualTo("none");
        }
    }

    @Nested
    class AreNamesDrawn {

        @Test
        void areNamesDrawnIsTrueForBothNamedForms() {
            assertThat(FactionNameFormatChoice.FULL.areNamesDrawn()).isTrue();
            assertThat(FactionNameFormatChoice.SHORT.areNamesDrawn()).isTrue();
        }

        @Test
        void areNamesDrawnIsFalseForNone() {
            // The gate the label passes read: None must skip the whole label build, not merely
            // resolve to an empty string.
            assertThat(FactionNameFormatChoice.NONE.areNamesDrawn()).isFalse();
        }
    }
}
