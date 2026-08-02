package kmu.maplayers.base.render.clusters.debug;

import java.util.List;

/**
 * The cluster-border smoothing pipeline captured one stage at a time: each pass's border loops
 * as flattened GL_LINE_LOOP runs, kept apart so a renderer can colour and layer them and what
 * each pass did to the geometry reads against the stage before it. Built in place of a layer's
 * normal ground while its border-tracing diagnostic is on.
 *
 * <p>{@code baseLoops} is the traced border before smoothing (the resolved inset envelope);
 * {@code despikedLoops} the same after spike sanding, and {@code roundedLoops} after corner
 * rounding. Each smoothed stage is populated only when its pass ran - the two gates
 * {@link kmu.maplayers.base.theme.BorderSmoothingStyle} carries - so an empty stage means that
 * pass was off, not that it ran and left the geometry alone. Each run is a flat
 * [x, y, x, y, ...] ring in world coordinates.
 */
public record ClusterBorderStageOverlay(
    List<float[]> baseLoops,
    List<float[]> despikedLoops,
    List<float[]> roundedLoops) {

    // True when no stage has any loop, so a renderer can skip the GL state push entirely.
    public boolean isEmpty() {
        return baseLoops.isEmpty()
            && despikedLoops.isEmpty()
            && roundedLoops.isEmpty();
    }
}
