package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the paint scheme's two answers: the shade its neutral pair reads as, and what a build that
 * never resolved a theme carries in place of one.
 *
 * <p>The placeholder is worth pinning precisely because nothing paints it. A slot left null there
 * would surface as a crash on the frame after a failed first build - the one frame no test of the
 * normal path ever reaches - so what this asserts is that every slot holds something at all.
 */
final class MapStylingTest {

    @Nested
    class ReadNeutralColour {

        @Test
        void readNeutralColourAnswersTheShadeTheNeutralPairHolds() {
            // A factionless cell names no faction, so its pair holds one shade twice and the
            // colour is whichever slot you look at.
            var styling = new MapStyling(
                null,
                new FactionPalette(Color.CYAN, Color.CYAN),
                new FactionPalette(Color.MAGENTA, Color.ORANGE),
                new FactionPalette(Color.PINK, Color.WHITE));

            assertThat(styling.readNeutralColour())
                .isEqualTo(Color.CYAN);
        }

        @Test
        void readNeutralColourReadsTheNeutralPairRatherThanEitherSpotlightPalette() {
            // The three palettes sit in adjacent same-typed slots, so a colour read from the wrong
            // one would still compile and still answer a Color. Only distinct shades catch it.
            var styling = new MapStyling(
                null,
                new FactionPalette(Color.CYAN, Color.CYAN),
                new FactionPalette(Color.MAGENTA, Color.ORANGE),
                new FactionPalette(Color.PINK, Color.WHITE));

            assertThat(styling.readNeutralColour())
                .isNotEqualTo(Color.MAGENTA)
                .isNotEqualTo(Color.PINK);
        }
    }

    @Nested
    class CreateEmpty {

        @Test
        void createEmptyCarriesNoThemeAtAll() {
            // The honest record of the failure: no theme was resolved, and the render path skips
            // an empty overlay before it would read one.
            assertThat(MapStyling.createEmpty().renderStyle())
                .isNull();
        }

        @Test
        void createEmptyFillsEveryPaletteSlotSoNoneIsNull() {
            
            var styling = MapStyling.createEmpty();

            assertThat(styling.neutralPalette())
                .isNotNull();
            assertThat(styling.desaturationPalette())
                .isNotNull();
            assertThat(styling.presencePalette())
                .isNotNull();
            assertThat(styling.readNeutralColour())
                .isNotNull();
        }

        @Test
        void createEmptyHoldsOneNeutralShadeInEverySlotOfEveryPalette() {
            // Every slot the same stand-in, so nothing in the placeholder reads as a choice
            // somebody made - and the colour read off the neutral pair answers that same shade.
            var styling = MapStyling.createEmpty();
            var placeholder = styling.readNeutralColour();

            assertThat(styling.neutralPalette())
                .isEqualTo(new FactionPalette(placeholder, placeholder));
            assertThat(styling.desaturationPalette())
                .isEqualTo(new FactionPalette(placeholder, placeholder));
            assertThat(styling.presencePalette())
                .isEqualTo(new FactionPalette(placeholder, placeholder));
        }
    }
}
