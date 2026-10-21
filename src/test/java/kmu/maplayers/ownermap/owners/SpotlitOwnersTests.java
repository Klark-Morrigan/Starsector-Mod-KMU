package kmu.maplayers.ownermap.owners;

import kmlib.colour.ColourPair;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one synthetic key a spotlit owner's footprint carries. The key is pinned as a literal and
 * recognised only by this class, so a resolve stamping it and the style side matching it cannot
 * disagree.
 */
final class SpotlitOwnersTests {

    private static final String SPOTLIT_OWNER_ID = "hegemony";

    private static final ColourPair HEGEMONY_SHADES = new ColourPair(Color.ORANGE, Color.DARK_GRAY);

    @Nested
    class IsSpotlitOwner {

        @Test
        void recognisesTheSpotlitKey() {

            var spotlitOwner = SpotlitOwners.createSpotlitOwner(HEGEMONY_SHADES);

            assertThat(SpotlitOwners.isSpotlitOwner(spotlitOwner.ownerId()))
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
    class CreateSpotlitOwner {

        @Test
        void carriesTheSentinelPrefixedKey() {
            // The "$" prefix is what keeps the key from colliding with any real owner or group ID.
            assertThat(SpotlitOwners.createSpotlitOwner(HEGEMONY_SHADES).ownerId())
                .isEqualTo("$kmu_filter_spotlit");
        }

        @Test
        void carriesTheShadesItIsHanded() {
            // Painted in the owner's own shades, so the spotlight reads as that owner rather than as
            // a cluster of its own.
            assertThat(SpotlitOwners.createSpotlitOwner(HEGEMONY_SHADES).palette())
                .isEqualTo(new ColourPair(Color.ORANGE, Color.DARK_GRAY));
        }

        @Test
        void answersNoOwnerForNoShades() {
            // Shades gone mid-session: the caller drops or recedes the system instead.
            assertThat(SpotlitOwners.createSpotlitOwner(null))
                .isNull();
        }
    }
}
