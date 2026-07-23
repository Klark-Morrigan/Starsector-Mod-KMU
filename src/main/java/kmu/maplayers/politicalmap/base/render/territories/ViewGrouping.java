package kmu.maplayers.politicalmap.base.render.territories;

import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;

/**
 * The view one build painted and the grouping snapshot it resolved ownership under, held
 * together so an incremental re-shape classifies a cell against the same view and the same
 * once-sampled grouping the full build did - the grouping is sampled once (the alliances view
 * reads Nexerelin) so every stage keys off one snapshot.
 */
public record ViewGrouping(
        PoliticalMapView view,
        OwnershipGrouping grouping) {
}
