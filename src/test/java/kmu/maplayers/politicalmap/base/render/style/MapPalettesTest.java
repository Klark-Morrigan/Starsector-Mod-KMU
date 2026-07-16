package kmu.maplayers.politicalmap.base.render.style;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.color.Colors;
import kmlib.starsector.factions.FactionPalette;

import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shared palette resolution both the fills and the cluster-name labels read: the
 * palette-color pick that maps a player's colour choice to a palette shade, and the
 * desaturation-palette resolver that a receded bloc recolours through.
 */
final class MapPalettesTest {

    // Two distinct shades so a pick can be told apart from its counterpart.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class PickPaletteColor {

        @Test
        void pickPaletteColorReturnsThePrimaryShadeForAPrimaryChoice() {
            assertThat(MapPalettes.pickPaletteColor(
                    FactionPaletteChoice.PRIMARY, PRIMARY, SECONDARY)).isEqualTo(PRIMARY);
        }

        @Test
        void pickPaletteColorReturnsTheSecondaryShadeForASecondaryChoice() {
            assertThat(MapPalettes.pickPaletteColor(
                    FactionPaletteChoice.SECONDARY, PRIMARY, SECONDARY)).isEqualTo(SECONDARY);
        }

        @Test
        void pickPaletteColorReturnsNullForNoColor() {
            // NONE is the player's "No color" choice; a null color signals the render
            // layer to skip that element.
            assertThat(MapPalettes.pickPaletteColor(
                    FactionPaletteChoice.NONE, PRIMARY, SECONDARY)).isNull();
        }
    }

    @Nested
    class ResolveDesaturationPalette {

        // A sample darkening strength (30% removed); mirrored here so the expected shades are the
        // Independent pair scaled to the same kept fraction the resolver applies.
        private static final double DARKENING_STRENGTH = 0.3;
        private static final float KEEP_FACTOR = (float) (1.0 - DARKENING_STRENGTH);

        @Test
        void resolveDesaturationPaletteDarkensTheIndependentFactionsOwnShades() {
            var independentMock = mock(FactionAPI.class);
            when(independentMock.getBrightUIColor()).thenReturn(Color.GREEN);
            when(independentMock.getDarkUIColor()).thenReturn(Color.YELLOW);
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction(Factions.INDEPENDENT)).thenReturn(independentMock);

            var palette = MapPalettes.resolveDesaturationPalette(sectorMock, DARKENING_STRENGTH);

            // The Independent pair, sunk toward black by the strength so the receded ground reads
            // behind genuine independent-held space rather than as it.
            assertThat(palette).isEqualTo(new FactionPalette(
                    Colors.darken(Color.GREEN, KEEP_FACTOR), Colors.darken(Color.YELLOW, KEEP_FACTOR)));
        }

        @Test
        void resolveDesaturationPaletteLeavesTheIndependentShadesUntouchedAtZeroStrength() {
            var independentMock = mock(FactionAPI.class);
            when(independentMock.getBrightUIColor()).thenReturn(Color.GREEN);
            when(independentMock.getDarkUIColor()).thenReturn(Color.YELLOW);
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction(Factions.INDEPENDENT)).thenReturn(independentMock);

            // A strength of 0 keeps full brightness, so the target is the raw Independent pair.
            var palette = MapPalettes.resolveDesaturationPalette(sectorMock, 0.0);

            assertThat(palette).isEqualTo(new FactionPalette(Color.GREEN, Color.YELLOW));
        }
    }
}
