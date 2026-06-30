package kmu.politicalmap.domain;

import java.awt.Color;

/**
 * The faction holding a star system on the political map, paired with the color
 * its cell draws in.
 *
 * <p>The render-ready output of the ownership pipeline: {@link SectorPolitics}
 * resolves both the dominant faction and its palette into this value, so the
 * render layer consumes a plain owner and never reaches into the economy or
 * touches {@code FactionAPI}. The renderer fills a cell from {@link #color()}
 * and styles it from {@link #factionId()} - dimming independent-held space, for
 * one. Retaining the id beside the color (rather than collapsing to color alone)
 * keeps the owner available for later per-owner behaviour, decided off the same
 * dominance the fill was.
 */
public record DominantOwner(String factionId, Color color) {
}
