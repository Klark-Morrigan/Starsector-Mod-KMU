package kmu.maplayers.politicalmap.base.render.territories;

/**
 * One system's inputs to the star-icon hit test: where its icon sits and how big it is in world
 * units. Captured once per full build so the cursor read can gate its tooltip on the icon by id,
 * with no per-frame system lookup.
 *
 * <p>The radius is baked in world units rather than as the raw star radius because the icon size is
 * a fixed derivation of the star (its radius, spec scale, and whether it is a star or nebula) that
 * never changes as ownership flips - computing it once at build keeps the derivation off the
 * per-frame path. Held apart from the fill polygon for the same reason: an anchor does not move and
 * a star does not resize, so unlike a re-shaped cell's extent this is written once and only read.
 *
 * @param anchorX         the system's hyperspace anchor x in world coordinates, the icon's centre
 * @param anchorY         the system's hyperspace anchor y in world coordinates
 * @param iconWorldRadius the icon's radius in world units, the zoom-independent size the hover gate
 *                        tests the cursor against
 */
public record StarIconGeometry(double anchorX, double anchorY, float iconWorldRadius) {
}
