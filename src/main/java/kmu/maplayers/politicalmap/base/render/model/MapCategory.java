package kmu.maplayers.politicalmap.base.render.model;

/**
 * The four political-map territory categories a per-category {@link CategoryStyle} is
 * keyed by. Two are owned (a coloured fill, national border, and interior seams) and two
 * are factionless (an outline only): a bloc paints in {@link #FACTION} or, where it
 * recedes to independent-held space, {@link #INDEPENDENT}; a system with no owner draws in
 * {@link #DECIVILISED} when a revealed dead world sits there and {@link #UNINHABITED}
 * otherwise. Making the category a type (rather than four hardcoded reader methods and
 * four fields) lets the theme carry the four styles as one keyed map the builders index.
 */
public enum MapCategory {
    FACTION,
    INDEPENDENT,
    DECIVILISED,
    UNINHABITED
}
