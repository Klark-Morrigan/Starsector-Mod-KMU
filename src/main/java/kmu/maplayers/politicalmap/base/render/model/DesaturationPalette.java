package kmu.maplayers.politicalmap.base.render.model;

import java.awt.Color;

/**
 * The two-shade palette a desaturated bloc recolours to, resolved once per pass from the
 * "Desaturation profile" setting and held on {@link PoliticalMapDrawables} beside the
 * neutral colour, so an incremental re-shape recolours against the same palette the full
 * pass used. A view's {@link kmu.maplayers.politicalmap.base.BlocStyleAdjustment} flags
 * only <em>whether</em> a bloc desaturates; this names <em>what</em> it desaturates to, so
 * the view stays out of the profile decision.
 *
 * @param primaryColor   the shade a PRIMARY palette choice resolves to under desaturation
 * @param secondaryColor the shade a SECONDARY palette choice resolves to under desaturation
 */
public record DesaturationPalette(Color primaryColor, Color secondaryColor) {
}
