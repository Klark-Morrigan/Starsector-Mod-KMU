package kmu.starsector.ui;

import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.starsector.ui.map.probes.ShownMapTab;

/**
 * The widget the map surface in front of the player hangs on, whichever kind of surface that is: the
 * game's own map tab when one is up, and the panel a mod docked a map into otherwise.
 *
 * <p>A caller rooting a walk at the map has to be rooted at the surface the player is actually
 * looking at. Which surfaces exist is this mod's knowledge rather than the library's - the library
 * states each question and can be driven without a display - so the two readings are composed here,
 * where both are already in hand.
 *
 * <p>One reading rather than a second composition of the same pair, for the reason the hover
 * permission is one class. The surface a hover was resolved against and the surface whose tooltip
 * something stands aside for have to be the same surface, or a box hides for a tooltip on a map the
 * pointer is not over, or draws over one on the map it is. Two compositions would be free to
 * disagree and nothing would say so.
 *
 * <p>The tab is asked first and answers alone. A vanilla host owning the frame settles which surface
 * is meant before an embedded one is looked for, so a docked panel travelling across the screen
 * cannot come to stand for the {@code M} map or the intel visor.
 *
 * <p>Nothing here reads any compatibility mode. That mode settles where the cursor means anything;
 * this settles which surface a frame belongs to, and a reading named for one mod would leave every
 * other mod that docks a map with no surface at all.
 */
public final class ShownMapSurface {

    private ShownMapSurface() {
    }

    /**
     * The surface the player is looking at, over the readings a running game has.
     *
     * @return the widget to reason about the shown map through, or null when no surface can be named
     *         - no map is up, or more than one embedded map leaves no way to say which was meant
     * @throws RuntimeException when the reach to the current tab is absent or fails outright, so a
     *                          caller applies its own policy to a genuinely broken reach
     */
    public static Object resolveShownMapSurface() {
        return resolveShownMapSurface(
            ShownMapTab.resolveShownMapTab(),
            SingleEmbeddedMapReader.INSTANCE.resolveSingleEmbeddedMap());
    }

    /**
     * The rule the live read applies, over readings taken elsewhere.
     *
     * @param shownMapTab      the game's own map widget, or null when no screen is showing one
     * @param singleEmbeddedMap the one map surface another mod has on screen, or null when there is
     *                          not exactly one of them
     * @return the widget the shown surface hangs on, or null when neither reading names one
     */
    static Object resolveShownMapSurface(Object shownMapTab, EmbeddedMap singleEmbeddedMap) {

        if (shownMapTab != null) {
            return shownMapTab;
        }
        // What the mod docked rather than the map widget inside it: which widget of a mod's assembly
        // hosts a given thing is a fact about how that mod built it, and the outermost covers every
        // assembly.
        return singleEmbeddedMap == null
            ? null
            : singleEmbeddedMap.resolveDockedWidget();
    }
}
