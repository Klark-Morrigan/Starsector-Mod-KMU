package kmu.mods.rat;

import kmlib.math.geometry.Rectangle;
import kmlib.starsector.ui.input.CursorPosition;
import kmlib.starsector.ui.input.VanillaCursorPosition;
import kmlib.starsector.ui.map.presence.MapPresence;
import kmlib.starsector.ui.map.probes.EmbeddedMap;

import kmu.maplayers.base.hover.cover.MapCover;
import kmu.starsector.ui.SingleEmbeddedMapReader;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The cover that confines the hover to a docked minimap: with the compatibility mode engaged and no
 * vanilla map on screen, the minimap's own box is the only place the cursor means anything, so
 * everywhere else on the screen is covered.
 *
 * <p>A confinement rather than a suppression, which is why it is the one cover that opens something
 * up. Permitting the hover in game space lets the layers answer the cursor on frames where the only
 * map drawn is somebody else's minimap; without this, that permission would answer the cursor over
 * the whole screen, since the map geometry underneath happily resolves a system for every pixel.
 *
 * <p>Reads in two steps, and the first is the whole of the non-interference guarantee. On a frame
 * where a vanilla map is showing, this answers before a box is read at all, so a minimap panel
 * travelling across the screen cannot reach the {@code M} map or the intel visor even in principle.
 * That read is redundant against the permission this pairs with - game space excludes every vanilla
 * host by construction - and it is kept anyway, because a guarantee resting on two reads agreeing
 * about what "no screen" means is one refactor away from resting on nothing.
 *
 * <p>The second step is the confinement, over a box read live every frame and never kept. A panel is
 * created off screen and walks to its resting place over many frames, so a box held even for the
 * length of that walk describes where the minimap has been rather than where it is.
 *
 * <p>That live box is also why no collapsed or expanded state is detected. A parked minimap's box is
 * off screen, so every cursor position is outside it and the same comparison covers the frame whole;
 * one halfway through its slide confines to where it has actually got to. The mod has no such state
 * either - only a position - so there is nothing to read even if it were wanted.
 *
 * <p>Exactly one map on screen is the confinement's precondition, and more than one covers exactly
 * as none does. The frame carries one transform and the hover resolves through whichever pass drew
 * last, so a box can only be opened up while there is no question which pass it belongs to: two
 * embedded maps drawn in one frame would let a hover over the first resolve through the second's
 * transform, which is a wrong answer rather than a missing one. That the surface is a single one is
 * {@link SingleEmbeddedMapReader}'s reading rather than this cover's own, shared with the rule that
 * switches such a surface off - the reason for needing one is per-rule and stated here, while two
 * copies of the reading could answer differently about the same frame.
 *
 * <p><b>Fails closed</b>, against the rule every other cover here follows. Those must not be able to
 * switch the hover off, because there is a legitimate hover behind them to protect. Here there is
 * not: on these frames nothing is pointable except the one box, so a finder coming back with nothing
 * has to cover rather than open up, or the leak returns by way of the read meant to stop it. What
 * failing closed costs is the behaviour the player had before switching the mode on.
 *
 * <p>Named for the mode rather than for what it reads - see
 * {@link RandomAssortmentOfThingsCompatibilityMode}. Nothing here names a mod: an embedded map is
 * found structurally, and its box is the game's own answer about a widget.
 */
public final class RandomAssortmentOfThingsMinimapCover implements MapCover {

    private final CursorPosition cursor;

    // SingleEmbeddedMapReader's reading, taken afresh each frame, as a supplier so this class
    // states its question and not where the answer is walked out of.
    private final Supplier<EmbeddedMap> findSingleEmbeddedMap;

    private final BooleanSupplier isAnyMapShowing;

    private final RandomAssortmentOfThingsCompatibilityMode mode;

    /**
     * @param mode                  whether the player's compatibility mode is on and there is a
     *                              minimap to confine to
     * @param isAnyMapShowing       whether either vanilla map host is showing, from
     *                              {@code MapPresence#isAnyMapShowing}
     * @param findSingleEmbeddedMap the one map surface on screen that is not the one the player
     *                              opened, or null when there is not exactly one
     * @param cursor                where the pointer is, in the UI units the boxes are laid out in
     */
    public RandomAssortmentOfThingsMinimapCover(
            RandomAssortmentOfThingsCompatibilityMode mode,
            BooleanSupplier isAnyMapShowing,
            Supplier<EmbeddedMap> findSingleEmbeddedMap,
            CursorPosition cursor) {

        this.cursor = cursor;
        this.findSingleEmbeddedMap = findSingleEmbeddedMap;
        this.isAnyMapShowing = isAnyMapShowing;
        this.mode = mode;
    }

    /**
     * @return the cover over the mode, the presence read and the shared widget walk a running game
     *         has
     */
    public static RandomAssortmentOfThingsMinimapCover createForLiveScreen() {
        return new RandomAssortmentOfThingsMinimapCover(
            RandomAssortmentOfThingsCompatibilityMode.createForLiveGame(),
            new MapPresence()::isAnyMapShowing,
            SingleEmbeddedMapReader.INSTANCE::resolveSingleEmbeddedMap,
            new VanillaCursorPosition());
    }

    @Override
    public boolean isCoveringCursor() {

        // Nothing to confine to. The player has not asked for the mode, or there is no minimap on
        // this install - either way this cover has no business narrowing where the cursor counts.
        if (!mode.isEngaged()) {
            return false;
        }
        // A vanilla host owns this frame, so the confinement does not apply to it at all. Answered
        // before any box is read - see the guarantee this class states.
        if (isAnyMapShowing.getAsBoolean()) {
            return false;
        }
        var minimapBox = resolveConfinableMinimapBox();

        return minimapBox == null
            || !minimapBox.containsPoint(cursor.getUiX(), cursor.getUiY());
    }

    // The box the cursor may be confined to, or null when no single box can stand for the frame -
    // no single map on screen, or one that is not drawn anywhere the player could point at. Both
    // cover, which is this cover's failing closed.
    private Rectangle resolveConfinableMinimapBox() {
        var minimap = findSingleEmbeddedMap.get();

        return minimap == null ? null : minimap.resolveDrawnBox();
    }
}
