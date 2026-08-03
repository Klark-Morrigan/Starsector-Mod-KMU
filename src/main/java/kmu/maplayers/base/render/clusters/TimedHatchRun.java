package kmu.maplayers.base.render.clusters;

import kmlib.opengl.hatch.HatchRun;

/**
 * One body's hatch as it came back from the clip, with what cutting it cost.
 *
 * <p>The two travel together because the cost is only interpretable against the run it produced:
 * the same elapsed time means one thing over eight hundred segments and another over eight, and a
 * duration arriving on its own could not be attributed to either.
 *
 * <p>Measured around the clip and merge alone, not around the tessellation that hands them their
 * ground. That tessellation costs the same whichever joining is in force, so including it would
 * add a constant to both sides of every comparison the timing exists to support - and, being much
 * the larger of the two, would swamp the difference being looked for.
 *
 * @param hatchRun     the segments cut for this body, and how its joins closed
 * @param elapsedNanos how long the cut took, from {@link System#nanoTime()}
 */
public record TimedHatchRun(HatchRun hatchRun, long elapsedNanos) {
}
