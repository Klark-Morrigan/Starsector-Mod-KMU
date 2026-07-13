package kmu.maplayers.politicalmap.base;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the column choice's three surfaces: the frozen persistence keys a save round-trips through, the
 * column count each choice feeds the list geometry, and the default a fresh or unrecognised save falls
 * back to. The keys are pinned as literals so a rename that would silently reset every save's column
 * choice fails here rather than shipping.
 */
final class BlocListColumnsTest {

    @Nested
    class FromKeyOrDefault {

        @Test
        void fromKeyOrDefaultResolvesAKnownKeyToItsChoice() {
            assertThat(BlocListColumns.fromKeyOrDefault("2")).isEqualTo(BlocListColumns.TWO);
        }

        @Test
        void fromKeyOrDefaultFallsBackToOneColumnWhenTheKeyIsNull() {
            // A fresh save has stored no key, which must resolve to the single-column default rather
            // than fail.
            assertThat(BlocListColumns.fromKeyOrDefault(null)).isEqualTo(BlocListColumns.DEFAULT);
            assertThat(BlocListColumns.DEFAULT).isEqualTo(BlocListColumns.ONE);
        }

        @Test
        void fromKeyOrDefaultFallsBackToDefaultWhenTheKeyIsUnrecognised() {
            // A key left by an older or modded build names no choice here, so the list defaults rather
            // than failing on it.
            assertThat(BlocListColumns.fromKeyOrDefault("no_such_count"))
                    .isEqualTo(BlocListColumns.DEFAULT);
        }
    }

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheFrozenKeyForEachChoice() {
            // Pinned as literals: renaming a key silently resets every save that stored that count back
            // to the default, so a change must break this test before it ships.
            assertThat(BlocListColumns.ONE.persistenceKey()).isEqualTo("1");
            assertThat(BlocListColumns.TWO.persistenceKey()).isEqualTo("2");
        }
    }

    @Nested
    class ColumnCount {

        @Test
        void columnCountIsOneForTheSingleChoiceAndTwoForTheDoubleChoice() {
            assertThat(BlocListColumns.ONE.columnCount()).isEqualTo(1);
            assertThat(BlocListColumns.TWO.columnCount()).isEqualTo(2);
        }
    }
}
