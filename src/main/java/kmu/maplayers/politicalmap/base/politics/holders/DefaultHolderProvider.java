package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;

import java.util.Set;

/**
 * The holding source the faction and alliance views resolve under: each inhabited system's
 * dominant holder from the live economy, or - while a bloc is spotlighted - the filter's
 * presence-aware holders that keeps the selected bloc drawn wherever it owns a market.
 *
 * <p>Stateless and shared as one instance, the way the views it serves are. It is the identity
 * holding source the pipeline was carved out of, so a view that resolves holding no
 * differently inherits it and only a view that paints something else supplies its own.
 */
public final class DefaultHolderProvider implements HolderProvider {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final DefaultHolderProvider INSTANCE = new DefaultHolderProvider();

    private DefaultHolderProvider() {
    }

    @Override
    public HolderResolution resolveHolder(
            SectorAPI sector, HolderGrouping grouping, String selectedBlocId) {
        // A spotlighted bloc switches to the presence-aware resolver, which keeps the selected
        // bloc drawn everywhere it owns a market - solid where it wins, contested where a rival
        // does - instead of collapsing every system to its lone winner. Off filter each system
        // resolves to its single dominant holder and nothing is contested.
        if (selectedBlocId != null) {
            var filtered =
                    FilteredPolitics.resolveFilteredHolder(sector, grouping, selectedBlocId);
            return new HolderResolution(
                    filtered.ownerBySystemId(), filtered.contestedSystemIds(), Set.of());
        }
        return new HolderResolution(
                SectorPolitics.resolveDominantHolderBySystemId(sector, grouping),
                Set.of(),
                Set.of());
    }
}
