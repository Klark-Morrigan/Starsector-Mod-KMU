package kmu.maplayers.politicalmap.base.render.territories;

import kmlib.starsector.factions.FactionPalette;

import kmu.maplayers.base.style.RenderStyle;

import java.awt.Color;

/**
 * The resolved paint scheme one build styles every cell and territory from: the theme (its
 * global tier plus one style per category), the shared neutral color an unfilled palette slot
 * or a factionless cell falls back to, and the palette a desaturated bloc recolours to.
 *
 * <p>Held on the built territories so an incremental re-shape bakes a rebuilt cell against the
 * same scheme the full build used, and every global knob resolves once off the one theme.
 */
public record MapStyling(
        RenderStyle renderStyle,
        Color neutralColor,
        FactionPalette desaturationPalette) {
}
