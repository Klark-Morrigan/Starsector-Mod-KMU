package kmu.maplayers.ownermap.sidebar;

import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;

import java.util.function.Predicate;

/**
 * The one flip every sidebar checkbox makes: read its preference on the panel it was placed on, and
 * write the opposite back there. Shared so each checkbox states only which preference it drives, and
 * the step where the carried panel becomes the address of the slot written is spelled once.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
final class PanelToggles {

    private PanelToggles() {
        // utility class, no instances.
    }

    /**
     * Flips one on/off preference on the panel a checkbox was placed on, so the box is a plain
     * on/off rather than a setter a caller has to feed.
     *
     * @param target     the panel the checkbox sits on: its screen addresses both the read and the
     *                   write, and its board is raised on so that sector repaints
     * @param readState  the preference's current state on a screen
     * @param writeState the preference's writer, handed the flipped state
     */
    static void flipToggle(
            BodyControlTarget target,
            Predicate<ScreenMemoryScope> readState,
            ToggleWriter writeState) {

        writeState.writeToggle(
            target.memoryScope(),
            !readState.test(target.memoryScope()),
            target.board());
    }

    /**
     * Writes one on/off preference for a screen and raises the repaint it owes on a board - the
     * shape every sidebar-only preference's setter already has.
     */
    @FunctionalInterface
    interface ToggleWriter {

        /**
         * @param memoryScope the screen whose slot is written
         * @param isOn        the new state
         * @param board       the refresh board of the sector the write repaints
         */
        void writeToggle(ScreenMemoryScope memoryScope, boolean isOn, MapLayerRefreshBoard board);
    }
}
