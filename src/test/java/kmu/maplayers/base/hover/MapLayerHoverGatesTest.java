package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the cursor read every layer's gates answer alike: it runs for whichever kind of feedback is
 * still on, since the read backs both and gating it on the effects alone would quietly take the box
 * down with them.
 */
final class MapLayerHoverGatesTest {

    @Nested
    class IsCursorReadNeeded {

        @Test
        void isCursorReadNeededIsTrueForTheEffectsAlone() {

            var gatesFake = MapLayerHoverGatesFake.createSilent();
            gatesFake.setHoverEffectsOn(true);

            assertThat(gatesFake.isCursorReadNeeded())
                .isTrue();
        }

        @Test
        void isCursorReadNeededIsTrueForTheHoverBoxAlone() {
            // The reason the read is the union of the two: the box names what the read resolves,
            // so gating the read on the effects would switch the box off with them.
            var gatesFake = MapLayerHoverGatesFake.createSilent();
            gatesFake.setHoverTooltipOn(true);

            assertThat(gatesFake.isCursorReadNeeded())
                .isTrue();
        }

        @Test
        void isCursorReadNeededIsFalseWithBothKindsOfFeedbackOff() {
            // Nothing is left to answer, so the map-matrix read and hit test behind the cursor are
            // skipped rather than resolved into a hover nothing draws.
            assertThat(MapLayerHoverGatesFake.createSilent().isCursorReadNeeded())
                .isFalse();
        }
    }
}
