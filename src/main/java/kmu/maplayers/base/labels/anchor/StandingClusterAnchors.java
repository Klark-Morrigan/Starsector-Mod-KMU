package kmu.maplayers.base.labels.anchor;

import java.util.List;
import java.util.function.Supplier;

/**
 * The cluster-label placements a map layer currently stands at, together with the
 * {@link AnchorFitFingerprint} they were fitted under.
 *
 * <p>The two are one fact rather than two values kept beside each other. A placement is reusable
 * only as a pair with the rules it was sized under - a list labelled with anything but its own
 * fit describes nothing, and a fit labelling a list it did not produce is worse than no label at
 * all, since it reads as permission to carry placements over. Holding them in one value makes
 * writing both together the only thing a caller can do, where two fields made it something to
 * remember on every path that touches either.
 *
 * <p>Mutable and updated in place, unlike the records around it: this is the one thing in the
 * placement subsystem that survives a rebuild rather than being replaced by it, so a rebuild
 * hands the standing pair in, reads the previous pass off it, and leaves its own in the same
 * object. That ordering is why the previous pass is safe to read after a rebuild has started -
 * nothing empties the list ahead of the fit that replaces it.
 *
 * <p>The room the placements' drawn lines take up is kept here too, once measured. It is a reading
 * of one pass, so it goes when the pass is replaced, and holding it beside the pass is what makes
 * that happen without any caller remembering to drop it.
 */
public final class StandingClusterAnchors {

    // The placements themselves, in the cluster order the fit produced them in. A fresh immutable
    // list per pass rather than one list edited in place, so the pair can only move through
    // replaceAnchors, and a reader holding work derived from one pass can tell by identity whether
    // that pass still stands.
    private List<ClusterAnchor> anchors = List.of();

    // The rules the placements above were fitted under. Null only before the first fit of a
    // session and after a discard, which is the reading that offers nothing for reuse.
    private AnchorFitFingerprint fitFingerprint;

    // The room the placements' drawn lines take up, measured on the first ask after a pass. Null
    // until then: every bake asks for it, and an incremental one re-lays a handful of cells against
    // names that did not move, so measuring it per bake re-measures every name on the map.
    private List<List<double[]>> lineBoxes;

    /**
     * @return the standing placements in cluster order, read-only, and a different list after
     *         every pass
     */
    public List<ClusterAnchor> getAnchors() {
        return anchors;
    }

    /** @return what the standing placements were fitted under, or null when none were */
    public AnchorFitFingerprint getFitFingerprint() {
        return fitFingerprint;
    }

    /**
     * The room the standing names' drawn lines take up, measured on the first ask after a pass and
     * kept until the next pass replaces the placements it was measured off.
     *
     * <p>The measurement is handed in because it sits above this package: it reads the label plan
     * and the face's metrics, and the placements are what both of those are built from.
     *
     * @param lineBoxMeasurement measures the drawn lines of the placements standing now; asked only
     *                           when nothing has been measured since the last pass
     * @return one closed ring of {x, y} corners per drawn line
     */
    public List<List<double[]>> computeLineBoxesIfAbsent(
            Supplier<List<List<double[]>>> lineBoxMeasurement) {

        if (lineBoxes == null) {
            lineBoxes = lineBoxMeasurement.get();
        }

        return lineBoxes;
    }

    /**
     * Drops the placements and what they were fitted under, returning this to the never-fitted
     * state a session starts in. Called when the sector behind the placements is gone: a record
     * of what the previous sector's labels were fitted under must not outlive the labels.
     */
    public void discardAnchors() {

        anchors = List.of();
        fitFingerprint = null;
        lineBoxes = null;
    }

    /**
     * Takes the placements a pass produced together with the rules it produced them under. The
     * pass that fitted nothing calls this too, with an empty list: a list nothing states the
     * rules of is indistinguishable from one still made under rules that hold, so the label
     * travels even where there is nothing to label.
     *
     * @param fittedAnchors the pass's placements, copied in so the pair owns its own list
     * @param fittedUnder   the tuning and geometry that pass ran under
     */
    public void replaceAnchors(
            List<ClusterAnchor> fittedAnchors,
            AnchorFitFingerprint fittedUnder) {

        anchors = List.copyOf(fittedAnchors);
        fitFingerprint = fittedUnder;
        lineBoxes = null;
    }
}
