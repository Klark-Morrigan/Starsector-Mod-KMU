package kmu.politicalmap.ui;

import kmlib.math.geometry.Rectangle;

import kmu.politicalmap.layer.PoliticalMapLayer;

/**
 * One laid-out tab in the layer bar: the {@link PoliticalMapLayer} it selects and its
 * {@code bounds} in UI coordinates. The renderer draws each tab from its bounds and the
 * input listener hit-tests the same rectangle, so the tab the player sees is the tab the
 * click reads.
 */
public record LayerTab(PoliticalMapLayer layer, Rectangle bounds) {
}
