package kmu.maplayers.politicalmap.base.render;

import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the builder's off-engine, deterministic piece: the palette-color pick that maps
 * a player's color choice to a palette shade. The rest shapes cells and reads settings
 * that only resolve in-engine; the cluster-anchor fit lives in
 * {@link ClusterAnchorsBuilder} and is pinned by its own suite.
 */
final class DrawablesBuilderTest {

    // Two distinct shades so a pick can be told apart from its counterpart.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class PickPaletteColor {

        @Test
        void pickPaletteColorReturnsThePrimaryShadeForAPrimaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.PRIMARY, PRIMARY, SECONDARY)).isEqualTo(PRIMARY);
        }

        @Test
        void pickPaletteColorReturnsTheSecondaryShadeForASecondaryChoice() {
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.SECONDARY, PRIMARY, SECONDARY)).isEqualTo(SECONDARY);
        }

        @Test
        void pickPaletteColorReturnsNullForNoColor() {
            // NONE is the player's "No color" choice; a null color signals the render
            // layer to skip that element.
            assertThat(DrawablesBuilder.pickPaletteColor(
                    FactionPaletteChoice.NONE, PRIMARY, SECONDARY)).isNull();
        }
    }
}
