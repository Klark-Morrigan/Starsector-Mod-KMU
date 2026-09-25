package kmu.maplayers.ownermap.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.MapStyleCategory;

/**
 * The four categories an owner map divides its cells into, and the keys its
 * per-category {@link CategoryStyle} bundles are held under. Two are owned (a coloured fill,
 * cluster border, and interior seams) and two are factionless (a neutral-coloured outline, and
 * a fill where a cell holds something): a bloc paints in {@link #FACTION} or, where it
 * recedes to independent-held space, {@link #INDEPENDENT}; a system with no holder draws in
 * {@link #DECIVILISED} when something stands there the layer's holding does not account for -
 * a revealed decivilised world, or a colony held by nobody this layer admits - and
 * {@link #UNINHABITED} when nothing stands there at all. Making the category a type (rather
 * than four hardcoded reader methods and four fields) lets the theme carry the four styles as
 * one keyed map the builders index.
 *
 * <p>{@link #DECIVILISED} is named for one case it covers rather than for the whole of what it
 * covers, its bundle being the one the player configures under that name.
 *
 * <p>Which cells that bundle covers is not fixed either. A player who has said decivilised worlds
 * draw no fill takes them out of the habitation projection the classification reads, so a
 * system holding nothing else falls to {@link #UNINHABITED} - one knob moving the membership of
 * two categories rather than the style of either. Everything else the bundle covers stays, which
 * is why the knob is not the same thing as zeroing this bundle's opacities: those reach all three
 * views at once, and this reaches one shape of colony.
 *
 * <p>Declared beside the layer that paints them rather than in the framework's theme: how the
 * a map divides into is the vocabulary of whoever is painting it, and the theme keys on the open
 * {@link MapStyleCategory} so a layer dividing the sector some other way brings its own set
 * instead of inheriting these four. An enum, so the layer side's own lookups over the
 * category stay a closed set the compiler checks.
 */
public enum OwnerMapCategory implements MapStyleCategory {
    FACTION,
    INDEPENDENT,
    DECIVILISED,
    UNINHABITED
}
