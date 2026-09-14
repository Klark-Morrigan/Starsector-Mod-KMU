package kmu.maplayers.base.labels.anchor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
 */
public final class StandingClusterAnchors {

    // The placements themselves, in the cluster order the fit produced them in. Held rather
    // than handed back and forth because the renderer reads this same list every frame.
    private final List<ClusterAnchor> anchors = new ArrayList<>();

    // What readers are given in place of the list above, so the pair can only move through
    // replaceAnchors and cannot drift by someone editing the list a getter handed them.
    private final List<ClusterAnchor> readableAnchors = Collections.unmodifiableList(anchors);

    // The rules the placements above were fitted under. Null only before the first fit of a
    // session and after a discard, which is the reading that offers nothing for reuse.
    private AnchorFitFingerprint fitFingerprint;

    /** @return the standing placements in cluster order, read-only */
    public List<ClusterAnchor> getAnchors() {
        return readableAnchors;
    }

    /** @return what the standing placements were fitted under, or null when none were */
    public AnchorFitFingerprint getFitFingerprint() {
        return fitFingerprint;
    }

    /**
     * Drops the placements and what they were fitted under, returning this to the never-fitted
     * state a session starts in. Called when the sector behind the placements is gone: a record
     * of what the previous sector's labels were fitted under must not outlive the labels.
     */
    public void discardAnchors() {
        anchors.clear();
        fitFingerprint = null;
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

        anchors.clear();
        anchors.addAll(fittedAnchors);
        fitFingerprint = fittedUnder;
    }
}
