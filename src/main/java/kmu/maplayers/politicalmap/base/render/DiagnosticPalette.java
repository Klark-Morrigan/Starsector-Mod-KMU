package kmu.maplayers.politicalmap.base.render;

import java.awt.Color;

/**
 * The fixed color ramp every political-map diagnostic overlay layers its stages with,
 * bottom to top: red for geometry that did not make it into the final result (a raw
 * pre-pass border stage, a rejected anchor candidate), yellow for an intermediate view
 * (a mid-pipeline stage, an alternative the result is compared against), and green for
 * the final result that ships to the player.
 *
 * <p>One source for the ramp so the border-tracing overlay and the label-anchor
 * overlay read identically - red always means discarded and green always means
 * accepted, whichever diagnostic is on - and the settings descriptions can promise
 * "from bottom to top: red, yellow, green" as one contract rather than two that
 * happen to agree.
 */
public final class DiagnosticPalette {
    public static final Color DISCARDED_COLOR = Color.RED;
    public static final Color INTERMEDIATE_COLOR = Color.YELLOW;
    public static final Color ACCEPTED_COLOR = Color.GREEN;

    // Colors only; never instantiated.
    private DiagnosticPalette() {
    }
}
