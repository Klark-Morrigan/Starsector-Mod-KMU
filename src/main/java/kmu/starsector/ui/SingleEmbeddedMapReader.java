package kmu.starsector.ui;

import kmlib.starsector.ui.map.probes.EmbeddedMap;
import kmlib.starsector.ui.map.probes.EmbeddedMapFinder;

import java.util.List;
import java.util.function.Supplier;

/**
 * The one map surface another mod has put on screen, or nothing when there is not exactly one of
 * them.
 *
 * <p>Two rules in this mod act on such a surface - the cursor is confined to it, and it is switched
 * off while its owner has it parked - and both can only act while there is no question which surface
 * is meant. Their reasons differ and are stated where each acts; what they share is this reading,
 * and sharing it is what stops the two acting on different answers. A count rule that lived in both
 * could be changed in one, leaving the cursor confined to a widget the other had already switched
 * off.
 *
 * <p>One walk behind both, which is the other half of why this is shared. The finder remembers what
 * it found against the widget tree it walked, but walks afresh at every ask while it has found
 * nothing - so two holders asking per frame walk the whole core UI twice per frame on exactly the
 * installs these rules exist for, where the panel has yet to be built.
 *
 * <p>Nothing here decides what "exactly one" is worth: it is the precondition both rules happen to
 * share, not a policy of its own. Neither does it read the compatibility mode - a caller asks that
 * first, so a walk is not paid for on an install that switched the mode off.
 *
 * <p>Session-scoped, and needs no clearing per load: the finder keys what it remembers on the widget
 * tree it walked, and a load stands up a new one.
 */
public final class SingleEmbeddedMapReader {

    // The one surface either rule can be stated over. None leaves nothing to act on, and more than
    // one leaves no way to say which was meant - the frame carries a single transform, and the mode
    // that permits acting names a mod the widgets themselves cannot be matched against.
    private static final int SINGLE_MAP_COUNT = 1;

    /** The reading over the widget walk a running game has, shared by everything that asks. */
    public static final SingleEmbeddedMapReader INSTANCE =
        new SingleEmbeddedMapReader(new EmbeddedMapFinder()::findEmbeddedMaps);

    // Every embedded map on screen, asked afresh each frame. A supplier rather than the finder
    // itself so this class states its question and not where the answer is walked out of.
    private final Supplier<List<EmbeddedMap>> findEmbeddedMaps;

    /**
     * @param findEmbeddedMaps the map surfaces on screen that are not the one the player opened
     */
    public SingleEmbeddedMapReader(Supplier<List<EmbeddedMap>> findEmbeddedMaps) {
        this.findEmbeddedMaps = findEmbeddedMaps;
    }

    /**
     * @return the single map surface on screen that is not the one the player opened, or null when
     *         none was found, more than one was, or the walk could not be made - none of which a
     *         caller acting on one surface can tell apart, or act on differently
     */
    public EmbeddedMap resolveSingleEmbeddedMap() {

        var embeddedMaps = findEmbeddedMaps.get();

        return embeddedMaps.size() == SINGLE_MAP_COUNT
            ? embeddedMaps.get(0)
            : null;
    }
}
