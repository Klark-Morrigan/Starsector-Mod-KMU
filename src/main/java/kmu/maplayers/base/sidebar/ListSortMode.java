package kmu.maplayers.base.sidebar;

import java.util.Comparator;

/**
 * One named mode a sidebar picker list can be ranked by - the seam between the framework's sort
 * mechanism and whatever a layer's list actually holds. The framework resolves, previews, and
 * persists a sort without learning what is being sorted; a layer declares its own modes (typically
 * an enum, though anything whose equality is stable will do) and each carries the whole of what
 * the mechanism needs: the save-stable key its choice persists under, the label its selector row
 * draws, the direction it naturally runs in, and the comparator that lays the list out under it.
 *
 * @param <T> the list item type this mode's comparator ranks
 */
public interface ListSortMode<T> {

    /**
     * @return the save-stable key this mode persists under; frozen once shipped, since renaming it
     *         silently resets every save that stored this mode back to the declaring layer's
     *         default
     */
    String persistenceKey();

    /** @return the string key of this mode's selector-row label */
    String labelKey();

    /**
     * The direction this mode ranks in until the player flips it. A fresh save and a mode the
     * player has just switched to both start here.
     *
     * @return this mode's natural sort direction
     */
    SortDirection defaultDirection();

    /**
     * The comparator that orders the picker's list under this mode in {@code direction}.
     *
     * @param direction the way the ranking runs - this mode's default, or the flipped opposite
     * @return the item comparator for this mode in the requested direction
     */
    Comparator<T> comparator(SortDirection direction);

    /**
     * The trailing value the picker draws on an item's row under this mode - the number the list
     * is visibly ranked by, so the rows read as a sorted table. Defaults to blank for a mode with
     * no number to show (a by-name mode), under which the rows read as a plain list.
     *
     * @param item the listed item
     * @return the mode's value for the item as text, or "" when this mode shows none
     */
    default String resolveTrailingValue(T item) {
        return "";
    }
}
