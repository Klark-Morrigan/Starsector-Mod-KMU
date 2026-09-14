package kmu.maplayers.base.tooltip.detail;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the holder that carries the hover box's detail level from the cycle key to the box that
 * draws: that it starts on the shallowest level rather than surprising a player with a deep one,
 * that each press is what the next read sees, that it goes exactly where it is sent rather than
 * stepping a cycle it cannot see the end of, that a load drops the level the previous save was left
 * at, and that the shared instance is genuinely one instance - two collaborators resolving different
 * holders would leave the box ignoring the key.
 *
 * <p>Each test builds its own holder rather than using {@link HoverTooltipDetailLevelState#getInstance},
 * so a move cannot leak into another test through the shared one.
 */
final class HoverTooltipDetailLevelStateTest {

    @Nested
    class GetLevel {

        @Test
        void getLevelStartsAtFactions() {

            assertThat(new HoverTooltipDetailLevelState().getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    @Nested
    class MoveToLevel {

        @Test
        void moveToLevelIsWhatTheNextReadSees() {

            var state = new HoverTooltipDetailLevelState();
            state.moveToLevel(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.SYSTEM_COMPOSITION);
        }

        @Test
        void moveToLevelTakesTheLevelItIsGivenRatherThanSteppingTheCycle() {
            // Where a press lands turns on how deep the box under the cursor goes, which this holder
            // cannot read. Stepping here it would walk a shallow box's player through tiers that
            // redraw the same thing, so it is told the destination and does no arithmetic of its own.
            var state = new HoverTooltipDetailLevelState();

            state.moveToLevel(HoverTooltipDetailLevel.PATROL_DETAILS);
            state.moveToLevel(HoverTooltipDetailLevel.FACTIONS);

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    @Nested
    class DiscardLevelFromPreviousSave {

        @Test
        void discardLevelFromPreviousSaveDropsBackToFactions() {

            var state = new HoverTooltipDetailLevelState();

            state.moveToLevel(HoverTooltipDetailLevel.MARKET_STATS);
            state.discardLevelFromPreviousSave();

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }

        @Test
        void discardLevelFromPreviousSaveLeavesFactionsAlone() {

            var state = new HoverTooltipDetailLevelState();

            state.discardLevelFromPreviousSave();

            assertThat(state.getLevel())
                .isEqualTo(HoverTooltipDetailLevel.FACTIONS);
        }
    }

    @Nested
    class GetInstance {

        @Test
        void getInstanceIsOneSharedHolder() {

            assertThat(HoverTooltipDetailLevelState.getInstance())
                .isSameAs(HoverTooltipDetailLevelState.getInstance());
        }
    }
}
