package kmu.politicalmap.domain;

import java.awt.Color;

/**
 * The faction holding a star system on the political map, paired with the two
 * palette colors its cell can draw in.
 *
 * <p>The render-ready output of the ownership pipeline: {@link SectorPolitics}
 * resolves the dominant faction and its palette into this value, so the render
 * layer consumes a plain owner and never reaches into the economy or touches
 * {@code FactionAPI}. The two colors are the faction's own authored UI shades -
 * {@link #primaryColor()} its bright color, {@link #secondaryColor()} its dark
 * color - and the player points each map element (fill, outer border, inner seam)
 * at one of them through the "Faction ... color" settings. Naming them by palette
 * slot rather than by element keeps the record neutral about which element uses
 * which, since that pairing is the player's choice. Retaining the id beside the
 * colors keeps the owner available for per-owner styling (dimming independent-held
 * space, for one) and later per-owner behaviour, decided off the same dominance the
 * fill was.
 */
public record DominantOwner(String factionId, Color primaryColor, Color secondaryColor) {
}
