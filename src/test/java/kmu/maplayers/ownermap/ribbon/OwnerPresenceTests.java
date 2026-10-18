package kmu.maplayers.ownermap.ribbon;

import kmlib.colour.ColourPair;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where presence starts: an owner counted at one holding is present, and one counted at none is
 * not.
 */
final class OwnerPresenceTests {

    private static final ColourPair PALETTE =
        new ColourPair(new Color(140, 160, 220), new Color(40, 60, 120));

    @Nested
    class HasStakes {

        @Test
        void isTrueForASingleStake() {

            assertThat(new OwnerPresence("first", PALETTE, 1).hasStakes())
                .isTrue();
        }

        @Test
        void isFalseForNoStakes() {

            assertThat(new OwnerPresence("first", PALETTE, 0).hasStakes())
                .isFalse();
        }
    }
}
