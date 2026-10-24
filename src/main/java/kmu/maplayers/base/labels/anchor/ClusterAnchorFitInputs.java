package kmu.maplayers.base.labels.anchor;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.ui.font.FontAtlas;

import java.util.Map;
import java.util.function.Function;

/**
 * Everything a layer hands the cluster-name fit: which systems cluster together, the face every
 * name is measured in, whether a fit is wanted at all, and how a cluster's name reads and shades.
 *
 * <p>The layer's whole half of a fit. The fit drive needs nothing else from it, so any layer that
 * partitions the sector into clusters fits names the same way, whatever its key means.
 *
 * <p>Held as one value because the parts have to describe one moment: a fit clustered under one
 * reading of the keys and named under another would put a name over a cluster it does not belong
 * to, and that would show as a wrong word rather than as an error.
 *
 * <p>The demand is one answer rather than one per thing a layer draws off the placements, because
 * the drive only ever asks whether to fit. Which of its drawings want the placements is the layer's
 * vocabulary.
 *
 * <p>The resolvers arrive as a function of the face rather than resolved, for two reasons.
 * Resolving them loads the label font and opens a memo per owner, which a pass that wants no fit
 * must not pay for. And the face is the drive's to hand over: it measures every name in the face its
 * fingerprint records, so the names and the record of what they were measured in cannot come from
 * two readings.
 *
 * @param ownerKeyBySystemKey  the opaque key each drawn system is clustered by; a system absent from
 *                             it clusters with nobody
 * @param labelFace            the face the sector settled its labels on, which the fit measures
 *                             every name in and records beside its placements
 * @param isFitWanted          whether anything the layer draws reads the placements; false empties
 *                             the list without a search
 * @param labelResolversByFace the shade and name measurement of each owner key, measured in the face
 *                             it is handed; resolved once per fit and only when one runs
 */
public record ClusterAnchorFitInputs(
    Map<SystemKey, String> ownerKeyBySystemKey,
    FontAtlas labelFace,
    boolean isFitWanted,
    Function<FontAtlas, ClusterLabelResolvers> labelResolversByFace) {
}
