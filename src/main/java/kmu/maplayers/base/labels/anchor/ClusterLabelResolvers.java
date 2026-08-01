package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.ui.label.LabelLengthEstimator;

import java.awt.Color;
import java.util.function.Function;

/**
 * The two things a cluster's name needs beyond geometry, each a plain function of the
 * cluster's opaque owner key: the shade the name and its debug dot draw in, and the
 * measurement the fit sizes its box against. The placement search never asks what a key
 * means, so whichever layer owns the clusters answers both.
 *
 * <p>They travel as one value because they are one lookup taken twice - the placement
 * resolves both for the same owner at the same point, so neither can be advanced without
 * the other, and a signature carrying them apart is one a call site can fill with a name's
 * metrics taken from a different owner than its shade.
 *
 * <p>Functions rather than a source interface because a layer supplies these by composing
 * whatever it already holds - a resolved holder map, a palette, a view's own naming - and
 * has no state of its own to hang an implementation on.
 */
public record ClusterLabelResolvers(
    Function<String, Color> labelColourByOwner,
    Function<String, LabelLengthEstimator> nameEstimatorByOwner) {

    /** @return the shade the owner's name and debug dot draw in. */
    public Color resolveLabelColourOf(String owner) {
        return labelColourByOwner.apply(owner);
    }

    /** @return the name measurement the owner's label boxes are sized against. */
    public LabelLengthEstimator resolveNameEstimatorOf(String owner) {
        return nameEstimatorByOwner.apply(owner);
    }
}
