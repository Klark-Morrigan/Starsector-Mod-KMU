package kmu.maplayers.ownermap.render.hover;

import kmlib.cache.memo.IdentityMemo;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.hover.HoverHighlightSource;
import kmu.maplayers.base.render.clusters.StyledClusterGroup;
import kmu.maplayers.base.theme.ElementPaintSelection;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusters;
import kmu.maplayers.ownermap.render.style.MapPalettes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An owner map's answers about the cell under the cursor: read straight off the draw
 * lists this frame painted, so a highlight can only ever trace what the map is showing.
 *
 * <p>Both derived answers turn on who holds the hovered system. The loops it might sit inside
 * are its owner's traced borders, which carry one ring per disjoint cluster and per enclave;
 * and the shade is the owner's own palette, or the shared neutral colour where nothing owns
 * the cell. The cell's painted extent is not derived at all - the frame's shapes pass
 * straight through, which is how the halo and the cursor read agree by construction rather
 * than by two lookups happening to land alike.
 *
 * <p>The border loops are the owner's rather than the hovered cluster's because the build bakes
 * them per owner - which of them encloses this particular cell is a geometric question, and one
 * the highlight pass answers for itself.
 *
 * <p>A caller keeps one source per build and makes a new one when a rebuild replaces the draw
 * lists. The source retains the loops it last flattened, and the highlight memoises its resolved
 * halo on the identity of that list, so a source made afresh per frame would re-clip the wash
 * sixty times a second while the cursor sits still.
 */
public final class OwnerMapHoverHighlightSource implements HoverHighlightSource {

    // The draw lists the frame painted, which every answer here is read from.
    private final OwnerMapClusters clusters;

    // Each owner's loops as last flattened, keyed on that owner's cluster group. A rebuild or an
    // incremental refresh replaces a group wholesale rather than editing it, so a group that still
    // matches is geometry that has not moved. One memo per owner rather than one in all, because a
    // previewed set asks after the owners of all its cells every frame, and a single entry would
    // evict one owner's loops for the next and miss on every cell. The source lives for one build,
    // so the map holds at most that build's owners.
    private final Map<String, IdentityMemo<StyledClusterGroup, List<float[]>>> candidateLoopsMemoByOwnerId =
        new HashMap<>();

    /**
     * @param clusters the draw lists the frame painted
     */
    public OwnerMapHoverHighlightSource(OwnerMapClusters clusters) {
        this.clusters = clusters;
    }

    @Override
    public Map<SystemKey, List<double[]>> getFillPolygonByCellKey() {
        // Handed over as the build holds them, not copied: the highlight memoises on these
        // instances, and they are the same ones the cursor was hit-tested against.
        return clusters.getFillPolygonByCellKey();
    }

    @Override
    public List<float[]> resolveCandidateFrontierLoopsOf(SystemKey cellKey) {

        // An unowned cell (settled, or uninhabited while its outline is drawn) fuses into
        // no cluster group, so it has no frontier at all and offers no candidates - its cell still
        // washes, just without a halo.
        var owner = clusters.getOccupancy().readOwnerOf(cellKey);
        if (owner == null) {
            return List.of();
        }

        var clusterGroup = clusters
            .getStyledClusterGroupByOwnerId()
            .get(owner.ownerId());

        if (clusterGroup == null) {
            return List.of();
        }

        // Every loop the owner strokes anywhere, across all of its bodies. Handing over only the
        // body this cell sits in would mean deciding here which body that is - the containment
        // question the highlight already answers to pick its loop, and answering it twice by two
        // rules is how a halo comes to trace a frontier the cursor is not inside.
        // The memo is made through a lambda that captures nothing, so a frame that finds it
        // allocates nothing.
        return candidateLoopsMemoByOwnerId
            .computeIfAbsent(owner.ownerId(), ownerId -> new IdentityMemo<>())
            .resolveValue(clusterGroup, OwnerMapHoverHighlightSource::flattenBorderLoopsOf);
    }

    @Override
    public Color resolveHighlightColourOf(SystemKey cellKey, ElementPaintSelection paintSelection) {
        // The owner off the live occupancy; the neutral off the scheme the build painted in, so
        // the halo over an unowned cell is the shade its own outline drew in.
        return MapPalettes.pickOwnerPaletteColour(
            paintSelection,
            clusters.getOccupancy().readOwnerOf(cellKey),
            clusters.getBuildInputs().getStyling().readNeutralColour());
    }

    // The group's loops, outer rings and enclaves alike, in cluster order.
    private static List<float[]> flattenBorderLoopsOf(StyledClusterGroup clusterGroup) {

        var loops = new ArrayList<float[]>();

        for (var cluster : clusterGroup.clusters()) {
            loops.addAll(cluster.listLoops());
        }

        return loops;
    }
}
