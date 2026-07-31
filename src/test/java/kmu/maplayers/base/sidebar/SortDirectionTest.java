package kmu.maplayers.base.sidebar;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the direction's three surfaces: the frozen persistence keys a save round-trips through, the
 * fallback an absent or unrecognised key resolves to, and the flip a re-pick applies. The keys are
 * pinned as literals so a rename that would silently reset every save's stored direction fails here
 * rather than shipping.
 */
final class SortDirectionTest {

    @Nested
    class FromKeyOrDefault {

        @Test
        void fromKeyOrDefaultResolvesAKnownKeyToItsDirection() {
            assertThat(SortDirection.fromKeyOrDefault("asc", SortDirection.DESCENDING))
                .isEqualTo(SortDirection.ASCENDING);
        }

        @Test
        void fromKeyOrDefaultFallsBackWhenTheKeyIsNull() {
            // A save from before the direction existed (or one that never flipped) has stored no key,
            // which must resolve to the caller's fallback - the active mode's default direction.
            assertThat(SortDirection.fromKeyOrDefault(null, SortDirection.DESCENDING))
                .isEqualTo(SortDirection.DESCENDING);
        }

        @Test
        void fromKeyOrDefaultFallsBackWhenTheKeyIsUnrecognised() {
            // A key left by an older or modded build names no direction here, so it reads as the
            // mode's natural order rather than failing on it.
            assertThat(SortDirection.fromKeyOrDefault("no_such_direction", SortDirection.ASCENDING))
                .isEqualTo(SortDirection.ASCENDING);
        }
    }

    @Nested
    class PersistenceKey {

        @Test
        void persistenceKeyIsTheFrozenKeyForEachDirection() {
            // Pinned as literals: renaming a key silently resets every save that stored that direction
            // back to its mode's default, so a change must break this test before it ships.
            assertThat(SortDirection.ASCENDING.persistenceKey())
                .isEqualTo("asc");

            assertThat(SortDirection.DESCENDING.persistenceKey())
                .isEqualTo("desc");
        }
    }

    @Nested
    class Opposite {

        @Test
        void oppositeFlipsEachDirectionToTheOther() {
            
            assertThat(SortDirection.ASCENDING.opposite())
                .isEqualTo(SortDirection.DESCENDING);

            assertThat(SortDirection.DESCENDING.opposite())
                .isEqualTo(SortDirection.ASCENDING);
        }
    }
}
