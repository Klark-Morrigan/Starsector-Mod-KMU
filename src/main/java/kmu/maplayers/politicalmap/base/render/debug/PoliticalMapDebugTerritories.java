package kmu.maplayers.politicalmap.base.render.debug;

import java.util.List;

/**
 * The debug border-tracing overlay's draw lists: each smoothing stage's border loops as
 * flattened GL_LINE_LOOP runs, kept apart so the renderer can color and layer them. Built
 * in place of the normal
 * {@link kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritories} while the
 * debug toggle is on.
 *
 * <p>{@code baseLoops} is the traced border before smoothing (the resolved inset
 * envelope); {@code despikedLoops} the same after spike sanding, and {@code roundedLoops}
 * after corner rounding. Each smoothed stage is populated only when its pass ran - the two
 * smoothing gates - so an empty stage means that pass was off and the renderer skips it.
 * Each run is a flat [x, y, x, y, ...] ring in world coordinates.
 */
public record PoliticalMapDebugTerritories(List<float[]> baseLoops, List<float[]> despikedLoops,
        List<float[]> roundedLoops) {
    // True when no stage has any loop, so the renderer can skip the GL state push entirely.
    public boolean isEmpty() {
        return baseLoops.isEmpty() && despikedLoops.isEmpty() && roundedLoops.isEmpty();
    }
}
