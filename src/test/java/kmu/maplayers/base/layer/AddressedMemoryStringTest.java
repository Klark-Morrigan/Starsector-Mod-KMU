package kmu.maplayers.base.layer;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the addressed string: it stores each address under a key of its own, reads an untouched address
 * back as null for the holder to resolve, clears one address alone, reports whether each write and
 * removal landed, and no-ops cleanly before the sector exists. The composed key is asserted as a
 * literal, since a string that composed it differently would still read back whatever it wrote.
 */
final class AddressedMemoryStringTest {

    // A base key for this suite alone, so the cases turn on the composition rather than on any shipped
    // preference's frozen key.
    private static final AddressedMemoryString SAMPLE_VALUE =
        new AddressedMemoryString("$sample_value");

    // That key under the two stand-in screens, spelled out: what fails if the segment stops being
    // appended, or is appended in a different shape.
    private static final String KEY = "$sample_value_test";
    private static final String OTHER_KEY = "$sample_value_other";

    private static final ScreenMemoryScope ADDRESS = ScreenMemoryScopes.createStandInScreen();

    private static final ScreenMemoryScope OTHER_ADDRESS =
        ScreenMemoryScopes.createOtherStandInScreen();

    private static final String STORED = "stored";
    private static final String OTHER_STORED = "other-stored";

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
    class Get {

        @Test
        void getReadsTheValueStoredAtTheAddressComposedKey() {

            sectorMemoryFake.storeValue(KEY, STORED);

            assertThat(SAMPLE_VALUE.get(ADDRESS))
                .isEqualTo(STORED);
        }

        @Test
        void getReadsEachAddressApart() {
            // One string, one base key, and as many slots as there are addresses - so what is stored at
            // one address is not what another reads.
            sectorMemoryFake.storeValue(KEY, STORED);
            sectorMemoryFake.storeValue(OTHER_KEY, OTHER_STORED);

            assertThat(SAMPLE_VALUE.get(ADDRESS))
                .isEqualTo(STORED);
            assertThat(SAMPLE_VALUE.get(OTHER_ADDRESS))
                .isEqualTo(OTHER_STORED);
        }

        @Test
        void getIsNullWhileTheAddressHoldsNothing() {
            // No default of its own: what an absent value stands for is the holder's vocabulary, so an
            // untouched slot reads back as nothing at all.
            assertThat(SAMPLE_VALUE.get(ADDRESS))
                .isNull();
        }

        @Test
        void getIsNullBeforeTheSectorExists() {
            sectorMemoryFake.removeSector();

            assertThat(SAMPLE_VALUE.get(ADDRESS))
                .isNull();
        }
    }

    @Nested
    class Set {

        @Test
        void setStoresTheValueAtTheAddressComposedKeyAndReportsTheWrite() {

            assertThat(SAMPLE_VALUE.set(ADDRESS, STORED))
                .isTrue();
            assertThat(sectorMemoryFake.readStoredValue(KEY))
                .isEqualTo(STORED);
        }

        @Test
        void setLeavesEveryOtherAddressUntouched() {

            SAMPLE_VALUE.set(ADDRESS, STORED);

            assertThat(sectorMemoryFake.hasStoredValue(OTHER_KEY))
                .isFalse();
        }

        @Test
        void setReportsNoWriteBeforeTheSectorExists() {
            sectorMemoryFake.removeSector();

            assertThat(SAMPLE_VALUE.set(ADDRESS, STORED))
                .isFalse();
        }
    }

    @Nested
    class Clear {

        @Test
        void clearRemovesTheAddressStoredValueAndReportsTheRemoval() {

            sectorMemoryFake.storeValue(KEY, STORED);

            assertThat(SAMPLE_VALUE.clear(ADDRESS))
                .isTrue();
            assertThat(sectorMemoryFake.hasStoredValue(KEY))
                .isFalse();
        }

        @Test
        void clearLeavesEveryOtherAddressStoredValueStanding() {
            // A clear is as partitioned as a write: emptying one panel's slot must not empty another's.
            sectorMemoryFake.storeValue(KEY, STORED);
            sectorMemoryFake.storeValue(OTHER_KEY, OTHER_STORED);

            SAMPLE_VALUE.clear(ADDRESS);

            assertThat(sectorMemoryFake.readStoredValue(OTHER_KEY))
                .isEqualTo(OTHER_STORED);
        }

        @Test
        void clearReportsNoRemovalWhenTheAddressHeldNothing() {
            // The report is what a caller gates its repaint on, so clearing an already-empty slot must
            // not look like a change.
            assertThat(SAMPLE_VALUE.clear(ADDRESS))
                .isFalse();
        }
    }
}
