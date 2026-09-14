package kmu.maplayers.base.theme;

/**
 * The sector-wide smoothing applied to every cluster border (and to an unowned cell's lone
 * outline, so it reads consistently), as one profile per pass: {@link SpikeSandingStyle} for the
 * thin spikes rounding cannot fix, {@link CornerRoundingStyle} for the corners themselves. One
 * profile for the whole map, so this is a global-tier value (part of {@link GlobalStyle}); with
 * both passes' gates off what is left is the raw Voronoi outline.
 *
 * <p>Both passes' shape lives here rather than being fetched where the pass runs, so one border
 * and the cell outline beside it can never smooth to different numbers. The pair travels as one
 * value for that reason.
 *
 * <p>It is a pair rather than one flat field list because the two passes share no value: each is
 * handed only its own half, so a spike angle is not in scope where corners are rounded and a
 * corner radius is not in scope where spikes are sanded. A knob wired to the wrong pass stops
 * compiling instead of quietly smoothing to a shape nobody authored.
 */
public record BorderSmoothingStyle(
    SpikeSandingStyle spikeSanding,
    CornerRoundingStyle cornerRounding) {
}
