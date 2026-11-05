package kmu.maplayers.ownermap.ribbon;

import kmlib.colour.ColourPair;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where presence starts - an owner holding one stake is present, and one holding none is not -
 * and that the stakes it was built with cannot change under the rule.
 */
final class OwnerPresenceTests {

    private static final ColourPair PALETTE =
        new ColourPair(new Color(140, 160, 220), new Color(40, 60, 120));

    @Nested
    class Constructor {

        @Test
        void keepsTheStakesItWasBuiltWithWhenTheCallersListGrows() {

            // ARRANGE
            var stakePalettes = new ArrayList<>(List.of(PALETTE));
            var presence = new OwnerPresence("first", stakePalettes);
            var expectedStakePalettes = List.of(PALETTE);

            // ACT
            stakePalettes.add(PALETTE);

            var actualStakePalettes = presence.stakePalettes();

            // ASSERT
            assertThat(actualStakePalettes)
                .containsExactlyElementsOf(expectedStakePalettes);
        }
    }

    @Nested
    class HasStakes {

        @Test
        void isTrueForASingleStake() {

            // ARRANGE
            var presence = new OwnerPresence("first", List.of(PALETTE));

            // ACT
            var actualHasStakes = presence.hasStakes();

            // ASSERT
            assertThat(actualHasStakes)
                .isTrue();
        }

        @Test
        void isFalseForNoStakes() {

            // ARRANGE
            var presence = new OwnerPresence("first", List.of());

            // ACT
            var actualHasStakes = presence.hasStakes();

            // ASSERT
            assertThat(actualHasStakes)
                .isFalse();
        }
    }
}
