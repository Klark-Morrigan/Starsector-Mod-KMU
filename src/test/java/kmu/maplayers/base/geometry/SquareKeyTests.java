package kmu.maplayers.base.geometry;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit coverage for packing a flood square's column and row into one key. */
class SquareKeyTests {

    @Nested
    class PackSquareKey {

        @Test
        void theColumnTakesTheHighHalfAndTheRowTheLow() {

            assertThat(SquareKey.packSquareKey(1, 2))
                .isEqualTo(0x0000_0001_0000_0002L);
        }

        @Test
        void aNegativeRowStaysInTheLowHalf() {
            // Sign-extended, a row of -1 would fill the high half too and land on the key of a
            // different column.
            assertThat(SquareKey.packSquareKey(0, -1))
                .isEqualTo(0x0000_0000_FFFF_FFFFL);
        }

        @Test
        void swappingColumnAndRowGivesADifferentKey() {

            assertThat(SquareKey.packSquareKey(3, 7))
                .isNotEqualTo(SquareKey.packSquareKey(7, 3));
        }

        @Test
        void squaresEitherSideOfTheOriginAreApart() {

            assertThat(SquareKey.packSquareKey(-1, 0))
                .isNotEqualTo(SquareKey.packSquareKey(0, 0))
                .isNotEqualTo(SquareKey.packSquareKey(0, -1));
        }
    }
}
