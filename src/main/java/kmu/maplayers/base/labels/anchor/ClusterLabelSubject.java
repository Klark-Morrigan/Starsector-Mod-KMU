package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.ui.label.LabelLengthEstimator;

import java.awt.Color;

/**
 * One cluster a label is being fitted for, together with everything about it the geometry
 * search cannot derive: which cluster it is, the shade its name and debug dot draw in, and
 * the measurement its label box is sized against.
 *
 * <p>The three travel as one value because they are one lookup taken three ways - the
 * identity's own owner is what both the shade and the name resolve from - so a signature
 * carrying them apart is one a call site can fill with a name's metrics taken from a
 * different owner than its shade.
 *
 * <p>Holding them together is also what makes restyling a standing placement a substitution
 * rather than a matter of remembering which of three parallel values to refresh: a placement
 * that still names its cluster takes this subject's shade and is re-checked against this
 * subject's wrap, with the identity held fixed by the match that found it.
 *
 * @param identity      which cluster the fit is for - its owner key and its exact members
 * @param colour        the shade the cluster's name and its debug dot draw in
 * @param nameEstimator the name measurement the cluster's label boxes are sized against
 */
public record ClusterLabelSubject(
    ClusterIdentity identity,
    Color colour,
    LabelLengthEstimator nameEstimator) {
}
