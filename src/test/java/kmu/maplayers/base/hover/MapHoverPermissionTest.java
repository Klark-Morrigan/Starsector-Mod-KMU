package kmu.maplayers.base.hover;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins that the binding hands the settings rule the two screen reads in the order the rule expects,
 * which is the one thing a composition can get wrong and nothing downstream would notice.
 *
 * <p>Swapping them is not a hypothetical: both are {@code BooleanSupplier}, so the compiler accepts
 * either order, and the two agree on most frames a player is ever on. Where they part - a map open
 * with a screen over the campaign, or the campaign itself with no map - is exactly where this
 * feature lives, so a swap would ship looking correct.
 *
 * <p>Driven through {@link HoverSwitchScopes} like every other hover case. Those scopes are named
 * for the tooltip switch, which nothing here reads: what this suite needs from them is the rest of
 * the tier they settle - the global permission withheld throughout, since granted it answers before
 * either screen read is taken and no case below could observe which read reached which disjunct.
 */
final class MapHoverPermissionTest {

    private static final BooleanSupplier A_MAP_IS_SHOWING = () -> true;
    private static final BooleanSupplier IN_GAME_SPACE = () -> true;
    private static final BooleanSupplier NOT_IN_GAME_SPACE = () -> false;
    private static final BooleanSupplier NO_MAP_SHOWING = () -> false;

    @Nested
    class IsCursorLocatable {

        @Test
        void isCursorLocatableIsTrueOnAVanillaHostWithNoPermissionGranted() {
            // The map read reaching the rule's own map disjunct, which answers with no permission
            // granted at all - so a swap that fed it the game-space read would be false here.
            HoverSwitchScopes.runWithHoverTooltipSwitchOn(() ->
                assertThat(buildPermission(A_MAP_IS_SHOWING, NOT_IN_GAME_SPACE).isCursorLocatable())
                    .isTrue());
        }

        @Test
        void isCursorLocatableIsTrueInGameSpaceWithTheGameSpacePermissionGranted() {
            // The game-space read reaching the disjunct the permission guards. The other half of the
            // swap: fed the map read, this would be false.
            HoverSwitchScopes.runWithHoverTooltipSwitchOnInGameSpace(() ->
                assertThat(buildPermission(NO_MAP_SHOWING, IN_GAME_SPACE).isCursorLocatable())
                    .isTrue());
        }

        @Test
        void isCursorLocatableIsFalseInGameSpaceWithoutThatPermission() {
            // Game space alone is not a frame the cursor can be located on - the permission is what
            // admits it, and withholding it leaves the vanilla hosts answering alone.
            HoverSwitchScopes.runWithHoverTooltipSwitchOn(() ->
                assertThat(buildPermission(NO_MAP_SHOWING, IN_GAME_SPACE).isCursorLocatable())
                    .isFalse());
        }

        @Test
        void isCursorLocatableIsFalseWithNoMapAndNoGameSpace() {
            // Some other screen with neither read open, which is most of the frames this is asked on.
            HoverSwitchScopes.runWithHoverTooltipSwitchOnInGameSpace(() ->
                assertThat(buildPermission(NO_MAP_SHOWING, NOT_IN_GAME_SPACE).isCursorLocatable())
                    .isFalse());
        }
    }

    private static MapHoverPermission buildPermission(
            BooleanSupplier isAnyMapShowing,
            BooleanSupplier isInGameSpace) {

        return new MapHoverPermission(isAnyMapShowing, isInGameSpace);
    }
}
