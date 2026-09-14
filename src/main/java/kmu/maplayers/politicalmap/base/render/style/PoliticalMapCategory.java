package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.MapStyleCategory;

/**
 * The four territory categories the political map divides its cells into, and the keys its
 * per-category {@link CategoryStyle} bundles are held under. Two are owned (a coloured fill,
 * national border, and interior seams) and two are factionless (a neutral-coloured outline, and
 * a fill where a cell holds something): a bloc paints in {@link #FACTION} or, where it
 * recedes to independent-held space, {@link #INDEPENDENT}; a system with no holder draws in
 * {@link #DECIVILISED} when something stands there the layer's holding does not account for -
 * a revealed dead world, or a colony held by nobody this layer admits - and
 * {@link #UNINHABITED} when nothing stands there at all. Making the category a type (rather
 * than four hardcoded reader methods and four fields) lets the theme carry the four styles as
 * one keyed map the builders index.
 *
 * <p>{@link #DECIVILISED} is named for the case that first needed it rather than for the whole
 * of what it now covers, its bundle being the one the player configures under that name.
 *
 * <p>Declared beside the layer that paints them rather than in the framework's theme: how the
 * a map divides into is the vocabulary of whoever is painting it, and the theme keys on the open
 * {@link MapStyleCategory} so a layer dividing the sector some other way brings its own set
 * instead of inheriting these four. An enum, so the political side's own lookups over the
 * category stay a closed set the compiler checks.
 */
public enum PoliticalMapCategory implements MapStyleCategory {
    FACTION,
    INDEPENDENT,
    DECIVILISED,
    UNINHABITED
}
