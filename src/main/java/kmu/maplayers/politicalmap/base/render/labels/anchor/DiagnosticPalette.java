package kmu.maplayers.politicalmap.base.render.labels.anchor;

import java.awt.Color;

/**
 * The fixed color ramp every political-map diagnostic overlay layers its stages with,
 * bottom to top: red for geometry that did not make it into the final result (a raw
 * pre-pass border stage, a rejected anchor candidate), yellow for an intermediate view
 * (a mid-pipeline stage, an alternative the result is compared against), and green for
 * the final result that ships to the player.
 *
 * <p>It lives with the label-anchor overlay because the ramp's discarded/intermediate/accepted
 * vocabulary is the anchor search's own outcome vocabulary (rejected, unbiased, accepted axis);
 * the debug border-tracing overlay reuses the same ramp for its base/despiked/rounded stages. One
 * source for it so both read identically - red always means discarded and green always means
 * accepted, whichever diagnostic is on - and the settings descriptions can promise "from bottom to
 * top: red, yellow, green" as one contract rather than two that happen to agree.
 */
public final class DiagnosticPalette {
    public static final Color DISCARDED_COLOR = Color.RED;
    public static final Color INTERMEDIATE_COLOR = Color.YELLOW;
    public static final Color ACCEPTED_COLOR = Color.GREEN;

    // Colors only; never instantiated.
    private DiagnosticPalette() {
    }
}
