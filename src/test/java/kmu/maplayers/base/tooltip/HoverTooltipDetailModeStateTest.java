package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the holder that carries the hover box's detail mode from the toggle key to the box that
 * draws: that it starts on the normal box rather than surprising a player with the richer one,
 * that a flip is what the next read sees, that two flips land back where they started (so one key
 * is a toggle and not a one-way switch), that a load drops the mode the previous save was left in,
 * and that the shared instance is genuinely one instance - two collaborators resolving different
 * holders would leave the box ignoring the key.
 *
 * <p>Each test builds its own holder rather than using {@link HoverTooltipDetailModeState#getInstance},
 * so a flip cannot leak into another test through the shared one.
 */
final class HoverTooltipDetailModeStateTest {

    @Nested
    class GetMode {

        @Test
        void getModeStartsAtNormal() {
            assertThat(new HoverTooltipDetailModeState().getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }
    }

    @Nested
    class ToggleMode {

        @Test
        void toggleModeTurnsNormalIntoExpanded() {

            var state = new HoverTooltipDetailModeState();
            state.toggleMode();

            assertThat(state.getMode())
                .isEqualTo(HoverTooltipDetailMode.EXPANDED);
        }

        @Test
        void toggleModeTurnsExpandedBackIntoNormal() {

            var state = new HoverTooltipDetailModeState();

            state.toggleMode();
            state.toggleMode();

            assertThat(state.getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }

    }

    @Nested
    class DiscardModeFromPreviousSave {

        @Test
        void discardModeFromPreviousSaveDropsBackToNormal() {

            var state = new HoverTooltipDetailModeState();
            state.toggleMode();

            state.discardModeFromPreviousSave();

            assertThat(state.getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }

        @Test
        void discardModeFromPreviousSaveLeavesNormalAlone() {

            var state = new HoverTooltipDetailModeState();

            state.discardModeFromPreviousSave();

            assertThat(state.getMode())
                .isEqualTo(HoverTooltipDetailMode.NORMAL);
        }
    }

    @Nested
    class GetInstance {

        @Test
        void getInstanceIsOneSharedHolder() {
            assertThat(HoverTooltipDetailModeState.getInstance())
                .isSameAs(HoverTooltipDetailModeState.getInstance());
        }
    }
}
