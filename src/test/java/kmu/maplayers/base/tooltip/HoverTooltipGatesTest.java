package kmu.maplayers.base.tooltip;

import kmu.maplayers.base.hover.HoverSwitchScopes;
import kmu.maplayers.base.hover.MapHoverPermission;
import kmu.maplayers.base.hover.MapHoverPermissionFixture;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the conditions two passes share - the one drawing the hover box and the one claiming the key
 * that selects which box - so neither can be left reading a condition the other does not. Both are
 * required, and the settings tier is asked first: a player who has switched hover boxes off pays
 * nothing for the live map read behind it, which walks the running game's widget tree.
 */
final class HoverTooltipGatesTest {

    @Nested
    class CanAnyBoxDraw {

        @Test
        void canAnyBoxDrawIsTrueWithTheSwitchOnAndAMapOnScreen() {
            HoverSwitchScopes.runWithHoverTooltipSwitchOn(() ->
                assertThat(HoverTooltipGates.canAnyBoxDraw(
                        MapHoverPermissionFixture.buildPermissionOnAVanillaHost()))
                    .isTrue());
        }

        @Test
        void canAnyBoxDrawIsFalseWhileNoMapIsOnScreen() {
            // The listeners reading this are called for the whole campaign UI, so without the map
            // read a box would float over the refit screen and F1 would be swallowed there.
            HoverSwitchScopes.runWithHoverTooltipSwitchOn(() ->
                assertThat(HoverTooltipGates.canAnyBoxDraw(
                        MapHoverPermissionFixture.buildPermissionOffEveryMap()))
                    .isFalse());
        }

        @Test
        void canAnyBoxDrawIsFalseWhileHoverTooltipsAreSwitchedOff() {
            HoverSwitchScopes.runWithHoverTooltipSwitchOff(() ->
                assertThat(HoverTooltipGates.canAnyBoxDraw(
                        MapHoverPermissionFixture.buildPermissionOnAVanillaHost()))
                    .isFalse());
        }

        @Test
        void canAnyBoxDrawSkipsTheScreenReadsWhileHoverTooltipsAreSwitchedOff() {
            // The order is the point: the screen reads walk the live widget tree every frame, and
            // there is nothing to ask them about once the player has switched the box off.
            var screenReadCount = new int[1];
            var countingPermission = new MapHoverPermission(
                () -> {
                    screenReadCount[0]++;
                    return true;
                },
                () -> false);

            HoverSwitchScopes.runWithHoverTooltipSwitchOff(() ->
                HoverTooltipGates.canAnyBoxDraw(countingPermission));

            assertThat(screenReadCount[0])
                .isZero();
        }
    }
}
