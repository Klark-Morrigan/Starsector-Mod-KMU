package kmu.maplayers.politicalmap.base.render.style;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;

import kmlib.colour.Colours;
import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the shared palette resolution both the fills and the cluster-name labels read: the
 * palette-colour pick that maps a player's colour choice to a palette shade, the holder-shade
 * pick that falls back to neutral for an unowned cell, the neutral pair an ownerless cell draws
 * in, the effective-palette swap a receding cell takes, and the desaturation-palette
 * resolver it recolours through.
 */
final class MapPalettesTest {

    // Two distinct shades so a pick can be told apart from its counterpart.
    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    private static final FactionPalette PALETTE = new FactionPalette(PRIMARY, SECONDARY);

    // A selection from no layer's option set, standing in for a second map layer's own options.
    // Not an enum, since the interface admits any implementation and a record proves the pick
    // is rejected on type rather than on being an unrecognised constant.
    private record ForeignPaintSelectionFake(String id) implements ElementPaintSelection {
    }

    @Nested
    class PickPaletteColour {

        @Test
        void pickPaletteColourReturnsThePrimaryShadeForAPrimaryChoice() {
            assertThat(MapPalettes.pickPaletteColour(
                    FactionPaletteSlot.PRIMARY,
                    PALETTE))
                .isEqualTo(PRIMARY);
        }

        @Test
        void pickPaletteColourReturnsTheSecondaryShadeForASecondaryChoice() {
            assertThat(MapPalettes.pickPaletteColour(
                    FactionPaletteSlot.SECONDARY,
                    PALETTE))
                .isEqualTo(SECONDARY);
        }

        @Test
        void pickPaletteColourReturnsNullForAnAbsentSelection() {
            // No selection at all is how a style says "paints nothing" - what the player's
            // "No color" choice resolves to - so the render layer skips that element.
            assertThat(MapPalettes.pickPaletteColour(null, PALETTE))
                .isNull();
        }

        @Test
        void pickPaletteColourReturnsNullForAnotherLayersSelection() {
            // The selection interface is open, so a second map layer's own pick can reach
            // here. It resolves to no shade rather than to a wrong one or a class cast:
            // these two shades are the political map's, and nothing else indexes them.
            assertThat(MapPalettes.pickPaletteColour(
                    new ForeignPaintSelectionFake("hazard-severe"),
                    PALETTE))
                .isNull();
        }
    }

    @Nested
    class ResolveNeutralPalette {

        @Test
        void resolveNeutralPaletteFillsBothSlotsWithTheOneColour() {
            // An ownerless cell has no palette to pick a slot from, so both slots answer the
            // same colour and whichever slot an element names paints neutral.
            var neutral = MapPalettes.resolveNeutralPalette(Color.GRAY);

            assertThat(neutral.primaryColour())
                .isEqualTo(Color.GRAY);
            assertThat(neutral.secondaryColour())
                .isEqualTo(Color.GRAY);
        }
    }

    @Nested
    class PickHolderPaletteColour {

        // A distinct third shade, so a neutral substitution cannot be mistaken for either of
        // the holder's own.
        private static final Color NEUTRAL = Color.GRAY;
        private static final DominantHolder OWNER =
            new DominantHolder("hegemony", PRIMARY, SECONDARY);

        @Test
        void pickHolderPaletteColourReturnsTheHoldersShadeForTheChoice() {

            assertThat(MapPalettes.pickHolderPaletteColour(
                    FactionPaletteSlot.PRIMARY,
                    OWNER,
                    NEUTRAL))
                .isEqualTo(PRIMARY);

            assertThat(MapPalettes.pickHolderPaletteColour(
                    FactionPaletteSlot.SECONDARY,
                    OWNER,
                    NEUTRAL))
                .isEqualTo(SECONDARY);
        }

        @Test
        void pickHolderPaletteColourFallsBackToTheNeutralShadeForAnUnownedCell() {
            // A factionless system has no palette, so both shades resolve neutral - the same
            // substitution its own cell outline draws under.
            assertThat(MapPalettes.pickHolderPaletteColour(
                    FactionPaletteSlot.PRIMARY,
                    null,
                    NEUTRAL))
                .isEqualTo(NEUTRAL);

            assertThat(MapPalettes.pickHolderPaletteColour(
                    FactionPaletteSlot.SECONDARY,
                    null,
                    NEUTRAL))
                .isEqualTo(NEUTRAL);
        }

        @Test
        void pickHolderPaletteColourReturnsNullForNoColourWhoeverHoldsTheCell() {
            assertThat(MapPalettes.pickHolderPaletteColour(
                    null,
                    OWNER,
                    NEUTRAL))
                .isNull();

            assertThat(MapPalettes.pickHolderPaletteColour(
                    null,
                    null,
                    NEUTRAL))
                .isNull();
        }
    }

    @Nested
    class ResolveEffectivePalette {

        // The pass's shared desaturation shades, distinct from every other colour here so a swap
        // to them is unmistakable.
        private static final FactionPalette DESATURATION =
            new FactionPalette(Color.GREEN, Color.YELLOW);

        private static final DominantHolder OWNER =
            new DominantHolder("hegemony", PRIMARY, SECONDARY);

        // The one pair a factionless cell holds: neutral in both slots, since it names no faction.
        private static final FactionPalette NEUTRAL_PAIR =
            new FactionPalette(Color.GRAY, Color.GRAY);

        @Test
        void resolveEffectivePaletteKeepsTheHoldersOwnShadesWhenTheAdjustmentDoesNotDesaturate() {

            var palette = MapPalettes.resolveEffectivePalette(
                new ElementStyleAdjustment(0.5, false),
                OWNER,
                DESATURATION);

            assertThat(palette.primaryColour())
                .isEqualTo(PRIMARY);
            assertThat(palette.secondaryColour())
                .isEqualTo(SECONDARY);
        }

        @Test
        void resolveEffectivePaletteSwapsAnHoldersShadesForThePassPaletteWhenItDesaturates() {
            // Muting is orthogonal: the multiplier scales opacity elsewhere and never touches
            // which two shades are painted.
            var palette = MapPalettes.resolveEffectivePalette(
                new ElementStyleAdjustment(1.0, true),
                OWNER,
                DESATURATION);

            assertThat(palette)
                .isEqualTo(DESATURATION);
        }

        @Test
        void resolveEffectivePaletteSwapsUnownedNeutralShadesForThePassPaletteWhenItDesaturates() {
            // A cell with no holder recolours by the same rule: a receding decivilised cell leaves
            // its neutral pair for the desaturation palette rather than staying neutral.
            var palette = MapPalettes.resolveEffectivePalette(
                new ElementStyleAdjustment(1.0, true),
                NEUTRAL_PAIR,
                DESATURATION);

            assertThat(palette)
                .isEqualTo(DESATURATION);
        }

        @Test
        void resolveEffectivePaletteKeepsUnownedNeutralShadesForTheNoneAdjustment() {

            var palette = MapPalettes.resolveEffectivePalette(
                ElementStyleAdjustment.NONE,
                NEUTRAL_PAIR,
                DESATURATION);

            assertThat(palette)
                .isEqualTo(NEUTRAL_PAIR);
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

            when(independentMock.getBrightUIColor())
                .thenReturn(Color.GREEN);
            when(independentMock.getDarkUIColor())
                .thenReturn(Color.YELLOW);

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction(Factions.INDEPENDENT))
                .thenReturn(independentMock);

            var palette = MapPalettes.resolveDesaturationPalette(sectorMock, DARKENING_STRENGTH);

            // The Independent pair, sunk toward black by the strength so the receded fills read
            // behind genuine independent-held space rather than as it.
            assertThat(palette)
                .isEqualTo(new FactionPalette(
                    Colours.darken(Color.GREEN, KEEP_FACTOR),
                    Colours.darken(Color.YELLOW, KEEP_FACTOR)));
        }

        @Test
        void resolveDesaturationPaletteLeavesTheIndependentShadesUntouchedAtZeroStrength() {

            var independentMock = mock(FactionAPI.class);

            when(independentMock.getBrightUIColor())
                .thenReturn(Color.GREEN);
            when(independentMock.getDarkUIColor())
                .thenReturn(Color.YELLOW);

            var sectorMock = mock(SectorAPI.class);

            when(sectorMock.getFaction(Factions.INDEPENDENT))
                .thenReturn(independentMock);

            // A strength of 0 keeps full brightness, so the target is the raw Independent pair.
            var palette = MapPalettes.resolveDesaturationPalette(sectorMock, 0.0);

            assertThat(palette)
                .isEqualTo(new FactionPalette(Color.GREEN, Color.YELLOW));
        }
    }

    @Nested
    class ResolvePresencePalette {

        // A mid grey standing in for the shared neutral, chosen because every channel holds the
        // same value: a wash that shifted hue rather than value would break that equality, which
        // is the whole property the lift is meant to keep.
        private static final Color NEUTRAL_GREY = new Color(128, 128, 128);

        @Test
        void resolvePresencePaletteLiftsTheNeutralTowardWhiteInBothSlots() {
            // 128 + (255 - 128) * 0.2 = 153.4, rounded to 153 on every channel: still a grey, a
            // fifth of the way to white. Both slots hold it, as the plain neutral palette does,
            // so whichever slot an element names it paints the lifted shade.
            var palette = MapPalettes.resolvePresencePalette(NEUTRAL_GREY, 0.2);

            assertThat(palette)
                .isEqualTo(new FactionPalette(
                    new Color(153, 153, 153),
                    new Color(153, 153, 153)));
        }

        @Test
        void resolvePresencePaletteLeavesTheNeutralUntouchedAtZeroStrength() {
            // A strength of 0 is the plain neutral palette, so switching the lift off returns a
            // spared cell to painting exactly as an unspared one does.
            var palette = MapPalettes.resolvePresencePalette(NEUTRAL_GREY, 0.0);

            assertThat(palette)
                .isEqualTo(new FactionPalette(NEUTRAL_GREY, NEUTRAL_GREY));
        }

        @Test
        void resolvePresencePaletteReachesWhiteAtFullStrength() {

            var palette = MapPalettes.resolvePresencePalette(NEUTRAL_GREY, 1.0);

            assertThat(palette)
                .isEqualTo(new FactionPalette(Color.WHITE, Color.WHITE));
        }
    }
}
