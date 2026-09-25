package kmu.maplayers.ownermap.owners.holders;

import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.owners.SystemOwner;

import java.util.Map;
import java.util.Set;

/**
 * The holding a single build resolves to drive the whole render pass: who paints each
 * star system, and which of those owned systems draw as a fill exception - hatched, or with
 * no fill at all - rather than solid inside their bloc's one border.
 *
 * <p>The one value the pipeline reads holding through, so the builder consumes this and
 * never names the concrete source that produced it - the same way it consumes the view's
 * grouping without knowing how the view derived it. That decoupling is what lets one view
 * resolve holding from the live economy while another resolves it some other way, with no
 * fork in the pass that shapes, borders, and labels the result.
 *
 * <p>The two exception sets ride inside this one value rather than the builder resolving them
 * through a second named source, because they are co-produced with the holder map in the one
 * pass that resolves it: a system's holder and how its fill is drawn are decided together.
 *
 * @param ownerBySystemKey the bloc painting each owned system, keyed by {@link SystemKey}; a
 *                        system with no holder is absent, so it draws as an uninhabited cell
 * @param contestedSystemKeys the owned systems drawn hatched rather than solid - under a filter,
 *                        the systems the spotlit bloc is present in while another bloc holds
 *                        them; empty when the whole resolution fills solid
 * @param unfilledSystemKeys the owned systems drawn with no fill rather than solid - held by
 *                        their bloc for border and label, but painting nothing inside its one
 *                        frontier; empty when the whole resolution fills solid
 */
public record HolderResolution(
    Map<SystemKey, SystemOwner> ownerBySystemKey,
    Set<SystemKey> contestedSystemKeys,
    Set<SystemKey> unfilledSystemKeys) {
}
