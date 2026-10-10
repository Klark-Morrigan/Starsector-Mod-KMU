package kmu.maplayers.ownermap.render.style;

import kmlib.colour.ColourPair;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.owners.SystemOwner;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one read every element of an owner is painted through: its slot picked out of the shades
 * the owner resolved, at its own opacity scaled by the owner's mute.
 *
 * <p>Both halves of that are what a caller would otherwise compose for itself, and a caller
 * composing half of it is the defect: a fill muted while the border beside it is not reads as a
 * deliberate weighting rather than as a bug. So each case states one element and reads back both
 * the colour and the alpha.
 *
 * <p>The desaturation swap itself is {@link MapPalettes}' rule and is exercised here only as far
 * as the shades reaching an element, since which shades an owner resolves is the whole reason the
 * three travel together.
 */
final class ResolvedOwnerPaintTests {

    private static final Color OWN_PRIMARY = Color.RED;
    private static final Color OWN_SECONDARY = Color.BLUE;
    private static final Color DESATURATED_PRIMARY = Color.GREEN;
    private static final Color DESATURATED_SECONDARY = Color.YELLOW;

    private static final ColourPair OWN_PALETTE =
        new ColourPair(OWN_PRIMARY, OWN_SECONDARY);

    private static final ColourPair DESATURATION_PALETTE =
        new ColourPair(DESATURATED_PRIMARY, DESATURATED_SECONDARY);

    // The holder whose shades the owner path reads, carrying the same pair the palette above holds
    // so a case can tell "read off the holder" from "read off the stated shades" by nothing but
    // which factory it called.
    private static final SystemOwner HOLDER =
        new SystemOwner("hegemony", new ColourPair(OWN_PRIMARY, OWN_SECONDARY));

    // The element opacity every case states, and the mute one applies over it. Distinct so the
    // product is neither of them and an unmuted alpha cannot pass by coincidence.
    private static final double ELEMENT_OPACITY = 0.8;
    private static final double MUTE_FACTOR = 0.5;
    private static final float MUTED_ALPHA = 0.4f;

    @Nested
    class PickPaintOf {

        @Test
        void picksTheElementsSlotAtItsOwnOpacityScaledByTheMute() {

            var paint = buildPaintAdjustedBy(new ElementStyleAdjustment(MUTE_FACTOR, false))
                .pickPaintOf(buildElementAt(OwnerPaletteSlot.SECONDARY));

            assertThat(paint.colour())
                .isEqualTo(OWN_SECONDARY);
            assertThat(paint.alpha())
                .isEqualTo(MUTED_ALPHA);
        }

        @Test
        void picksOutOfTheDesaturationPaletteWhenTheAdjustmentDesaturates() {
            // The swap is one decision for the whole owner, so it is applied as the paint source is
            // resolved rather than per element: a fill reading the desaturated shades while the
            // border beside it reads the holder's own would split one surface into two owners.
            var paint = buildPaintAdjustedBy(new ElementStyleAdjustment(MUTE_FACTOR, true))
                .pickPaintOf(buildElementAt(OwnerPaletteSlot.SECONDARY));

            assertThat(paint.colour())
                .isEqualTo(DESATURATED_SECONDARY);
            assertThat(paint.alpha())
                .isEqualTo(MUTED_ALPHA);
        }

        @Test
        void leavesTheOpacityAloneForTheIdentityAdjustment() {
            // Off filter the pass recedes nothing, so an element paints at the opacity the player
            // set it to.
            var paint = buildPaintAdjustedBy(ElementStyleAdjustment.NONE)
                .pickPaintOf(buildElementAt(OwnerPaletteSlot.PRIMARY));

            assertThat(paint.colour())
                .isEqualTo(OWN_PRIMARY);
            assertThat(paint.alpha())
                .isEqualTo((float) ELEMENT_OPACITY);
        }

        @Test
        void carriesNoColourForAnElementPointedAtNoShade() {
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
        void picksTheElementsSlotOutOfTheResolvedShades() {

            assertThat(buildPaintAdjustedBy(ElementStyleAdjustment.NONE)
                    .pickColourOf(buildElementAt(OwnerPaletteSlot.PRIMARY)))
                .isEqualTo(OWN_PRIMARY);
        }

        @Test
        void returnsNothingForAnElementPointedAtNoShade() {
            // The question a caller asks before it has anything to paint: an owner whose fill and
            // border both answer null bakes no geometry at all.
            assertThat(buildPaintAdjustedBy(ElementStyleAdjustment.NONE)
                    .pickColourOf(buildElementAt(null)))
                .isNull();
        }
    }

    @Nested
    class ResolveFrom {

        @Test
        void readsAHolderShadesThroughTheStylingItWasResolvedUnder() {
            // The owner path: the bundle and adjustment come off the styling, and the shades off
            // whoever holds the system, so all three describe one owner.
            var styling = new OwnerStyling(
                buildStyleAt(OwnerPaletteSlot.PRIMARY),
                new ElementStyleAdjustment(MUTE_FACTOR, false));

            var paint = ResolvedOwnerPaint.resolveFrom(styling, HOLDER, DESATURATION_PALETTE);

            assertThat(paint.style())
                .isSameAs(styling.style());
            assertThat(paint.adjustment())
                .isSameAs(styling.adjustment());
            assertThat(paint.palette())
                .isEqualTo(OWN_PALETTE);
        }

        @Test
        void sinksAHolderToTheDesaturationShadesWhereTheStylingDesaturates() {

            var paint = ResolvedOwnerPaint.resolveFrom(
                new OwnerStyling(
                    buildStyleAt(OwnerPaletteSlot.PRIMARY),
                    new ElementStyleAdjustment(MUTE_FACTOR, true)),
                HOLDER,
                DESATURATION_PALETTE);

            assertThat(paint.palette())
                .isSameAs(DESATURATION_PALETTE);
        }

        @Test
        void takesStatedShadesForACellNobodyHolds() {
            // The unowned path: a cell's own pair is the neutral, or the neutral lifted where
            // a spotlight spared it, and neither belongs to anyone the pass could ask for it.
            var paint = ResolvedOwnerPaint.resolveFrom(
                buildStyleAt(OwnerPaletteSlot.PRIMARY),
                ElementStyleAdjustment.NONE,
                OWN_PALETTE,
                DESATURATION_PALETTE);

            assertThat(paint.palette())
                .isSameAs(OWN_PALETTE);
        }
    }

    // One owner's paint source over the stated adjustment, holding the shades above and a bundle
    // whose every element names the slot a case asks for.
    private static ResolvedOwnerPaint buildPaintAdjustedBy(ElementStyleAdjustment adjustment) {

        return ResolvedOwnerPaint.resolveFrom(
            buildStyleAt(OwnerPaletteSlot.PRIMARY),
            adjustment,
            OWN_PALETTE,
            DESATURATION_PALETTE);
    }

    // A bundle every slot of which names the given selection. Which element a case reads is what it
    // states at the call, so the bundle itself cannot account for an answer.
    private static CategoryStyle buildStyleAt(OwnerPaletteSlot slot) {

        var element = buildElementAt(slot);

        return new CategoryStyle(element, element, 1.0, element, 1.0);
    }

    private static ElementStyle buildElementAt(OwnerPaletteSlot slot) {

        return new ElementStyle(slot, ELEMENT_OPACITY);
    }
}
