package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.FontAtlas;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Everything a layer hands the cluster-name fit: which systems cluster together, the face every
 * name is measured in, which of the fit's two consumers are asking for it, and how a cluster's name
 * reads and shades.
 *
 * <p>The layer's whole half of a fit. The fit drive needs nothing else from it, so any layer that
 * partitions the sector into clusters fits names the same way, whatever its key means.
 *
 * <p>Held as one value because the parts have to describe one moment: a fit clustered under one
 * reading of the keys and named under another would put a name over a cluster it does not belong
 * to, and that would show as a wrong word rather than as an error.
 *
 * <p>The resolvers arrive as a supplier rather than resolved, because resolving them loads the
 * label font and opens a memo per owner, and a pass whose consumers are both switched off fits
 * nothing and must pay for neither.
 *
 * @param ownerKeyBySystemKey  the opaque key each drawn system is clustered by; a system absent from
 *                             it clusters with nobody
 * @param labelFace            the face the sector settled its labels on, which the fit measures
 *                             every name in and records beside its placements
 * @param areNamesDrawn        whether the names themselves are drawn, one of the two consumers a fit
 *                             is run for
 * @param isAnchorOverlayShown whether the diagnostic anchor overlay is shown, the other consumer
 * @param labelResolversSource the shade and name measurement of each owner key, resolved once per
 *                             fit and only when one runs
 */
public record ClusterAnchorFitInputs(
    Map<SystemKey, String> ownerKeyBySystemKey,
    FontAtlas labelFace,
    boolean areNamesDrawn,
    boolean isAnchorOverlayShown,
    Supplier<ClusterLabelResolvers> labelResolversSource) {
}
