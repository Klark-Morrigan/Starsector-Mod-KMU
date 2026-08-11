package kmu.maplayers.base.hover.cover;

import kmu.starsector.consolecommands.ConsoleCommandsOverlay;

import java.util.List;

/**
 * Whether anything at all stands between the cursor and the cells beneath it - the one question a
 * layer asks before resolving a hover, in place of remembering each thing that can be drawn over
 * the map.
 *
 * <p>One answer for every layer rather than one per layer, because nothing about a cover is a
 * layer's own: a console, a panel and the map's chrome sit over whatever is painted underneath
 * them. A layer holding its own set would be a layer that could be given a cover its neighbour was
 * not, which is how the console came to hide the sidebar while the map went on lighting cells
 * behind it.
 *
 * <p>The covers are held as a list in ascending cost and asked in that order, stopping at the first
 * that answers: the composition is what states the order, so a cover states only its own reading
 * and a further one is a class plus a line in {@link #createForLiveScreen()}.
 */
public final class MapCoverReader {

    // In ascending cost, since the first cover to answer ends the read: the console's settled flag,
    // then arithmetic over a box this mod laid out, then a walk of the live widget tree.
    private final List<MapCover> covers;

    /**
     * @param covers the covers to ask, in the order they are worth asking in - cheapest first,
     *               since the first to answer ends the read
     */
    public MapCoverReader(List<MapCover> covers) {
        this.covers = List.copyOf(covers);
    }

    /**
     * @return the reader over the covers a running game actually has, composed in the order they
     *         are worth asking in
     */
    public static MapCoverReader createForLiveScreen() {
        return new MapCoverReader(List.of(
            new ConsoleMapCover(ConsoleCommandsOverlay.INSTANCE),
            new SidebarMapCover(),
            new VanillaChromeMapCover()));
    }

    /**
     * @return whether something is drawn over the map where the cursor rests, so the cell beneath it
     *         is not what the player is pointing at
     */
    public boolean isMapCoveredAtCursor() {
        for (var cover : covers) {
            if (cover.isCoveringCursor()) {
                return true;
            }
        }
        return false;
    }
}
