package kmu.maplayers.politicalmap.base.geometry;

/**
 * How a cell edge sits relative to faction ownership.
 *
 * <p>An interior seam runs between two systems the same faction holds; a
 * boundary runs against a different owner, unowned space, or the map frontier.
 * The distinction drives whether the political map draws the edge as a faint
 * interior province line or a bold national border.
 */
public enum EdgeClass {
    INTERIOR_SEAM,
    BOUNDARY,
}
