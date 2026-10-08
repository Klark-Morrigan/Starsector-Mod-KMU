package kmu.maplayers.ownermap.ribbon;

import kmu.maplayers.ownermap.owners.OwnerPalette;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where presence starts: an owner counted at one holding is present, and one counted at none is
 * not.
 */
final class OwnerPresenceTests {

    private static final OwnerPalette PALETTE =
        new OwnerPalette(new Color(140, 160, 220), new Color(40, 60, 120));

    @Nested
    class HasMarkets {

        @Test
        void isTrueForASingleHolding() {

            assertThat(new OwnerPresence("first", PALETTE, 1).hasMarkets())
                .isTrue();
        }

        @Test
        void isFalseForNoHoldings() {

            assertThat(new OwnerPresence("first", PALETTE, 0).hasMarkets())
                .isFalse();
        }
    }
}
