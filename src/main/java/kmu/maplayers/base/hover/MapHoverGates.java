package kmu.maplayers.base.hover;

import kmu.settings.KmuMapHoverSettings;

import java.util.function.BooleanSupplier;

/**
 * The hover switches that answer for every map layer at once: whether the map answers the cursor at
 * all, and then whether each kind of answer - the effects (the halo and the cell wash) and the
 * tooltip (the box naming what is under the cursor) - is on across the whole map.
 *
 * <p>Hover feedback is switched at three tiers, and this is the top two of them. The master covers
 * both kinds, the pair under it covers one kind each, and a layer carries its own pair below these
 * for its own paint. Every tier ANDs with the one above, so a layer switch can only withhold
 * feedback the tiers above already allow, and a global switch reaches every layer's.
 *
 * <p>The AND lives here rather than at each reader because the tiers are the whole point of the
 * arrangement: a reader that forgot one would silently answer for a tier a player had switched off.
 * A layer asks these for the tiers above it and ANDs its own switch in, which is the only reading a
 * layer needs to do to take part.
 *
 * <p>Every switch is read live rather than cached, so a player toggling one on the settings screen
 * sees the map change on the next frame without a rebuild.
 */
public final class MapHoverGates {

    private MapHoverGates() {
    }

    /**
     * @return whether hover effects may draw on any layer at all - the master ANDed with the global
     *         effects switch. A layer ANDs its own effects switch into this before painting a halo
     *         or a wash
     */
    public static boolean isHoverEffectsEnabled() {
        return KmuMapHoverSettings.isMapHoveringEnabled()
            && KmuMapHoverSettings.areMapHoverEffectsEnabled();
    }

    /**
     * @return whether a hover tooltip may draw on any layer at all - the master ANDed with the
     *         global tooltip switch. A layer ANDs its own tooltip switch into this before offering
     *         a box
     */
    public static boolean isHoverTooltipEnabled() {
        return KmuMapHoverSettings.isMapHoveringEnabled()
            && KmuMapHoverSettings.isMapHoverTooltipEnabled();
    }

    /**
     * Whether the cursor can be located against the pass now running.
     *
     * <p>A different question from the switches above, and the reason it sits beside them: those
     * ask what the player wants answered, this asks whether an answer is possible. The map hook the
     * layers draw through belongs to the sector map by convention alone, and a map another mod
     * builds drives it too - with its own position and zoom, and no way for this end to tell that
     * transform from the vanilla one. A cursor unprojected against it still yields a world point,
     * and in hyperspace, where campaign and sector map coordinates are one space, that point lands
     * inside real cells. So the wrong answer here is not a missing one but a confident one.
     *
     * <p>The presence read is host-blind by design: it says a vanilla map is on screen somewhere,
     * never that this pass is that map's. With one showing while a foreign map also draws, both
     * passes answer true, and which of them the frame's single cursor read lands on is settled
     * elsewhere. The render constraint is what closes that case, by keeping the foreign pass from
     * running at all.
     *
     * <p>Three ways to be locatable, of which the vanilla hosts are the one that answers with no
     * permission given at all. The two beside it are the player's, and each widens what the pass may
     * answer rather than narrowing it:
     *
     * <ul>
     *   <li>The global permission admits every pass there is, foreign surfaces on any screen
     *       included, and so short-circuits before anything is read from the screen. Off by
     *       default: granted, a wrong hover is heard where no map is drawn at all.</li>
     *   <li>The game-space permission admits the frames where the player is looking at the campaign
     *       world itself. That is where a mod docking a map surface over the campaign puts the only
     *       map on screen - and it closes the same surface again the moment the panel is parked,
     *       since a mod parks it on exactly the conditions that end game space. Which is what makes
     *       it a general rule rather than a per-mod one. On by default, because it is inert without
     *       such a surface: no map on the campaign view means no pass here to widen.</li>
     * </ul>
     *
     * <p>Game space deliberately excludes nothing about the pause menu, which is raised without
     * taking the screen under it down. {@code PauseMenuMapCover} owns that read, and a permission
     * testing it as well would be answering a question a cover already answers - two places to
     * change when the menu read moves, and one of them silent.
     *
     * <p>Both live reads arrive as suppliers so neither is taken on a frame whose answer does not
     * turn on it: a player who has permitted the hover globally pays for no screen read at all.
     *
     * @param isAnyMapShowing whether either vanilla map host is showing, from
     *                        {@code MapPresence#isAnyMapShowing}
     * @param isInGameSpace   whether the player is looking at the campaign world with no screen over
     *                        it, from {@code CampaignScreenView#isShowingGameSpace}
     * @return whether the hover may be resolved on this pass
     */
    public static boolean isCursorLocatableOn(
            BooleanSupplier isAnyMapShowing,
            BooleanSupplier isInGameSpace) {

        if (KmuMapHoverSettings.isMapLayerMouseoverGlobal()) {
            return true;
        }
        if (isAnyMapShowing.getAsBoolean()) {
            return true;
        }
        return KmuMapHoverSettings.isMapLayerMouseoverEnabledInGameSpace()
            && isInGameSpace.getAsBoolean();
    }
}
