package kmu.politicalmap.render.model;

import java.awt.Color;

/**
 * The resolved paint of one drawable map element: its color and opacity, baked from the
 * settings once at build time. Groups the two values that always travel together for a
 * fill or a stroke, so the draw pass can ask a single element whether it is worth
 * emitting rather than juggling a loose color/alpha pair per element.
 *
 * <p>A null color is a "No color" choice and a zero (or negative) opacity a fully
 * transparent element; either way {@link #isHidden} reports it shows nothing, so the
 * draw pass skips it. The element's geometry still lives on its record - a hidden cell
 * keeps shaping its neighbours' borders - but emitting its run would only rasterise
 * pixels the blend discards.
 */
public record ElementPaint(Color color, float alpha) {
    public boolean isHidden() {
        return color == null || alpha <= 0f;
    }
}
