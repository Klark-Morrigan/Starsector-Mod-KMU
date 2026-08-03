package kmu.maplayers.base.render.clusters;

import kmlib.opengl.hatch.HatchRun;

/**
 * What a caller wants extracted from each hatch run as it is baked - handed to the fill builder
 * rather than decided inside it, so building the geometry and reading something off it stay
 * separate jobs.
 *
 * <p>The build has the runs and no opinion about them; whoever asked for the build has the opinion
 * and no other chance to see them, since a run is packed into a draw record that keeps only its
 * segments. Inverting it this way is what keeps the builder free of the question - it neither
 * formats, nor decides which readings are meaningful under the joining in force, nor holds a
 * logger to put them anywhere.
 *
 * <p>Called once per body, during the rebuild that bakes it, so an implementation is on a
 * build-time path and not a per-frame one. It must not retain the run: the segments it carries
 * go on to be drawn.
 */
@FunctionalInterface
public interface HatchRunObserver {

    /** Extracts nothing, for a build no one is reading - which is every build but a diagnostic. */
    HatchRunObserver IGNORED = hatchRun -> { };

    /**
     * Offers one body's freshly baked hatch.
     *
     * @param hatchRun the segments cut for this body, and how the joining that packed them closed
     *                 its joins
     */
    void observeHatchRun(HatchRun hatchRun);
}
