package kmu.maplayers.base.render.clusters;

import kmlib.math.geometry.RingRegion;
import kmlib.opengl.GlVertexRuns;
import kmlib.opengl.PolygonTessellator;
import kmlib.opengl.hatch.Hatching;

import kmu.maplayers.base.render.clusters.SplitFillBuilder.ClusterFill;
import kmu.maplayers.base.theme.HatchStyle;

import java.util.List;

/**
 * One owner's fill, resolved as far as it can be before any one of its bodies is named, and able
 * to cut that body's fill on demand.
 *
 * <p>Sealed over the three ways an owner fills, because what each needs to know differs and only
 * one of them traces anything. {@link Unpainted} carries no geometry; {@link WholeCluster} needs
 * none, since a body filling solid fills from the loops it is asked about; {@link PerFillState}
 * carries the state rings traced once across the whole holding, which each body then clips
 * against.
 *
 * <p>Resolved ahead of the bodies rather than per body for two reasons. The trace only the third
 * form pays for happens once however many bodies the owner holds. And a body's fill is cut where
 * that body is named, so no caller can pair a body with another body's fill - which handing back
 * a list of fills to be walked alongside a list of bodies leaves open, and which nothing about the
 * drawn frame would reveal.
 */
public sealed interface TracedFill {

    /** An owner that paints no fill at all, so every body it holds fills with nothing. */
    TracedFill UNPAINTED = new Unpainted();

    /** An owner filling solid throughout, so every body fills from its own loops. */
    TracedFill WHOLE_CLUSTER = new WholeCluster();

    /**
     * Cuts one body's fill.
     *
     * @param clusterRegion the body's smoothed loops - its outer ring plus the enclaves cut out
     *                      of it
     * @return that body's solid triangles and hatch segments
     */
    ClusterFill buildFillFor(RingRegion clusterRegion);

    /**
     * The fill of an owner whose fill colour is "No color": nothing is tessellated for any body,
     * rather than triangles the draw pass would then skip.
     */
    record Unpainted() implements TracedFill {

        @Override
        public ClusterFill buildFillFor(RingRegion clusterRegion) {
            return new ClusterFill(
                GlVertexRuns.NO_VERTICES,
                GlVertexRuns.NO_VERTICES);
        }
    }

    /**
     * The fill of an owner holding every member solid - the common case, and the one that pays
     * nothing for the split machinery.
     *
     * <p>Each body fills from the same smoothed loops its own boundary strokes, so fill and
     * boundary stop in the same place by construction rather than by two derivations agreeing.
     */
    record WholeCluster() implements TracedFill {

        @Override
        public ClusterFill buildFillFor(RingRegion clusterRegion) {
            return new ClusterFill(
                PolygonTessellator.tessellateToTriangles(clusterRegion.toRings()),
                GlVertexRuns.NO_VERTICES);
        }
    }

    /**
     * The fill of an owner whose members do not all fill solid: the solid and hatched states
     * traced as their own clusters across the whole holding, cut to a body when one is named.
     *
     * <p>The unfilled state is deliberately absent - it holds its place for a boundary and a label
     * and paints nothing, so there is no geometry to carry for it.
     *
     * @param solidRings        the solid state's traced rings, empty when no member is solid
     * @param hatchedRings      the hatched state's traced rings, empty when no member is hatched
     * @param hatch             the sector-wide hatch geometry the hatched area is cut with
     * @param hatchRunObserver  what the caller wants read off each body's hatch as it is baked;
     *                          {@link HatchRunObserver#IGNORED} for a build nobody is reading
     */
    record PerFillState(
        List<List<double[]>> solidRings,
        List<List<double[]>> hatchedRings,
        HatchStyle hatch,
        HatchRunObserver hatchRunObserver) implements TracedFill {

        @Override
        public ClusterFill buildFillFor(RingRegion clusterRegion) {
            var clusterRings = clusterRegion.toRings();

            // The hatched fill is tessellated before the clock starts, so what is timed is the
            // clip and merge alone - the part a joining or a tolerance changes. Tessellating costs
            // the same either way, and is much the larger of the two.
            var hatchedTriangles = clipToCluster(hatchedRings, clusterRings);

            var cutStart = System.nanoTime();
            var hatchRun = Hatching.computeHatchRun(
                hatchedTriangles,
                hatch.angleRadians(),
                hatch.spacing(),
                hatch.joinToleranceFraction());
            var elapsedNanos = System.nanoTime() - cutStart;

            // Offered only where there is a hatch to offer. A body of a splitting owner that
            // holds no hatched member cuts nothing, and an observer counting bodies would find
            // those indistinguishable from ones whose hatch came out empty for a reason.
            if (hatchRun.segments().length > 0) {
                hatchRunObserver.observeHatchRun(new TimedHatchRun(hatchRun, elapsedNanos));
            }
            return new ClusterFill(
                clipToCluster(solidRings, clusterRings),
                hatchRun.segments());
        }

        // The part of one state's traced rings falling inside one body, as a triangle soup. The
        // clip does double duty: it confines the state to this body, and it clamps the state's
        // outer edge onto the exact line the boundary strokes, since the traced rings still carry
        // the mitered corners the smoothing rounded off. The states' shared seam is interior to
        // both operands, so it survives the clip untouched and they still meet exactly along it.
        private static float[] clipToCluster(
                List<List<double[]>> stateRings,
                List<List<double[]>> clusterRings) {

            if (stateRings.isEmpty()) {
                return GlVertexRuns.NO_VERTICES;
            }
            return PolygonTessellator.tessellateIntersectionToTriangles(stateRings, clusterRings);
        }
    }
}
