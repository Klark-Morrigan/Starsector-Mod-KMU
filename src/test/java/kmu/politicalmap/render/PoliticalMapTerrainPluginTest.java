package kmu.politicalmap.render;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the two contracts of the terrain that are testable off-engine: the
 * engine-layer override the map relies on, and the per-owner alpha scale that
 * draws independent-held space more faintly. The GL emission itself runs only
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
    class ResolveAlphaMultiplier {

        @Test
        void resolveAlphaMultiplierHalvesIndependentRelativeToAFaction() {
            var factionScale = PoliticalMapTerrainPlugin.resolveAlphaMultiplier("hegemony");

            // Independent-held space draws (fill and outline) at half a faction
            // province's strength.
            assertThat(PoliticalMapTerrainPlugin.resolveAlphaMultiplier("independent"))
                    .isEqualTo(factionScale * 0.5f);
        }

        @Test
        void resolveAlphaMultiplierUsesFullStrengthForANonIndependentFaction() {
            // Any other owner draws at full scale; only independent is singled out,
            // so two unrelated factions match.
            assertThat(PoliticalMapTerrainPlugin.resolveAlphaMultiplier("tritachyon"))
                    .isEqualTo(PoliticalMapTerrainPlugin.resolveAlphaMultiplier("hegemony"));
        }

        @Test
        void resolveAlphaMultiplierUsesFullStrengthForAnUnknownOwner() {
            // A cell whose dominant id failed to resolve (null) is not independent,
            // so it keeps full strength rather than being dimmed by accident.
            assertThat(PoliticalMapTerrainPlugin.resolveAlphaMultiplier(null))
                    .isEqualTo(PoliticalMapTerrainPlugin.resolveAlphaMultiplier("hegemony"));
        }
    }
}
