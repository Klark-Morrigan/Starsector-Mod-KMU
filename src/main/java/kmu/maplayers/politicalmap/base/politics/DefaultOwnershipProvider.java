package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;

import java.util.Set;

/**
 * The ownership source the faction and alliance views resolve under: each inhabited system's
 * dominant owner from the live economy, or - while a bloc is spotlighted - the filter's
 * presence-aware ownership that keeps the selected bloc drawn wherever it owns a market.
 *
 * <p>Stateless and shared as one instance, the way the views it serves are. It is the identity
 * ownership source the pipeline was carved out of, so a view that resolves ownership no
 * differently inherits it and only a view that paints something else supplies its own.
 */
public final class DefaultOwnershipProvider implements OwnershipProvider {

    /** The one shared instance; stateless, so every pass reuses it. */
    public static final DefaultOwnershipProvider INSTANCE = new DefaultOwnershipProvider();

    private DefaultOwnershipProvider() {
    }

    @Override
    public OwnershipResolution resolveOwnership(
            SectorAPI sector, OwnershipGrouping grouping, String selectedBlocId) {
        // A spotlighted bloc switches to the presence-aware resolver, which keeps the selected
        // bloc drawn everywhere it owns a market - solid where it wins, contested where a rival
        // does - instead of collapsing every system to its lone winner. Off filter each system
        // resolves to its single dominant owner and nothing is contested.
        if (selectedBlocId != null) {
            var filtered =
                    FilteredPolitics.resolveFilteredOwnership(sector, grouping, selectedBlocId);
            return new OwnershipResolution(
                    filtered.ownerBySystemId(), filtered.contestedSystemIds());
        }
        return new OwnershipResolution(
                SectorPolitics.resolveDominantOwnerBySystemId(sector, grouping), Set.of());
    }
}
