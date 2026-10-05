package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.ui.font.FontAtlas;

import kmu.maplayers.base.labels.anchor.specifications.LabelAnchorSpecification;

/**
 * What a pass of label placements was fitted under: the tuning the search ran with, the revision the
 * cell geometry stood at while it ran, and the face every name was measured in. Two passes were made
 * under the same rules exactly when their fingerprints are equal, which is the question a standing
 * placement list has to answer before any of it can be trusted against a later rebuild.
 *
 * <p>These three travel together because each invalidates every placement at once rather than any
 * cluster's in particular, so none can be answered by looking at a cluster. A tuning change re-aims the
 * whole search; the keep-out sites the boxes are trimmed clear of are the whole sector's, so a system
 * appearing anywhere moves every fit even where no membership changed; and a box sized to one face's
 * glyphs is the wrong size for another's. None shows up in a cluster's own {@link ClusterIdentity},
 * which is why the three values are what a reuse decision needs: the identity says a placement still
 * names its cluster, this says it was sized under rules that still apply.
 *
 * <p>The face rides here too because everything after the fit - the minted names, the room their lines
 * are kept clear in - has to use the face the boxes were sized in, and reading it off the placements'
 * own record is what makes that hold by construction rather than by every caller passing the same face.
 *
 * <p>The geometry is named by revision rather than by its contents: the revision is what the refresh
 * pipeline already advances when the cells are recut, so the comparison costs one int rather than a
 * walk over the partition. Opaque here - nothing reads it but equality.
 *
 * @param specification    the whole tuning surface the search ran under
 * @param geometryRevision the revision the cells and their keep-out sites stood at
 * @param labelFace        the face every name was measured in, and so the face it is drawn in
 */
public record AnchorFitFingerprint(
    LabelAnchorSpecification specification,
    int geometryRevision,
    FontAtlas labelFace) {
}
