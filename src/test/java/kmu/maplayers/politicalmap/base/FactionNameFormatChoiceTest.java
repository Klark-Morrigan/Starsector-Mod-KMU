package kmu.maplayers.politicalmap.base;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link FactionNameFormatChoice}: each option round-trips through the key it persists under,
 * an unknown or missing key falls back to the caller's default (a save that stored a format this
 * build no longer knows, or one that never picked a format, must not throw or mislabel), and the
 * keys themselves are the frozen literals every existing save reads back through.
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
        }
    }
}
