package kmu.politicalmap.render;

import kmu.politicalmap.domain.DominantOwner;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Pins the contracts of the terrain that are testable off-engine: the engine-
 * layer override the map relies on, and the pure palette-color pick that maps a
 * player's color choice to one of an owner's two shades (or no color). The GL
 * emission itself runs only in-engine and is out of scope here.
 *
 * <p>{@code BaseTerrain.getActiveLayers} throws by default, and the engine calls
 * it the moment the terrain is added on a fresh game - so failing to override it
 * crashed onGameLoad. This pins the override: an empty set (map-only terrain, no
 * world layers) and no throw.
 */
final class PoliticalMapTerrainPluginTest {

    private static final DominantOwner OWNER =
            new DominantOwner("hegemony", Color.RED, Color.BLUE);

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
    class PickPaletteColor {

        @Test
        void pickPaletteColorReturnsThePrimaryShadeForAPrimaryChoice() {
            assertThat(PoliticalMapTerrainPlugin.pickPaletteColor(
                    FactionPaletteChoice.PRIMARY, OWNER)).isEqualTo(OWNER.primaryColor());
        }

        @Test
        void pickPaletteColorReturnsTheSecondaryShadeForASecondaryChoice() {
            assertThat(PoliticalMapTerrainPlugin.pickPaletteColor(
                    FactionPaletteChoice.SECONDARY, OWNER)).isEqualTo(OWNER.secondaryColor());
        }

        @Test
        void pickPaletteColorReturnsNullForNoColor() {
            // NONE is the player's "No color" choice; a null color signals the
            // render layer to skip that element.
            assertThat(PoliticalMapTerrainPlugin.pickPaletteColor(
                    FactionPaletteChoice.NONE, OWNER)).isNull();
        }
    }
}
