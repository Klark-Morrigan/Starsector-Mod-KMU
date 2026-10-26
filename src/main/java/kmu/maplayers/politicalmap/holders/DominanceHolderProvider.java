package kmu.maplayers.politicalmap.holders;

import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import java.util.Optional;

/**
 * The held-dominance holding source: each inhabited system's dominant holder from the live
 * economy, or - while a bloc is spotlighted - the filter's presence-aware holders that keep the
 * selected bloc drawn wherever it owns a market.
 *
 * <p>Stateless and shared as one instance, the way the views it serves are. The faction and
 * alliance views resolve under {@link ClaimAugmentedHolderProvider}, which layers claims on top of
 * this one; nothing resolves through this one bare.
 */
public final class DominanceHolderProvider
        implements HolderProvider {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final DominanceHolderProvider INSTANCE = new DominanceHolderProvider();

    private DominanceHolderProvider() {
    }

    @Override
    public HolderResolution resolveHolder(HolderPass pass, String selectedBlocId) {
        return resolveHolderUnder(pass, BlocSpotlight.openOver(pass, selectedBlocId));
    }

    /**
     * The same resolve under a spotlight its caller already opened, for a caller sharing that
     * spotlight with a second resolve over the pass.
     *
     * @param pass      the rebuild's reading of the sector; a pass over no sector yields empty
     *                  holding
     * @param spotlight the spotlight opened over that pass; empty off filter
     * @return the holding per system and the systems drawn hatched
     */
    public HolderResolution resolveHolderUnder(HolderPass pass, Optional<BlocSpotlight> spotlight) {

        // A spotlighted bloc switches to the presence-aware resolver, which keeps the selected
        // bloc drawn everywhere it owns a market - solid where it wins, contested where a rival
        // does - instead of collapsing every system to its lone winner. Off filter each system
        // resolves to its single dominant holder and nothing is contested.
        return spotlight
            .map(openedSpotlight -> FilteredPolitics.resolveFilteredHolder(pass, openedSpotlight))
            .orElseGet(() -> new HolderResolution(
                SectorPolitics.resolveDominantHolderBySystemKey(pass),
                SystemFillStates.ALL_SOLID));
    }
}
