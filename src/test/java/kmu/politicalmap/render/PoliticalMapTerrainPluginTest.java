package kmu.politicalmap.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the contracts of the terrain that are testable off-engine: the engine-
 * layer override the map relies on, and the two pure opacity resolvers that
 * decide how each cell category is drawn. The GL emission itself runs only
 * in-engine and is out of scope here.
 *
 * <p>{@code BaseTerrain.getActiveLayers} throws by default, and the engine calls
 * it the moment the terrain is added on a fresh game - so failing to override it
 * crashed onGameLoad. This pins the override: an empty set (map-only terrain, no
 * world layers) and no throw.
 */
final class PoliticalMapTerrainPluginTest {

    @Nested
    class GetActiveLayers {

        @Test
        void getActiveLayersReturnsEmptyWithoutThrowing() {
            var plugin = new PoliticalMapTerrainPlugin();

            assertThatCode(plugin::getActiveLayers).doesNotThrowAnyException();
            assertThat(plugin.getActiveLayers()).isEmpty();
        }
    }

    @Nested
    class ResolveOwnedOpacity {

        @Test
        void resolveOwnedOpacityUsesTheIndependentOpacitiesForIndependentSpace() {
            var opacity = PoliticalMapTerrainPlugin.resolveOwnedOpacity("independent", 0.4, 0.75);

            // Independent space draws at the player's independent fill/border
            // opacities, so it reads as loosely held.
            assertThat(opacity.fillAlpha()).isEqualTo(0.4f);
            assertThat(opacity.borderAlpha()).isEqualTo(0.75f);
        }

        @Test
        void resolveOwnedOpacityUsesTheFixedFactionDefaultForAnyOtherFaction() {
            // A core faction ignores the independent opacities (passed here as
            // distinct values) for the fixed province default: a 0.4 fill under a
            // fully opaque (1.0) border.
            var opacity = PoliticalMapTerrainPlugin.resolveOwnedOpacity("hegemony", 0.11, 0.22);

            assertThat(opacity.fillAlpha()).isEqualTo(0.4f);
            assertThat(opacity.borderAlpha()).isEqualTo(1f);
        }

        @Test
        void resolveOwnedOpacityTreatsAnUnknownOwnerAsANonIndependentFaction() {
            // A null owner id (dominant faction failed to resolve) is not
            // independent, so it gets the faction default, not the independent
            // opacities.
            var opacity = PoliticalMapTerrainPlugin.resolveOwnedOpacity(null, 0.11, 0.22);

            assertThat(opacity.fillAlpha()).isEqualTo(0.4f);
            assertThat(opacity.borderAlpha()).isEqualTo(1f);
        }
    }

    @Nested
    class ResolveNeutralBorderOpacity {

        @Test
        void resolveNeutralBorderOpacityUsesTheDecivilisedOpacityForADeadColony() {
            // A revealed decivilised system always draws, at the decivilised
            // opacity, regardless of the uninhabited toggle.
            assertThat(PoliticalMapTerrainPlugin.resolveNeutralBorderOpacity(true, false, 0.3, 0.15))
                    .hasValue(0.3);
        }

        @Test
        void resolveNeutralBorderOpacityUsesTheUninhabitedOpacityWhenEmptyAndOptedIn() {
            assertThat(PoliticalMapTerrainPlugin.resolveNeutralBorderOpacity(false, true, 0.3, 0.15))
                    .hasValue(0.15);
        }

        @Test
        void resolveNeutralBorderOpacityIsEmptyForAnEmptySystemWhenNotOptedIn() {
            // A genuinely empty system is not drawn unless the player opts in.
            assertThat(PoliticalMapTerrainPlugin.resolveNeutralBorderOpacity(false, false, 0.3, 0.15))
                    .isEmpty();
        }

        @Test
        void resolveNeutralBorderOpacityPrefersDecivilisedWhenOptedIn() {
            // Decivilised takes precedence: a dead colony uses its own opacity, not
            // the uninhabited one, even with the toggle on.
            assertThat(PoliticalMapTerrainPlugin.resolveNeutralBorderOpacity(true, true, 0.3, 0.15))
                    .hasValue(0.3);
        }
    }
}
