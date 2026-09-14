package kmu.maplayers.politicalmap.base.render.style;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one read every element of a bloc is painted through: its slot picked out of the shades
 * the bloc resolved, at its own opacity scaled by the bloc's mute.
 *
 * <p>Both halves of that are what a caller would otherwise compose for itself, and a caller
 * composing half of it is the defect: a fill muted while the border beside it is not reads as a
 * deliberate weighting rather than as a bug. So each case states one element and reads back both
 * the colour and the alpha.
 *
 * <p>The desaturation swap itself is {@link MapPalettes}' rule and is exercised here only as far
 * as the shades reaching an element, since which shades a bloc resolves is the whole reason the
 * three travel together.
 */
final class ResolvedBlocPaintTest {

    private static final Color OWN_PRIMARY = Color.RED;
    private static final Color OWN_SECONDARY = Color.BLUE;
    private static final Color DESATURATED_PRIMARY = Color.GREEN;
    private static final Color DESATURATED_SECONDARY = Color.YELLOW;

    private static final FactionPalette OWN_PALETTE =
        new FactionPalette(OWN_PRIMARY, OWN_SECONDARY);
    private static final FactionPalette DESATURATION_PALETTE =
        new FactionPalette(DESATURATED_PRIMARY, DESATURATED_SECONDARY);

    // The holder whose shades the bloc path reads, carrying the same pair the palette above holds
    // so a case can tell "read off the holder" from "read off the stated shades" by nothing but
    // which factory it called.
    private static final DominantHolder HOLDER =
        new DominantHolder("hegemony", OWN_PRIMARY, OWN_SECONDARY);

    // The element opacity every case states, and the mute one applies over it. Distinct so the
    // product is neither of them and an unmuted alpha cannot pass by coincidence.
    private static final double ELEMENT_OPACITY = 0.8;
    private static final double MUTE_FACTOR = 0.5;
    private static final float MUTED_ALPHA = 0.4f;

    @Nested
    class PickPaintOf {

        @Test
        void pickPaintOfPicksTheElementsSlotAtItsOwnOpacityScaledByTheMute() {

            var paint = buildPaintAdjustedBy(new ElementStyleAdjustment(MUTE_FACTOR, false))
                .pickPaintOf(buildElementAt(FactionPaletteSlot.SECONDARY));

            assertThat(paint.colour())
                .isEqualTo(OWN_SECONDARY);
            assertThat(paint.alpha())
                .isEqualTo(MUTED_ALPHA);
        }

        @Test
        void pickPaintOfPicksOutOfTheDesaturationPaletteWhenTheAdjustmentDesaturates() {
            // The swap is one decision for the whole bloc, so it is applied as the paint source is
            // resolved rather than per element: a fill reading the desaturated shades while the
            // border beside it reads the holder's own would split one surface into two blocs.
            var paint = buildPaintAdjustedBy(new ElementStyleAdjustment(MUTE_FACTOR, true))
                .pickPaintOf(buildElementAt(FactionPaletteSlot.SECONDARY));

            assertThat(paint.colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(paint.alpha())
                .isEqualTo(MUTED_ALPHA);
        }

        @Test
        void pickPaintOfLeavesTheOpacityAloneForTheIdentityAdjustment() {
            // Off filter the pass recedes nothing, so an element paints at the opacity the player
            // set it to.
            var paint = buildPaintAdjustedBy(ElementStyleAdjustment.NONE)
                .pickPaintOf(buildElementAt(FactionPaletteSlot.PRIMARY));

            assertThat(paint.colour())
                .isEqualTo(OWN_PRIMARY);
            assertThat(paint.alpha())
                .isEqualTo((float) ELEMENT_OPACITY);
        }

        @Test
        void pickPaintOfCarriesNoColourForAnElementPointedAtNoShade() {
            // A "No color" choice is how the player switches one element off, and the draw pass
            // skips a paint carrying no colour - so the alpha still resolves rather than the whole
            // paint being absent.
            var paint = buildPaintAdjustedBy(new ElementStyleAdjustment(MUTE_FACTOR, false))
                .pickPaintOf(buildElementAt(null));

            assertThat(paint.colour())
                .isNull();
            assertThat(paint.alpha())
                .isEqualTo(MUTED_ALPHA);
        }
    }

    @Nested
    class PickColourOf {

        @Test
        void pickColourOfPicksTheElementsSlotOutOfTheResolvedShades() {

            assertThat(buildPaintAdjustedBy(ElementStyleAdjustment.NONE)
                    .pickColourOf(buildElementAt(FactionPaletteSlot.PRIMARY)))
                .isEqualTo(OWN_PRIMARY);
        }

        @Test
        void pickColourOfReturnsNothingForAnElementPointedAtNoShade() {
            // The question a caller asks before it has anything to paint: a bloc whose fill and
            // border both answer null bakes no geometry at all.
            assertThat(buildPaintAdjustedBy(ElementStyleAdjustment.NONE)
                    .pickColourOf(buildElementAt(null)))
                .isNull();
        }
    }

    @Nested
    class ResolveFrom {

        @Test
        void resolveFromReadsAHolderShadesThroughTheStylingItWasResolvedUnder() {
            // The bloc path: the bundle and adjustment come off the styling, and the shades off
            // whoever holds the system, so all three describe one bloc.
            var styling = new BlocStyling(
                buildStyleAt(FactionPaletteSlot.PRIMARY),
                new ElementStyleAdjustment(MUTE_FACTOR, false));

            var paint = ResolvedBlocPaint.resolveFrom(styling, HOLDER, DESATURATION_PALETTE);

            assertThat(paint.style())
                .isSameAs(styling.style());
            assertThat(paint.adjustment())
                .isSameAs(styling.adjustment());
            assertThat(paint.palette())
                .isEqualTo(OWN_PALETTE);
        }

        @Test
        void resolveFromSinksAHolderToTheDesaturationShadesWhereTheStylingDesaturates() {

            var paint = ResolvedBlocPaint.resolveFrom(
                new BlocStyling(
                    buildStyleAt(FactionPaletteSlot.PRIMARY),
                    new ElementStyleAdjustment(MUTE_FACTOR, true)),
                HOLDER,
                DESATURATION_PALETTE);

            assertThat(paint.palette())
                .isSameAs(DESATURATION_PALETTE);
        }

        @Test
        void resolveFromTakesStatedShadesForACellNobodyHolds() {
            // The factionless path: a cell's own pair is the neutral, or the neutral lifted where
            // a spotlight spared it, and neither belongs to anyone the pass could ask for it.
            var paint = ResolvedBlocPaint.resolveFrom(
                buildStyleAt(FactionPaletteSlot.PRIMARY),
                ElementStyleAdjustment.NONE,
                OWN_PALETTE,
                DESATURATION_PALETTE);

            assertThat(paint.palette())
                .isSameAs(OWN_PALETTE);
        }
    }

    // One bloc's paint source over the stated adjustment, holding the shades above and a bundle
    // whose every element names the slot a case asks for.
    private static ResolvedBlocPaint buildPaintAdjustedBy(ElementStyleAdjustment adjustment) {
        return ResolvedBlocPaint.resolveFrom(
            buildStyleAt(FactionPaletteSlot.PRIMARY),
            adjustment,
            OWN_PALETTE,
            DESATURATION_PALETTE);
    }

    // A bundle every slot of which names the given selection. Which element a case reads is what it
    // states at the call, so the bundle itself cannot account for an answer.
    private static CategoryStyle buildStyleAt(FactionPaletteSlot slot) {

        var element = buildElementAt(slot);
        return new CategoryStyle(element, element, 1.0, element, 1.0);
    }

    private static ElementStyle buildElementAt(FactionPaletteSlot slot) {
        return new ElementStyle(slot, ELEMENT_OPACITY);
    }
}
