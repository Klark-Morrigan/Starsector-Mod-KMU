package kmu.maplayers.politicalmap.factions.render.model;

import kmu.settings.FactionPaletteChoice;

/**
 * One category's full political-map style, read once per rebuild and applied to
 * every cluster of that category: the fill, outer-border, and inner-seam color
 * choices with their opacities, plus the two border widths. The colors are choices
 * (resolved against each cluster's two palette shades), not concrete colors, since
 * one bundle serves many clusters. A factionless category sets fill and inner to
 * NONE so only its outline draws.
 */
public record MapStyle(
        FactionPaletteChoice fillColor, double fillOpacity,
        FactionPaletteChoice outerColor, double outerOpacity, double outerWidth,
        FactionPaletteChoice innerColor, double innerOpacity, double innerWidth) {
}
