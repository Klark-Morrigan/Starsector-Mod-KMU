package kmu.maplayers.politicalmap.base.render.style;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.starsector.factions.FactionPalette;

import kmu.settings.DesaturationProfileChoice;
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
    class ResolveSpotlightSafeDesaturationProfile {

        @Test
        void resolveSpotlightSafeDesaturationProfileFallsBackToNeutralWhenSpotlightingIndependent() {
            // The only colliding case: spotlit Independent would paint the same palette the
            // Independent profile desaturates the receded background to, so it must grey out.
            assertThat(MapPalettes.resolveSpotlightSafeDesaturationProfile(
                    DesaturationProfileChoice.INDEPENDENT, Factions.INDEPENDENT))
                    .isEqualTo(DesaturationProfileChoice.NEUTRAL);
        }

        @Test
        void resolveSpotlightSafeDesaturationProfileKeepsIndependentWhenSpotlightingAnotherBloc() {
            assertThat(MapPalettes.resolveSpotlightSafeDesaturationProfile(
                    DesaturationProfileChoice.INDEPENDENT, Factions.HEGEMONY))
                    .isEqualTo(DesaturationProfileChoice.INDEPENDENT);
        }

        @Test
        void resolveSpotlightSafeDesaturationProfileKeepsIndependentWhenNothingIsSpotlit() {
            // A null selection is the un-filtered pass, where no bloc collides with the background.
            assertThat(MapPalettes.resolveSpotlightSafeDesaturationProfile(
                    DesaturationProfileChoice.INDEPENDENT, null))
                    .isEqualTo(DesaturationProfileChoice.INDEPENDENT);
        }

        @Test
        void resolveSpotlightSafeDesaturationProfileLeavesTheNeutralProfileUntouched() {
            // The Neutral profile already greys the background, so spotlit Independent never
            // collides and the override does not apply.
            assertThat(MapPalettes.resolveSpotlightSafeDesaturationProfile(
                    DesaturationProfileChoice.NEUTRAL, Factions.INDEPENDENT))
                    .isEqualTo(DesaturationProfileChoice.NEUTRAL);
        }
    }

    @Nested
    class ResolveDesaturationPalette {

        @Test
        void resolveDesaturationPaletteForgesTheIndependentFactionsSharesUnderTheIndependentProfile() {
            var independentMock = mock(FactionAPI.class);
            when(independentMock.getBrightUIColor()).thenReturn(Color.GREEN);
            when(independentMock.getDarkUIColor()).thenReturn(Color.YELLOW);
            var sectorMock = mock(SectorAPI.class);
            when(sectorMock.getFaction(Factions.INDEPENDENT)).thenReturn(independentMock);

            var palette = MapPalettes.resolveDesaturationPalette(
                    DesaturationProfileChoice.INDEPENDENT, sectorMock, Color.GRAY);

            assertThat(palette).isEqualTo(new FactionPalette(Color.GREEN, Color.YELLOW));
        }

        @Test
        void resolveDesaturationPaletteYieldsTheNeutralColorInBothSlotsUnderTheNeutralProfile() {
            // The Neutral profile never touches the sector, so a bare mock stands in.
            var palette = MapPalettes.resolveDesaturationPalette(
                    DesaturationProfileChoice.NEUTRAL, mock(SectorAPI.class), Color.GRAY);

            assertThat(palette).isEqualTo(new FactionPalette(Color.GRAY, Color.GRAY));
        }
    }
}
