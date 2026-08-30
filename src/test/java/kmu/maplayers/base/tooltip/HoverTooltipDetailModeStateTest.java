package kmu.maplayers.base.tooltip;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the holder that carries the hover box's detail level from the cycle key to the box that
 * draws: that it starts on the shallowest level rather than surprising a player with a deep one,
 * that each press is what the next read sees, that the cycle wraps from the deepest level back to
 * the first (so one key is a cycle and not a one-way descent), that a load drops the level the
 * previous save was left at, and that the shared instance is genuinely one instance - two
 * collaborators resolving different holders would leave the box ignoring the key.
 *
 * <p>Each test builds its own holder rather than using {@link HoverTooltipDetailModeState#getInstance},
 * so an advance cannot leak into another test through the shared one.
 */
final class HoverTooltipDetailModeStateTest {

    @Nested
    class GetLevel {

        @Test
        void getLevelStartsAtFactions() {

            assertThat(new HoverTooltipDetailModeState().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    @Nested
    class AdvanceLevel {

        @Test
        void advanceLevelStepsFromFactionsToSystemComposition() {

            var state = new HoverTooltipDetailModeState();
            state.advanceLevel();

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);
        }

        @Test
        void advanceLevelReachesTheDeepestLevelOnTheThirdPress() {

            var state = new HoverTooltipDetailModeState();

            state.advanceLevel();
            state.advanceLevel();
            state.advanceLevel();

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.PATROL_DETAILS);
        }

        @Test
        void advanceLevelWrapsBackToFactionsOnTheFourthPress() {
            // The wrap is what keeps every press acting: from the deepest level the key collapses
            // rather than dead-ending, so the player is never stuck in the tallest box.
            var state = new HoverTooltipDetailModeState();

            state.advanceLevel();
            state.advanceLevel();
            state.advanceLevel();
            state.advanceLevel();

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    @Nested
    class DiscardModeFromPreviousSave {

        @Test
        void discardModeFromPreviousSaveDropsBackToFactions() {

            var state = new HoverTooltipDetailModeState();

            state.advanceLevel();
            state.discardModeFromPreviousSave();

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void discardModeFromPreviousSaveLeavesFactionsAlone() {

            var state = new HoverTooltipDetailModeState();

            state.discardModeFromPreviousSave();

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
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
