package kmu.maplayers.ownermap.owners;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one synthetic key a spotlit owner's footprint carries. The key is pinned as a literal and
 * recognised only by this class, so a resolve stamping it and the style side matching it cannot
 * disagree.
 */
final class SpotlitOwnersTests {

    private static final String SPOTLIT_OWNER_ID = "hegemony";

    @Nested
    class IsSpotlitOwner {

        @Test
        void recognisesTheSpotlitKey() {

            assertThat(SpotlitOwners.isSpotlitOwner(SpotlitOwners.readSpotlitOwnerKey()))
                .isTrue();
        }

        @Test
        void rejectsARealOwnerId() {

            assertThat(SpotlitOwners.isSpotlitOwner(SPOTLIT_OWNER_ID))
                .isFalse();
        }

        @Test
        void rejectsNoKey() {

            assertThat(SpotlitOwners.isSpotlitOwner(null))
                .isFalse();
        }
    }

    @Nested
    class ReadSpotlitOwnerKey {

        @Test
        void answersTheSentinelPrefixedKey() {
            // The "$" prefix is what keeps the key from colliding with any real owner or group ID.
            assertThat(SpotlitOwners.readSpotlitOwnerKey())
                .isEqualTo("$kmu_filter_spotlit");
        }
    }

}
