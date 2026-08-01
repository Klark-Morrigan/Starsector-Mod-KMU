package kmu.maplayers.politicalmap.base.render.style;

import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one crossing between the settings screen's wire format and the form a style can
 * hold: which stored choice selects which palette slot, and which selects none at all.
 *
 * <p>Asserted against the selection constants directly rather than by calling the crossing
 * again, because an expectation computed by the method under test cannot fail when the mapping
 * is wrong - and a mapping that swapped the two slots would repaint every element on the map.
 */
final class FactionPaletteSlotTest {

    @Nested
    class ResolvePaintSelectionOf {

        @Test
        void resolvePaintSelectionOfSelectsTheBrightSlotForThePrimaryChoice() {
            assertThat(FactionPaletteSlot.resolvePaintSelectionOf(FactionPaletteChoice.PRIMARY))
                .isEqualTo(FactionPaletteSlot.PRIMARY);
        }

        @Test
        void resolvePaintSelectionOfSelectsTheDarkSlotForTheSecondaryChoice() {
            assertThat(FactionPaletteSlot.resolvePaintSelectionOf(FactionPaletteChoice.SECONDARY))
                .isEqualTo(FactionPaletteSlot.SECONDARY);
        }

        @Test
        void resolvePaintSelectionOfTakesTheNoColourChoiceToNoSelection() {
            // The settings enum's third option names no slot: a style says "paints
            // nothing" by holding no selection, which is why the render-side type is narrower
            // than the wire format and cannot express an explicit no-colour value.
            assertThat(FactionPaletteSlot.resolvePaintSelectionOf(FactionPaletteChoice.NONE))
                .isNull();
        }

        @Test
        void resolvePaintSelectionOfTakesAnUnresolvedSettingToNoSelection() {
            // A setting that did not resolve reads the same to a style as an explicit
            // no-colour pick, so the crossing absorbs the null rather than forcing every
            // caller to guard one branch that ends in the same place.
            assertThat(FactionPaletteSlot.resolvePaintSelectionOf(null))
                .isNull();
        }
    }
}
