package kmu.maplayers.politicalmap.base.render.territories;

/**
 * One system's inputs to the star-icon hit test: where its icon sits and how big the star sizing it
 * is. Captured once per full build so the cursor read can gate its tooltip on the icon by id, with
 * no per-frame system lookup.
 *
 * <p>Held apart from the fill polygon because it does not share the fill's lifecycle: an anchor does
 * not move and a star does not resize, so unlike a re-shaped cell's extent this is written once at
 * build and only read after, never rewritten as ownership flips.
 *
 * @param anchorX    the system's hyperspace anchor x in world coordinates, the icon's centre
 * @param anchorY    the system's hyperspace anchor y in world coordinates
 * @param starRadius the star's radius, the input the icon is sized by; zero when the system has no
 *                   star, which reads as the smallest icon
 */
public record StarIconGeometry(double anchorX, double anchorY, float starRadius) {
}
