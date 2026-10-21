package kmu.maplayers.base.hover.cover;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether anything at all stands between the cursor and the cells beneath it - the one question a
 * layer asks before resolving a hover, in place of remembering each thing that can be drawn over
 * the map.
 *
 * <p>One answer for every layer rather than one per layer, because nothing about a cover is a
 * layer's own: a console, a panel and the map's chrome sit over whatever is painted underneath
 * them. A layer holding its own set could be given a cover its neighbour was not - a console hiding
 * the sidebar while the map went on lighting cells behind it.
 *
 * <p>The covers are held as a list in ascending cost and asked in that order, stopping at the first
 * that answers: the composition is what states the order, listed once on the field holding it, so a
 * cover states only its own reading and a further one is a class plus a line in
 * {@link #createForLiveScreen()}.
 *
 * <p>The optional mods' covers are registered by the composition root, which is the one place that
 * names what is installed, so this framework never names an adapter. The root registers a mod's
 * cover only where the mod is installed at all, the same gate its installer stands behind. Presence
 * is the one condition that cannot move within a run, so it is answered once; everything that can
 * move - the compatibility mode, the minimap's own switch - stays each cover's own live read.
 */
public final class MapCoverReader {

    // The optional mods' covers, registered once at application load and asked after every cover
    // of the framework's own. Empty until then, which is what an install with no such mod reads.
    private static List<MapCover> optionalModCovers = List.of();

    // The order the covers are asked in, stated here and nowhere else. Ascending cost, since the
    // first cover to answer ends the read:
    //
    // 1. HeldPointerMapCover - a polled mouse flag;
    // 2. PauseMenuMapCover - a published one-call read;
    // 3. ScreenClaimMapCover - a console's settled flag, one hop off the app state for the codex,
    //    this mod's own dialog state, then a walk of the core UI's own children for a modal;
    // 4. SidebarMapCover - arithmetic over a box this mod laid out;
    // 5. VanillaChromeMapCover - a walk into the map tab's own layout;
    // 6. the optional mods' covers, in the order they were registered - a walk of the surfaces
    //    another mod put on screen being the dearest, because it is rooted above every tab.
    //
    // The claim sits ahead of the sidebar for a reason beyond its cost, stated where it is composed.
    //
    // How many there are is the composition's, not this field's: the optional mods' covers are
    // absent on an install without them, and the order of whatever remains is unchanged.
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
        return new MapCoverReader(composeLiveCovers());
    }

    /**
     * Records the covers the installed optional mods lay over the map. Called once by the
     * composition root at application load, before any sector map can open, and so before any
     * reader is composed.
     *
     * @param covers the covers of the mods installed, cheapest first; an empty list where none is
     */
    public static void registerOptionalModCovers(List<MapCover> covers) {
        optionalModCovers = List.copyOf(covers);
    }

    /**
     * @return whether the cell where the cursor rests is not what the player is pointing at -
     *         because something is drawn over the map there, or because the cursor is outside the
     *         only surface on the frame that could be pointed at
     */
    public boolean isMapCoveredAtCursor() {
        return covers.stream().anyMatch(MapCover::isCoveringCursor);
    }

    // The composition itself, named apart from the reader it is handed to so which covers a given
    // install actually has is a question with its own answer, rather than one only a reader can be
    // asked.
    static List<MapCover> composeLiveCovers() {

        var covers = new ArrayList<MapCover>();

        covers.add(new HeldPointerMapCover());
        covers.add(new PauseMenuMapCover());

        // Everything here stands the sidebar down, so it sits ahead of the sidebar's cover for a
        // reason beyond its own cost: on every frame it answers, the cover below it is resolving
        // placements for a panel that is not drawn - work spent to reach a false it can no longer
        // reach anything but.
        covers.add(new ScreenClaimMapCover());
        covers.add(new SidebarMapCover());
        covers.add(new VanillaChromeMapCover());

        covers.addAll(optionalModCovers);
        return covers;
    }
}
