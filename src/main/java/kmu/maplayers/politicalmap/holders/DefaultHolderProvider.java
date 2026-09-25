package kmu.maplayers.politicalmap.holders;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.holders.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.HolderResolution;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import java.util.Set;

/**
 * The held-dominance holding source: each inhabited system's dominant holder from the live
 * economy, or - while a bloc is spotlighted - the filter's presence-aware holders that keep the
 * selected bloc drawn wherever it owns a market.
 *
 * <p>Stateless and shared as one instance, the way the views it serves are. The faction and
 * alliance views resolve under {@link ClaimAugmentedHolderProvider}, which layers claims on top of
 * this one; the layer also hands this one to the tier bare, as the holding its diagnostic overlays
 * read.
 */
public final class DefaultHolderProvider implements HolderProvider {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final DefaultHolderProvider INSTANCE = new DefaultHolderProvider();

    private DefaultHolderProvider() {
    }

    @Override
    public HolderResolution resolveHolder(HolderPass pass, String selectedBlocId) {

        // A spotlighted bloc switches to the presence-aware resolver, which keeps the selected
        // bloc drawn everywhere it owns a market - solid where it wins, contested where a rival
        // does - instead of collapsing every system to its lone winner. Off filter each system
        // resolves to its single dominant holder and nothing is contested.
        if (selectedBlocId != null) {
            return FilteredPolitics.resolveFilteredHolder(pass, selectedBlocId);
        }
        return new HolderResolution(
            SectorPolitics.resolveDominantHolderBySystemKey(pass),
            Set.of(),
            Set.of());
    }
}
