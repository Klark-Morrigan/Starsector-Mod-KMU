package kmu.maplayers.base.theme;

/**
 * One painted element's colour selection, held opaquely.
 *
 * <p>Which shades a map layer offers, and how many, is the layer's own question: one layer may
 * let the player pick between two authored shades, another may offer a single fixed colour or a
 * whole ramp. The theme records carry the player's pick without ever asking what it names, so
 * adding or removing an option costs nothing here.
 *
 * <p>Deliberately empty. The only thing the theme tier decides for itself is whether an element
 * paints at all, and that is answered by the selection being absent (null) rather than by
 * interrogating it - so there is no method a selection has to supply, and no way for the tier to
 * start depending on what the choices mean. Turning a selection into an actual shade happens
 * where the palette is known, against the concrete type the layer put in.
 */
public interface ElementPaint {
}
