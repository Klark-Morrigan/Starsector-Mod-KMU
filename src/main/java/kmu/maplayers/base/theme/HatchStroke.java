package kmu.maplayers.base.theme;

/**
 * How the hatch's clipped line geometry is put on screen - the half of {@link HatchStyle} the draw
 * pass reads, as against the half that was baked into the geometry long before the frame.
 *
 * <p>Sealed rather than one record carrying every field, because what each way of stroking needs
 * does not overlap: a line quality means nothing to a stroke that is not rasterised as a line, and
 * a width means different units to different strokes. One flat record would leave combinations
 * that cannot be drawn representable, and every reader guarding against them; the sealed form
 * leaves the renderer dispatching on which arrived and reading only what that one carries.
 */
public sealed interface HatchStroke permits GlLineHatchStroke {
}
