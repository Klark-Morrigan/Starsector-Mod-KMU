package kmu.politicalmap.domain;

import java.awt.Color;

/**
 * The faction holding a star system on the political map, paired with the two
 * palette colors its cell draws in.
 *
 * <p>The render-ready output of the ownership pipeline: {@link SectorPolitics}
 * resolves the dominant faction and its palette into this value, so the render
 * layer consumes a plain owner and never reaches into the economy or touches
 * {@code FactionAPI}. The renderer fills a cell and strokes its national border
 * from {@link #color()}, strokes its interior province seams from
 * {@link #seamColor()}, and styles it from {@link #factionId()} - dimming
 * independent-held space, for one. Both colors are the faction's own authored
 * shades (its bright and dark UI colors), so a seam stays true to that
 * faction's palette rather than a mechanical darkening of the bright color.
 * Retaining the id beside the colors keeps the owner available for later
 * per-owner behaviour, decided off the same dominance the fill was.
 */
public record DominantOwner(String factionId, Color color, Color seamColor) {
}
