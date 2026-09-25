package kmu.maplayers.ownermap.preferences;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FactionNameFormatChoice}: each option round-trips through the key it persists under,
 * an unknown or missing key falls back to the caller's default (a save that stored a format this
 * build no longer knows, or one that never picked a format, must not throw or mislabel), the keys
 * themselves are the frozen literals every existing save reads back through, and only the two named
 * forms draw a label.
 */
final class FactionNameFormatChoiceTest {

    @Nested
    class FromKeyOrDefault {

        @Test
        void fromKeyOrDefaultReturnsFullForItsKey() {
            assertThat(FactionNameFormatChoice.fromKeyOrDefault("full",
                    FactionNameFormatChoice.SHORT)).isEqualTo(FactionNameFormatChoice.FULL);
        }

        @Test
        void fromKeyOrDefaultReturnsShortForItsKey() {
            assertThat(FactionNameFormatChoice.fromKeyOrDefault("short",
                    FactionNameFormatChoice.FULL)).isEqualTo(FactionNameFormatChoice.SHORT);
        }

        @Test
        void fromKeyOrDefaultReturnsNoneForItsKey() {
            assertThat(FactionNameFormatChoice.fromKeyOrDefault("none",
                    FactionNameFormatChoice.FULL)).isEqualTo(FactionNameFormatChoice.NONE);
        }

        @Test
        void fromKeyOrDefaultReturnsTheFallbackForAnUnknownKey() {
            // A key from a build that offered another format matches nothing, so the caller's
            // default stands rather than throwing.
            assertThat(FactionNameFormatChoice.fromKeyOrDefault("medium",
                    FactionNameFormatChoice.FULL)).isEqualTo(FactionNameFormatChoice.FULL);
        }

        @Test
        void fromKeyOrDefaultReturnsTheFallbackForNull() {
            // Sector memory reads back null before a save exists or when no format was ever
            // picked; the fallback covers that read.
            assertThat(FactionNameFormatChoice.fromKeyOrDefault(null, FactionNameFormatChoice.FULL))
                    .isEqualTo(FactionNameFormatChoice.FULL);
        }
    }

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
