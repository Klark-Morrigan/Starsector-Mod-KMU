package kmu.maplayers.base.render;

/**
 * Where one part of a layer's overlay is painted in the map's own draw order, relative to the
 * per-system nebulae the map holds icons for. The map surface paints one band per pass and tells the
 * renderer which, so a sub-layer moves between them by being emitted for a different band rather
 * than by anything about it changing.
 *
 * <p>Those nebula icons are drawn in <em>both</em> map looks, not only in Starscape - what the
 * Starscape filter changes is how: a large blended sprite over the sector rather than the ordinary
 * nebula terrain render. The names carry the mode because that sprite is what the split is for, and
 * because Starscape is the only look with a surface above the nebulae at all - the schematic map
 * owns one icon and so paints both bands in one pass, below them, exactly as it always has.
 *
 * <p>The bands are named for their positions, not their contents. What rides above is a question
 * about how the picture reads, and the answer is expected to move: naming them for their contents
 * would make every such move a rename of the seam that carries them.
 */
public enum MapOverlayBand {

    /**
     * Under the nebulae: everything that reads as an area - the fills, the contested hatch, the
     * cluster borders, and the hover feedback that traces them. Fog over a fill dims it without
     * costing it its meaning, which is what makes this the band with room for the geometry.
     */
    BENEATH_STARSCAPE_NEBULAE,

    /**
     * Over the nebulae, and still under the vanilla star and constellation names: the text. A name
     * stops being readable well before a fill stops reading as an area, which is what earns the
     * labels the clearer band.
     */
    ABOVE_STARSCAPE_NEBULAE
}
