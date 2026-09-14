package kmu.maplayers.base.theme;

/**
 * One named division of a map's cells, and the key a {@link RenderStyle} looks its
 * per-category {@link CategoryStyle} bundle up by. The theme declares only that the cells
 * divide into <em>some</em> named set: what the divisions actually are - who holds a cell,
 * how hazardous it is, what it trades - is the vocabulary of the layer painting it,
 * so the constants are declared alongside that layer rather than here. An enum may implement
 * this, which is what lets a layer keep a closed set the compiler checks its switches against
 * while the theme stays open to any set.
 *
 * <p>No members: a category is looked up rather than asked anything, so being a distinct,
 * stable key is the whole of what an implementation owes. An enum constant is that for free;
 * anything else has to carry {@code equals} and {@code hashCode} that agree across the
 * rebuild that populates a theme and the draws that read it, or its bundle goes missing.
 */
public interface MapStyleCategory {
}
