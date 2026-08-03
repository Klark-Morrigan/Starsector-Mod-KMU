package kmu.maplayers.base.sidebar;

/**
 * One row of a sidebar picker list - the seam between the framework's picker and whatever a layer's
 * list actually holds. The picker draws and stores through this and nothing else: an id it hands to
 * {@link FilterSelection}, a label it writes on the row, and a crest it draws beside the label. What
 * else an item carries - the numbers it is ranked by, the thing on the map the id resolves to - the
 * picker never opens, since ranking runs through the layer's own {@link ListSortMode} comparators
 * and resolving is the layer's business.
 *
 * <p>A layer declares its own item type (typically a record) and implements this on it, so the
 * comparators keep ranking the type the layer declared and nothing is copied into a framework value
 * on the way into the picker.
 */
public interface SelectableListItem {

    /**
     * @return the item's save-stable id - the value {@link FilterSelection} stores and the picker
     *         resolves its lit row by; must be unique within one list, since a pick is remembered
     *         as an id rather than a position
     */
    String itemId();

    /**
     * @return the item's label for its picker row; null when no name resolves, which the row draws
     *         as unlabelled
     */
    String displayName();

    /**
     * @return the crest sprite path drawn beside the label, or null when the item has no crest,
     *         which the row draws as the label alone
     */
    String crestSpritePath();
}
