package kmu.maplayers.base.sidebar;

import kmlib.starsector.ui.widgets.lists.SelectableListItem;

/**
 * Test fixture: one row of a picker list no part of the political map declares - the stand-in item
 * a second layer's sidebar list would rank and spotlight. Implements the {@link SelectableListItem}
 * seam through its own components, so the binder reads and writes a foreign type's ID without
 * anything being mapped into a framework value. Carries two numerics beside them so the foreign
 * sort modes ({@link HazardSortMode}) have distinct keys to rank and flip on.
 *
 * @param itemId          the ID a pick stores, kept apart from the label so a suite can tell an
 *                        id-resolved lit row from a label-matched one
 * @param displayName     the label the row draws, null standing in for a name that did not resolve
 * @param crestSpritePath the crest path the row draws beside the label, null standing in for none
 * @param severity        one numeric a foreign mode ranks on
 * @param radius          the other, so a mode switch visibly reorders rather than relabels
 */
record Hazard(
    String itemId,
    String displayName,
    String crestSpritePath,
    int severity,
    int radius) implements SelectableListItem {

    /**
     * A hazard listed for the sort suites, which rank on the name and the numerics and never read an
     * ID or a crest: the name doubles as the ID and no crest is carried, so those suites name only
     * what they assert on.
     *
     * @param displayName the label, also standing in as the ID
     * @param severity    one numeric a foreign mode ranks on
     * @param radius      the other
     */
    Hazard(String displayName, int severity, int radius) {
        this(displayName, displayName, null, severity, radius);
    }
}
