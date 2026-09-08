package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the addressed flag: it stores each address under a key of its own, reads an untouched address at
 * the declared default rather than at false, reports whether a write landed, and no-ops cleanly before
 * the sector exists. The composed key is asserted as a literal, since a flag that composed it differently
 * would still read back whatever it wrote and pass every round-trip case.
 */
final class AddressedMemoryFlagTest {

    // A base key for this suite alone, so the cases turn on the composition rather than on any shipped
    // preference's frozen key.
    private static final String BASE_KEY = "$sample_flag";

    // That key under the two stand-in screens, spelled out: what fails if the segment stops being
    // appended, or is appended in a different shape.
    private static final String KEY = "$sample_flag_test";
    private static final String OTHER_KEY = "$sample_flag_other";

    private static final ScreenMemoryScope ADDRESS = ScreenMemoryScopes.createStandInScreen();

    private static final ScreenMemoryScope OTHER_ADDRESS =
        ScreenMemoryScopes.createOtherStandInScreen();

    // Declared on rather than off, so a read falling through to a bare false shows up as a failure
    // rather than as the answer the case wanted anyway.
    private static final AddressedMemoryFlag FLAG_DEFAULTING_ON =
        new AddressedMemoryFlag(BASE_KEY, true);

    private SectorMemoryFake sectorMemoryFake;

    @BeforeEach
    void openTheSave() {
        sectorMemoryFake = new SectorMemoryFake();
    }

    @AfterEach
    void closeTheSave() {
        sectorMemoryFake.close();
    }

    @Nested
    class IsSet {

        @Test
        void isSetReadsTheValueStoredAtTheAddressComposedKey() {

            sectorMemoryFake.storeValue(KEY, false);

            assertThat(FLAG_DEFAULTING_ON.isSet(ADDRESS))
                .isFalse();
        }

        @Test
        void isSetReadsEachAddressApart() {
            // The whole point of the type: one flag, one base key, and as many slots as there are
            // addresses - so a value stored at one is invisible at another.
            sectorMemoryFake.storeValue(KEY, false);

            assertThat(FLAG_DEFAULTING_ON.isSet(ADDRESS))
                .isFalse();
            assertThat(FLAG_DEFAULTING_ON.isSet(OTHER_ADDRESS))
                .isTrue();
        }

        @Test
        void isSetIsTheDeclaredDefaultWhileTheAddressHoldsNothing() {
            // Absence is not false: the declared default is what a slot that was never written reads
            // as, which is the distinction a bare memory read cannot make.
            assertThat(FLAG_DEFAULTING_ON.isSet(ADDRESS))
                .isTrue();
        }

        @Test
        void isSetIsTheDeclaredDefaultBeforeTheSectorExists() {
            // No sector means no save to read, which reads as the same untouched state.
            sectorMemoryFake.removeSector();

            assertThat(FLAG_DEFAULTING_ON.isSet(ADDRESS))
                .isTrue();
        }
    }

    @Nested
    class Set {

        @Test
        void setStoresTheValueAtTheAddressComposedKeyAndReportsTheWrite() {

            assertThat(FLAG_DEFAULTING_ON.set(ADDRESS, false))
                .isTrue();
            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(false);
        }

        @Test
        void setLeavesEveryOtherAddressUntouched() {

            FLAG_DEFAULTING_ON.set(ADDRESS, false);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void setReportsNoWriteBeforeTheSectorExists() {
            // The report is what a caller gates its repaint on, so "nothing landed" has to be
            // distinguishable from a write of the same value.
            sectorMemoryFake.removeSector();

            assertThat(FLAG_DEFAULTING_ON.set(ADDRESS, false))
                .isFalse();
        }
    }
}
