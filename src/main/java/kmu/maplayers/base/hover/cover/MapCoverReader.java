package kmu.maplayers.base.hover.cover;

import kmlib.mods.consolecommands.ConsoleCommandsPresence;
import kmlib.mods.rat.RandomAssortmentOfThingsPresence;

import kmu.starsector.consolecommands.ConsoleMapCover;
import kmu.starsector.rat.RandomAssortmentOfThingsMinimapCover;

import java.util.ArrayList;
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
 *
 * <p>That factory is also where the optional mods' covers join or do not: a mod's cover is
 * composed only where the mod is installed at all, the same gate its installer stands behind.
 * Presence is the one condition that cannot move within a run, so it is answered once at
 * composition; everything that can move - a console opening, the compatibility mode, the minimap's
 * own switch - stays each cover's own live read.
 */
public final class MapCoverReader {

    // In ascending cost, since the first cover to answer ends the read: a polled mouse flag, then a
    // published one-call read, then this mod's own dialog state, then the console's settled flag, then
    // one hop off the app state,
    // then a walk of the core UI's own children, then arithmetic over a box this mod laid out, then
    // the two that walk further into the live widget tree - the map tab's own layout, and finally
    // the surfaces another mod put on screen, which is the dearest because its walk is rooted above
    // every tab.
    //
    // How many there are is the composition's, not this field's: the two that belong to optional
    // mods are absent on an install without them, and the order of whatever remains is unchanged.
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
     * @return whether the cell where the cursor rests is not what the player is pointing at -
     *         because something is drawn over the map there, or because the cursor is outside the
     *         only surface on the frame that could be pointed at
     */
    public boolean isMapCoveredAtCursor() {
        for (var cover : covers) {
            if (cover.isCoveringCursor()) {
                return true;
            }
        }
        return false;
    }

    // The composition itself, named apart from the reader it is handed to so which covers a given
    // install actually has is a question with its own answer, rather than one only a reader can be
    // asked.
    static List<MapCover> composeLiveCovers() {

        var covers = new ArrayList<MapCover>();

        covers.add(new HeldPointerMapCover());
        covers.add(new PauseMenuMapCover());

        // Beside those two rather than beside the modal walk it behaves like, because it is read like
        // these: a field on a panel this mod stood up itself, with nothing to search.
        covers.add(new ArrangementDialogMapCover());

        // Composed only where the mod is present, which is safe to settle here: the mod set is
        // fixed for the launch, and this factory runs no earlier than the renderer's first ask,
        // long after the game has stood that set up. An install without the mod then never holds
        // the cover, rather than asking one every frame that could only ever answer no.
        if (ConsoleCommandsPresence.isModEnabled()) {
            covers.add(ConsoleMapCover.createForLiveScreen());
        }

        // Both of these stand the sidebar down, so they sit ahead of its cover for a reason beyond
        // their own cost: on every frame either answers, the cover below them is resolving
        // placements for a panel that is not drawn - work spent to reach a false it can no longer
        // reach anything but. The codex goes first of the two, being one hop where the modal is a
        // walk; that they are separate covers at all is CodexMapCover's own to explain.
        covers.add(new CodexMapCover());
        covers.add(new ModalDialogMapCover());
        covers.add(new SidebarMapCover());
        covers.add(new VanillaChromeMapCover());

        if (RandomAssortmentOfThingsPresence.isModEnabled()) {
            covers.add(RandomAssortmentOfThingsMinimapCover.createForLiveScreen());
        }
        return covers;
    }
}
